package exoticatechnologies.modifications

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.VariantSource
import exoticatechnologies.util.datastructures.Optional
import exoticatechnologies.util.fixVariant
import exoticatechnologies.util.getRefitVariant
import exoticatechnologies.util.log
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.json.JSONException
import org.json.JSONObject
import java.util.WeakHashMap

open class VariantTagProvider : ShipModLoader.Provider {
    companion object {
        @JvmStatic
        var inst: VariantTagProvider = VariantTagProvider()
        private val logger = Logger.getLogger(VariantTagProvider::class.java)

        private fun diagnosticLog(message: String) {
            logger.info("[DIAG] $message")
        }
    }

    var currGets: Int = 0
    val maxGetsPerMember: Int = 10

    val cache: MutableMap<FleetMemberAPI, MutableMap<String, ShipModifications>> = WeakHashMap()
    val EXOTICA_INDICATOR = "$\$EXOTICA$$"

    override fun get(member: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications? {
        val members: Int = Global.getSector()?.playerFleet?.numMembersFast ?: 0
        if (currGets++ >= maxGetsPerMember * members) {
            cache.clear()
            currGets = 0
        }

        val variantId = variant.hullVariantId
        val cacheMods: ShipModifications? = cache[member]?.get(variantId)

        if (cacheMods != null) {
            diagnosticLog("VariantTagProvider.get | CACHE HIT | member=${member.id} variant=$variantId variantTags=${variant.tags.size} result=UPGRADES: ${cacheMods.getUpgradeMap()}, EXOTICS: ${cacheMods.getExoticSet()}")
            return cacheMods
        }

        val fuzzyKey = findFuzzyKey(member, variantId)
        if (fuzzyKey.isPresent()) {
            val matchId = fuzzyKey.get()
            val fuzzyMods = cache[member]!![matchId]
            diagnosticLog("VariantTagProvider.get | FUZZY CACHE HIT | member=${member.id} query=$variantId match=$matchId")
            return fuzzyMods
        }

        if (variant == member.variant && variant.source != VariantSource.REFIT) {
            member.fixVariant()
        }

        getFromVariant(variant)?.let {
            if (Global.getSector().campaignUI.currentCoreTab == CoreUITabId.REFIT || Global.getSector().campaignUI.currentCoreTab == CoreUITabId.FLEET) {
                diagnosticLog("VariantTagProvider.get | TAG READ (no cache) | member=${member.id} variant=$variantId variantTags=${variant.tags.size} result=UPGRADES: ${it.getUpgradeMap()}, EXOTICS: ${it.getExoticSet()}")
                return it
            } else {
                cache.getOrPut(member) { mutableMapOf() }[variantId] = it
            }
            diagnosticLog("VariantTagProvider.get | TAG READ + cache | member=${member.id} variant=$variantId variantTags=${variant.tags.size} result=UPGRADES: ${it.getUpgradeMap()}, EXOTICS: ${it.getExoticSet()}")
            return it
        }
        diagnosticLog("VariantTagProvider.get | NULL  | member=${member.id} variant=$variantId variantTags=${variant.tags.size}")
        return null
    }

    override fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
        if (variant == member.variant && variant.source != VariantSource.REFIT) {
            member.fixVariant()
        }

        removeFromTags(variant)

        val tag = EXOTICA_INDICATOR + convertToJson(member, mods)
        variant.addTag(tag)
        // Add to refit variant if it's not already there
        if (variant.getRefitVariant().tags.contains(tag).not()) {
            // This is new
            variant.getRefitVariant().addTag(tag)
        }
        if (variant != member.variant) {
            // TODO we add the tag to member variant if we're dealing with root module's member and variant
            // TODO otherwise, if we're dealing with child's member/variant, we add the tag to the respective "laxer" variant.
            member.variant.addTag(tag)
        }

        cache.getOrPut(member) { mutableMapOf() }[variant.hullVariantId] = mods
    }

    override fun remove(member: FleetMemberAPI, variant: ShipVariantAPI) {
        removeFromTags(variant)
    }

    private fun removeFromTags(variant: ShipVariantAPI) {
        variant.tags.removeAll { it.startsWith(EXOTICA_INDICATOR) }
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
