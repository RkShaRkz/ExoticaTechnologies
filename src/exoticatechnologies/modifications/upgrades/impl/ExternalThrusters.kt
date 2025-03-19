package exoticatechnologies.modifications.upgrades.impl

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipCommand
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.upgrades.Upgrade
import exoticatechnologies.util.AfterimageData
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.addAfterimageToWholeShip
import exoticatechnologies.util.getAbsoluteAngleToAnotherShip
import org.apache.log4j.Logger
import org.json.JSONObject
import org.lazywizard.lazylib.MathUtils
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class ExternalThrusters(key: String, settings: JSONObject) : Upgrade(key, settings) {
    override var maxLevel: Int = 1

    override fun applyToShip(member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications) {
        MagicSubsystemsManager.addSubsystemToShip(ship, BoosterRockets(ship))
    }

    override fun applyUpgradeToStats(stats: MutableShipStatsAPI, fm: FleetMemberAPI, mods: ShipModifications, level: Int) {
        super.applyUpgradeToStats(stats, fm, mods, level)

        stats.maxSpeed.modifyFlat(buffId, MAX_SPEED_FLAT_BOOST)
        stats.suppliesPerMonth.modifyFlat(buffId, SUPPLIES_PER_MONTH)
        stats.suppliesPerMonth.modifyPercent(buffId, SUPPLIES_PER_MONTH_PERCENTAGE)
    }

    override fun shouldAffectModule(ship: ShipAPI?, module: ShipAPI?): Boolean {
        return false
    }

    override fun modifyToolTip(
            tooltip: TooltipMakerAPI,
            stats: MutableShipStatsAPI,
            member: FleetMemberAPI,
            mods: ShipModifications,
            expand: Boolean
    ): TooltipMakerAPI {
        val imageText = tooltip.beginImageWithText(iconPath, 64f)
        imageText.addPara("$name (%s)", 0f, color, mods.getUpgrade(this).toString())
        if (expand) {
            StringUtils
                    .getTranslation("ExternalThrusters", "tooltip")
                    .format("maxSpeed", MAX_SPEED_FLAT_BOOST)
                    .format("suppliesPerMonthFlat", SUPPLIES_PER_MONTH)
                    .format("suppliesPerMonthPercentage", SUPPLIES_PER_MONTH_PERCENTAGE)
                    .addToTooltip(imageText)
        }
        tooltip.addImageWithText(5f)

        return imageText
    }

    override fun showStatsInShop(tooltip: TooltipMakerAPI, member: FleetMemberAPI, mods: ShipModifications) {
        StringUtils
                .getTranslation("ExternalThrusters", "tooltip")
                .format("maxSpeed", MAX_SPEED_FLAT_BOOST)
                .format("suppliesPerMonthFlat", SUPPLIES_PER_MONTH)
                .format("suppliesPerMonthPercentage", SUPPLIES_PER_MONTH_PERCENTAGE)
                .addToTooltip(tooltip)
    }

    inner class BoosterRockets(ship: ShipAPI) : MagicSubsystem(ship) {

        override fun getBaseActiveDuration(): Float {
            return BOOSTER_ROCKETS_ACTIVE_DURATION
        }

        override fun getBaseCooldownDuration(): Float {
            return BOOSTER_ROCKETS_COOLDOWN_DURATION
        }

        override fun getOutDuration(): Float {
            return BOOSTER_ROCKETS_OUT_DURATION
        }

        override fun shouldActivateAI(amount: Float): Boolean {
            return if (ship.shipTarget != null) {
                val target = ship.shipTarget

                val differenceInDegrees = ship.getAbsoluteAngleToAnotherShip(target)

                val activationDistanceSquared = DISTANCE_TO_HINT_ACTIVATION_TO_AI * DISTANCE_TO_HINT_ACTIVATION_TO_AI
                val distanceToTargetSquared = MathUtils.getDistanceSquared(ship.location, target.location)
                val farEnoughToActivate = distanceToTargetSquared.absoluteValue > activationDistanceSquared
                if (farEnoughToActivate && differenceInDegrees < 15) {
                    // If further than 2000 range and within a 30-degree arc, activate the boosters
                    true
                } else {
                    // otherwise, just activate if far enough - this must remain here because kotlin reasons
                    farEnoughToActivate
                }
            } else {
                // Nothing to charge at or chase, return false
                false
            }
        }

        override fun onStateSwitched(oldState: State?) {
            if (state == State.IN || state == State.ACTIVE) {
                // Give 30 to top speed, modify top speed by an additional 100%, but make steering very rigid (-80%)
                stats.maxSpeed.modifyFlat(boosterRocketsBuffId, BOOSTER_ROCKETS_MAX_SPEED_FLAT_BOOST)
                stats.maxSpeed.modifyPercent(boosterRocketsBuffId, BOOSTER_ROCKETS_MAX_SPEED_PERCENT_BOOST)
                stats.turnAcceleration.modifyPercent(boosterRocketsBuffId, BOOSTER_ROCKETS_TURNING_PENALTY)
                stats.maxTurnRate.modifyPercent(boosterRocketsBuffId, BOOSTER_ROCKETS_TURNING_PENALTY)

                // adding visual flair to the engines by turning them up to the max will be done in advance()

                // Give a visual que
                addAfterimageToWholeShip(
                        ship,
                        AfterimageData(
                                color = AFTERIMAGE_COLOR,
                                locX = 0f,
                                locY = 0f,
                                velX = 0f,
                                velY = 0f,
                                maxJitter = 6f,
                                inDuration = 0f,
                                duration = this.activeDuration,
                                outDuration= 0.25f,
                                additive = true,
                                combineWithSpriteColor = false,
                                aboveShip = true
                        )
                )

                // Finally, put the pedal to the metal
                ship.giveCommand(ShipCommand.ACCELERATE, null, 0)
            }
        }

        override fun onFinished() {
            stats.maxSpeed.unmodify(boosterRocketsBuffId)
            stats.turnAcceleration.unmodify(boosterRocketsBuffId)
            stats.maxTurnRate.unmodify(boosterRocketsBuffId)
        }

        override fun advance(amount: Float, isPaused: Boolean) {
            if (isPaused) return //lets try this

            // Once activated, start running the "rocket visuals" which extend the engine's flames with a hardcoded duration of 1 second
            // since the hardcoding was done in the Starsector API's ValueShifter, this works fine with no IntervalUtil

            if (state == State.IN || state == State.ACTIVE) {
                rocketEngineVisuals()
            }

            if (state == State.OUT) {
                val speed = ship.angularVelocity

                // Modify turning
                stats.maxTurnRate.modifyPercent(boosterRocketsBuffId, (stats.maxTurnRate.getPercentStatMod(boosterRocketsBuffId).value - (BOOSTER_ROCKETS_TURNING_PENALTY / outDuration) * amount).coerceAtLeast(0f))
                stats.turnAcceleration.modifyPercent(boosterRocketsBuffId, (stats.maxTurnRate.getPercentStatMod(boosterRocketsBuffId).value - (BOOSTER_ROCKETS_TURNING_PENALTY / outDuration) * amount).coerceAtLeast(0f))

                // Modify max speed
                stats.maxSpeed.modifyFlat(boosterRocketsBuffId, (stats.maxSpeed.getFlatStatMod(boosterRocketsBuffId).value - (BOOSTER_ROCKETS_MAX_SPEED_FLAT_BOOST / outDuration) * amount).coerceAtLeast(0f))
                stats.maxSpeed.modifyPercent(boosterRocketsBuffId, (stats.maxSpeed.getPercentStatMod(boosterRocketsBuffId).value - (BOOSTER_ROCKETS_MAX_SPEED_PERCENT_BOOST / outDuration) * amount).coerceAtLeast(0f))

                // Turning part
                if (speed.absoluteValue > ship.mutableStats.maxTurnRate.modifiedValue) {
                    val negative = speed < 0
                    if (negative) {
                        ship.angularVelocity = (speed + amount * 4500f).coerceIn(-ship.mutableStats.maxTurnRate.modifiedValue..0f)
                    } else {
                        ship.angularVelocity = (speed - amount * 4500f).coerceIn(0f..ship.mutableStats.maxTurnRate.modifiedValue)
                    }
                }
            }
        }

        override fun getDisplayText(): String {
            return this@ExternalThrusters.name
        }

        private fun rocketEngineVisuals() {
            ship.engineController.fadeToOtherColor(
                    this,
                    ENGINE_COLOR,
                    ENGINE_ENTRAIL_COLOR,
                    effectLevel,
                    ENGINE_CONTROLLER_MAX_BLEND
            )
            ship.engineController.extendFlame(
                    this,
                    ENGINE_CONTROLLER_EXTENDED_FLAME_LENGTH * effectLevel,
                    ENGINE_CONTROLLER_EXTENDED_FLAME_WIDTH * effectLevel,
                    ENGINE_CONTROLLER_EXTENDED_FLAME_GLOW_FACTOR * effectLevel
            )
            ship.engineController.forceShowAccelerating()

            for (engine in ship.engineController.shipEngines) {
                if (engine.isSystemActivated) {
                    ship.engineController.setFlameLevel(engine.engineSlot, 1f)
                }
            }
        }
    }

    companion object {
        const val buffId = "ExoticaExternalThrusters"
        const val boosterRocketsBuffId = "ExoticaBoosterRockets"

        const val MAX_SPEED_FLAT_BOOST = 30f;
        const val SUPPLIES_PER_MONTH = 10f;
        const val SUPPLIES_PER_MONTH_PERCENTAGE = 25f;

        const val BOOSTER_ROCKETS_MAX_SPEED_FLAT_BOOST = 30f;
        const val BOOSTER_ROCKETS_MAX_SPEED_PERCENT_BOOST = 100f;
        const val BOOSTER_ROCKETS_TURNING_PENALTY = -80f

        const val BOOSTER_ROCKETS_ACTIVE_DURATION = 15f
        const val BOOSTER_ROCKETS_COOLDOWN_DURATION = 45f
        const val BOOSTER_ROCKETS_OUT_DURATION = 0.6f

        const val DISTANCE_TO_HINT_ACTIVATION_TO_AI = 2000

        private val ENGINE_COLOR = Color(225, 95, 0, 255)
        private val ENGINE_ENTRAIL_COLOR = Color(255, 35, 0, 165)
        private val AFTERIMAGE_COLOR = Color(255, 200, 15, (0.65f * 255).roundToInt())

        // engine controller (rocket visuals) constants
        private val ENGINE_CONTROLLER_MAX_BLEND = 0.67f
        private val ENGINE_CONTROLLER_EXTENDED_FLAME_LENGTH = 3f
        private val ENGINE_CONTROLLER_EXTENDED_FLAME_WIDTH = 2f
        private val ENGINE_CONTROLLER_EXTENDED_FLAME_GLOW_FACTOR = 2f

        val log: Logger = Logger.getLogger(ExternalThrusters::class.java)
    }
}
