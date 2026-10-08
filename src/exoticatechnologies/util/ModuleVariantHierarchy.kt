package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import exoticatechnologies.campaign.listeners.CampaignEventListener
import org.apache.log4j.Level
import org.apache.log4j.Logger
import exoticatechnologies.refit.checkRefitVariant

/**
 * Resolves multi-module ship hierarchy from stable [ShipVariantAPI.hullVariantId] string keys.
 *
 * A multi-module (station) ship is a tree: its root variant owns other module variants via
 * [ShipVariantAPI.getStationModules]. Callers that hold only a stats object or a variant — the
 * refit, combat, and whole-ship pipelines — often need to know whether that variant is a child
 * module and, if so, which variant is the root of its tree, without access to the concrete
 * ShipAPI graph.
 *
 * This object is an in-memory lookup cache for those two questions, answered by string key:
 *  - "Is this variant a child module?" → [isChildStats]
 *  - "Which variant is the root of this child's tree?" → [findRootVariantId]
 *
 * ## Why string keys, not variant identities
 *
 * Starsector can recreate the same logical variant multiple times (stock → REFIT clone while
 * editing the refit screen, → combat clone at deployment). Object-identity links between those
 * instances go stale, but the [ShipVariantAPI.hullVariantId] string survives every re-creation.
 * All lookups here are therefore keyed on that string, never on variant instance equality.
 *
 * ## Lifecycle
 *
 * The cache is not persisted. [reinitialize] clears it and rebuilds it from all active fleets on
 * every game load / new game (called from ETModPlugin). [refreshFleetCache] re-maps a single
 * variant tree after refit operations have replaced variants. Nothing else writes the map.
 */
object ModuleVariantHierarchy {

    private val logger: Logger = Logger.getLogger(ModuleVariantHierarchy::class.java)

    /**
     * Child module variant hullVariantId → its parent variant's hullVariantId. One entry per
     * child module (the root appears only as a key target, never as a key itself). Populated
     * by [mapVariantTree].
     *
     * Keyed by the stable hullVariantId string (not variant object identity) so this map is
     * safe to query from [MutableShipStatsAPI]-based contexts, where the concrete
     * [ShipVariantAPI] instance may differ from the one that populated the map (e.g. a REFIT
     * clone created while the refit screen is open).
     */
    private val variantIdToParentId = HashMap<String, String>()

    /**
     * Whether [stats] belongs to a child module of a multi-module ship.
     *
     * Mainly used by ExoticaTechHM's shared-effect/skip logic to tell a module's stats apart
     * from the root's. Returns false for the root variant and for any single-ship variant,
     * which have no parent entry.
     */
    @JvmStatic fun isChildStats(stats: MutableShipStatsAPI?): Boolean {
        if (stats?.variant == null) return false
        return variantIdToParentId.containsKey(stats.variant.hullVariantId)
    }

    /** Number of child module variants currently mapped. */
    @JvmStatic fun getCacheSize(): Int = variantIdToParentId.size

    /**
     * Returns the hullVariantId of the root variant of the module tree that [childVariantId]
     * belongs to, or null when [childVariantId] is itself the root or not part of any module tree.
     *
     * Callers use this to anchor a whole-ship decision to the root member when they only hold a
     * child's variant — e.g. ExoticaTechHM root-hullmod resolution and ShipModLoader whole-ship
     * reads. The walk climbs parent links until no parent exists; a cycle guard stops runaway loops.
     *
     * ## Safe across fixVariant churn
     *
     * Walks the string-keyed [variantIdToParentId] (never object identity): hullVariantId strings
     * are stable across variant instance re-creation (stock → REFIT clones), so this works even
     * after [exoticatechnologies.util.fixVariant]/[exoticatechnologies.util.fixModuleVariants]
     * replace variant objects in the root's station module tree.
     */
    @JvmStatic fun findRootVariantId(childVariantId: String): String? {
        var current = childVariantId
        val seen = HashSet<String>()
        while (variantIdToParentId.containsKey(current)) {
            val parent = variantIdToParentId[current]
            if (parent != null && seen.add(current)) {
                current = parent
            } else {
                break
            }
        }
        return if (current == childVariantId) null else current
    }

    /**
     * Re-maps the module subtree(s) under [variant]. Call after refit operations that rebuilt or
     * cloned a root's variants, so the fresh hullVariantIds receive their parent links.
     */
    @JvmStatic fun refreshFleetCache(variant: ShipVariantAPI) {
        mapVariantTree(variant)
    }

    /**
     * Re-maps every module tree of the currently tracked fleets. Called from [reinitialize]
     * after the cache is cleared.
     */
    @JvmStatic fun refreshAllCaches() {
        for (fleet in CampaignEventListener.activeFleets) {
            refreshCache(fleet)
        }
    }

    /**
     * Clears the cache and rebuilds it from all active fleets.
     *
     * Called at game start and on every game load (ETModPlugin). Requires a live sector; if
     * none exists yet the cache is left empty (it is rebuilt on the next load).
     *
     * ## Why clearing is needed
     *
     * String keys survive variant instance churn, so entries do not go stale the way object
     * identity does — but a rebuilt/retrieved refit tree can leave parent links pointing at
     * variants that no longer belong to any tracked fleet. Clearing first guarantees the map
     * reflects exactly the currently tracked fleets, and re-maps freshly after any
     * [exoticatechnologies.util.fixVariant]-style operation that replaced variants.
     */
    @JvmStatic fun reinitialize() {
        variantIdToParentId.clear()
        if (Global.getSector() != null) {
            refreshAllCaches()
        } else {
            logError("Global.getSector() was null! Bailing out without calling refreshAllCaches() ...")
        }
    }

    /** Re-maps the module tree of every member in [fleet]. No-op for a null fleet. */
    private fun refreshCache(fleet: CampaignFleetAPI?) {
        if (fleet == null) {
            // If fleet was null, just do nothing
            return
        }
        // Otherwise remap everything
        for (m in fleet.fleetData.membersListCopy) {
            mapVariantTree(m.checkRefitVariant())
        }
    }

    /** Walks [parent]'s station modules recursively, recording each child → parent hullVariantId link. */
    private fun mapVariantTree(parent: ShipVariantAPI) {
        for (slotId in parent.stationModules.keys) {
            val child = parent.getModuleVariant(slotId)
            if (child != null) {
                variantIdToParentId[child.hullVariantId] = parent.hullVariantId
                mapVariantTree(child)
            }
        }
    }

    private fun logError(message: String) {
        log(message, logger, Level.ERROR)
    }
}