package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.AnonymousLogger
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import org.json.JSONObject
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.abs

class EqualizerCore(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color: Color = Color.orange.darker()
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
        if (expand) {
            StringUtils.getTranslation(key, "longDescription")
                .format("recoilReduction", abs(RECOIL_REDUCTION) * getPositiveMult(member, mods, exoticData))
                .format("weaponTurnBonus", TURN_RATE_BUFF * getPositiveMult(member, mods, exoticData))
                .format("lowRangeThreshold", getLowerRangeLimit(member, mods, exoticData))
                .format("rangeBonus", RANGE_BOTTOM_BUFF * getPositiveMult(member, mods, exoticData))
                .format("highRangeThreshold", getUpperRangeLimit(member, mods, exoticData))
                .format("rangeMalus", abs(RANGE_TOP_BUFF) * getNegativeMult(member, mods, exoticData))
                .format(
                    "rangeDecreaseDamageIncrease",
                    getRangeDecreaseDamageIncreasePerHundredRange(member, mods, exoticData)
                )
                .addToTooltip(tooltip, title)
        }
    }

    fun getRangeDecreaseDamageIncreasePerHundredRange(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RANGE_DECREASE_DAMAGE_INCREASE_PERCENT_PER_100_RANGE * getPositiveMult(member, mods, exoticData)
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
        stats.recoilDecayMult.modifyMult(
            buffId,
            abs(RECOIL_REDUCTION) / 100f * getPositiveMult(member, mods, exoticData)
        )
        stats.recoilPerShotMult.modifyMult(
            buffId,
            abs(RECOIL_REDUCTION) / 100f * getPositiveMult(member, mods, exoticData)
        )
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
            ship.addListener(ET_EqualizerCoreListener(member, mods, exoticData))
        }
    }

    override fun shouldShareEffectToOtherModules(ship: ShipAPI?, module: ShipAPI?) = false

    // Our range listener
    private inner class ET_EqualizerCoreListener(
        val member: FleetMemberAPI,
        val mods: ShipModifications,
        val exoticData: ExoticData
    ) : WeaponBaseRangeModifier, DamageDealtModifier {
        override fun getWeaponBaseRangePercentMod(ship: ShipAPI, weapon: WeaponAPI): Float {
            return 0f
        }

        override fun getWeaponBaseRangeMultMod(ship: ShipAPI, weapon: WeaponAPI): Float {
            return 1f
        }

        override fun getWeaponBaseRangeFlatMod(ship: ShipAPI, weapon: WeaponAPI): Float {
            AnonymousLogger.log("--> getWeaponBaseRangeFlatMod()\tweaponId ${weapon.id} of shipId: ${ship.id} has base maxRange: ${weapon.spec.maxRange}", "EqualizerCore")
            AnonymousLogger.log("getWeaponBaseRangeFlatMod()\tgetUpperRangeLimit(): ${getUpperRangeLimit(member, mods, exoticData)}\tgetLowerRangeLimit(): ${getLowerRangeLimit(member, mods, exoticData)}", "EqualizerCore")
            if (weapon.type == WeaponAPI.WeaponType.MISSILE) {
                return 0f
            }

            var baseRangeMod = 0f
            if (weapon.spec.maxRange >= getUpperRangeLimit(member, mods, exoticData)) {
                baseRangeMod =
                    RANGE_TOP_BUFF * (weapon.spec.maxRange - getUpperRangeLimit(member, mods, exoticData)) / 100
            } else if (weapon.spec.maxRange <= getLowerRangeLimit(member, mods, exoticData)) {
                baseRangeMod = RANGE_BOTTOM_BUFF.toFloat() * getPositiveMult(member, mods, exoticData)
            }

            AnonymousLogger.log("<-- getWeaponBaseRangeFlatMod()\tweaponId ${weapon.id} of shipId: ${ship.id} returning modified baseRangeMod ${baseRangeMod}\t\tfor adjusted maxRange: ${weapon.spec.maxRange + baseRangeMod}", "EqualizerCore")
            return baseRangeMod
        }

        override fun modifyDamageDealt(
            param: Any?,
            target: CombatEntityAPI,
            damage: DamageAPI,
            point: Vector2f,
            shieldHit: Boolean
        ): String? {
            val weapon: WeaponAPI? = param?.let {
                if (param is BeamAPI) {
                    param.weapon
                } else if (param is DamagingProjectileAPI) {
                    param.weapon
                } else null
            }

            // Since the range adjustment is done in a different method - getWeaponBaseRangeFlatMod(),
            // we will only adjust the damage here based on similar calculations
            weapon?.let {
                if (it.type == WeaponAPI.WeaponType.MISSILE) return null
                if (it.spec.maxRange > getUpperRangeLimit(member, mods, exoticData)) {
//                    val buff = (RANGE_DECREASE_DAMAGE_INCREASE / 100f * getPositiveMult(member, mods, exoticData)) * ((it.spec.maxRange - getUpperRangeLimit(member, mods, exoticData)) / 100f).coerceAtLeast(0f)
                    // Calculate the coefficient for damage-increase-per-100-range-lost
                    // This is actually range-agnostic, and consists of only RANGE_DECREASE_DAMAGE_INCREASE * positiveMult()
                    // but it will give us how much damage (percent) we will get for every 100 range lost.
                    // Should typically be 10 for positiveMult of 1, but can be 25 (or more) for e.g. PureType with it's 2.5x positiveMult
                    val rangeDecreaseDamageIncrease = getRangeDecreaseDamageIncreasePerHundredRange(member, mods, exoticData)
                    // Calculate the new "max range ratio" by checking how much range we're over the upper range limit
                    // E.g. if a weapon had 1800 range, and the upper range limit is 800, our maxRangeRatio would end up being 1000/100 = 10
                    val maxRangeRatio = (it.spec.maxRange - getUpperRangeLimit(member, mods, exoticData)) / 100f
                    // Finally, calculate the buff by
                    val buff = rangeDecreaseDamageIncrease * maxRangeRatio.coerceAtLeast(0f)
                    // Since the 'buff' will only determine the multiplier, e.g. 2.5, and using that with modifyPercent will apply only a 2.5% bonus, we need to scale it up
//                    val buffScaledForPercents = buff * 100
//                    AnonymousLogger.log("modifying damage of weapon: ${it.id}\trangeDecreaseDamageIncrease: ${rangeDecreaseDamageIncrease}, maxRange: ${maxRange}, buff: ${buff}, buffScaledForPercents: ${buffScaledForPercents}", "EqualizerCore")
                    AnonymousLogger.log("modifying damage of weapon: ${it.id}\trangeDecreaseDamageIncrease: ${rangeDecreaseDamageIncrease}, maxRange: ${maxRangeRatio}, buff: ${buff}", "EqualizerCore")
//                    AnonymousLogger.log("modifying damage of weapon: ${it.id} [modifier.modifyPercent]\tbuffId: ${buffId}, buff: ${buff}", "EqualizerCore")
//                    AnonymousLogger.log("modifying damage of weapon: ${it.id} [modifier.modifyPercent]\tbuffId: ${buffId}, buffScaledForPercents: ${buffScaledForPercents}\tdamage: ${damage.damage}", "EqualizerCore")
                    AnonymousLogger.log("modifying damage of weapon: ${it.id} [modifier.modifyPercent]\tbuffId: ${buffId}, buff: ${buff}\tdamage: ${damage.damage} [BEFORE]", "EqualizerCore")
                    damage.modifier.modifyPercent(buffId, buff)
//                    damage.modifier.modifyPercent(buffId, buffScaledForPercents)
                    AnonymousLogger.log("modifying damage of weapon: ${it.id}\tmodified damage: ${damage.damage} [AFTER]", "EqualizerCore")
//                    AnonymousLogger.log("modifying damage of weapon: ${it.id} [modifier.modifyMult]\tbuffId: ${buffId}, buff: ${buff}", "EqualizerCore")
//                    damage.modifier.modifyMult(buffId, buff)
                }

                return buffId
            }

            return null
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
        private const val RANGE_DECREASE_DAMAGE_INCREASE_PERCENT_PER_100_RANGE = 10f
    }
}
