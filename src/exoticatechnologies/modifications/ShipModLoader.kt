package exoticatechnologies.modifications

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.util.FleetMemberUtils
import exoticatechnologies.util.ModuleVariantHierarchy
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
        val seenSlotPaths = HashSet<String>(8)
        val seenVariantIds = HashSet<String>(8)
        val rootVariant = resolveRootVariant(member, variant)
        collectWholeShipMods(member, rootVariant, emptyList(), result, seenSlotPaths, seenVariantIds)
        // The REFIT display variant tree is a second object graph carrying the same tags
        // (refit clones preserve child tags 1:1). Union it in so a read never depends on
        // which variant instance the engine happened to hand us. Its modules are lined up with the
        // root tree by slot path, so each module still counts once.
        val refitVariant = runCatching { member.checkRefitVariant() }.getOrNull()
        if (refitVariant != null && refitVariant !== rootVariant) {
            val base = alignWithin(rootVariant, refitVariant)
            if (base != null) {
                collectWholeShipMods(member, refitVariant, base, result, seenSlotPaths, seenVariantIds)
            } else {
                collectWholeShipModsById(member, refitVariant, result, seenVariantIds)
            }
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

    // Recursive stationModules walk. Dedupes by slot path from the ship root, so the same module seen
    // in two graphs (stock / REFIT clone / combat clone) is never collected twice — otherwise a single
    // installation would be iterated multiple times by whole-ship consumers like advanceInCampaign.
    // A hullVariantId is not unique inside a ship: identical modules (one module variant in several
    // slots) are different slots, and each one's mods count (#76, F1). Includes the variant's own
    // ShipModifications. Records ids too, for collectWholeShipModsById.
    private fun collectWholeShipMods(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        path: List<String>,
        result: MutableList<ShipModifications>,
        seenSlotPaths: MutableSet<String>,
        seenVariantIds: MutableSet<String>
    ) {
        seenVariantIds.add(wholeShipModKey(member, variant))
        if (seenSlotPaths.add(path.joinToString("/"))) {
            get(member, variant)?.let { result.add(it) }
        }
        for (slotId in variant.stationModules.keys) {
            val childV = variant.getModuleVariant(slotId) ?: continue
            collectWholeShipMods(member, childV, path + slotId, result, seenSlotPaths, seenVariantIds)
        }
    }

    // The previous dedup, kept for a refit graph that cannot be lined up with the root tree: by
    // (member id, hullVariantId). The member id scopes the key so identical-root twin ships (whose
    // nodes share hullVariantIds) can never have the first ship's mods swallowed by the second's walk.
    private fun collectWholeShipModsById(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        result: MutableList<ShipModifications>,
        seenVariantIds: MutableSet<String>
    ) {
        if (!seenVariantIds.add(wholeShipModKey(member, variant))) return
        get(member, variant)?.let { result.add(it) }
        // Shared pre-order walk over every station-module descendant (Extensions.kt).
        // The lambda captures only locals (member, result, seenVariantIds) and is a no-op
        // append per visited node — stateless, short-lived, nothing allocated per iteration.
        variant.forEachModuleVariant { childV ->
            if (seenVariantIds.add(wholeShipModKey(member, childV))) {
                get(member, childV)?.let { result.add(it) }
            }
        }
    }

    // Deliberately collision-prone-safe composite key: scoping the hullVariantId by the acting
    // member's stable id costs a couple of string concats per node and guarantees identical-root
    // twins never dedupe into each other's mods.
    private fun wholeShipModKey(member: FleetMemberAPI, variant: ShipVariantAPI): String {
        return member.id + ":" + variant.hullVariantId
    }

    // Slot path of [other]'s root inside [root] (empty when it is the same ship), or null when the two
    // cannot be lined up: by instance, then a copy of the same ship, then an id found exactly once.
    private fun alignWithin(root: ShipVariantAPI, other: ShipVariantAPI): List<String>? {
        slotPathTo(root, other)?.let { return it }
        if (isSameShip(root, other)) return emptyList()
        return slotPathsWithId(root, other.hullVariantId, emptyList()).singleOrNull()
    }

    // Copies of one ship: the same id, or the same id up to the last '_suffix' with the same,
    // non-empty module slots. Leaf modules never match by suffix alone.
    private fun isSameShip(a: ShipVariantAPI, b: ShipVariantAPI): Boolean {
        if (a.hullVariantId == b.hullVariantId) return true
        val slots = a.stationModules.keys
        return slots.isNotEmpty() && slots == b.stationModules.keys
                && FleetMemberUtils.fuzzyVariantIdEquals(a.hullVariantId, b.hullVariantId)
    }

    private fun slotPathTo(root: ShipVariantAPI, target: ShipVariantAPI): List<String>? {
        if (root === target) return emptyList()
        for (slotId in root.stationModules.keys) {
            val childV = root.getModuleVariant(slotId) ?: continue
            val below = slotPathTo(childV, target) ?: continue
            return listOf(slotId) + below
        }
        return null
    }

    private fun slotPathsWithId(root: ShipVariantAPI, id: String, path: List<String>): List<List<String>> {
        val found = ArrayList<List<String>>()
        if (root.hullVariantId == id) found.add(path)
        for (slotId in root.stationModules.keys) {
            val childV = root.getModuleVariant(slotId) ?: continue
            found.addAll(slotPathsWithId(childV, id, path + slotId))
        }
        return found
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
         * children), deduped by (member id, hullVariantId). Used by the hullmod install/uninstall
         * decision and by campaign-layer effects so child-owned modifications are never missed.
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
