package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import org.json.JSONObject
import java.awt.Color

class GuardianShield(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color = Color(0xD93636)
    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI?): Boolean {
        return Utilities.hasItem(fleet.cargo, ITEM)
    }

    override fun removeItemsFromFleet(fleet: CampaignFleetAPI, member: FleetMemberAPI, market: MarketAPI?): Boolean {
        Utilities.takeItemQuantity(fleet.cargo, ITEM, 1f)
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
        StringUtils.getTranslation(key, "longDescription")
                .format("cargoToFuelPercent", 0)
                .format("burnBonusFuelReq", 0)
                .format("burnBonus", 0)
                .addToTooltip(tooltip, title)
    }

    override fun applyExoticToStats(id: String, stats: MutableShipStatsAPI, member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyExoticToStats(id, stats, member, mods, exoticData)

//        ship.shield.radius = 1500f
//        ship.shield.arc = 360f
//        ship.shield.toggleOn()
//        ship.mutableStats.shieldDamageTakenMult
        stats.shieldDamageTakenMult.modifyMult(BUFF_ID, SHIELD_DAMAGE_TAKEN_MULTIPLIER)
    }

    override fun applyToShip(id: String, member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        ship.shield.radius = 1500f
        ship.shield.arc = 360f
        ship.shield.toggleOn()
    }

    companion object {
        private const val ITEM = "et_ammospool"

        private const val BUFF_ID = "guardian_shield_buff"
        private const val SHIELD_DAMAGE_TAKEN_MULTIPLIER = 2f
    }
}