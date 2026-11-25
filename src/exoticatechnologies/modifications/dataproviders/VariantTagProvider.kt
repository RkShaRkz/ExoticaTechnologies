package exoticatechnologies.modifications.dataproviders

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.VariantSource
import exoticatechnologies.modifications.ShipModLoader
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.refit.getRefitDisplayMemberVariant
import exoticatechnologies.refit.getRefitDisplayVariant
import exoticatechnologies.util.*
import exoticatechnologies.util.datastructures.Optional
import org.apache.log4j.Level
import org.apache.log4j.Logger
import java.util.WeakHashMap

open class VariantTagProvider : AbstractDataProvider(), ModuleSlotDataProvider {
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
         * orthogonal (each root's station-module tree is walked node-by-node in
         * [ModuleWriteGraphContext.findMirrorVariant], never enumerated here). The four graphs, all
         * subtrees of the same acting ship, are:
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

    /**
     * Mirrors tag data per member, hull id, and slot path.
     *
     * Hull ids repeat across identical sibling modules, so a hull-id-only mirror made two modules
     * share one cached result even though their tags were independent.
     */
    private val cache: MutableMap<FleetMemberAPI, MutableMap<String, MutableMap<String, ShipModifications>>> = WeakHashMap()
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
        return findModifications(member, variant, Optional.empty())
    }

    override fun getAtSlotPath(member: FleetMemberAPI, variant: ShipVariantAPI, slotPath: String): ShipModifications? {
        return findModifications(member, variant, Optional.of(slotPath))
    }

    /**
     * Reads a module's tags through a cache keyed by its actual slot.
     *
     * Tree walks already know the slot, and plain reads can usually derive it; without that key a
     * fuzzy hull-id hit could return a sibling module's cached modifications.
     */
    private fun findModifications(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        requestedSlotPath: Optional<String>
    ): ShipModifications? {
        val variantId = variant.hullVariantId
        val slotPath = resolveSlotPath(member, variant, requestedSlotPath)

        // Periodic sweep for members whose cached data age has exceeded the TTL
        if (++accessCounter % CLEANUP_INTERVAL == 0L) {
            sweepExpired()
        }

        val memberCache = cache[member]
        if (memberCache != null && slotPath.isPresent()) {
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
                    val cachedMods = findCachedModifications(memberCache, variantId, slotPath.get())
                    if (cachedMods != null) {
                        return cachedMods
                    }
                }
            }
        }

        if (variant == member.variant && variant.source != VariantSource.REFIT) {
            member.fixVariant()
        }

        val fresh = getFromVariant(variant)
        if (fresh != null) {
            if (!StarsectorAPIInteractor.runningFromRefitOrFleetScreen()) {
                rememberModificationsInCache(member, variantId, fresh, slotPath)
            }
            return fresh
        }
        return null
    }

    /**
     * Finds the best available slot path for a plain read.
     *
     * Instance identity is preferred over hull id because the engine can place the same stock
     * object at more than one slot; hull matching is only a fallback for working-copy graphs.
     */
    private fun resolveSlotPath(
        member: FleetMemberAPI,
        variant: ShipVariantAPI,
        requestedSlotPath: Optional<String>
    ): Optional<String> {
        if (requestedSlotPath.isPresent()) {
            return requestedSlotPath
        }
        val shipVariant: ShipVariantAPI? = member.variant
        if (shipVariant == null) {
            return Optional.empty()
        }
        val byInstance = ModuleSlotPathsUtils.findInstanceSlotPath(shipVariant, variant)
        if (byInstance.isPresent()) {
            return byInstance
        }
        return ModuleSlotPathsUtils.findGraphRootSlotPath(shipVariant, variant)
    }

    /**
     * Looks up exact and fuzzy cache keys at one slot.
     *
     * Fuzzy support remains necessary for refit suffix churn, but the slot must match in both
     * branches or identical sibling modules would still share data.
     */
    private fun findCachedModifications(
        memberCache: MutableMap<String, MutableMap<String, ShipModifications>>,
        variantId: String,
        slotPath: String
    ): ShipModifications? {
        val variantCache = memberCache[variantId]
        if (variantCache != null) {
            val exact = variantCache[slotPath]
            if (exact != null) {
                return exact
            }
        }

        val fuzzyKey = findFuzzyVariantId(memberCache, variantId)
        if (fuzzyKey.isPresent()) {
            val fuzzyVariantCache = memberCache[fuzzyKey.get()]
            if (fuzzyVariantCache != null) {
                return fuzzyVariantCache[slotPath]
            }
        }
        return null
    }

    /**
     * Stores a fresh tag read only when its slot is known.
     *
     * Unknown-slot data must not be cached: a hull-id-only key is exactly the ambiguity that lets
     * one sibling's modifications leak into another.
     */
    private fun rememberModificationsInCache(
        member: FleetMemberAPI,
        variantId: String,
        mods: ShipModifications,
        slotPath: Optional<String>
    ) {
        if (!slotPath.isPresent()) {
            return
        }
        cache.getOrPut(member) { mutableMapOf() }
                .getOrPut(variantId) { mutableMapOf() }[slotPath.get()] = mods
        lastWrite[member] = currentGameTime()
    }

    override fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
        val slotPath = resolveSlotPath(member, variant, Optional.empty())

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
        // reach the equivalent slot in each sibling graph, or the whole-ship decision keeps
        // reading stale tags. Slot paths decide the matching node; fuzzy hull ids are only a
        // last resort for working-copy churn ('left_0' -> 'left_Start'); only the matching node
        // is touched, never the root — blanket-tagging the root would duplicate a child's
        // installation across entries.
        writeThrough(member, variant, tag)

        rememberModificationsInCache(member, variant.hullVariantId, mods, slotPath)
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
     * working tree. [ModuleWriteGraphContext] maps the written object's slot into each graph so a
     * write can never land on a different sibling with the same hull id.
     *
     * The display tree is the graph the refit screen actually renders and re-binds on confirm; the
     * strip side already reaches it ([ExoticaTechHM.removeHullmodEverywhere]), so the install/tag
     * side must reach it too or the edited module's live OP/costs never react to an install until a
     * screen switch re-derives everything. It is attributed O(1) to the acting member via refit
     * state, so a write on one ship can never mirror onto another ship's display tree.
     */
    private fun writeThrough(member: FleetMemberAPI, variant: ShipVariantAPI, tag: String?) {
        val originalVariant: ShipVariantAPI? = member.variant
        if (originalVariant == null) {
            return
        }
        val rootGraph = FleetMemberUtils.findShipScopedRootVariant(member, variant)

        val graphs = ArrayList<ShipVariantAPI>(NUMBER_OF_GRAPHS)
        graphs.add(originalVariant)
        if (rootGraph != null && graphs.none { it === rootGraph }) {
            graphs.add(rootGraph)
        }
        val refitVariant = runCatching { member.checkRefitVariant() }.getOrNull()
        if (refitVariant != null && graphs.none { it === refitVariant }) {
            graphs.add(refitVariant)
        }
        val displayVariant = getRefitDisplayVariant(member)
        if (displayVariant != null && graphs.none { it === displayVariant }) {
            graphs.add(displayVariant)
        }

        val displayMemberVariant = getRefitDisplayMemberVariant(member).firstOrNull()
        val context = ModuleWriteGraphContext(originalVariant, graphs, displayVariant, displayMemberVariant)
        val writtenSlotPathOptional = context.findWrittenSlotPath(variant)
        val writtenSlotPath: String? = if (writtenSlotPathOptional.isPresent()) {
            writtenSlotPathOptional.get()
        } else {
            null
        }

        for (graph in graphs) {
            val target = context.findMirrorVariant(graph, variant, writtenSlotPath)
            if (target != null) {
                removeFromTags(target)
                if (tag != null) {
                    target.addTag(tag)
                }
            }
        }
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
     * purge every cached mirror of that variantId across ALL members and slots, exact-match or
     * prefix-fuzzy.
     *
     * This runs only on the cold write path (install/remove), never inside get(). The mirrors it
     * drops are re-synced from the variant tags (the durable source of truth) on their next read —
     * the cost is one tag read per affected member, only when it is next requested. Fuzzy matching
     * (stripping the last '_suffix', mirroring [findFuzzyVariantId]) is required because the same module
     * variant appears under many suffixes ('left_0' / 'left_Start' / 'left_Hull') depending on where
     * the engine sourced it; writing any one of them must invalidate all siblings.
     *
     * @param variantId the variantId whose mirrors must be dropped
     */
    private fun invalidateVariantAcrossAll(variantId: String) {
        // Mirrors findFuzzyVariantId's prefix derivation; precomputed once, not per entry.
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

    /**
     * Finds a cached hull id that differs only in its refit suffix.
     *
     * The suffix changes when the engine clones variants, but the slot path is carried separately,
     * so this fallback cannot collapse two modules in different slots.
     */
    private fun findFuzzyVariantId(
        memberCache: MutableMap<String, MutableMap<String, ShipModifications>>,
        variantId: String
    ): Optional<String> {
        // we have the member's cache, so find the best-matching key
        val lastUnderscoreIndex = variantId.lastIndexOf("_")
        // hopefully all ship variants have the snakecase naming scheme but if they don't...
        if (lastUnderscoreIndex <= 0) {
            // no underscore, bail out
            logIfOverMinLogLevel(
                logMsg = "No underscore found in cache keyset for variantId ${variantId} - bailing out!",
                logLevel = Level.INFO
            )
            return Optional.empty()
        }

        val prefix = variantId.substringBeforeLast("_", variantId)
        // Now, find the best-matching key to this prefix
        // If it matches completely on everything but the last '_suffix' part - we'll consider it "fuzzy equal"
        // We use this to match "different" variants we get from the game for the same member
        // e.g. 'tbj_overslaught_left_0' to 'tbj_overslaught_left_Start' and 'tbj_overslaught_right_1' to 'tbj_overslaught_right_Start'
        for (cacheVariantId in memberCache.keys) {
            val sanitizedCacheVariantId = cacheVariantId.substringBeforeLast("_", cacheVariantId)
            if (sanitizedCacheVariantId.contentEquals(prefix)) {
                return Optional.of(cacheVariantId)
            }
        }

        // No fuzzy-matching key, or we bailed out due to index - return empty
        return Optional.empty()
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
}
