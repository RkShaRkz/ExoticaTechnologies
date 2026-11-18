package exoticatechnologies.modifications

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.VariantSource
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.refit.getRefitDisplayMemberVariant
import exoticatechnologies.refit.getRefitDisplayVariant
import exoticatechnologies.util.*
import exoticatechnologies.util.datastructures.Optional
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.json.JSONException
import org.json.JSONObject
import java.util.WeakHashMap

open class VariantTagProvider : ShipModLoader.Provider {
    companion object {
        val MIN_LOG_LEVEL: Level = Level.WARN

        @JvmStatic
        private var inst: VariantTagProvider = VariantTagProvider()
        @JvmStatic
        fun getInstance(): VariantTagProvider { return inst }
        private val logger = Logger.getLogger(VariantTagProvider::class.java)

        /**
         * How many in-game days a member's cached data may live before it must be re-synced
         * from the variant tags (the durable source of truth). Measured from the last WRITE,
         * not the last read — otherwise a member that is polled every frame (refit screen,
         * campaign fleet render) would never age out and stale data would be served forever.
         */
        private const val CACHE_TTL_GAME_DAYS = 2L

        /**
         * Sentinel: forces eviction when time can't be measured (null sector/clock) or is mismatched.
         * Deliberately 2x the TTL: a genuine floored elapsed can never be that large at the FIRST
         * expiry (floored days first reach CACHE_TTL_GAME_DAYS+1), so the log label branching on
         * elapsed == EVICT_FROM_CACHE_DAYS stays unambiguous. It is still > CACHE_TTL_GAME_DAYS, so
         * the plain Long comparison triggers the eviction exactly as before. */
        private const val EVICT_FROM_CACHE_DAYS = CACHE_TTL_GAME_DAYS * 2L

        /** How often we do a full sweep for expired entries (every N calls to get()). */
        private const val CLEANUP_INTERVAL = 1000

        /**
         * Upper bound on the number of distinct object GRAPHS a single tag write is mirrored onto by
         * [writeThrough]. This counts tree ROOTS, not module nodes: a ship's actual module count is
         * orthogonal (each root's station-module tree is walked node-by-node in [findMirrorNode],
         * never enumerated here). The four graphs, all subtrees of the same acting ship, are:
         *
         * 1. the member's own variant tree ([FleetMemberAPI.variant])
         * 2. the resolved root tree ([FleetMemberUtils.findShipScopedRootVariant])
         * 3. the REFIT contract tree ([FleetMemberAPI.checkRefitVariant])
         * 4. the refit DISPLAY working tree ([getRefitDisplayVariant])
         *
         * Each of these may alias one another (a single-module ship resolves all four to the same
         * variant), and [writeThrough] dedupes by identity before populating the list.
         */
        private const val NUMBER_OF_GRAPHS = 4
    }

    private val cache: MutableMap<FleetMemberAPI, MutableMap<String, ShipModifications>> = WeakHashMap()
    private val EXOTICA_INDICATOR = "$\$EXOTICA$$"

    /**
     * Tracks the last game-time each member's cached data was WRITTEN (data-age TTL eviction).
     * Updated only on set()/cache insert — cache HITS must NOT extend the lifetime.
     */
    private val lastWrite: MutableMap<FleetMemberAPI, Long> = WeakHashMap()

    /** Lightweight counter for periodic sweeps. */
    private var accessCounter: Long = 0

    /**
     * Drops every cached ship modification mirror and its write timestamp.
     *
     * Safe to call at any time: the cache is only a mirror of the variant tags (the durable source
     * of truth), so clearing it merely forces the next reads to re-sync straight from those tags.
     * Intended for lifecycle boundaries (application load / game load) where the FleetMemberAPI
     * instances held by the WeakHashMap keys may reference stale or discarded fleet members.
     */
    fun clearCache() {
        cache.clear()
        lastWrite.clear()
        accessCounter = 0
    }

    override fun get(member: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications? {
        val variantId = variant.hullVariantId

        // Periodic sweep for members whose cached data age has exceeded the TTL
        if (++accessCounter % CLEANUP_INTERVAL == 0L) {
            sweepExpired()
        }

        val memberCache = cache[member]
        if (memberCache != null) {
            val writeTime = lastWrite[member]
            if (writeTime != null) {
                // TTL is measured from the last WRITE, not the last read. A member read every
                // frame must still re-sync from its tags once CACHE_TTL_GAME_DAYS have passed
                // since its data was written — otherwise the mirror never ages out and stale
                // data would be served indefinitely.
                val elapsed = elapsedSince(writeTime)
                if (elapsed > CACHE_TTL_GAME_DAYS) {
                    // Two distinct failure modes, logged separately so genuine data-age evictions
                    // can be told apart from the clock-unavailable sentinel:
                    //   days=N            -> data genuinely older than the TTL
                    //   CLOCK-UNAVAILABLE -> elapsed == EVICT_FROM_CACHE_DAYS, i.e. time could not
                    //                        be measured (null sector/clock or clock mismatch).
                    cache.remove(member)
                    lastWrite.remove(member)
                    // fall through to tag read below
                } else {
                    val cacheMods = memberCache[variantId]
                    if (cacheMods != null) {
                        return cacheMods
                    }

                    val fuzzyKey = findFuzzyKey(member, variantId)
                    if (fuzzyKey.isPresent()) {
                        val matchId = fuzzyKey.get()
                        val fuzzyMods = memberCache[matchId]
                        return fuzzyMods
                    }
                }
            }
        }

        if (variant == member.variant && variant.source != VariantSource.REFIT) {
            member.fixVariant()
        }

        getFromVariant(variant)?.let {
            if (StarsectorAPIInteractor.runningFromRefitOrFleetScreen()) {
                return it
            } else {
                cache.getOrPut(member) { mutableMapOf() }[variantId] = it
                lastWrite[member] = currentGameTime()
            }
            return it
        }
        return null
    }

    override fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
        // Write-side invalidation BEFORE the fresh write: purge stale mirrors of this variantId
        // from every member's cache so the new data can never be shadowed by a stale sibling
        // served via exact or fuzzy match on a later read.
        invalidateVariantAcrossAll(variant.hullVariantId)

        if (variant == member.variant && variant.source != VariantSource.REFIT) {
            member.fixVariant()
        }

        removeFromTags(variant)

        val tag = EXOTICA_INDICATOR + convertToJson(member, mods)
        variant.addTag(tag)

        // Write-through: mirror the write onto every object graph a later whole-ship read may
        // walk (the member's own variant tree, the resolved root tree, the REFIT display tree).
        // An install/removal that lands on a transient child FM variant or a refit clone must
        // reach the equivalent node in each sibling graph, or the whole-ship decision keeps
        // reading stale tags. The equivalent node is the one in the same module slot; its
        // variantId only has to agree fuzzily, so 'left_0' still finds the root-tree's
        // 'left_Start' copy. Only that node is touched, never the root —
        // blanket-tagging the root would duplicate a child's installation across entries.
        writeThrough(member, variant, tag)

        cache.getOrPut(member) { mutableMapOf() }[variant.hullVariantId] = mods
        lastWrite[member] = currentGameTime()
    }

    override fun remove(member: FleetMemberAPI, variant: ShipVariantAPI) {
        // A removal must purge the cached mirrors too, or the removed exotic keeps being served
        // from another FM key until the TTL sweep happens to re-sync it.
        invalidateVariantAcrossAll(variant.hullVariantId)
        removeFromTags(variant)
        // Write-through removal: strip the stale EXOTICA tags from every sibling object graph the
        // whole-ship read may walk. This is the core fix for the strip bug — without it a removal
        // that lands on a child FM variant leaves the old tag on the root tree's clone, so
        // getWholeShipMods keeps decoding the removed exotic and the hullmod is never stripped.
        writeThrough(member, variant, null)
    }

    /**
     * Mirrors a single tag write ([tag] == null means strip) onto every object graph a later
     * whole-ship read may walk: the member's own variant tree, the resolved root tree (which may
     * be a different object after fixVariant churn), the REFIT variant tree, and the refit DISPLAY
     * working tree. Each graph is independently written at the node that stands for the written
     * module in that graph — see [findMirrorNode].
     *
     * The display tree is the graph the refit screen actually renders and re-binds on confirm; the
     * strip side already reaches it ([ExoticaTechHM.removeHullmodEverywhere]), so the install/tag
     * side must reach it too or the edited module's live OP/costs never react to an install until a
     * screen switch re-derives everything. It is attributed O(1) to the acting member via refit
     * state, so a write on one ship can never mirror onto another ship's display tree.
     *
     * @param member the member the write was made for
     * @param variant the variant instance the caller has just tagged (or stripped)
     * @param tag the tag to mirror, or null to strip the Exotica tag from the mirror nodes
     */
    private fun writeThrough(member: FleetMemberAPI, variant: ShipVariantAPI, tag: String?) {
        val rootGraph = FleetMemberUtils.findShipScopedRootVariant(member, variant)

        val graphs = ArrayList<ShipVariantAPI>(NUMBER_OF_GRAPHS)
        member.variant?.let { graphs.add(it) }
        rootGraph?.let { rootVariant ->
            if (rootVariant !== member.variant && graphs.none { it === rootVariant }) graphs.add(rootVariant)
        }
        val refitVariant = runCatching { member.checkRefitVariant() }.getOrNull()
        if (refitVariant != null && graphs.none { it === refitVariant }) graphs.add(refitVariant)
        val displayVariant = getRefitDisplayVariant(member)
        if (displayVariant != null && graphs.none { it === displayVariant }) graphs.add(displayVariant)

        // Every slot path used below is relative to ONE root: the ship's root tree when it
        // resolved; else the acting member's own variant (a ship without station modules resolves
        // no root tree, it IS its own root); else the written variant itself.
        val memberVariant: ShipVariantAPI? = member.variant
        val shipRoot: ShipVariantAPI = if (rootGraph != null) {
            rootGraph
        } else if (memberVariant != null) {
            memberVariant
        } else {
            variant
        }
        mirrorWrite(
            ShipGraphs(shipRoot, memberVariant, refitVariant, displayVariant, getRefitDisplayMemberVariant(member).orElse(null)),
            graphs,
            variant,
            tag
        )
    }

    /**
     * The engine-free half of [writeThrough]: writes [tag] onto the node of every graph in
     * [graphs] that stands for the written module, and leaves every other node alone.
     *
     * It is split from [writeThrough] so that this decision - which is all of issue #76 F2 - can
     * be unit-tested on plain variant trees, without a refit screen or a fleet.
     *
     * @param shipGraphs how the graphs of the ship relate to each other
     * @param graphs the roots of the graphs to mirror the write onto
     * @param variant the variant instance that was written
     * @param tag the tag to mirror, or null to strip the Exotica tag from the mirror nodes
     */
    private fun mirrorWrite(shipGraphs: ShipGraphs, graphs: List<ShipVariantAPI>, variant: ShipVariantAPI, tag: String?) {
        // Resolved once per write: WHICH module of the ship was written. A hullVariantId cannot
        // answer that, because several modules of one ship can carry the same id; the slot the
        // module is mounted in can.
        val writtenSlotPath = shipGraphs.slotPathOfWrittenVariant(graphs, variant)

        for (graph in graphs) {
            val target = findMirrorNode(shipGraphs, graph, variant, writtenSlotPath)
            if (target.isPresent()) {
                val targetVariant = target.get()
                removeFromTags(targetVariant)
                if (tag != null) {
                    targetVariant.addTag(tag)
                }
            }
        }
    }

    /**
     * Finds the node of [graph] that stands for the written module: the node a write to [variant]
     * must be mirrored onto so that this copy of the ship says the same as the copy that was
     * written.
     *
     * A module is identified by the slot it is mounted in, not by its hullVariantId alone. The
     * id-only lookup this replaces (findVariantInTree) returned the graph's root whenever the
     * root's id matched, and otherwise the FIRST child carrying the id. On a ship that mounts one
     * module variant in several slots that is the wrong module for every write but one, and the
     * root-first rule made the refit screen's selected module - a graph of a single node - receive
     * the write of every identical sibling (#76 F2).
     *
     * There are three ways to know the node, strongest first:
     *
     * 1. [graph] holds the written INSTANCE: it is that node, nothing is inferred.
     * 2. [graph] and [variant] are a refit working copy and its original
     *    ([ShipGraphs.isWorkingCopyPair]): they are one module by definition, so the node is the
     *    graph's root. This is what the root-first rule existed for and it still holds: for a
     *    single-module ship the whole tree is just the root, and the write target can be a refit
     *    clone ('ship_0') of the member's variant ('ship_Start') — without it member.variant
     *    would be skipped entirely and the whole-ship read on it would then see nothing. It is
     *    now limited to the two graphs that really are the same thing, instead of applying to any
     *    graph whose root happens to carry a matching id.
     * 3. Both the written module and [graph] can be placed on the ship: the node mounted in the
     *    written module's slot, as seen from this graph.
     *
     * In cases 2 and 3 the node must also carry the written variant's id, exactly or fuzzily
     * ('left_0' is the refit clone of 'left_Start'). Slot path and id together are the composite
     * key (hullVariantId, slot path) of a module; a node that sits in the right slot but is a
     * different variant altogether means the two graphs are not copies of each other, and it is
     * not written.
     *
     * What that comes to for each of the graphs [writeThrough] collects:
     *
     * - the member's own tree and the ship-scoped root tree are placed by instance or are the
     *   ship root itself, so every module of them is found by its slot;
     * - the refit variant and the display tree, while the SHIP is shown, are copies of the whole
     *   ship: every slot lines up with the root tree;
     * - the display tree while a MODULE is shown is one module's variant, detached from the
     *   ship's tree. A module whose id only one module of the ship carries is placed by that id.
     *   One of several identical modules can only be placed through its original (the edited
     *   member's own variant), and only when that is an instance of the ship's tree. When it is
     *   not, the display module is not placed: a write on it stays on it and on its original, and
     *   writes to other modules do not reach it. Nothing is lost by that: the refit screen saves
     *   the display variant into its slot ([exoticatechnologies.refit.RefitButtonAdder.syncVariantIfNeeded]).
     *
     * @param shipGraphs how the graphs of the ship relate to each other
     * @param graph the root of the graph to search
     * @param variant the variant instance that was written
     * @param writtenSlotPath the slot path of [variant] on the ship, or [Optional.empty] when it
     * could not be determined
     * @return the node to mirror the write onto, or [Optional.empty] when no node of this graph
     * provably stands for the written module. Empty is a deliberate answer and not a failure: the
     * written module may simply not be part of this graph (the graph is ANOTHER module of the
     * ship), or nothing proves which of several identical modules the graph is. No node would be
     * "sure to work" then - any node returned anyway would be a guess, and a wrong guess
     * overwrites another module's installations - so the caller leaves the graph untouched.
     */
    private fun findMirrorNode(
        shipGraphs: ShipGraphs,
        graph: ShipVariantAPI,
        variant: ShipVariantAPI,
        writtenSlotPath: Optional<String>
    ): Optional<ShipVariantAPI> {
        if (ModuleSlotPathsUtils.slotPathOf(graph, variant).isPresent()) {
            return Optional.of(variant)
        }

        if (shipGraphs.isWorkingCopyPair(graph, variant)) {
            return if (carriesSameVariantId(graph, variant)) {
                Optional.of(graph)
            } else {
                Optional.empty()
            }
        }

        if (writtenSlotPath.isPresent().not()) {
            // Nobody knows which module was written (it is a detached clone of one of several
            // identical modules), so no other graph can be asked for "the same slot".
            return Optional.empty()
        }
        val graphSlotPath = shipGraphs.slotPathOfGraph(graph)
        if (graphSlotPath.isPresent().not()) {
            // This graph cannot be placed on the ship, so its nodes have no known slot.
            return Optional.empty()
        }
        val slotPathInGraph = ModuleSlotPathsUtils.relativeSlotPath(writtenSlotPath.get(), graphSlotPath.get())
        if (slotPathInGraph.isPresent().not()) {
            // The written module is not mounted at or below this graph's root: the graph is a
            // DIFFERENT module of the ship. This is the case the root-first rule got wrong.
            return Optional.empty()
        }
        val node = ModuleSlotPathsUtils.variantAtSlotPath(graph, slotPathInGraph.get())
        return if (node.isPresent() && carriesSameVariantId(node.get(), variant)) {
            node
        } else {
            Optional.empty()
        }
    }

    /**
     * Whether two variants carry the same hullVariantId, exactly or fuzzily (the same prefix
     * before the last '_suffix', which is all that tells the refit and combat clones of one
     * variant apart - see [FleetMemberUtils.fuzzyVariantIdEquals]).
     *
     * @param first the first variant
     * @param second the second variant
     * @return true when the ids agree; false when they differ or the engine returned no id for
     * either variant
     */
    private fun carriesSameVariantId(first: ShipVariantAPI, second: ShipVariantAPI): Boolean {
        val firstId: String? = first.hullVariantId
        val secondId: String? = second.hullVariantId
        if (firstId == null || secondId == null) {
            return false
        }
        return firstId == secondId || FleetMemberUtils.fuzzyVariantIdEquals(firstId, secondId)
    }

    /**
     * How the object graphs of ONE ship relate to each other, for the duration of a single
     * [writeThrough]. It is built for one write and dropped when that write returns, so the
     * variant instances it looks at cannot go stale: nothing in here is cached or stored.
     *
     * It knows two things:
     *
     * - WHERE the root of a graph sits on the ship ([slotPathOfGraph]). The same slot path means
     *   the same module, in whichever copy of the ship.
     * - which graph is the refit WORKING COPY of which variant ([originalOf]). The refit screen
     *   edits a copy and never the original, and for a module that copy is a single detached
     *   variant that says nothing about where it belongs. Its original may: when the original is
     *   still an instance of the ship's own tree, its slot is known for certain.
     *
     * @param shipRoot the root variant that all slot paths are relative to
     * @param memberVariant the acting member's own variant, null when the engine returned none
     * @param refitVariant the acting member's refit variant ([checkRefitVariant]): the working
     * copy of [memberVariant] while the refit screen is editing that member, [memberVariant]
     * itself otherwise, null when it could not be read
     * @param displayVariant the refit screen's display working tree ([getRefitDisplayVariant]),
     * null when the refit screen is closed or is showing another ship
     * @param displayMemberVariant the variant that [displayVariant] is the working copy of
     * ([getRefitDisplayMemberVariant]), null under the same conditions
     */
    private class ShipGraphs(
        private val shipRoot: ShipVariantAPI,
        private val memberVariant: ShipVariantAPI?,
        private val refitVariant: ShipVariantAPI?,
        private val displayVariant: ShipVariantAPI?,
        private val displayMemberVariant: ShipVariantAPI?
    ) {
        /**
         * Finds what a refit working copy is a copy of.
         *
         * @param workingCopy the variant that may be a refit working copy
         * @return the variant that [workingCopy] is the working copy of, or [Optional.empty] when
         * it is neither of the two working copies known here, or is its own original (which is
         * the case whenever the refit screen is not editing that member)
         */
        fun originalOf(workingCopy: ShipVariantAPI): Optional<ShipVariantAPI> {
            if (workingCopy === refitVariant && memberVariant != null && memberVariant !== workingCopy) {
                return Optional.of(memberVariant)
            }
            if (workingCopy === displayVariant && displayMemberVariant != null && displayMemberVariant !== workingCopy) {
                return Optional.of(displayMemberVariant)
            }
            return Optional.empty()
        }

        /**
         * Whether two variants are a refit working copy and its original, in either order.
         *
         * @param first one variant
         * @param second the other variant
         * @return true when one of them is the working copy of the other
         */
        fun isWorkingCopyPair(first: ShipVariantAPI, second: ShipVariantAPI): Boolean {
            val originalOfFirst = originalOf(first)
            return if (originalOfFirst.isPresent() && originalOfFirst.get() === second) {
                true
            } else {
                val originalOfSecond = originalOf(second)
                originalOfSecond.isPresent() && originalOfSecond.get() === first
            }
        }

        /**
         * Places the root of a graph on the ship.
         *
         * Directly when possible ([ModuleSlotPathsUtils.slotPathOfGraphRoot]: by instance, as a copy
         * of the whole ship, or by an id that only one module of the ship carries). A refit
         * working copy that cannot be placed that way - a detached clone of one of several
         * identical modules - is placed through its original instead: the working copy is the
         * same module as its original, and the original may still be the very instance mounted
         * in the ship's tree.
         *
         * @param graphRoot the root of the graph to place
         * @return the slot path of [graphRoot] on the ship, or [Optional.empty] when neither it
         * nor its original can be placed without guessing
         */
        fun slotPathOfGraph(graphRoot: ShipVariantAPI): Optional<String> {
            val placed = ModuleSlotPathsUtils.slotPathOfGraphRoot(shipRoot, graphRoot)
            if (placed.isPresent()) {
                return placed
            }
            val original = originalOf(graphRoot)
            return if (original.isPresent()) {
                ModuleSlotPathsUtils.slotPathOfGraphRoot(shipRoot, original.get())
            } else {
                Optional.empty()
            }
        }

        /**
         * Works out which module of the ship was written.
         *
         * @param graphs the roots of the ship's graphs
         * @param variant the variant instance that was written
         * @return the slot path of [variant] on the ship, or [Optional.empty] when it sits in no
         * graph that can be placed and cannot be placed by itself either
         */
        fun slotPathOfWrittenVariant(graphs: List<ShipVariantAPI>, variant: ShipVariantAPI): Optional<String> {
            // Through a graph that holds this very instance first: its position inside that graph
            // is certain, so only the position of the graph itself has to be known.
            for (graph in graphs) {
                val slotPathInGraph = ModuleSlotPathsUtils.slotPathOf(graph, variant)
                if (slotPathInGraph.isPresent()) {
                    val graphSlotPath = slotPathOfGraph(graph)
                    if (graphSlotPath.isPresent()) {
                        return Optional.of(ModuleSlotPathsUtils.joinSlotPaths(graphSlotPath.get(), slotPathInGraph.get()))
                    }
                }
            }
            // Held by no graph that can be placed: place it as if it were a graph root itself.
            return slotPathOfGraph(variant)
        }
    }

    /**
     * Test-only entry to [mirrorWrite], the engine-free half of [writeThrough]. It takes the
     * graphs as plain arguments, which production code gets from the fleet member and the refit
     * screen.
     *
     * **NEVER USE THIS IN PRODUCTION CODE**
     *
     * @param shipRoot the root variant that all slot paths are relative to
     * @param memberVariant the acting member's own variant
     * @param refitVariant the acting member's refit variant
     * @param displayVariant the refit screen's display working tree
     * @param displayMemberVariant the variant that [displayVariant] is the working copy of
     * @param graphs the roots of the graphs to mirror the write onto
     * @param variant the variant instance that was written
     * @param tag the tag to mirror, or null to strip the Exotica tag from the mirror nodes
     */
    @VisibleForTesting
    @TestOnly
    internal fun testsOnly_mirrorWrite(
        shipRoot: ShipVariantAPI,
        memberVariant: ShipVariantAPI?,
        refitVariant: ShipVariantAPI?,
        displayVariant: ShipVariantAPI?,
        displayMemberVariant: ShipVariantAPI?,
        graphs: List<ShipVariantAPI>,
        variant: ShipVariantAPI,
        tag: String?
    ) {
        mirrorWrite(ShipGraphs(shipRoot, memberVariant, refitVariant, displayVariant, displayMemberVariant), graphs, variant, tag)
    }

    private fun removeFromTags(variant: ShipVariantAPI) {
        variant.tags.removeAll { it.startsWith(EXOTICA_INDICATOR) }
    }

    /**
     * Current in-game timestamp (days). Falls back to 0L if clock isn't available.
     */
    private fun currentGameTime(): Long {
        return Global.getSector()?.clock?.timestamp ?: 0L
    }

    /**
     * Whole days elapsed since the given timestamp, as a whole number (floored).
     *
     * **NOTE:** Instead of returning e.g. "1.9" this floors towards 0 and returns "1". The Long
     * comparison against CACHE_TTL_GAME_DAYS is deliberately kept cheaper than a float compare — the
     * exact 2-vs-3-day boundary is irrelevant, it only needs to be "short".
     *
     * @param lastTime the last "timestamp" to check [com.fs.starfarer.api.campaign.CampaignClockAPI.getElapsedDaysSince] from
     *
     * @return number of days since [lastTime] (floored), **OR** [EVICT_FROM_CACHE_DAYS] when time
     * can't be measured (main menu / null sector) or when the clock is mismatched (negative elapsed).
     * The sentinel is deliberately 2x the TTL: it still triggers an eviction, yet a genuine floored
     * elapsed can never collide with it at first expiry — stale entries are never served as fresh,
     * and the cache stays bounded.
     */
    private fun elapsedSince(lastTime: Long): Long {
        val elapsed = Global.getSector()?.clock?.getElapsedDaysSince(lastTime)
        return when {
            elapsed == null || elapsed < 0f -> EVICT_FROM_CACHE_DAYS
            else -> elapsed.toLong()
        }
    }

    /**
     * Periodic full sweep: drops members whose cached data is older than CACHE_TTL_GAME_DAYS,
     * or whose age cannot be measured (clock unavailable / mismatched) — the sentinel is also
     * > CACHE_TTL_GAME_DAYS, so a bounded cache is guaranteed even across save/load transitions.
     */
    private fun sweepExpired() {
        val iter = cache.entries.iterator()
        while (iter.hasNext()) {
            val (member, _) = iter.next()
            val last = lastWrite[member]
            if (last != null) {
                val elapsed = elapsedSince(last)
                if (elapsed > CACHE_TTL_GAME_DAYS) {
                    iter.remove()
                    lastWrite.remove(member)
                }
            }
        }
    }

    /**
     * Write-side cross-instance invalidation.
     *
     * The cache is keyed by FleetMemberAPI identity, but a variant can be written under one FM
     * instance (a transient refit FM) and later read under a different one (combat FM, or the root
     * FM for child modules via the variant-tree walk). FleetMemberAPI has no stable id usable as a
     * cache key across those identities — but the variantId is stable. So on every write/removal we
     * purge every cached mirror of that variantId across ALL members, exact-match or prefix-fuzzy.
     *
     * This runs only on the cold write path (install/remove), never inside get(). The mirrors it
     * drops are re-synced from the variant tags (the durable source of truth) on their next read —
     * the cost is one tag read per affected member, only when it is next requested. Fuzzy matching
     * (stripping the last '_suffix', mirroring [findFuzzyKey]) is required because the same module
     * variant appears under many suffixes ('left_0' / 'left_Start' / 'left_Hull') depending on where
     * the engine sourced it; writing any one of them must invalidate all siblings.
     *
     * @param variantId the variantId whose mirrors must be dropped
     */
    private fun invalidateVariantAcrossAll(variantId: String) {
        // Mirrors findFuzzyKey's prefix derivation; precomputed once, not per entry.
        // substringBeforeLast("_", variantId) falls back to the whole id when there's no
        // underscore, degrading cleanly to exact-match-only for flat variant names.
        val prefix = variantId.substringBeforeLast("_", variantId)

        val iter = cache.entries.iterator()
        while (iter.hasNext()) {
            val (member, memberCache) = iter.next()
            val purged = memberCache.keys.removeAll { cachedId ->
                cachedId == variantId || cachedId.substringBeforeLast("_", cachedId) == prefix
            }
            // if we ended up purging something, check if we ended up purging everything...
            if (purged) {
                if (memberCache.isEmpty()) {
                    // No data left for this member — drop the member entirely so neither the
                    // inner map nor its lastWrite timestamp lingers as dead weight.
                    iter.remove()
                    lastWrite.remove(member)
                }
            }
        }
    }

    private fun findFuzzyKey(member: FleetMemberAPI, variantId: String): Optional<String> {
        val cacheForMember = cache[member]
        return if (cacheForMember != null) {
            // we have it, so find the best-matching key
            val lastUnderscoreIndex = variantId.lastIndexOf("_")
            // hopefully all ship variants have the snakecase naming scheme but if they don't...
            if (lastUnderscoreIndex <= 0) {
                // no underscore, bail out
                logIfOverMinLogLevel(
                    logMsg = "No underscore found in cache keyset for variantId ${variantId} - bailing out!",
                    logLevel = Level.INFO
                )
            } else {
                val prefix = variantId.substringBeforeLast("_", variantId)
                // Now, find the best-matching key to this prefix
                // If it matches completely on everything but the last '_suffix' part - we'll consider it "fuzzy equal"
                // We use this to match "different" variants we get from the game for the same member
                // e.g. 'tbj_overslaught_left_0' to 'tbj_overslaught_left_Start' and 'tbj_overslaught_right_1' to 'tbj_overslaught_right_Start'
                for (cacheVariantId in cacheForMember.keys) {
                    val sanitizedCacheVariantId = cacheVariantId.substringBeforeLast("_", cacheVariantId)
                    if (sanitizedCacheVariantId.contentEquals(prefix)) {
                        return Optional.of(cacheVariantId)
                    }
                }
            }

            // In case we didn't find a fuzzy-matching key or we bailed out due to index - return empty
            Optional.empty()
        } else {
            // Nothing in the cache, bail out
            Optional.empty()
        }
    }

    private fun logIfOverMinLogLevel(logMsg: String, logLevel: Level) {
        shouldLog(
            logMsg = logMsg,
            logger = logger,
            logLevel = logLevel,
            minLogLevel = MIN_LOG_LEVEL
        )
    }

    fun getFromVariant(variant: ShipVariantAPI): ShipModifications? {
        val exoticaTag: String? = variant.tags.firstOrNull { it.startsWith(EXOTICA_INDICATOR) }

        if (exoticaTag != null) {
            val jsonStr = exoticaTag.replace(EXOTICA_INDICATOR, "")
            val mods = convertFromJson(jsonStr)
            return mods
        }

        return null
    }

    fun convertFromJson(json: String): ShipModifications {
        try {
            val modsObj = JSONObject(json)
            return ShipModifications(modsObj)
        } catch (ex: JSONException) {
            throw RuntimeException("Failed to load Exotica modifications object from variant tags.", ex)
        }
    }

    fun convertToJson(member:FleetMemberAPI, mods: ShipModifications): String {
        try {
            return mods.toJson(member).toString()
        } catch (ex: JSONException) {
            throw RuntimeException("Failed to save Exotica object.")
        }
    }
}
