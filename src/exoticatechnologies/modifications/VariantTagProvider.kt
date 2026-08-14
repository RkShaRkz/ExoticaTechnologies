package exoticatechnologies.modifications

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.VariantSource
import exoticatechnologies.refit.checkRefitVariant
import exoticatechnologies.util.FleetMemberUtils
import exoticatechnologies.util.StarsectorAPIInteractor
import exoticatechnologies.util.datastructures.Optional
import exoticatechnologies.util.fixVariant
import exoticatechnologies.util.forEachModuleVariant
import exoticatechnologies.util.log
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.json.JSONException
import org.json.JSONObject
import java.util.WeakHashMap

open class VariantTagProvider : ShipModLoader.Provider {
    companion object {
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
        // reading stale tags. Matching is fuzzy by variantId prefix so 'left_0' finds the
        // root-tree's 'left_Start' copy; only the matching node is touched, never the root —
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
     * be a different object after fixVariant churn), and the REFIT display tree. Each graph is
     * independently written at the node fuzzy-matching the target variant — see [findVariantInTree].
     */
    private fun writeThrough(member: FleetMemberAPI, variant: ShipVariantAPI, tag: String?) {
        val targetId = variant.hullVariantId

        val graphs = ArrayList<ShipVariantAPI>(3)
        member.variant?.let { graphs.add(it) }
        val rootVariant = FleetMemberUtils.findRootVariant(member, variant)
        if (rootVariant !== member.variant) graphs.add(rootVariant)
        val refitVariant = runCatching { member.checkRefitVariant() }.getOrNull()
        if (refitVariant != null && graphs.none { it === refitVariant }) graphs.add(refitVariant)

        for (graph in graphs) {
            val target = findVariantInTree(graph, targetId) ?: continue
            removeFromTags(target)
            if (tag != null) {
                target.addTag(tag)
            }
        }
    }

    /**
     * Finds the node in [root]'s variant tree corresponding to [targetId]: an exact hullVariantId
     * match first, then a fuzzy match (same prefix before the last '_suffix'). Returns null when
     * the tree has no such node (e.g. a child variant whose tree is unreachable from [root]).
     */
    private fun findVariantInTree(root: ShipVariantAPI, targetId: String): ShipVariantAPI? {
        // The root IS fuzzy-matched too: for a single-module ship the whole tree is just the root,
        // and the write target can be a refit clone ('ship_0') of the member's variant ('ship_Start')
        // — exact-only matching would skip member.variant entirely and the whole-ship read on it
        // would then see nothing. For module ships the root's prefix differs from every child's, so
        // a root fuzzy-match never collides with a child target.
        if (root.hullVariantId == targetId || fuzzyVariantIdEquals(root.hullVariantId, targetId)) return root
        var exact: ShipVariantAPI? = null
        var fuzzy: ShipVariantAPI? = null
        root.forEachModuleVariant { childV ->
            when {
                exact != null -> { /* already found an exact node, keep looking for nothing */ }
                childV.hullVariantId == targetId -> exact = childV
                fuzzy == null && fuzzyVariantIdEquals(childV.hullVariantId, targetId) -> fuzzy = childV
            }
        }
        return exact ?: fuzzy
    }

    // Same rule as findFuzzyKey/invalidateVariantAcrossAll: refit/combat clones differ only in
    // the last '_suffix' ('left_0' vs 'left_Start' vs 'left_Hull'), so compare stripped prefixes.
    private fun fuzzyVariantIdEquals(a: String, b: String): Boolean {
        return a.substringBeforeLast("_", a) == b.substringBeforeLast("_", b)
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
                log(logMsg = "No underscore found in cache keyset for variantId ${variantId} - bailing out!", logger = logger, logLevel = Level.INFO)
            } else {
                val prefix = variantId.substring(0, lastUnderscoreIndex)
                // Now, find the best-matching key to this prefix
                // If it matches completely on everything but the last '_suffix' part - we'll consider it "fuzzy equal"
                // We use this to match "different" variants we get from the game for the same member
                // e.g. 'tbj_overslaught_left_0' to 'tbj_overslaught_left_Start' and 'tbj_overslaught_right_1' to 'tbj_overslaught_right_Start'
                for (cacheVariantId in cacheForMember.keys) {
                    val lastCacheUnderscoreIndex = cacheVariantId.lastIndexOf("_")
                    val sanitizedCacheVariantId = cacheVariantId.substring(0, lastCacheUnderscoreIndex)
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
