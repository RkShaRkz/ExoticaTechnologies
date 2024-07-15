package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize.*
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.combat.WeaponAPI
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
import org.lazywizard.lazylib.combat.entities.SimpleEntity
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
                .format("how_much", calculateTooltipStringReplacement())
                .format("cooldown_string", cooldownString)
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
        private var engine: CombatEngineAPI = Global.getCombatEngine()
        private val secondsTracker = IntervalUtil(0.95f, 1.05f)
        private val arcTimer = IntervalUtil(0.25f, 0.5f)
        private var timeElapsed: Float = 0f
        private val previousStates: RingBuffer<ShipParams> = RingBuffer<ShipParams>(
                determineRewindLength(ship) + 5,
                ShipParams.EmptyShipParams,
                ShipParams::class.java
        )

        //this should be either empty, or have an actual value
        private var rewindCandidate: Optional<ShipParams.ConcreteShipParams> = Optional.empty()

        override fun getBaseInDuration(): Float = 1f

        override fun getBaseActiveDuration(): Float = 1f

        override fun getBaseOutDuration(): Float = 1f

        override fun canActivate(): Boolean {
            // If less time has passed than we need to, we can't for sure
            if (timeElapsed < determineRewindLength(ship)) return false

            return findActivationCandidate().isPresent()
        }

        //TODO fix this!!!
        override fun getBaseCooldownDuration() = 5.toFloat() // COOLDOWN.toFloat()

        override fun onActivate() {
            super.onActivate()
            val candidate = findActivationCandidate()

            debugLog("--> onActivate()\tshould teleport from ${ship.location} to candidate ${candidate}\ttimeElapsed: ${timeElapsed}")

            if (candidate.isPresent()) {
                rewindCandidate = candidate

                // (un)necessary kotlin drama
                candidate.get().let {
                    debugLog("onActivate()\tcandidate was present, candidate location: ${it.location}")
                    //part 1 - afterimage on ship
                    ship.addAfterimage(
                            Color.CYAN.brighter(),
//                            it.location.getX(),
//                            it.location.getY(),
                            ship.location.getX(),
                            ship.location.getY(),
//                            0f,
//                            0f,
                            ship.velocity.getX(),
                            ship.velocity.getY(),
                            MAX_TIME,
                            0f,
                            1f,
                            MAX_TIME - 1f,
                            true,
                            false,
                            true
                    )

                    //part 2 - arc towards teleport location
                    engine.spawnEmpArc(
                            ship,
                            ship.location,
                            ship,
                            SimpleEntity(it.location),
                            DamageType.OTHER,
                            0f,
                            0f,
                            69420f,
                            null,
                            30f, //it used some dynamic formula but why not just use 30 all the time
                            Color.CYAN.brighter().brighter(),
                            Color.CYAN.brighter()
                    )

                    //part 3 - teleport
                    deploySavedState(rewindCandidate.get())
                    rewindCandidate = Optional.empty()
                }
            } else {
                debugLog("onActivate()\tcandidate was NOT present !!!")
            }

            debugLog("<-- onActivate()\tnew ship location is now ${ship.location}")
        }


        /*
        override fun onFinished() {
            super.onFinished()

//            val amount = engine.elapsedInLastFrame

//            arcTimer.advance(amount)
//            if (arcTimer.intervalElapsed()) {
                if (rewindCandidate.isPresent()) {
                    engine.spawnEmpArc(
                            ship,
                            ship.location,
                            ship,
                            SimpleEntity(rewindCandidate.get().location),
                            DamageType.OTHER,
                            0f,
                            0f,
                            69420f,
                            null,
                            30f, //it used some dynamic formula but why not just use 30 all the time
                            Color.CYAN.brighter().brighter(),
                            Color.CYAN.brighter()
                    )
                } else {
                    logger.error("rewindCandidate was not present in onFinished() for the spawnEmpArc!!!")
                }
//            }

            // And now, restore the ship!
            if (rewindCandidate.isPresent()) {
                deploySavedState(rewindCandidate.get())
                rewindCandidate = Optional.empty()
            } else {
                logger.error("rewindCandidate was not present in onFinished() for deploying the rewind candidate!!!")
            }
        }
         */

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
                findActivationCandidate().isPresent()
            } else if (healthLow && hasIncomingFire) {
                // true, check if possible
                findActivationCandidate().isPresent()
            } else if (healthReallyLow) {
                // true, check if possible
                findActivationCandidate().isPresent()
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

            // We have a ConcreteShipParams (non-invalid data) and we found a timestamp within a 1second of our target past time
            return shipParams is ShipParams.ConcreteShipParams
                    && ((shipParams.timestamp - pastTime >= 0) && (shipParams.timestamp - pastTime <= 1))
        }

        private fun findActivationCandidate(): Optional<ShipParams.ConcreteShipParams> {
            val activationCandidate = previousStates.find { shipParams ->
                isActivationPossible(ship, shipParams)
            }

            val retVal = if (activationCandidate == null) {
                Optional.empty()
            } else {
                activationCandidate as ShipParams.ConcreteShipParams

                Optional.of(activationCandidate)
            }
            debugLog("<-- findActivationCandidate() returning activationCandidate $activationCandidate (retVal = $retVal)")
            return retVal
        }

        override fun getDisplayText(): String = "Rewind System"

        override fun advance(amount: Float, isPaused: Boolean) {
            super.advance(amount, isPaused)

            if (!isPaused) {
                timeElapsed += amount
                secondsTracker.advance(amount)
                if (secondsTracker.intervalElapsed()) {
                    // Another second passed, record a snapshot
                    debugLog("Saving stats and location at time ${timeElapsed}\t(X, Y): (${ship.location.x}, ${ship.location.y})")
                    previousStates.put(ShipParams.ConcreteShipParams(ship, timeElapsed))
                }
            }
        }

        private fun deploySavedState(savedState: ShipParams.ConcreteShipParams) {
            debugLog("--> deploySavedState()\tDeploying stats and location from time ${savedState.timestamp}\t(X, Y): (${savedState.location.x}, ${savedState.location.y})\tcurrent loc: (${ship.location.x}, ${ship.location.y})")
            ship.velocity.set(savedState.velocity)
            ship.angularVelocity = savedState.angularVelocity
            ship.location.set(savedState.location.x, savedState.location.y)
            ship.facing = savedState.facing
            ship.maxHitpoints = savedState.maxHitpoints
            ship.hitpoints = savedState.hitpoints
            for (index in 0 until savedState.usableWeapons.size) {
                ship.usableWeapons[index] = savedState.usableWeapons[index]
            }
        }
    }


    internal fun determineRewindLength(ship: ShipAPI): Int {
        return when (ship.hullSize) {
            null -> {
                //can never happen but gets rid of the warning
                0
            }

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

    private fun calculateTooltipStringReplacement(): String {
        return if (::originalShip.isInitialized) {
            determineRewindLength(originalShip).toString()
        } else {
            "15 / 20 / 30 / 45"
        }
    }

    private fun debugLog(log: String) {
        if (DEBUG) logger.info("[RewindSystem] $log")
    }

    sealed class ShipParams {

        data class ConcreteShipParams(val ship: ShipAPI, val timestamp: Float) : ShipParams() {
            // Make an actual copy of all of the values instead of just reusing their reference
            // Primitives are assigned by-value, so they're automatically copied during assignment
            val location: Vector2f = Vector2f(ship.location)
            val velocity: Vector2f = Vector2f(ship.velocity)
            val angularVelocity = ship.angularVelocity
            val facing = ship.facing
            val maxHitpoints: Float = ship.maxHitpoints
            val hitpoints: Float = ship.hitpoints
            val usableWeapons: List<WeaponAPI> = ship.usableWeapons.toList()
        }

        object EmptyShipParams : ShipParams()

    }

    companion object {
        private const val DEBUG = true
        private const val ITEM = "et_rewindchip"
        private const val MAX_TIME = 15f
        private const val COOLDOWN = 300

        private const val COOLDOWN_REPLACEMENT = "*Has a cooldown of {cooldown} seconds*."
        private val cooldownString: String by lazy {
            "${
                COOLDOWN_REPLACEMENT.replace("{cooldown}", COOLDOWN.toString())
            }"
        }
    }

}