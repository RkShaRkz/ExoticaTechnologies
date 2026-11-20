package exoticatechnologies.modifications

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.util.FleetMemberUtils
import exoticatechnologies.util.ModuleSlotPathsUtils
import exoticatechnologies.util.ModuleVariantHierarchy
import exoticatechnologies.util.FleetMemberUtils.findMemberFromShip
import exoticatechnologies.util.combineIntoList
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

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
     * [getData] for a caller that already knows the slot path of [variant] on the ship, which is
     * every caller that walks the ship's module tree. Passing it on saves the provider from
     * searching the tree for a node the walk is standing on, and makes the read exact for a
     * module-level member, which has no tree of its own to search.
     *
     * @param member the member the read is made for
     * @param variant the variant to read
     * @param slotPath the slot path of [variant] on the ship ([ModuleSlotPathsUtils])
     * @return the modifications of [variant], or null when no provider has any
     */
    private fun getData(member: FleetMemberAPI, variant: ShipVariantAPI, slotPath: String): ShipModifications? {
        return providers.firstNotNullOfOrNull { it.get(member, variant, slotPath) }
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
        collectModuleMods(rootFM, rootFM.variant, ModuleSlotPathsUtils.ROOT_SLOT_PATH, allModsList)

        return allModsList.toList()
    }

    /**
     * Recursive stationModules walk that collects the modifications of every module below
     * [variant].
     *
     * @param member the member the reads are made for
     * @param variant the variant whose station modules are read
     * @param variantSlotPath the slot path of [variant] on the ship
     * @param result receives the modifications found
     */
    private fun collectModuleMods(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        variantSlotPath: String,
        result: MutableSet<ShipModifications>
    ) {
        for (slotId in variant.stationModules.keys) {
            val childV = variant.getModuleVariant(slotId) ?: continue
            // This walk runs every frame in combat and knows which slot it is standing on, so it
            // hands the slot path to the read instead of having the provider search the tree for
            // the node again. For a module mounted on the root the path is the slot id itself.
            val childSlotPath = ModuleSlotPathsUtils.childSlotPath(variantSlotPath, slotId)
            val childMods = getData(member, childV, childSlotPath)
            childMods?.let {
                result.add(it)
            }
            collectModuleMods(member, childV, childSlotPath, result)
        }
    }

    /**
     * Collects the modifications of every node of the ship that [member] and [variant] belong to.
     * This half gathers the object graphs from the engine; the reading and deduplicating is
     * [collectWholeShip].
     *
     * @param member the member the read is made for
     * @param variant any variant of the ship: its root, or one of its modules
     * @return the modifications found, see [collectWholeShip]
     */
    private fun getWholeShipModsData(member: FleetMemberAPI, variant: ShipVariantAPI): List<ShipModifications> {
        val rootVariant = resolveRootVariant(member, variant)
        val refitVariant = runCatching { member.checkRefitVariant() }.getOrNull()
        return collectWholeShip(member, rootVariant, refitVariant)
    }

    /**
     * The engine-free half of [getWholeShipModsData]: reads every node of the ship's root tree,
     * then the nodes of the member's refit graph that the root tree did not already cover.
     *
     * It is split from [getWholeShipModsData] so that the reading and deduplicating - which is
     * all of issue #76 F1 - can be unit-tested on plain variant trees, without a refit screen.
     *
     * @param member the member the read is made for
     * @param rootVariant the root variant of the ship ([resolveRootVariant])
     * @param refitVariant the member's refit variant ([checkRefitVariant]), null when it could
     * not be read
     * @return the modifications of every module, each module read once per
     * (hullVariantId, slot path) and each variant object read once
     */
    private fun collectWholeShip(
        member: FleetMemberAPI,
        rootVariant: ShipVariantAPI,
        refitVariant: ShipVariantAPI?
    ): List<ShipModifications> {
        val result = mutableListOf<ShipModifications>()
        val seenModuleKeys = mutableSetOf<String>()
        val readVariants = mutableListOf<ShipVariantAPI>()
        collectWholeShipMods(member, rootVariant, ModuleSlotPathsUtils.ROOT_SLOT_PATH, result, seenModuleKeys, readVariants)
        // The REFIT display variant tree is a second object graph carrying the same tags
        // (refit clones preserve child tags 1:1). Union it in so a read never depends on
        // which variant instance the engine happened to hand us.
        if (refitVariant != null && refitVariant !== rootVariant) {
            // The slot path is part of the key now, so the refit graph has to be walked with the
            // slot paths its nodes have ON THE SHIP - otherwise the same module would get one key
            // in the root tree and another one here, and be collected twice.
            val refitSlotPath = slotPathOfGraph(rootVariant, refitVariant, member.variant)
            collectWholeShipMods(member, refitVariant, refitSlotPath, result, seenModuleKeys, readVariants)
        }
        return result
    }

    /**
     * Works out where the root of a second object graph of the ship sits on the ship, so that
     * [collectWholeShipMods] gives its nodes the same slot paths - and with them the same keys - as
     * the nodes of the root tree that stand for the same modules.
     *
     * Such a graph is not always a copy of the whole ship. For a module-level member (the
     * transient member the refit screen creates while a module tab is selected) the refit graph
     * is that one module's variant, and its slot path is the slot the module is mounted in, not
     * the root's.
     *
     * @param rootVariant the root variant of the ship, as resolved by [resolveRootVariant]
     * @param graphRoot the root of the graph to place
     * @param original the variant that [graphRoot] is the refit working copy of, null when there
     * is none or the engine returned none
     * @return the slot path of [graphRoot] on the ship, or [UNPLACED_GRAPH_SLOT_PATH] when it
     * cannot be placed. This function always returns a usable slot path, so it has no "absent"
     * result to report.
     */
    private fun slotPathOfGraph(rootVariant: ShipVariantAPI, graphRoot: ShipVariantAPI, original: ShipVariantAPI?): String {
        val placed = ModuleSlotPathsUtils.slotPathOfGraphRoot(rootVariant, graphRoot)
        if (placed.isPresent()) {
            return placed.get()
        }
        // A detached clone of one of several identical modules cannot be placed by itself: it is
        // not an instance of the ship's tree and its id names several slots. It is the working
        // copy of another variant though, and that one may still be the very instance mounted
        // in the ship's tree - then the graph sits wherever its original sits.
        if (original != null && original !== graphRoot) {
            val placedThroughOriginal = ModuleSlotPathsUtils.slotPathOfGraphRoot(rootVariant, original)
            if (placedThroughOriginal.isPresent()) {
                return placedThroughOriginal.get()
            }
        }
        // Nothing says which module this graph is. It is still read, under a slot path of its
        // own: its tags are real data of this ship, and dropping them could hide an installation
        // from the hullmod keep/strip decision. The price is that this one module can be listed
        // twice, once from the root tree and once from here.
        return UNPLACED_GRAPH_SLOT_PATH
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
     * Recursive stationModules walk. Dedupes by (member id, hullVariantId, slot path) so the same
     * logical variant (stock / REFIT clone / combat clone) is never collected twice — otherwise a
     * single installation would be iterated multiple times by whole-ship consumers like
     * advanceInCampaign. The member id scopes the key so identical-root twin ships (whose nodes
     * share hullVariantIds) can never have the first ship's mods swallowed by the second's walk.
     * The slot path tells identical modules apart: a ship can mount ONE module variant in several
     * slots (the five segments of a train ship), and without it every such module after the first
     * looked like a second copy of the first and was skipped (#76 F1).
     * Includes the variant's own ShipModifications.
     *
     * @param member the member the reads are made for
     * @param variant the root of the graph to walk
     * @param variantSlotPath the slot path of [variant] on the ship
     * @param result receives the modifications found
     * @param seenModuleKeys the keys of the modules read so far in this whole-ship read
     * @param readVariants the variant objects read so far in this whole-ship read
     */
    private fun collectWholeShipMods(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        variantSlotPath: String,
        result: MutableList<ShipModifications>,
        seenModuleKeys: MutableSet<String>,
        readVariants: MutableList<ShipVariantAPI>
    ) {
        if (!seenModuleKeys.add(wholeShipModKey(member, variant, variantSlotPath))) return
        readOnce(member, variant, variantSlotPath, result, readVariants)
        // Pre-order walk over every station-module descendant, like forEachModuleVariant in
        // Extensions.kt but with each node's slot path, which the key and the read both need
        // (ModuleSlotPathsUtils). One lambda is created per walk, not per node; per node the walk costs
        // the key's string concats and the read. A module mounted on the root gets its slot id as
        // its slot path, without a new String.
        ModuleSlotPathsUtils.forEachModuleVariantWithSlotPath(variant, variantSlotPath) { childV, childSlotPath ->
            if (seenModuleKeys.add(wholeShipModKey(member, childV, childSlotPath))) {
                readOnce(member, childV, childSlotPath, result, readVariants)
            }
        }
    }

    /**
     * Reads the modifications of [variant] and appends them to [result], unless this very variant
     * object was already read in this whole-ship read.
     *
     * The key keeps one MODULE from being read twice. This keeps one variant OBJECT from being
     * read twice, which several slots can lead to: until a slot gets its own per-ship copy, the
     * game mounts the same shared stock variant object in every slot that uses it. One object
     * carries one set of tags, however many slots lead to it, so it is one set of installations.
     *
     * @param member the member the read is made for
     * @param variant the variant to read
     * @param slotPath the slot path of [variant] on the ship
     * @param result receives the modifications when there are any
     * @param readVariants the variant objects read so far in this whole-ship read
     */
    private fun readOnce(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        slotPath: String,
        result: MutableList<ShipModifications>,
        readVariants: MutableList<ShipVariantAPI>
    ) {
        // An index loop over a handful of entries: no iterator, no set of identity wrappers.
        for (index in 0 until readVariants.size) {
            if (readVariants[index] === variant) {
                return
            }
        }
        readVariants.add(variant)
        val mods = getData(member, variant, slotPath)
        if (mods != null) {
            result.add(mods)
        }
    }

    /**
     * Deliberately collision-prone-safe composite key: scoping the hullVariantId by the acting
     * member's stable id costs a couple of string concats per node and guarantees identical-root
     * twins never dedupe into each other's mods. The slot path is the third part of the key: it
     * is what makes two modules with the same hullVariantId two different keys.
     *
     * @param member the member the read is made for
     * @param variant the variant the key is for
     * @param slotPath the slot path of [variant] on the ship
     * @return the key of that module in this whole-ship read
     */
    private fun wholeShipModKey(member: FleetMemberAPI, variant: ShipVariantAPI, slotPath: String): String {
        return member.id + ":" + variant.hullVariantId + ":" + slotPath
    }

    companion object {
        /**
         * Slot path under which a graph that cannot be placed on the ship is walked (see
         * [slotPathOfGraph]). It is written like the `$$EXOTICA$$` tag marker so that it cannot
         * be taken for a real slot id: no hull names a slot like this, so a key built from it
         * never equals the key of a module that was placed.
         */
        private const val UNPLACED_GRAPH_SLOT_PATH = "$\$UNPLACED$$"

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
         * are never missed.
         *
         * Every module slot is read, also when several slots mount the same module variant. The
         * member's refit graph is read too, for the modules the root tree did not already cover.
         */
        @JvmStatic
        @Synchronized
        fun getWholeShipMods(member: FleetMemberAPI, variant: ShipVariantAPI): List<ShipModifications> {
            return inst.getWholeShipModsData(member, variant)
        }

        /**
         * Test-only entry to [collectWholeShip], the engine-free half of [getWholeShipMods]. It
         * takes the object graphs as plain arguments, which production code gets from the fleet
         * member.
         *
         * **NEVER USE THIS IN PRODUCTION CODE**
         *
         * @param member the member the read is made for
         * @param rootVariant the root variant of the ship
         * @param refitVariant the member's refit variant, or null
         * @return the modifications of every module of the ship
         */
        @JvmStatic
        @Synchronized
        @VisibleForTesting
        @TestOnly
        internal fun testsOnly_collectWholeShip(
            member: FleetMemberAPI,
            rootVariant: ShipVariantAPI,
            refitVariant: ShipVariantAPI?
        ): List<ShipModifications> {
            return inst.collectWholeShip(member, rootVariant, refitVariant)
        }
    }

    interface Provider {
        fun get(member: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications?

        /**
         * [get] for a caller that already knows the slot path of [variant] on the ship.
         *
         * The slot path only helps a provider that keeps a separate record for every module of a
         * ship: it is what tells two modules with the same hullVariantId apart
         * ([VariantTagProvider] keys its cache by it). A provider that keeps one record per fleet
         * member, or one for a particular ship, has nothing to tell apart. That is why the default
         * ignores the slot path and answers exactly as the plain [get] does: such a provider, and
         * any provider added later, needs no code for it.
         *
         * @param member the member the read is made for
         * @param variant the variant to read
         * @param slotPath the slot path of [variant] on the ship
         * ([exoticatechnologies.util.ModuleSlotPathsUtils])
         * @return the modifications of [variant], or null when the provider has none
         */
        fun get(member: FleetMemberAPI, variant: ShipVariantAPI, slotPath: String): ShipModifications? {
            return get(member, variant)
        }

        fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications)
        fun remove(member: FleetMemberAPI, variant: ShipVariantAPI)

        fun setOnlyIfFirst(): Boolean {
            return true
        }
    }
}
