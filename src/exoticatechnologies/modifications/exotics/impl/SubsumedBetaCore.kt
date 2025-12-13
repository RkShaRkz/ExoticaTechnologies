package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import org.json.JSONObject
import java.awt.Color

class SubsumedBetaCore(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color = Color(0x6AA900)
    override var canDropFromCombat: Boolean = true

    override fun shouldShow(member: FleetMemberAPI, mods: ShipModifications, market: MarketAPI?): Boolean {
        return Utilities.hasExoticChipOrItemInCargo(key, ITEM, market)
    }


    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI?): Boolean {
        return Utilities.hasItemInFleetCargoOrMarketStorageCargo(fleet, market, ITEM)
    }

    override fun removeItemsFromFleet(fleet: CampaignFleetAPI, member: FleetMemberAPI, market: MarketAPI?): Boolean {
        if (Utilities.hasItem(fleet.cargo, ITEM)) {
            Utilities.takeItemQuantity(fleet.cargo, ITEM, 1f)
        } else {
            Utilities.takeItemQuantity(Misc.getStorageCargo(market), ITEM, 1f)
        }
        return true
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

    override fun canApply(member: FleetMemberAPI, mods: ShipModifications?): Boolean {
        if (member.fleetData == null || member.fleetData.fleet == null) {
            return false
        }

        // If belongs to Omega faction or is owned by player, let it apply - otherwise not.
        return if (member.fleetData.fleet.faction.id == Factions.OMEGA || member.owner == Misc.OWNER_PLAYER) {
            super.canApply(member, mods)
        } else {
            false
        }
    }

    override fun countsTowardsExoticLimit(member: FleetMemberAPI): Boolean {
        return false
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
                    .format("bandwidthIncrease", getExtraBandwidth(member, mods, exoticData))
                    .addToTooltip(tooltip, title)
        }
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
        return 35f * getPositiveMult(member, mods, exoticData)
    }

    companion object {
        private const val ITEM = "beta_core"
    }
}
