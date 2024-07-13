package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize.*
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.IntervalUtil
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import exoticatechnologies.util.datastructures.Optional
import exoticatechnologies.util.datastructures.RingBuffer
import org.apache.log4j.Logger
import org.json.JSONObject
import org.lwjgl.util.vector.Vector2f
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color

class RewindSystem(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color = Color(225, 225, 225)
    private lateinit var originalShip: ShipAPI

    private val logger: Logger = Logger.getLogger(RewindSystem::class.java)

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
            expand: Boolean) {
        StringUtils.getTranslation(key, "longDescription")
//                .format("flux_method", GuardianShield.lazyFluxMethodString)
//                .format("flux_effects", GuardianShield.lazyFluxEffectString)
                .addToTooltip(tooltip, title)
    }

    override fun applyToShip(
            id: String,
            member: FleetMemberAPI,
            ship: ShipAPI,
            mods: ShipModifications,
            exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        val subsystem = RewindSubsystem(ship)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)
    }

    inner class RewindSubsystem(ship: ShipAPI) : MagicSubsystem(ship) {
        var engine: CombatEngineAPI = Global.getCombatEngine()
        private val secondsTracker = IntervalUtil(0.95f, 1.05f)
        private var timeElapsed: Float = 0f
        private val previousStates: RingBuffer<ShipParams> = exoticatechnologies.util.datastructures.RingBuffer<ShipParams>(
                determineRewindLength(ship) + 5,
                ShipParams.EmptyShipParams,
                ShipParams::class.java
        )

        override fun getBaseInDuration(): Float = 1f

        override fun getBaseActiveDuration(): Float = 1f

        override fun getBaseOutDuration(): Float = 1f

        override fun canActivate(): Boolean {
            return findActivationCandidate().isPresent()
        }

        override fun getBaseCooldownDuration() = 300.toFloat()

        override fun shouldActivateAI(amount: Float): Boolean {
            // AI should activate if:
            //  - we're venting and have incoming fire
            //  - we're at <=25% HP and have incoming fire
            //  - we're at <=10% HP

            if (engine.isPaused) {
                return false
            }


            val isVenting = ship.fluxTracker.isOverloadedOrVenting
            val hasIncomingFire = ship.aiFlags != null
                    && ship.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)
                    && ship.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP)
            val healthLow = ship.hullLevel <= 0.25f
            val healthReallyLow = ship.hullLevel <= 0.10f

//            return if (possibleToActivate) {
//                if (isVenting && hasIncomingFire) {
//                    true
//                } else if (healthLow && hasIncomingFire) {
//                    true
//                } else if (healthReallyLow) {
//                    true
//                } else {
//                    false
//                }
//            } else {
//                false
//            }

            // Instead of always searching the buffer, then checking a few eliminating constants
            // we should instead make the constants the eliminating/narrowing factor, then when
            // they allow activation - then search through the buffer
            return if (isVenting && hasIncomingFire) {
                // true, check if possible
                val candidate = findActivationCandidate()
                candidate.isPresent()
            } else if (healthLow && hasIncomingFire) {
                // true, check if possible
                val candidate = findActivationCandidate()
                candidate.isPresent()
            } else if (healthReallyLow) {
                // true, check if possible
                val candidate = findActivationCandidate()
                candidate.isPresent()
            } else {
                false
            }
        }

        /**
         * Activation is only possible IFF:
         * 1. we have a snapshot exactly enough seconds in the past
         * 2. we find a snapshot that is <1sec difference from ideal timing (duration seconds in the past from now)
         */
        private fun isActivationPossible(ship: ShipAPI, shipParams: ShipParams): Boolean {
            // calculate the timestamp we're looking for in the past
            val pastTime = timeElapsed - determineRewindLength(ship)

            // We have a ConcreteShipParams (non invalid data) and we found a timestamp within a 1second of our target past time
            return shipParams is ShipParams.ConcreteShipParams
                    && ((shipParams.timestamp - pastTime >= 0) && (shipParams.timestamp - pastTime <= 1))
        }

        private fun findActivationCandidate() : Optional<ShipParams.ConcreteShipParams> {
            val activationCandidate = previousStates.find { shipParams ->
                isActivationPossible(ship, shipParams)
            }

            activationCandidate as ShipParams.ConcreteShipParams

            return Optional.ofNullable(activationCandidate)
        }

        override fun getDisplayText(): String = "Rewind System"

        override fun advance(amount: Float, isPaused: Boolean) {
            super.advance(amount, isPaused)

            if (!isPaused) {
                timeElapsed += amount
                secondsTracker.advance(amount)
                if (secondsTracker.intervalElapsed()) {
                    // Another second passed, record a snapshot
                    previousStates.put(ShipParams.ConcreteShipParams(ship, timeElapsed))
                }
            }
        }

        override fun onStateSwitched(oldState: State?) {
            super.onStateSwitched(oldState)
        }

    }

    internal fun determineRewindLength(ship: ShipAPI): Int {
        return when (ship.hullSize) {
            null -> { 0 } //can never happen but gets rid of the warning

            DEFAULT,
            FIGHTER -> {
                //These two don't happen so... still, add support and make it 10 sec for them.
                10
            }

            FRIGATE -> 15
            DESTROYER -> 20
            CRUISER -> 30
            CAPITAL_SHIP -> 45
        }
    }

    sealed class ShipParams {

        data class ConcreteShipParams(val ship: ShipAPI, val timestamp: Float) : ShipParams() {
            val acceleration = ship.acceleration
            val location: Vector2f = ship.copyLocation
            val velocity: Vector2f = ship.velocity
            val angularVelocity = ship.angularVelocity
            val facing = ship.facing
            val maxHitpoints = ship.maxHitpoints
            val hitpoints = ship.hitpoints
            val usableWeapons = ship.usableWeapons
        }

        object EmptyShipParams : ShipParams()

    }


}