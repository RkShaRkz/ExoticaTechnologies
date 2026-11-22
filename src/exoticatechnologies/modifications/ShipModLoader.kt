package exoticatechnologies.modifications

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import exoticatechnologies.modifications.dataproviders.ModuleSlotDataProvider
import exoticatechnologies.modifications.dataproviders.PersistentDataProvider
import exoticatechnologies.modifications.dataproviders.VariantTagProvider
import exoticatechnologies.modifications.dataproviders.ZigguratDataProvider
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.util.FleetMemberUtils
import exoticatechnologies.util.ModuleSlotPathsUtils
import exoticatechnologies.util.ModuleVariantHierarchy
import exoticatechnologies.util.FleetMemberUtils.findMemberFromShip
import exoticatechnologies.util.combineIntoList

class ShipModLoader {
    private var providers: List<Provider> = mutableListOf(
        VariantTagProvider.getInstance(),
        ZigguratDataProvider.inst,
        PersistentDataProvider.inst
    )

    private fun getData(member: FleetMemberAPI, variant: ShipVariantAPI = member.variant): ShipModifications? {
        return providers.firstNotNullOfOrNull { it.get(member, variant) }
    }

    /**
     * Reads one known module through providers that can distinguish slots.
     *
     * Hull ids repeat across identical sibling modules, so a tree walk must carry its slot path
     * into the provider instead of asking the provider to guess from the variant.
     */
    private fun getData(member: FleetMemberAPI, variant: ShipVariantAPI, slotPath: String): ShipModifications? {
        return providers.firstNotNullOfOrNull { provider ->
            if (provider is ModuleSlotDataProvider) {
                provider.getAtSlotPath(member, variant, slotPath)
            } else {
                provider.get(member, variant)
            }
        }
    }

    private fun saveData(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
        for (i in providers.indices) {
            val provider = providers[i]
            if (!provider.setOnlyIfFirst() || i == 0) {
                provider.set(member, variant, mods)
            }
        }
    }

    private fun removeData(member: FleetMemberAPI, variant: ShipVariantAPI) {
        providers.forEach { it.remove(member, variant) }
    }

    private fun getAllShipSections(ship: ShipAPI): List<ShipAPI> {
        // If ship is parent, apply to children
        if (ship.childModulesCopy.isNotEmpty()) {
            return combineIntoList(ship.childModulesCopy, ship)
        }

        // If ship is a submodule, get parent, and apply to all his children
        if (ship.parentStation != null) {
            val parent = ship.parentStation
            return combineIntoList(parent.childModulesCopy, parent)
        }

        // The last scenario is - it's single module ship, so just return that
        return listOf(ship)
    }

    private fun getAllDataForShipAPI(ship: ShipAPI): List<ShipModifications> {
        // First, grab all ship's sections
        val allShipSections = getAllShipSections(ship)
        // Now, map them onto variants
        val allShipSectionVariants = allShipSections.map { it.variant }
        val allShipSectionsFMAPIs = allShipSections.map { findMemberFromShip(it) }

        val allModsList = mutableListOf<ShipModifications>()
        for (index in allShipSectionVariants.indices) {
            val variant = allShipSectionVariants[index]
            val fmapi = allShipSectionsFMAPIs[index]
            fmapi?.let {member ->
                val mods = getData(member, variant)
                mods?.let { shipMods ->
                    allModsList.add(shipMods)
                }
            }
        }
        // Now we have all mods, so return them.
        return allModsList.toList()
    }

    private fun getAllDataFromStatsAPI(stats: MutableShipStatsAPI): List<ShipModifications> {
        val allModsList = mutableSetOf<ShipModifications>()

        // Collect the root FM's own mods, THEN walk the variant tree for every module variant
        // (sibling modules and child-module data live on the child variants themselves).
        val rootFM = FleetMemberUtils.findMemberForStats(stats) ?: return allModsList.toList()
        getData(rootFM, rootFM.variant, ModuleSlotPathsUtils.ROOT_SLOT_PATH)?.let { allModsList.add(it) }
        collectModuleMods(rootFM, rootFM.variant, ModuleSlotPathsUtils.ROOT_SLOT_PATH, allModsList)

        return allModsList.toList()
    }

    private fun collectModuleMods(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        slotPath: String,
        result: MutableSet<ShipModifications>
    ) {
        for (slotId in variant.stationModules.keys) {
            val childV = variant.getModuleVariant(slotId)
            if (childV != null) {
                val childSlotPath = ModuleSlotPathsUtils.childSlotPath(slotPath, slotId)
                val childMods = getData(member, childV, childSlotPath)
                if (childMods != null) {
                    result.add(childMods)
                }
                collectModuleMods(member, childV, childSlotPath, result)
            }
        }
    }

    private fun getWholeShipModsData(member: FleetMemberAPI, variant: ShipVariantAPI): List<ShipModifications> {
        val result = ArrayList<ShipModifications>(4)
        val seenModuleKeys = HashSet<String>(8)
        val readVariants = ArrayList<ShipVariantAPI>(4)
        val rootVariant = resolveRootVariant(member, variant)

        collectWholeShipMods(member, rootVariant, ModuleSlotPathsUtils.ROOT_SLOT_PATH, result, seenModuleKeys, readVariants)

        // Only the refit working graph is a second source of truth. The display graph is not read
        // here because its root belongs to the UI, not to this member's variant tree.
        val refitVariant = runCatching { member.checkRefitVariant() }.getOrNull()
        if (refitVariant != null && refitVariant !== rootVariant) {
            val refitSlotPath = findWholeShipGraphSlotPath(rootVariant, refitVariant, member.variant)
            collectWholeShipMods(member, refitVariant, refitSlotPath, result, seenModuleKeys, readVariants)
        }
        return result
    }

    // A variant that owns station modules IS the root of its tree. Prefer ship-scoped root first
    // to avoid cold hierarchy maps for child variants (especially refit clones).
    private fun resolveRootVariant(member: FleetMemberAPI, variant: ShipVariantAPI): ShipVariantAPI {
        val shipScopedRoot = FleetMemberUtils.findShipScopedRootVariant(member, variant)
        if (shipScopedRoot != null) {
            return shipScopedRoot
        }
        if (variant.stationModules.isNotEmpty()) return variant
        val rootVariantId = ModuleVariantHierarchy.findRootVariantId(variant.hullVariantId) ?: return variant
        return FleetMemberUtils.findRootVariantByHullId(member, rootVariantId) ?: variant
    }

    /**
     * Places a whole-ship refit graph so its nodes get stable paths.
     *
     * A graph that cannot be placed must still be read once under an impossible key rather than
     * falling back to hull-id matching, which could duplicate or drop a sibling module.
     */
    private fun findWholeShipGraphSlotPath(
        rootVariant: ShipVariantAPI,
        graphRoot: ShipVariantAPI,
        originalVariant: ShipVariantAPI?
    ): String {
        val placed = ModuleSlotPathsUtils.findGraphRootSlotPath(rootVariant, graphRoot)
        if (placed.isPresent()) {
            return placed.get()
        }
        if (originalVariant != null && originalVariant !== graphRoot) {
            val originalPlacement = ModuleSlotPathsUtils.findGraphRootSlotPath(rootVariant, originalVariant)
            if (originalPlacement.isPresent()) {
                return originalPlacement.get()
            }
        }
        return ModuleSlotPathsUtils.UNPLACED_INDICATOR
    }

    /**
     * Reads one variant tree by slot path.
     *
     * The slot path is part of identity so identical sibling modules stay separate, while the
     * object-identity check prevents one shared stock variant from being counted twice.
     */
    private fun collectWholeShipMods(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        slotPath: String,
        result: MutableList<ShipModifications>,
        seenModuleKeys: MutableSet<String>,
        readVariants: MutableList<ShipVariantAPI>
    ) {
        // Recursive stationModules walk. Dedupes by (member id, hullVariantId, slot path) so the same
        // logical variant (stock / REFIT clone / combat clone) is never collected twice at one
        // position — otherwise a single installation would be iterated multiple times by
        // whole-ship consumers like advanceInCampaign. The member id scopes the key so identical-root
        // twin ships (whose nodes share hullVariantIds) can never have the first ship's mods swallowed
        // by the second's walk. Includes the variant's own ShipModifications.
        if (!seenModuleKeys.add(wholeShipModKey(member, variant, slotPath))) return
        readOnce(member, variant, slotPath, result, readVariants)

        // Shared pre-order walk over every station-module descendant (ModuleSlotPathsUtils).
        // The lambda captures only locals (member, result, seenModuleKeys, readVariants) and is a
        // no-op append per visited node — stateless, short-lived, nothing allocated per iteration.
        ModuleSlotPathsUtils.forEachModuleVariantWithSlotPath(variant, slotPath) { childV, childSlotPath ->
            if (seenModuleKeys.add(wholeShipModKey(member, childV, childSlotPath))) {
                readOnce(member, childV, childSlotPath, result, readVariants)
            }
        }
    }

    /**
     * Reads a variant once even when the engine shares that object between module slots.
     *
     * Shared objects have one physical set of tags, so adding their mods twice would inflate
     * whole-ship consumers.
     */
    private fun readOnce(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        slotPath: String,
        result: MutableList<ShipModifications>,
        readVariants: MutableList<ShipVariantAPI>
    ) {
        if (readVariants.any { it === variant }) {
            return
        }
        readVariants.add(variant)
        val mods = getData(member, variant, slotPath)
        if (mods != null) {
            result.add(mods)
        }
    }

    /**
     * Builds a slot-aware identity for a whole-ship walk.
     *
     * The member id scopes twin ships, the hull id scopes object copies, and the slot path scopes
     * identical sibling modules, which hull ids cannot distinguish.
     */
    private fun wholeShipModKey(member: FleetMemberAPI, variant: ShipVariantAPI, slotPath: String): String {
        // Collision-safe composite key: scoping the hullVariantId by the acting member's stable id
        // and by slot path costs a couple of string concats per node and guarantees identical-root
        // twins and identical sibling modules never dedupe into each other's mods.
        return member.id + ":" + variant.hullVariantId + ":" + slotPath
    }

    companion object {
        private val inst = ShipModLoader()

        @JvmStatic
        @Synchronized
        fun get(member: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications? {
            return inst.getData(member, variant)
        }

        @JvmStatic
        @Synchronized
        fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
            return inst.saveData(member, variant, mods)
        }

        @JvmStatic
        @Synchronized
        fun remove(member: FleetMemberAPI, variant: ShipVariantAPI) {
            return inst.removeData(member, variant)
        }

        @JvmStatic
        @Synchronized
        fun getForSpecialData(shipData: ShipRecoverySpecial.PerShipData): ShipModifications? {
            if (shipData.getVariant() != null) {
                val mods = VariantTagProvider.getInstance().getFromVariant(shipData.getVariant())
                if (mods != null) {
                    return mods
                }
            }

            if (shipData.fleetMemberId != null) {
                val mods = PersistentDataProvider.inst.getFromId(shipData.fleetMemberId)
                if (mods != null) {
                    return mods
                }
            }

            return null
        }

        @JvmStatic
        @Synchronized
        fun getFromVariant(variant: ShipVariantAPI): ShipModifications? {
            return VariantTagProvider.getInstance().getFromVariant(variant)
        }

        @JvmStatic
        @Synchronized
        fun getAllForShipAPI(ship: ShipAPI): List<ShipModifications> {
            return inst.getAllDataForShipAPI(ship)
        }

        @JvmStatic
        @Synchronized
        fun getAllForStats(stats: MutableShipStatsAPI): List<ShipModifications> {
            return inst.getAllDataFromStatsAPI(stats).distinct()
        }

        /**
         * All ShipModifications present anywhere on the ship's variant tree (root + station module
         * children), deduped by (member id, hullVariantId, slot path). Used by the hullmod
         * install/uninstall decision and by campaign-layer effects so child-owned modifications
         * are never missed and identical sibling modules cannot be conflated.
         */
        @JvmStatic
        @Synchronized
        fun getWholeShipMods(member: FleetMemberAPI, variant: ShipVariantAPI): List<ShipModifications> {
            return inst.getWholeShipModsData(member, variant)
        }
    }

    interface Provider {
        fun get(member: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications?
        fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications)
        fun remove(member: FleetMemberAPI, variant: ShipVariantAPI)

        fun setOnlyIfFirst(): Boolean {
            return true
        }
    }
}
