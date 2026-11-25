package exoticatechnologies.modifications.dataproviders

import com.fs.starfarer.api.combat.ShipVariantAPI
import exoticatechnologies.util.ModuleSlotPathsUtils
import exoticatechnologies.util.datastructures.Optional
import java.util.IdentityHashMap

/**
 * Maps a tag write from the variant the UI touched to the same module in every live object graph.
 *
 * Refit and display graphs contain different objects for the same physical module. Matching by
 * hullVariantId alone is unsafe because identical sibling modules share that id, so this context
 * resolves one ship-relative slot path from the written object and reuses it in the other graphs.
 * A detached display module can be placed through the member variant it is a working copy of.
 */
class ModuleWriteGraphContext(
    private val originalVariant: ShipVariantAPI,
    private val graphs: List<ShipVariantAPI>,
    private val displayVariant: ShipVariantAPI?,
    private val displayOriginalVariant: ShipVariantAPI?
) {
    private val graphSlotPaths: IdentityHashMap<ShipVariantAPI, String> = IdentityHashMap()
    private val graphRootPaths: IdentityHashMap<ShipVariantAPI, Optional<String>> = IdentityHashMap()

    init {
        for (graph in graphs) {
            var rootPath = ModuleSlotPathsUtils.findGraphRootSlotPath(originalVariant, graph)
            // A display module tab is a detached clone whose own id may be shared by several
            // modules, so place it through the member variant it is editing instead of guessing.
            if (!rootPath.isPresent() && graph === displayVariant && displayOriginalVariant != null) {
                rootPath = ModuleSlotPathsUtils.findGraphRootSlotPath(originalVariant, displayOriginalVariant)
            }
            graphRootPaths[graph] = rootPath
            graphSlotPaths[graph] = if (rootPath.isPresent()) {
                rootPath.get()
            } else {
                ModuleSlotPathsUtils.UNPLACED_INDICATOR
            }
        }
    }

    /**
     * Returns the stable tree the refit/display copies represent.
     *
     * Every mirror decision is anchored to one tree so a working-copy graph cannot silently become
     * a different ship's source of truth.
     */
    fun originalOf(graph: ShipVariantAPI): ShipVariantAPI {
        if (graph === displayVariant && displayOriginalVariant != null) {
            return displayOriginalVariant
        }
        return originalVariant
    }

    /**
     * True when two objects are different graph objects for the same module.
     *
     * Equal slot paths are safer than fuzzy hull ids here: they stay correct when two identical
     * modules are mounted at different slots.
     */
    fun isRefitWorkingCopyPair(first: ShipVariantAPI, second: ShipVariantAPI): Boolean {
        if (first === second) {
            return true
        }
        val firstPath = findGraphRootSlotPath(first)
        if (!firstPath.isPresent()) {
            return false
        }
        val secondPath = findGraphRootSlotPath(second)
        return secondPath.isPresent() && firstPath.get() == secondPath.get()
    }

    /**
     * Returns where a whole graph root sits on the original ship.
     *
     * A graph rooted at a child module needs its own prefix removed before its internal paths can
     * be compared with ship-relative paths.
     */
    fun findGraphRootSlotPath(graph: ShipVariantAPI): Optional<String> {
        val rootPath = graphRootPaths[graph]
        return if (rootPath != null) {
            rootPath
        } else {
            Optional.empty()
        }
    }

    /**
     * Resolves the written object's ship-relative slot path.
     *
     * The first graph that owns the object is the source of truth; other graphs may be older
     * working copies and should never redefine where the write happened.
     */
    fun findWrittenSlotPath(writtenVariant: ShipVariantAPI): Optional<String> {
        for (graph in graphs) {
            val slotPathInGraph = ModuleSlotPathsUtils.findInstanceSlotPath(graph, writtenVariant)
            if (!slotPathInGraph.isPresent()) {
                continue
            }
            val graphRootSlotPath = graphSlotPaths[graph]
            if (graphRootSlotPath != null && graphRootSlotPath != ModuleSlotPathsUtils.UNPLACED_INDICATOR) {
                return Optional.of(
                    ModuleSlotPathsUtils.joinSlotPaths(graphRootSlotPath, slotPathInGraph.get())
                )
            }
        }
        return Optional.empty()
    }

    /**
     * Finds the same module inside one of the mirror graphs.
     *
     * Slot paths are tried first because they distinguish identical siblings; hull ids are only a
     * last resort when the id is unique in that graph.
     */
    fun findMirrorVariant(graph: ShipVariantAPI, writtenVariant: ShipVariantAPI, writtenSlotPath: String?): ShipVariantAPI? {
        // The root IS considered when the graph is a working copy of the written tree: for a
        // single-module ship the whole graph is just the root, and an exact-only instance check
        // would skip the other graph entirely. Slot matching above prevents this from colliding
        // with child modules in multi-module ships.
        if (writtenSlotPath != null) {
            val graphRootSlotPath = findGraphRootSlotPath(graph)
            if (graphRootSlotPath.isPresent()) {
                val relativeSlotPath = ModuleSlotPathsUtils.findRelativeSlotPath(
                    writtenSlotPath,
                    graphRootSlotPath.get()
                )
                if (relativeSlotPath.isPresent()) {
                    val mirror = ModuleSlotPathsUtils.findVariantAtSlotPath(graph, relativeSlotPath.get())
                    if (mirror.isPresent()) {
                        return mirror.get()
                    }
                }
            }
        }

        if (isRefitWorkingCopyPair(graph, writtenVariant)) {
            return graph
        }

        // Last resort: a hull id is safe only when this graph contains it exactly once. An
        // ambiguous id means several identical siblings, and guessing would recreate the clobber.
        val writtenId: String? = writtenVariant.hullVariantId
        if (writtenId != null) {
            val uniqueSlotPath = ModuleSlotPathsUtils.findOnlyVariantSlotPathWithHullId(graph, writtenId)
            if (uniqueSlotPath.isPresent()) {
                val mirror = ModuleSlotPathsUtils.findVariantAtSlotPath(graph, uniqueSlotPath.get())
                if (mirror.isPresent()) {
                    return mirror.get()
                }
            }
        }
        return null
    }
}
