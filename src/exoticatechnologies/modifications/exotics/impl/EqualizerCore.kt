package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier
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
import kotlin.math.abs

class EqualizerCore(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color: Color = Color.orange.darker()
    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI): Boolean {
        return Utilities.hasItem(fleet.cargo, ITEM)
    }

    override fun removeItemsFromFleet(fleet: CampaignFleetAPI, member: FleetMemberAPI, market: MarketAPI): Boolean {
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
        if (expand) {
            StringUtils.getTranslation(key, "longDescription")
                .format("recoilReduction", abs(RECOIL_REDUCTION) * getPositiveMult(member, mods, exoticData))
                .format("weaponTurnBonus", TURN_RATE_BUFF * getPositiveMult(member, mods, exoticData))
                .format("lowRangeThreshold", getLowerRangeLimit(member, mods, exoticData))
                .format("rangeBonus", RANGE_BOTTOM_BUFF * getPositiveMult(member, mods, exoticData))
                .format("highRangeThreshold", getUpperRangeLimit(member, mods, exoticData))
                .format("rangeMalus", abs(RANGE_TOP_BUFF) * getNegativeMult(member, mods, exoticData))
                .format("rangeDecreaseDamageIncrease", RANGE_DECREASE_DAMAGE_INCREASE * getPositiveMult(member, mods, exoticData))
                .addToTooltip(tooltip, title)
        }
    }

    private fun getLowerRangeLimit(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RANGE_LIMIT_BOTTOM + (100 * (1 - getNegativeMult(member, mods, exoticData)))
    }

    private fun getUpperRangeLimit(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RANGE_LIMIT_TOP - (100 * (1 - getNegativeMult(member, mods, exoticData)))
    }

    override fun applyExoticToStats(
        id: String,
        stats: MutableShipStatsAPI,
        member: FleetMemberAPI,
        mods: ShipModifications,
        exoticData: ExoticData
    ) {
        stats.autofireAimAccuracy.modifyPercent(buffId, 1000f)
        stats.maxRecoilMult.modifyMult(buffId, abs(RECOIL_REDUCTION) / 100f * getPositiveMult(member, mods, exoticData))
        stats.recoilDecayMult.modifyMult(buffId, abs(RECOIL_REDUCTION) / 100f * getPositiveMult(member, mods, exoticData))
        stats.recoilPerShotMult.modifyMult(buffId, abs(RECOIL_REDUCTION) / 100f * getPositiveMult(member, mods, exoticData))
        stats.weaponTurnRateBonus.modifyPercent(buffId, TURN_RATE_BUFF * getPositiveMult(member, mods, exoticData))
        stats.beamWeaponTurnRateBonus.modifyPercent(buffId, TURN_RATE_BUFF * getPositiveMult(member, mods, exoticData))
    }

    override fun advanceInCombatUnpaused(
        ship: ShipAPI,
        amount: Float,
        member: FleetMemberAPI,
        mods: ShipModifications,
        exoticData: ExoticData
    ) {
        if (!ship.hasListenerOfClass(ET_EqualizerCoreListener::class.java)) {
            for (weapon in ship.allWeapons) {
                if (weapon.type == WeaponAPI.WeaponType.MISSILE) continue
                if (weapon.spec.maxRange > getUpperRangeLimit(member, mods, exoticData)) {
                    val buff = (RANGE_DECREASE_DAMAGE_INCREASE / 100f * getPositiveMult(member, mods, exoticData)) * (weapon.spec.maxRange - getUpperRangeLimit(member, mods, exoticData)).coerceAtLeast(0f)
                    weapon.damage.modifier.modifyPercent(buffId, buff)
                }
            }

            ship.addListener(ET_EqualizerCoreListener(member, mods, exoticData))
        }
    }

    // Our range listener
    private inner class ET_EqualizerCoreListener(val member: FleetMemberAPI, val mods: ShipModifications, val exoticData: ExoticData) : WeaponBaseRangeModifier {
        override fun getWeaponBaseRangePercentMod(ship: ShipAPI, weapon: WeaponAPI): Float {
            return 0f
        }

        override fun getWeaponBaseRangeMultMod(ship: ShipAPI, weapon: WeaponAPI): Float {
            return 1f
        }

        override fun getWeaponBaseRangeFlatMod(ship: ShipAPI, weapon: WeaponAPI): Float {
            if (weapon.type == WeaponAPI.WeaponType.MISSILE) {
                return 0f
            }

            var baseRangeMod = 0f
            if (weapon.spec.maxRange >= getUpperRangeLimit(member, mods, exoticData)) {
                baseRangeMod = RANGE_TOP_BUFF * (weapon.spec.maxRange - getUpperRangeLimit(member, mods, exoticData)) / 100
            } else if (weapon.spec.maxRange <= getLowerRangeLimit(member, mods, exoticData)) {
                baseRangeMod = RANGE_BOTTOM_BUFF.toFloat() * getPositiveMult(member, mods, exoticData)
            }
            return baseRangeMod
        }
    }

    companion object {
        private const val ITEM = "et_equalizercore"
        private const val RECOIL_REDUCTION = -25f
        private const val TURN_RATE_BUFF = 50f
        private const val RANGE_LIMIT_BOTTOM = 550
        private const val RANGE_BOTTOM_BUFF = 200
        private const val RANGE_LIMIT_TOP = 800
        private const val RANGE_TOP_BUFF = -50 //per 100 units
        private const val RANGE_DECREASE_DAMAGE_INCREASE = 10f
    }
}