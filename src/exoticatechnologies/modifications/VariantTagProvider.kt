package exoticatechnologies.modifications

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CoreUITabId
import com.fs.starfarer.api.combat.ShipVariantAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.VariantSource
import exoticatechnologies.util.fixVariant
import org.apache.log4j.Logger
import org.json.JSONException
import org.json.JSONObject
import java.util.WeakHashMap
import java.util.logging.Level

open class VariantTagProvider : ShipModLoader.Provider {
    companion object {
        @JvmStatic
        var inst: VariantTagProvider = VariantTagProvider()
        private val log = Logger.getLogger(VariantTagProvider::class.java)

        private fun diagnosticLog(message: String) {
            log.info("[DIAG] $message")
        }
    }

    var currGets: Int = 0
    val maxGetsPerMember: Int = 10

    val cache: MutableMap<FleetMemberAPI, ShipModifications> = WeakHashMap()
    val EXOTICA_INDICATOR = "$\$EXOTICA$$"

    override fun get(member: FleetMemberAPI, variant: ShipVariantAPI): ShipModifications? {
        val members: Int = Global.getSector()?.playerFleet?.numMembersFast ?: 0
        if (currGets++ >= maxGetsPerMember * members) {
            cache.clear()
            currGets = 0
        }

        val cacheMods: ShipModifications? = cache.keys
            .filter { it == member && it.specId == member.specId }
            .firstNotNullOfOrNull { cache[it] }

        if (cacheMods != null) {
            diagnosticLog("VariantTagProvider.get | CACHE HIT | member=${member.id} variant=${variant.hullVariantId} variantTags=${variant.tags.size} result=UPGRADES: ${cacheMods.getUpgradeMap()}, EXOTICS: ${cacheMods.getExoticSet()}")
            return cacheMods
        }

        if (variant == member.variant && variant.source != VariantSource.REFIT) {
            member.fixVariant()
        }

        getFromVariant(variant)?.let {
            if (Global.getSector().campaignUI.currentCoreTab == CoreUITabId.REFIT || Global.getSector().campaignUI.currentCoreTab == CoreUITabId.FLEET) {
                diagnosticLog("VariantTagProvider.get | TAG READ (no cache) | member=${member.id} variant=${variant.hullVariantId} variantTags=${variant.tags.size} result=UPGRADES: ${it.getUpgradeMap()}, EXOTICS: ${it.getExoticSet()}")
                return it
            } else {
                cache[member] = it
            }
            diagnosticLog("VariantTagProvider.get | TAG READ + cache | member=${member.id} variant=${variant.hullVariantId} variantTags=${variant.tags.size} result=UPGRADES: ${it.getUpgradeMap()}, EXOTICS: ${it.getExoticSet()}")
            return it
        }
        diagnosticLog("VariantTagProvider.get | NULL  | member=${member.id} variant=${variant.hullVariantId} variantTags=${variant.tags.size}")
        return null
    }

    override fun set(member: FleetMemberAPI, variant: ShipVariantAPI, mods: ShipModifications) {
        if (variant == member.variant && variant.source != VariantSource.REFIT) {
            member.fixVariant()
        }

        removeFromTags(variant)

        val tag = EXOTICA_INDICATOR + convertToJson(member, mods)
        variant.addTag(tag)
        if (variant != member.variant) {
            member.variant.addTag(tag)
        }

        cache[member] = mods
    }

    override fun remove(member: FleetMemberAPI, variant: ShipVariantAPI) {
        removeFromTags(variant)
    }

    private fun removeFromTags(variant: ShipVariantAPI) {
        variant.tags.removeAll { it.startsWith(EXOTICA_INDICATOR) }
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
