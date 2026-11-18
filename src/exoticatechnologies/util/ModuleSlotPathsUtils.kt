package exoticatechnologies.util

import com.fs.starfarer.api.combat.ShipVariantAPI
import exoticatechnologies.util.datastructures.Optional

/**
 * Single-abstract-method callback for [ModuleSlotPathsUtils.forEachModuleVariantWithSlotPath].
 *
 * Declared as a `fun interface` for the same reason as [ModuleVariantAction]: Java callers get a
 * native void-returning SAM while Kotlin callers keep lambda syntax. The same rules apply too -
 * implementations must stay small and short-lived and capture only local values.
 */
fun interface SlotPathVariantAction {
    /**
     * Called once for every visited module variant.
     *
     * @param variant the visited module variant
     * @param slotPath the slot path of [variant], see [ModuleSlotPathsUtils]
     */
    fun accept(variant: ShipVariantAPI, slotPath: String)
}

/**
 * Identifies a station module by WHERE it is mounted on its ship: the chain of station-module slot
 * ids that leads from the ship's root variant down to the module (its "slot path").
 *
 * ## Why a slot path, when every module already has a hullVariantId
 *
 * A [ShipVariantAPI.getHullVariantId] says WHAT a module is, not WHICH module it is. The same
 * module variant can be mounted in several slots of one ship: ED Shipyards' Helltrain and
 * Newfoundland war freighters mount the same `..._combatfreighter_standard` variant in all five of
 * their SEGMENT1..SEGMENT5 slots. Those five modules share one hullVariantId, so anything keyed by
 * `(member id, hullVariantId)` sees them as a single module: reads skip four of them, writes land
 * on whichever comes first, and cached data is served to the wrong one (issue #76). A slot can
 * hold only one module, so `(member id, hullVariantId, slot path)` names exactly one module.
 *
 * ## Why this does not go stale the way variant identities do
 *
 * Starsector re-creates the same logical variant many times (stock -> REFIT clone -> combat
 * clone), which is why this codebase keys on strings and never on variant instances (see
 * [ModuleVariantHierarchy]). A slot path is a plain String built from slot ids, and slot ids are
 * defined by the hull, so every copy of a ship has the same slot paths. Nothing in here stores a
 * variant. Instance identity is used only to ASK where a variant sits in a tree at this very
 * moment ([slotPathOf]); the answer is a String.
 *
 * ## Format
 *
 * - the ship's root variant: [ROOT_SLOT_PATH] (the empty String - the root sits in no slot);
 * - a module mounted on the root: its slot id, e.g. `SEGMENT3`;
 * - a module mounted on a module: the slot ids joined by `/`, outermost first, e.g. `SEGMENT3/TURRET`.
 *
 * This object is stateless: every function is a pure walk over the tree it is handed.
 *
 * The functions that return an [Optional] are for Kotlin callers only and carry no `@JvmStatic`:
 * [Optional] is an inline class, so on the JVM such a function gets a mangled name that Java
 * cannot call anyway.
 */
object ModuleSlotPathsUtils {

    /** The slot path of a ship's root variant. Empty, because the root is not mounted in any slot. */
    const val ROOT_SLOT_PATH: String = ""

    /** Joins the slot ids of a path that is more than one slot deep (a module mounted on a module). */
    private const val SLOT_SEPARATOR: Char = '/'

    /**
     * Builds the slot path of a module from its parent's slot path and the slot it is mounted in.
     *
     * A module mounted directly on the ship root - which is every module of every ship seen so
     * far - gets its slot id back unchanged, so the common case allocates nothing.
     *
     * @param parentSlotPath the slot path of the variant that owns the slot, [ROOT_SLOT_PATH] for
     * the ship root
     * @param slotId the station-module slot id the module is mounted in
     * @return the slot path of the module mounted in [slotId]
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
     * Joins the slot path of a variant with a slot path that is relative to that variant.
     *
     * This is [childSlotPath] for a relative path of any length, including none at all. It turns
     * "this graph's root sits at X on the ship" plus "the module sits at Y inside this graph" into
     * the module's slot path on the ship.
     *
     * @param parentSlotPath the slot path of the variant that [relativeSlotPath] starts from
     * @param relativeSlotPath a slot path relative to that variant, [ROOT_SLOT_PATH] for the
     * variant itself
     * @return the combined slot path
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
     * Pre-order walk of every station-module descendant of [root], handing each one to [action]
     * together with its slot path.
     *
     * This is [forEachModuleVariant] with the slot path passed along, for callers that key on it.
     * As there, [root] itself is NOT visited, and the walk is deliberately not inline because it
     * recurses into itself.
     *
     * @param root the variant whose station-module descendants are walked
     * @param rootSlotPath the slot path of [root] on its ship: [ROOT_SLOT_PATH] when [root] is the
     * ship's root variant, or the slot path of the module when the walk starts further down
     * @param action invoked once per descendant
     */
    @JvmStatic
    fun forEachModuleVariantWithSlotPath(root: ShipVariantAPI, rootSlotPath: String, action: SlotPathVariantAction) {
        val stationModules: Map<String, String>? = root.stationModules
        if (stationModules == null) {
            return
        }
        for (slotId in stationModules.keys) {
            // A slot can be declared without a module variant behind it; there is nothing to visit.
            val child: ShipVariantAPI? = root.getModuleVariant(slotId)
            if (child != null) {
                val childPath = childSlotPath(rootSlotPath, slotId)
                action.accept(child, childPath)
                forEachModuleVariantWithSlotPath(child, childPath, action)
            }
        }
    }

    /**
     * Finds where exactly this [target] instance sits in the tree of [shipRoot].
     *
     * The comparison is by identity on purpose: two modules can be equal in every other respect
     * (same hullVariantId, same loadout) and still be different modules. The instance is only
     * looked at, never kept - see the class documentation.
     *
     * One instance CAN be mounted in several slots: until a slot gets its own per-ship copy, the
     * game hands out the same shared stock variant object for it. The first such slot in walk
     * order is returned then. Those slots are physically one object carrying one set of tags, so
     * there is nothing to tell apart; what matters is that the answer is the same every time.
     *
     * @param shipRoot the root of the tree to search
     * @param target the variant instance to look for
     * @return the slot path of [target] ([ROOT_SLOT_PATH] when it is [shipRoot] itself), or
     * [Optional.empty] when this instance is not part of the tree - typically because [target] is
     * a clone that lives in another object graph of the same ship
     */
    fun slotPathOf(shipRoot: ShipVariantAPI, target: ShipVariantAPI): Optional<String> {
        return if (shipRoot === target) {
            Optional.of(ROOT_SLOT_PATH)
        } else {
            slotPathBelow(shipRoot, ROOT_SLOT_PATH, target)
        }
    }

    /**
     * Recursive step of [slotPathOf]: searches the station-module descendants of [parent].
     *
     * @param parent the variant whose station modules are searched
     * @param parentSlotPath the slot path of [parent]
     * @param target the variant instance to look for
     * @return the slot path of [target], or [Optional.empty] when it is not below [parent]
     */
    private fun slotPathBelow(parent: ShipVariantAPI, parentSlotPath: String, target: ShipVariantAPI): Optional<String> {
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
                val deeper = slotPathBelow(child, childPath, target)
                if (deeper.isPresent()) {
                    return deeper
                }
            }
        }
        // Every slot below this parent was checked and none of them holds the target instance.
        return Optional.empty()
    }

    /**
     * Finds the variant mounted at [slotPath] in the tree of [shipRoot]: the same module as seen
     * in THIS copy of the ship.
     *
     * The path is never split back into slot ids. The tree is walked and the slot path of each
     * visited child is compared instead, so a slot id that happens to contain the separator
     * still resolves - unless a module mounted on a module spells the very same path (root slot
     * `A/B` next to slot `B` below root slot `A`). Slot ids are assumed not to do that; no known
     * ship has a `/` in a slot id, or a module mounted on a module.
     *
     * @param shipRoot the root of the tree to search
     * @param slotPath the slot path to resolve, [ROOT_SLOT_PATH] for the root itself
     * @return the variant at [slotPath], or [Optional.empty] when this tree has no module there
     * (the slot does not exist in this copy, or it holds no module variant)
     */
    fun variantAtSlotPath(shipRoot: ShipVariantAPI, slotPath: String): Optional<ShipVariantAPI> {
        return if (slotPath.isEmpty()) {
            Optional.of(shipRoot)
        } else {
            variantBelowAtSlotPath(shipRoot, ROOT_SLOT_PATH, slotPath)
        }
    }

    /**
     * Recursive step of [variantAtSlotPath]: searches the station-module descendants of [parent].
     *
     * @param parent the variant whose station modules are searched
     * @param parentSlotPath the slot path of [parent]
     * @param wantedSlotPath the slot path to resolve
     * @return the variant at [wantedSlotPath], or [Optional.empty] when it is not below [parent]
     */
    private fun variantBelowAtSlotPath(parent: ShipVariantAPI, parentSlotPath: String, wantedSlotPath: String): Optional<ShipVariantAPI> {
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
                // Only a child whose own path is a prefix of the wanted one can lead to it, so
                // every other subtree is skipped without being walked.
                if (isBelow(wantedSlotPath, childPath)) {
                    val deeper = variantBelowAtSlotPath(child, childPath, wantedSlotPath)
                    if (deeper.isPresent()) {
                        return deeper
                    }
                }
            }
        }
        // No module of this parent sits at, or on the way to, the wanted slot path.
        return Optional.empty()
    }

    /**
     * Whether [slotPath] names a module mounted somewhere below the module at [ancestorSlotPath].
     *
     * @param slotPath the slot path to test
     * @param ancestorSlotPath the slot path of the candidate ancestor, never [ROOT_SLOT_PATH]
     * @return true when [slotPath] is [ancestorSlotPath] followed by at least one more slot
     */
    private fun isBelow(slotPath: String, ancestorSlotPath: String): Boolean {
        // Checked by length and separator position so that no prefix String has to be built.
        return slotPath.length > ancestorSlotPath.length
                && slotPath[ancestorSlotPath.length] == SLOT_SEPARATOR
                && slotPath.startsWith(ancestorSlotPath)
    }

    /**
     * Re-expresses a slot path that is relative to the ship root as a path relative to one of
     * its ancestors, so it can be followed inside an object graph that is rooted at that ancestor
     * instead of at the ship root.
     *
     * @param slotPath a slot path relative to the ship root
     * @param ancestorSlotPath the slot path (relative to the ship root) of the variant the result
     * should be relative to
     * @return [slotPath] relative to [ancestorSlotPath] ([ROOT_SLOT_PATH] when they are the same
     * node), or [Optional.empty] when [slotPath] is not at or below [ancestorSlotPath] - the
     * module then simply is not part of that ancestor's subtree
     */
    fun relativeSlotPath(slotPath: String, ancestorSlotPath: String): Optional<String> {
        if (ancestorSlotPath.isEmpty()) {
            // Relative to the ship root: already in the wanted form.
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
     * Finds the slot path of the ONE variant in the tree of [shipRoot] that carries
     * [hullVariantId]: an exact id match first, and only when no variant matches exactly, a fuzzy
     * match ([FleetMemberUtils.fuzzyVariantIdEquals] - the same prefix before the last `_suffix`,
     * which is how a refit clone `left_0` is recognised as the stock `left_Start`).
     *
     * This is the only place a hullVariantId is turned into a position, and it refuses to guess:
     * when several variants match, they are identical modules (or siblings that differ only in
     * their suffix) and the id cannot tell which one is meant. Taking the first of them is exactly
     * the defect behind issue #76, so an ambiguous id resolves to nothing.
     *
     * @param shipRoot the root of the tree to search ([shipRoot] itself is a candidate too)
     * @param hullVariantId the hullVariantId to look for
     * @return the slot path of the single matching variant, or [Optional.empty] when no variant
     * matches or more than one does
     */
    fun slotPathOfOnlyVariantWithId(shipRoot: ShipVariantAPI, hullVariantId: String): Optional<String> {
        // Counted first, located second. This also runs on read paths, so the tree is walked
        // twice instead of collecting the matches into a list. No list or path is built for an
        // exact match; only the fuzzy pass, reached when nothing matches exactly, costs the two
        // prefix Strings per node that fuzzyVariantIdEquals creates.
        val exactMatches = countVariantsWithId(shipRoot, hullVariantId, false)
        if (exactMatches == 1) {
            return slotPathOfFirstVariantWithId(shipRoot, ROOT_SLOT_PATH, hullVariantId, false)
        }
        if (exactMatches == 0 && countVariantsWithId(shipRoot, hullVariantId, true) == 1) {
            return slotPathOfFirstVariantWithId(shipRoot, ROOT_SLOT_PATH, hullVariantId, true)
        }
        // Either nothing carries the id, or several variants do and the id cannot say which one.
        return Optional.empty()
    }

    /**
     * Whether [variant] carries [hullVariantId], exactly or fuzzily.
     *
     * @param variant the variant to test
     * @param hullVariantId the hullVariantId to compare against
     * @param isFuzzy false: the ids must be equal; true: they must be fuzzy-equal
     * ([FleetMemberUtils.fuzzyVariantIdEquals])
     * @return whether the id of [variant] matches
     */
    private fun hasVariantId(variant: ShipVariantAPI, hullVariantId: String, isFuzzy: Boolean): Boolean {
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
     * Counts the variants in the tree of [root], [root] included, that carry [hullVariantId].
     *
     * @param root the root of the tree to count in
     * @param hullVariantId the hullVariantId to look for
     * @param isFuzzy see [hasVariantId]
     * @return the number of matching variants
     */
    private fun countVariantsWithId(root: ShipVariantAPI, hullVariantId: String, isFuzzy: Boolean): Int {
        var count = if (hasVariantId(root, hullVariantId, isFuzzy)) 1 else 0
        val stationModules: Map<String, String>? = root.stationModules
        if (stationModules == null) {
            return count
        }
        for (slotId in stationModules.keys) {
            val child: ShipVariantAPI? = root.getModuleVariant(slotId)
            if (child != null) {
                count += countVariantsWithId(child, hullVariantId, isFuzzy)
            }
        }
        return count
    }

    /**
     * Finds the slot path of the first variant, in pre-order from [root], that carries
     * [hullVariantId]. Only called by [slotPathOfOnlyVariantWithId] once the match is known to be
     * the only one, so "first" never has to choose between candidates.
     *
     * @param root the root of the tree to search ([root] itself is a candidate)
     * @param rootSlotPath the slot path of [root]
     * @param hullVariantId the hullVariantId to look for
     * @param isFuzzy see [hasVariantId]
     * @return the slot path of the first match, or [Optional.empty] when there is none
     */
    private fun slotPathOfFirstVariantWithId(root: ShipVariantAPI, rootSlotPath: String, hullVariantId: String, isFuzzy: Boolean): Optional<String> {
        if (hasVariantId(root, hullVariantId, isFuzzy)) {
            return Optional.of(rootSlotPath)
        }
        val stationModules: Map<String, String>? = root.stationModules
        if (stationModules == null) {
            return Optional.empty()
        }
        for (slotId in stationModules.keys) {
            val child: ShipVariantAPI? = root.getModuleVariant(slotId)
            if (child != null) {
                val found = slotPathOfFirstVariantWithId(child, childSlotPath(rootSlotPath, slotId), hullVariantId, isFuzzy)
                if (found.isPresent()) {
                    return found
                }
            }
        }
        // Nothing in this subtree carries the id.
        return Optional.empty()
    }

    /**
     * Works out where the root of another object graph of the same ship sits on that ship.
     *
     * One ship exists as several object graphs at once (the fleet member's variant tree, the
     * REFIT clone, the refit screen's working tree), and a graph does not have to start at the
     * ship's root: it can be a single module's variant. Before a slot path can be followed inside
     * such a graph, or a node of it be given a key, the graph's root has to be placed on the ship.
     * Three ways are tried, most reliable first:
     *
     * 1. [graphRoot] is an instance inside [shipRoot]'s tree: its position there.
     * 2. [graphRoot] is a copy of the whole ship (see [isCopyOfShip]): the ship root.
     * 3. Exactly one variant of the ship carries [graphRoot]'s hullVariantId
     *    ([slotPathOfOnlyVariantWithId]): that variant's position.
     *
     * @param shipRoot the root variant of the ship
     * @param graphRoot the root of the other object graph
     * @return the slot path of [graphRoot] on the ship, or [Optional.empty] when it cannot be
     * placed without guessing - a clone of a module whose hullVariantId is shared by several
     * modules of the ship, or a variant that is no part of this ship at all. Callers must then
     * treat that graph as unplaced rather than pick a slot for it.
     */
    fun slotPathOfGraphRoot(shipRoot: ShipVariantAPI, graphRoot: ShipVariantAPI): Optional<String> {
        val byInstance = slotPathOf(shipRoot, graphRoot)
        if (byInstance.isPresent()) {
            return byInstance
        }
        if (isCopyOfShip(shipRoot, graphRoot)) {
            return Optional.of(ROOT_SLOT_PATH)
        }
        val graphRootId: String? = graphRoot.hullVariantId
        return if (graphRootId != null) {
            slotPathOfOnlyVariantWithId(shipRoot, graphRootId)
        } else {
            Optional.empty()
        }
    }

    /**
     * Whether [candidate] is another copy of the whole ship whose root variant is [shipRoot].
     *
     * Both must own station modules, in the same slots, and carry the same hullVariantId or one
     * that differs only in its last `_suffix` (refit clones are renamed that way). The slot check
     * is what makes the fuzzy id comparison safe here: a module that merely shares the root's id
     * prefix does not own the root's module slots.
     *
     * Two variants WITHOUT station modules are deliberately never matched by this function, even
     * when their hullVariantIds are equal: they can be two different modules of the same ship (the
     * five identical segments of a train ship). Such a variant is placed by
     * [slotPathOfOnlyVariantWithId] instead, which only answers when its id is unique on the ship.
     *
     * @param shipRoot the root variant of the ship
     * @param candidate the variant that may be a copy of [shipRoot]
     * @return true when [candidate] is a copy of the ship's root variant
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
