package exoticatechnologies.util

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import org.apache.log4j.Level
import java.util.*

/**
 * Registry for aggregating all MutableShipStatsAPI instances belonging to a single ship construct
 * (main hull + all attached modules).
 *
 * Uses a stable, session-scoped UUID per root ship to avoid issues with transient FleetMemberAPI IDs.
 * Variant hierarchy traversal ensures module stats are correctly linked even before ShipAPI exists.
 */
object ShipStatsRegistry {

    // Stable session UUID -> Set of all stats (main hull + modules)
    private val registry = WeakHashMap<UUID, MutableSet<MutableShipStatsAPI>>()

    // Root variant -> session UUID (assigned once per root member)
    private val rootVariantToUuid = WeakHashMap<ShipVariantAPI, UUID>()

    // Reverse map for variant hierarchy traversal: module variant -> parent variant
    private val parentVariantMap = WeakHashMap<ShipVariantAPI, ShipVariantAPI>()

    // Cache of root FleetMemberAPI to UUID (to reuse UUID for the same member across calls)
    private val rootMemberToUuid = WeakHashMap<FleetMemberAPI, UUID>()

    /**
     * Given a single stats object (from a module or main hull), returns a list of ALL stats objects
     * associated with that ship construct.
     *
     * @param stats The stats of any part of the ship.
     * @return A list of all stats (main + modules). If stats is null, returns empty list.
     */
    @JvmStatic
    fun getWholeShipsStatsFromSingleStats(stats: MutableShipStatsAPI?): List<MutableShipStatsAPI> {
        if (stats == null) return emptyList()

        val variant = stats.variant ?: return Collections.singletonList(stats)

        // Ensure the variant hierarchy is mapped (populates parent relationships)
        mapVariantHierarchy(variant)

        // Find the root variant by climbing parent links
        val rootVariant = getRootVariant(variant)

        // Get or create the stable session UUID for this root variant
        val sessionUuid = getOrCreateSessionUuid(rootVariant, stats)
        //TODO temporary test code
        val member = stats.fleetMember
        val rootMember = when {
            sessionUuid != null -> {
                // Look up root member from our cache (if we have one)
                rootMemberToUuid.entries.firstOrNull { it.value == sessionUuid }?.key
            }
            else -> null
        }
        AnonymousLogger.log("[SHARK]\tgetWholeShipStatsFromSingleStats\tmember == rootModuleMember ? "+(member == rootMember), Level.INFO);
        AnonymousLogger.log("[SHARK]\tgetWholeShipStatsFromSingleStats\tmember: "+member, Level.INFO);
        AnonymousLogger.log("[SHARK]\tgetWholeShipStatsFromSingleStats\trootModuleMember: "+rootMember, Level.INFO);
        //end of test code

        if (sessionUuid == null) {
            // Main hull not yet seen; defer caching
            return Collections.singletonList(stats)
        }

        // Add this stats to the aggregated set for that UUID
        val statSet = registry.getOrPut(sessionUuid) {
            Collections.synchronizedSet(LinkedHashSet())
        }
        statSet.add(stats)

        // Return a defensive copy to prevent external modification
        return statSet.toList()
    }

    /**
     * Recursively maps parent-child relationships for the given variant and its modules.
     */
    private fun mapVariantHierarchy(variant: ShipVariantAPI) {
        for (slotId in variant.stationModules.keys) {
            val moduleVariant = variant.getModuleVariant(slotId) ?: continue
            // Only map if not already present (avoid infinite loops on malformed data)
            if (!parentVariantMap.containsKey(moduleVariant)) {
                parentVariantMap[moduleVariant] = variant
                mapVariantHierarchy(moduleVariant)
            }
        }
    }

    /**
     * Climbs the parentVariantMap to find the topmost variant (the one without a parent).
     */
    private fun getRootVariant(variant: ShipVariantAPI): ShipVariantAPI {
        var current = variant
        while (true) {
            val parent = parentVariantMap[current] ?: break
            current = parent
        }
        return current
    }

    /**
     * Retrieves or creates the stable session UUID for a given root variant.
     * Returns null if the main hull has not been processed yet (module stats before main hull).
     */
    private fun getOrCreateSessionUuid(
        rootVariant: ShipVariantAPI,
        stats: MutableShipStatsAPI
    ): UUID? {
        // Check existing UUID for this root variant
        rootVariantToUuid[rootVariant]?.let { return it }

        // If this stats belongs to the main hull (its variant is the root variant and it has a fleetMember),
        // we can assign a permanent UUID.
        if (stats.variant == rootVariant) {
            val member = stats.fleetMember
            if (member != null) {
                // Check if we already have a UUID for this FleetMemberAPI
                val existingUuid = rootMemberToUuid[member]
                if (existingUuid != null) {
                    rootVariantToUuid[rootVariant] = existingUuid
                    return existingUuid
                }
                // First time seeing this root member: assign a new session UUID
                val newUuid = UUID.randomUUID()
                rootMemberToUuid[member] = newUuid
                rootVariantToUuid[rootVariant] = newUuid
                return newUuid
            }
        }

        // Cannot determine stable UUID yet (e.g., module stats before main hull)
        return null
    }

    /**
     * Clears all caches. Call this if you need a hard reset (e.g., game unload).
     */
    @JvmStatic
    fun clear() {
        registry.clear()
        rootVariantToUuid.clear()
        parentVariantMap.clear()
        rootMemberToUuid.clear()
    }

    /**
     * Returns the current size of the registry (for debugging).
     */
    @JvmStatic
    fun getRegistrySize(): Int = registry.size
}