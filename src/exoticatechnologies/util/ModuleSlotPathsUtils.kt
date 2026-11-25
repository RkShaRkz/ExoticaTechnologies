package exoticatechnologies.util

import com.fs.starfarer.api.combat.ShipVariantAPI
import exoticatechnologies.util.datastructures.Optional

/**
 * Callback for [ModuleSlotPathsUtils.forEachModuleVariantWithSlotPath].
 *
 * A dedicated SAM keeps Java callers free of `Unit.INSTANCE` while letting Kotlin callers pass a
 * lambda, matching [ModuleVariantAction].
 */
fun interface SlotPathVariantAction {
    /**
     * Receives one module and its ship-relative slot path.
     *
     * The path is passed with the variant so callers can key by position without walking the tree
     * a second time to rediscover where that variant instance sat.
     */
    fun accept(variant: ShipVariantAPI, slotPath: String)
}

/**
 * Locates station modules by where they are mounted on their ship.
 *
 * A hullVariantId says what a module is, not which one it is. Ships can mount the same module
 * variant in several slots, so id-only keys collapse distinct modules into one. A slot path is a
 * String built from stable slot ids, so it survives the stock/REFIT/combat object churn that makes
 * variant instances unusable as keys.
 *
 * Format: root is [ROOT_SLOT_PATH] (empty), a root-mounted module is its slot id, and deeper
 * modules join slot ids with `/`, outermost first.
 */
object ModuleSlotPathsUtils {
    /** The root's path is empty because the root is mounted in no slot; callers need one stable root key. */
    const val ROOT_SLOT_PATH: String = ""

    /**
     * Marker for a graph that cannot be placed on the ship.
     *
     * It mirrors VariantTagProvider's EXOTICA_INDICATOR style so an unreadable graph can be read
     * under a key that no hull slot can ever produce, instead of being silently dropped.
     */
    const val UNPLACED_INDICATOR: String = "$\$UNPLACED$$"

    private const val SLOT_SEPARATOR: Char = '/'

    /**
     * Builds a child path from its parent path.
     *
     * Direct children avoid allocation because they are the common case; deeper paths need the
     * separator to remain unambiguous.
     */
    @JvmStatic
    fun childSlotPath(parentSlotPath: String, slotId: String): String {
        return if (parentSlotPath.isEmpty()) {
            slotId
        } else {
            parentSlotPath + SLOT_SEPARATOR + slotId
        }
    }

    /**
     * Joins a graph-relative path onto the path of the graph's root.
     *
     * This lets one graph's internal node be named on the whole ship without guessing from ids.
     */
    @JvmStatic
    fun joinSlotPaths(parentSlotPath: String, relativeSlotPath: String): String {
        return if (relativeSlotPath.isEmpty()) {
            parentSlotPath
        } else {
            childSlotPath(parentSlotPath, relativeSlotPath)
        }
    }

    /**
     * Walks every module descendant with its path.
     *
     * Callers that already stand on a node need the path at the same time as the node; otherwise
     * they would repeat the walk to reconstruct a key. The receiver itself is not visited, matching
     * [ShipVariantAPI.forEachModuleVariant].
     */
    @JvmStatic
    fun forEachModuleVariantWithSlotPath(root: ShipVariantAPI, rootSlotPath: String, action: SlotPathVariantAction) {
        val stationModules: Map<String, String>? = root.stationModules
        if (stationModules == null) {
            return
        }
        for (slotId in stationModules.keys) {
            val child: ShipVariantAPI? = root.getModuleVariant(slotId)
            if (child != null) {
                val childPath = childSlotPath(rootSlotPath, slotId)
                action.accept(child, childPath)
                forEachModuleVariantWithSlotPath(child, childPath, action)
            }
        }
    }

    /**
     * Finds where a variant instance sits in a tree.
     *
     * Identity is used only for this lookup and never stored: two equal modules may still be
     * different slots, while a shared stock object can appear in several slots and then has one
     * physical set of tags.
     */
    fun findInstanceSlotPath(shipRoot: ShipVariantAPI, target: ShipVariantAPI): Optional<String> {
        return if (shipRoot === target) {
            Optional.of(ROOT_SLOT_PATH)
        } else {
            findInstanceSlotPathBelow(shipRoot, ROOT_SLOT_PATH, target)
        }
    }

    /**
     * Recursive helper for [findInstanceSlotPath].
     *
     * Keeping the recursion separate lets the public API express the root special case once.
     */
    private fun findInstanceSlotPathBelow(parent: ShipVariantAPI, parentSlotPath: String, target: ShipVariantAPI): Optional<String> {
        val stationModules: Map<String, String>? = parent.stationModules
        if (stationModules == null) {
            return Optional.empty()
        }
        for (slotId in stationModules.keys) {
            val child: ShipVariantAPI? = parent.getModuleVariant(slotId)
            if (child != null) {
                val childPath = childSlotPath(parentSlotPath, slotId)
                if (child === target) {
                    return Optional.of(childPath)
                }
                val deeper = findInstanceSlotPathBelow(child, childPath, target)
                if (deeper.isPresent()) {
                    return deeper
                }
            }
        }
        return Optional.empty()
    }

    /**
     * Finds the variant mounted at a path in a particular object graph.
     *
     * Resolving by path is what lets writes and reads hit the same module across stock, refit, and
     * display graphs even though their variant objects differ.
     */
    fun findVariantAtSlotPath(shipRoot: ShipVariantAPI, slotPath: String): Optional<ShipVariantAPI> {
        return if (slotPath.isEmpty()) {
            Optional.of(shipRoot)
        } else {
            findVariantBelowSlotPath(shipRoot, ROOT_SLOT_PATH, slotPath)
        }
    }

    /**
     * Recursive helper for [findVariantAtSlotPath].
     *
     * Prefix pruning avoids walking subtrees that cannot contain the requested path.
     */
    private fun findVariantBelowSlotPath(parent: ShipVariantAPI, parentSlotPath: String, wantedSlotPath: String): Optional<ShipVariantAPI> {
        val stationModules: Map<String, String>? = parent.stationModules
        if (stationModules == null) {
            return Optional.empty()
        }
        for (slotId in stationModules.keys) {
            val child: ShipVariantAPI? = parent.getModuleVariant(slotId)
            if (child != null) {
                val childPath = childSlotPath(parentSlotPath, slotId)
                if (childPath == wantedSlotPath) {
                    return Optional.of(child)
                }
                if (isBelow(wantedSlotPath, childPath)) {
                    val deeper = findVariantBelowSlotPath(child, childPath, wantedSlotPath)
                    if (deeper.isPresent()) {
                        return deeper
                    }
                }
            }
        }
        return Optional.empty()
    }

    /**
     * True when one path names a descendant of another.
     *
     * Checking the separator after the prefix prevents `SEGMENT1` from falsely containing
     * `SEGMENT10`.
     */
    private fun isBelow(slotPath: String, ancestorSlotPath: String): Boolean {
        return slotPath.length > ancestorSlotPath.length
                && slotPath[ancestorSlotPath.length] == SLOT_SEPARATOR
                && slotPath.startsWith(ancestorSlotPath)
    }

    /**
     * Re-expresses a ship-relative path relative to an ancestor.
     *
     * A graph rooted at a module needs paths inside that graph, not paths from the ship root.
     */
    fun findRelativeSlotPath(slotPath: String, ancestorSlotPath: String): Optional<String> {
        if (ancestorSlotPath.isEmpty()) {
            return Optional.of(slotPath)
        }
        if (slotPath == ancestorSlotPath) {
            return Optional.of(ROOT_SLOT_PATH)
        }
        return if (isBelow(slotPath, ancestorSlotPath)) {
            Optional.of(slotPath.substring(ancestorSlotPath.length + 1))
        } else {
            Optional.empty()
        }
    }

    /**
     * Finds the only variant carrying a hull id.
     *
     * Refusing ambiguous ids is the point: choosing the first identical module is the original
     * cross-module clobber bug.
     */
    fun findOnlyVariantSlotPathWithHullId(shipRoot: ShipVariantAPI, hullVariantId: String): Optional<String> {
        val exactMatches = countVariantsWithHullId(shipRoot, hullVariantId, false)
        if (exactMatches == 1) {
            return findFirstVariantSlotPathWithHullId(shipRoot, ROOT_SLOT_PATH, hullVariantId, false)
        }
        if (exactMatches == 0 && countVariantsWithHullId(shipRoot, hullVariantId, true) == 1) {
            return findFirstVariantSlotPathWithHullId(shipRoot, ROOT_SLOT_PATH, hullVariantId, true)
        }
        return Optional.empty()
    }

    /**
     * Tests exact or fuzzy hull-id identity.
     *
     * Fuzzy comparison exists only for refit/combat suffix renames; it must never be allowed to
     * pick between modules that are actually distinct.
     */
    private fun hasHullVariantId(variant: ShipVariantAPI, hullVariantId: String, isFuzzy: Boolean): Boolean {
        val candidateId: String? = variant.hullVariantId
        if (candidateId == null) {
            return false
        }
        return if (isFuzzy) {
            FleetMemberUtils.fuzzyVariantIdEquals(candidateId, hullVariantId)
        } else {
            candidateId == hullVariantId
        }
    }

    /**
     * Counts matching variants before locating one.
     *
     * A count pass allocates no paths and lets the caller prove uniqueness before choosing.
     */
    private fun countVariantsWithHullId(root: ShipVariantAPI, hullVariantId: String, isFuzzy: Boolean): Int {
        var count = if (hasHullVariantId(root, hullVariantId, isFuzzy)) 1 else 0
        val stationModules: Map<String, String>? = root.stationModules
        if (stationModules == null) {
            return count
        }
        for (slotId in stationModules.keys) {
            val child: ShipVariantAPI? = root.getModuleVariant(slotId)
            if (child != null) {
                count += countVariantsWithHullId(child, hullVariantId, isFuzzy)
            }
        }
        return count
    }

    /**
     * Locates the single match after uniqueness has been established.
     *
     * Splitting this from the count keeps "first match" from silently becoming "best guess".
     */
    private fun findFirstVariantSlotPathWithHullId(root: ShipVariantAPI, rootSlotPath: String, hullVariantId: String, isFuzzy: Boolean): Optional<String> {
        if (hasHullVariantId(root, hullVariantId, isFuzzy)) {
            return Optional.of(rootSlotPath)
        }
        val stationModules: Map<String, String>? = root.stationModules
        if (stationModules == null) {
            return Optional.empty()
        }
        for (slotId in stationModules.keys) {
            val child: ShipVariantAPI? = root.getModuleVariant(slotId)
            if (child != null) {
                val found = findFirstVariantSlotPathWithHullId(child, childSlotPath(rootSlotPath, slotId), hullVariantId, isFuzzy)
                if (found.isPresent()) {
                    return found
                }
            }
        }
        return Optional.empty()
    }

    /**
     * Places another object graph's root on the ship.
     *
     * Writes must compare the same module across graphs; without a safe placement, an id-only
     * fallback would hit whichever identical sibling happened to be visited first.
     */
    fun findGraphRootSlotPath(shipRoot: ShipVariantAPI, graphRoot: ShipVariantAPI): Optional<String> {
        val byInstance = findInstanceSlotPath(shipRoot, graphRoot)
        if (byInstance.isPresent()) {
            return byInstance
        }
        if (isCopyOfShip(shipRoot, graphRoot)) {
            return Optional.of(ROOT_SLOT_PATH)
        }
        val graphRootId: String? = graphRoot.hullVariantId
        return if (graphRootId != null) {
            findOnlyVariantSlotPathWithHullId(shipRoot, graphRootId)
        } else {
            Optional.empty()
        }
    }

    /**
     * Detects another copy of the whole ship.
     *
     * Matching both module slots and the root id prevents a module that merely shares a root
     * prefix from being mistaken for the ship root.
     */
    private fun isCopyOfShip(shipRoot: ShipVariantAPI, candidate: ShipVariantAPI): Boolean {
        val shipSlots: Map<String, String>? = shipRoot.stationModules
        val candidateSlots: Map<String, String>? = candidate.stationModules
        if (shipSlots == null || candidateSlots == null) {
            return false
        }
        if (shipSlots.isEmpty() || shipSlots.keys != candidateSlots.keys) {
            return false
        }
        val shipId: String? = shipRoot.hullVariantId
        val candidateId: String? = candidate.hullVariantId
        if (shipId == null || candidateId == null) {
            return false
        }
        return shipId == candidateId || FleetMemberUtils.fuzzyVariantIdEquals(shipId, candidateId)
    }
}
