package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import exoticatechnologies.combat.ExoticaCombatUtils
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.StringUtils
import org.json.JSONObject
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color
import kotlin.math.abs

class FullMetalSalvo(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color = Color(0xD99836)

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
                    .format("projSpeedBoost", getProjectileSpeedBoostBonus(member, mods, exoticData))
                    .format("damageBoost", getDamageBoostBonus(member, mods, exoticData))
                    .formatFloat("boostTime", getBoostTimeBonus(member, mods, exoticData))
                    .formatFloat("cooldown", getCooldownMalus(member, mods, exoticData))
                    .format("firerateMalus", abs(getRateOfFireMalus(member, mods, exoticData)))
                    .addToTooltip(tooltip, title)
        }
    }

    // Now the utility bonus/malus applying getters for constants, with helper methods for individual bonuses/penalties
    fun getProjectileSpeedBoostBonus(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return getDamageBuffBonus(member, mods, exoticData)
    }

    fun getDamageBoostBonus(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return getDamageBuffBonus(member, mods, exoticData) * getDamageBoostCoeficientBonus(member, mods, exoticData)
    }

    fun getBoostTimeBonus(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return BUFF_DURATION * getPositiveMult(member, mods, exoticData)
    }

    fun getCooldownMalus(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return COOLDOWN * getNegativeMult(member, mods, exoticData)
    }

    fun getRateOfFireMalus(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return RATE_OF_FIRE_DEBUFF * getNegativeMult(member, mods, exoticData)
    }

    fun getDamageBoostCoeficientBonus(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        // We will not be applying the positive multiplier to the coeficient as well, because then it will ramp up the
        // damage boost from 33% to 206% (due to the positive multiplier appearing twice, and 2.5*2.5=6.25) for Pure type
        return DAMAGE_BUFF_COEF// * getPositiveMult(member, mods, exoticData)
    }

    fun getDamageBuffBonus(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return DAMAGE_BUFF * getPositiveMult(member, mods, exoticData)
    }

    override fun applyExoticToStats(
            id: String,
            stats: MutableShipStatsAPI,
            member: FleetMemberAPI,
            mods: ShipModifications,
            exoticData: ExoticData
    ) {
        // these will turn out to be 1+ -33/100 = 0.67 which makes the weapons slower
        stats.ballisticRoFMult.modifyMult(buffId, 1 + getRateOfFireMalus(member, mods, exoticData) / 100f)
        stats.energyRoFMult.modifyMult(buffId, 1 + getRateOfFireMalus(member, mods, exoticData) / 100f)
    }

    fun gigaProjectiles(source: ShipAPI, member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData) {
        for (proj in Global.getCombatEngine().projectiles) {
            if (proj.source == source && proj.elapsed <= Global.getCombatEngine().elapsedInLastFrame) {
                val damageBoostBonus = getDamageBuffBonus(member, mods, exoticData) / 100f
                val damageBoostCoef = getDamageBoostCoeficientBonus(member, mods, exoticData)
                // Don't be fooled, *everything* is always nullable in alex's starsector API ...
                proj?.let { nonNullProj ->
                    nonNullProj.damage.modifier.modifyMult(buffId, 1 + damageBoostBonus * damageBoostCoef)
                }
            }
        }
    }

    override fun applyToShip(
            id: String,
            member: FleetMemberAPI,
            ship: ShipAPI,
            mods: ShipModifications,
            exoticData: ExoticData
    ) {
        if (MagicSubsystemsManager.getSubsystemsForShipCopy(ship)?.filterIsInstance<SalvoActivator>()?.isEmpty() != false) {
            val activator = SalvoActivator(ship, member, mods, exoticData)
            MagicSubsystemsManager.addSubsystemToShip(ship, activator)
        }
    }

    override fun shouldShareEffectToOtherModules(ship: ShipAPI?, module: ShipAPI?) = true

    inner class SalvoActivator(
            ship: ShipAPI,
            val member: FleetMemberAPI,
            val mods: ShipModifications,
            val exoticData: ExoticData
    ) :
            MagicSubsystem(ship) {
        override fun getDisplayText(): String {
            return Global.getSettings().getString(exoticData.key, "systemText")
        }

        override fun getBaseActiveDuration(): Float {
            return getBoostTimeBonus(member, mods, exoticData)
        }

        override fun getBaseCooldownDuration(): Float {
            return getCooldownMalus(member, mods, exoticData)
        }

        override fun advance(amount: Float, isPaused: Boolean) {
            if (state == State.ACTIVE) {
                gigaProjectiles(ship, member, mods, exoticData)
            }
        }

        override fun onStateSwitched(oldState: State) {
            if (state == State.ACTIVE) {
                ship.mutableStats.ballisticProjectileSpeedMult.modifyMult(this.toString(), 1 + getDamageBuffBonus(member, mods, exoticData) / 100f)
                ship.mutableStats.energyProjectileSpeedMult.modifyMult(this.toString(), 1 + getDamageBuffBonus(member, mods, exoticData) / 100f)
                ship.mutableStats.missileMaxSpeedBonus.modifyMult(this.toString(), 1 + getDamageBuffBonus(member, mods, exoticData) / 100f)

                ship.addAfterimage(
                        Color(255, 125, 0, 150),
                        0f,
                        0f,
                        0f,
                        0f,
                        6f,
                        0f,
                        this.activeDuration,
                        0.25f,
                        true,
                        false,
                        true
                )
            } else {
                ship.mutableStats.ballisticProjectileSpeedMult.unmodify(this.toString())
                ship.mutableStats.energyProjectileSpeedMult.unmodify(this.toString())
                ship.mutableStats.missileMaxSpeedBonus.unmodify(this.toString())
            }
        }

        override fun shouldActivateAI(amount: Float): Boolean {
            val target = ship.shipTarget
            if (target != null) {
                var score = 0f
                score += (target.currFlux / target.maxFlux) * 12f

                if (target.fluxTracker.isOverloadedOrVenting) {
                    score += 10f
                }

                val dist = Misc.getDistance(ship.location, target.location)
                if (dist > ExoticaCombatUtils.getMaxWeaponRange(ship, false)) {
                    return false
                }

                val avgRange = ExoticaCombatUtils.getAverageWeaponRange(ship, false)
                score += (avgRange / dist).coerceAtMost(6f)

                if (score > 10f) {
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val DAMAGE_BUFF = 100f
        private const val DAMAGE_BUFF_COEF = 0.33f
        private const val RATE_OF_FIRE_DEBUFF = -33f
        private const val COOLDOWN = 8f
        private const val BUFF_DURATION = 2
    }
}
