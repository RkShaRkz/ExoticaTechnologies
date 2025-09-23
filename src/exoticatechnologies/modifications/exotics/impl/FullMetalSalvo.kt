package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.DamagingProjectileAPI
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
import exoticatechnologies.util.AnonymousLogger
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.addAfterimage
import org.json.JSONObject
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color
import kotlin.math.abs
import kotlin.math.sin

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
                proj?.let projLet@ { nonNullProj ->
                    nonNullProj.damage.modifier.modifyMult(buffId, 1 + damageBoostBonus * damageBoostCoef)

                    if (ADD_AFTERIMAGE_TO_PROJECTILES) {
                        AnonymousLogger.log("ADD_AFTERIMAGE_TO_PROJECTILES was true, adding afterimage to projectile ${nonNullProj}", "FullMetalSalvo") //TODO delete
                        addProjectileAfterimage(nonNullProj, member, mods, exoticData)
                    }
                }
            }
        }
    }

    /**
     * Method that adds an afterimage to a projectile, internally dependant on [PROJECTILE_AFTERIMAGE_USE_PULSES]
     *
     * @param projectile the projectile to add the afterimage to
     * @param member the owning FleetMemberAPI of the firing weapon, used to calculate the afterimage duration and pulse speed
     * @param mods the owning FleetMemberAPI's mods, used same as above
     * @param exoticData the owning FleetMemberAPI's exotic data, used same as above
     */
    fun addProjectileAfterimage(projectile: DamagingProjectileAPI, member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData) {
        val spriteAfterimageAlpha = if (PROJECTILE_AFTERIMAGE_USE_PULSES) {
            // If we want to pulse, grab the projectileSpec - which might not be there - and then grab the
            // glowColor from it, which might not also be there. In case the glow color isn't there,
            // assume a full RGBA white color for the glow - or better, the 'color' parameter we already have
            // Afterwards grab the alpha, and use it as the pulse alpha
            val glowColor = projectile.projectileSpec?.let projSpecLet@{ projSpec ->
                AnonymousLogger.log("projectile ${projectile} sprite name is: ${projSpec.bulletSpriteName}", "FullMetalSalvo") //TODO delete
                // While this could have been nonNullProj.projectileSpec?.glowColor ?: Color(1,1,1,1)
                // lets leave the let {} blocks in here
                return@projSpecLet projSpec.glowColor?.let glowColorLet@{ color ->
                    color
                }
                // In case where we can't get the glow color, assume a full RGBA white color
            } ?: if (PROJECTILE_AFTERIMAGE_FALLBACK_USES_WHITE) { Color(1.0f, 1.0f, 1.0f, 1.0f) } else { color }
            val baseAlpha = glowColor.alpha / 255f

            getPulseAlpha(
                    baseAlpha = baseAlpha,
                    time = Global.getCombatEngine().getTotalElapsedTime(false),
                    // Since 'speed' is actually in "cycles per second" and not "seconds per cycle",
                    // we'll be dividing the speed (5) with the duration, so that we end up with 5 pulses
                    // over the course of ability duration
                    speed = PROJECTILE_AFTERIMAGE_PULSE_SPEED / getBoostTimeBonus(member, mods, exoticData)
            )
        } else {
            // If we do not want to use afterimage pulses - which might not even be possible due to sprites becoming
            // immutable after rendering (being "baked in") - just default to using 1f for the projectile afterimage alpha
            1f
        }

        AnonymousLogger.log("--> projectile.addAfterimage()\t\tprojectile specID: ${projectile.projectileSpecId}\tprojectile: ${projectile}", "FullMetalSalvo") //TODO delete
        projectile.addAfterimage(
                fadeInTime = 0.2f,
                fullTime = 1f,
                fadeOutTime = 0.5f,
                spriteSize = 2f,
                spriteAlpha = spriteAfterimageAlpha,
                spriteOverrideColor = Color(1.0f, 1.0f, 1.0f, 1.0f)
        )
        AnonymousLogger.log("<-- projectile.addAfterimage()\t\tprojectile: ${projectile}", "FullMetalSalvo") //TODO delete
    }

    fun getPulseAlpha(baseAlpha: Float, time: Float, speed: Float = 5f): Float {
        return baseAlpha * (0.5f + 0.5f * sin(time * speed))
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

        private const val ADD_AFTERIMAGE_TO_PROJECTILES = true
        private const val PROJECTILE_AFTERIMAGE_PULSE_SPEED = 5f
        private const val PROJECTILE_AFTERIMAGE_USE_PULSES = true // since the SpriteAPI is baked in once rendered, we can't really modify it //TODO change to false
        private const val PROJECTILE_AFTERIMAGE_FALLBACK_USES_WHITE = false
    }
}
