package exoticatechnologies.modifications

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.util.FleetMemberHierarchy
import exoticatechnologies.util.FleetMemberUtils
import exoticatechnologies.util.FleetMemberUtils.findMemberFromShip
import exoticatechnologies.util.combineIntoList
import exoticatechnologies.util.forEachModuleVariant

class ShipModLoader {
    private var providers: List<Provider> = mutableListOf(
        VariantTagProvider.getInstance(),
        ZigguratDataProvider.inst,
        PersistentDataProvider.inst
    )

    private fun getData(member: FleetMemberAPI, variant: ShipVariantAPI = member.variant): ShipModifications? {
        return providers.firstNotNullOfOrNull { it.get(member, variant) }
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
        get(rootFM, rootFM.variant)?.let { allModsList.add(it) }
        collectModuleMods(rootFM, rootFM.variant, allModsList)

        return allModsList.toList()
    }

    private fun collectModuleMods(member: FleetMemberAPI, variant: ShipVariantAPI, result: MutableSet<ShipModifications>) {
        for (slotId in variant.stationModules.keys) {
            val childV = variant.getModuleVariant(slotId) ?: continue
            val childMods = get(member, childV)
            childMods?.let {
                result.add(it)
            }
            collectModuleMods(member, childV, result)
        }
    }

    private fun getWholeShipModsData(member: FleetMemberAPI, variant: ShipVariantAPI): List<ShipModifications> {
        val result = ArrayList<ShipModifications>(4)
        val seenVariantIds = HashSet<String>(8)
        val rootVariant = resolveRootVariant(member, variant)
        collectWholeShipMods(member, rootVariant, result, seenVariantIds)
        // The REFIT display variant tree is a second object graph carrying the same tags
        // (refit clones preserve child tags 1:1). Union it in so a read never depends on
        // which variant instance the engine happened to hand us.
        val refitVariant = runCatching { member.checkRefitVariant() }.getOrNull()
        if (refitVariant != null && refitVariant !== rootVariant) {
            collectWholeShipMods(member, refitVariant, result, seenVariantIds)
        }
        return result
    }

    // A variant that owns station modules IS the root of its tree. A leaf is either a
    // single-module ship (no parent -> itself) or a child module -> climb to the root via
    // stable hullVariantId keys (FleetMemberHierarchy.findRootVariantId), NOT the identity
    // map, which breaks after fixVariant churn.
    private fun resolveRootVariant(member: FleetMemberAPI, variant: ShipVariantAPI): ShipVariantAPI {
        if (variant.stationModules.isNotEmpty()) return variant
        val rootVariantId = FleetMemberHierarchy.findRootVariantId(variant.hullVariantId) ?: return variant
        return FleetMemberUtils.findRootVariantByHullId(member, rootVariantId) ?: variant
    }

    // Recursive stationModules walk. Dedupes by hullVariantId so the same logical variant
    // (stock / REFIT clone / combat clone) is never collected twice — otherwise a single
    // installation would be iterated multiple times by whole-ship consumers like
    // advanceInCampaign. Includes the variant's own ShipModifications.
    private fun collectWholeShipMods(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        result: MutableList<ShipModifications>,
        seenVariantIds: MutableSet<String>
    ) {
        if (!seenVariantIds.add(variant.hullVariantId)) return
        get(member, variant)?.let { result.add(it) }
        // Shared pre-order walk over every station-module descendant (Extensions.kt).
        // The lambda captures only locals (member, result, seenVariantIds) and is a no-op
        // append per visited node — stateless, short-lived, nothing allocated per iteration.
        variant.forEachModuleVariant { childV ->
            if (seenVariantIds.add(childV.hullVariantId)) {
                get(member, childV)?.let { result.add(it) }
            }
        }
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
         * children), deduped by hullVariantId. Used by the hullmod install/uninstall decision and by
         * campaign-layer effects so child-owned modifications are never missed.
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
