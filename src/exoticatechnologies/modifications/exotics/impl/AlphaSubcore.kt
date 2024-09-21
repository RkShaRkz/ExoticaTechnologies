package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.hullmods.AlphaSubcoreHM
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import org.json.JSONObject
import java.awt.Color

class AlphaSubcore(key: String, settingsObj: JSONObject) :
    HullmodExotic(key, settingsObj, "et_alphasubcore", "AlphaSubcore", Color.cyan) {

    override fun getSalvageChance(chanceMult: Float): Float {
        return 0.05f * chanceMult
    }

    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI?): Boolean {
        return Utilities.hasItem(fleet.cargo, ITEM)
                || Utilities.hasItem(Misc.getStorageCargo(market), ITEM)
    }

    override fun removeItemsFromFleet(fleet: CampaignFleetAPI, member: FleetMemberAPI, market: MarketAPI?): Boolean {
        if (Utilities.hasItem(fleet.cargo, ITEM)) {
            Utilities.takeItemQuantity(fleet.cargo, ITEM, 1f)
        } else {
            Utilities.takeItemQuantity(Misc.getStorageCargo(market), ITEM, 1f)
        }
        return true
    }

    override fun modifyToolTip(
        tooltip: TooltipMakerAPI,
        title: UIComponentAPI,
        member: FleetMemberAPI,
        mods: ShipModifications,
        exoticData: ExoticData,
        expand: Boolean
    ) {
        if (expand) {
            StringUtils.getTranslation(key, "longDescription")
                .format("bandwidthIncrease", BANDWIDTH_INCREASE * getPositiveMult(member, mods, exoticData))
                .format("smallReduction", AlphaSubcoreHM.WEAPON_REDUCTIONS[WeaponAPI.WeaponSize.SMALL])
                .format("medReduction", AlphaSubcoreHM.WEAPON_REDUCTIONS[WeaponAPI.WeaponSize.MEDIUM])
                .format("largeReduction", AlphaSubcoreHM.WEAPON_REDUCTIONS[WeaponAPI.WeaponSize.LARGE])
                .format("fghtrReduction", AlphaSubcoreHM.FIGHTER_REDUCTION)
                .format("bmberReduction", AlphaSubcoreHM.BOMBER_REDUCTION)
                .addToTooltip(tooltip, title)

            if(member.variant.hullMods.any { BLOCKED_HULLMODS.contains(it) }) {
                StringUtils
                        .getTranslation("AlphaSubcore", "conflictDetected")
                        .addToTooltip(tooltip)
            }
        }
    }

    override fun getResourceCostMap(
        fm: FleetMemberAPI,
        mods: ShipModifications,
        market: MarketAPI?
    ): MutableMap<String, Float> {
        val resourceCosts: MutableMap<String, Float> = HashMap()
        resourceCosts[ITEM] = 1f
        return resourceCosts
    }

    override fun applyExoticToStats(
        id: String,
        stats: MutableShipStatsAPI,
        member: FleetMemberAPI,
        mods: ShipModifications,
        exoticData: ExoticData
    ) {
        onInstall(member)
    }

    /**
     * extra bandwidth added directly to ship.
     *
     * @param member
     * @param mods
     * @param exoticData
     * @return
     */
    override fun getExtraBandwidth(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData?): Float {
        return BANDWIDTH_INCREASE * getPositiveMult(member, mods, exoticData)
    }

    override fun shouldShareEffectToOtherModules(ship: ShipAPI?, module: ShipAPI?) = true

    override fun shouldAffectModulesToShareEffectsToOtherModules() = false

    companion object {
        private const val ITEM = "alpha_core"
        private const val BANDWIDTH_INCREASE = 60

        val BLOCKED_HULLMODS: MutableSet<String> = HashSet<String>().apply {
            add("specialsphmod_alpha_core_upgrades")
            add("specialsphmod_beta_core_upgrades")
            add("specialsphmod_gamma_core_upgrades")
        }
    }
}