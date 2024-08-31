package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.ShipAPI.HullSize.*
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.sun.javafx.beans.annotations.NonNull
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.*
import exoticatechnologies.util.datastructures.Optional
import exoticatechnologies.util.datastructures.RingBuffer
import exoticatechnologies.util.timeutils.getClockTime
import exoticatechnologies.util.timeutils.getTimeFromSeconds
import org.apache.log4j.Logger
import org.json.JSONObject
import org.lazywizard.lazylib.combat.entities.SimpleEntity
import org.lwjgl.util.vector.Vector2f
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color
import java.util.concurrent.atomic.AtomicBoolean

class RewindSystem(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color = Color(225, 225, 225)
    private lateinit var originalShip: ShipAPI

    private val logger: Logger = Logger.getLogger(RewindSystem::class.java)

    override fun getBasePrice(): Int = 2500000

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
                .formatFloat("cooldown", COOLDOWN * getNegativeMult(member, mods, exoticData))
                .addToTooltip(tooltip, title)
    }

    override fun shouldAffectModule(ship: ShipAPI?, module: ShipAPI?) = false

    override fun applyToShip(
            id: String,
            member: FleetMemberAPI,
            ship: ShipAPI,
            mods: ShipModifications,
            exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        val subsystem = RewindSubsystem(ship, member, mods, exoticData, key)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)
    }

    inner class RewindSubsystem(
            ship: ShipAPI,
            val member: FleetMemberAPI,
            val mods: ShipModifications,
            val exoticData: ExoticData,
            private val stringKey: String
    ) : MagicSubsystem(ship) {
        private var engine: CombatEngineAPI = Global.getCombatEngine()
        private val secondsTracker = IntervalUtil(0.95f, 1.05f)
        private val arcTimer = IntervalUtil(0.25f, 0.35f)
        private val teleportTimer = IntervalUtil(PHASE_IN_DURATION - 0.05f, PHASE_IN_DURATION + 0.05f)//IntervalUtil(1.95f, 2.05f)
        private var timeElapsed: Float = 0f
        private val systemActivated = AtomicBoolean(false)
        private val justTurnedOff = AtomicBoolean(false)
        private val performedTeleport = AtomicBoolean(false)
        private val spawnAfterTeleportAfterimages = AtomicBoolean(false)
        @Volatile
        private var performedTeleportImagePosition: Vector2f? = null
        /**
        Measures *when* the system was activated and not *how long* it's been active for.
        Calculate how long it's active for by doing *(timeElapsed - activationTime)*

         If the value is less than 0, that should be interpreted as if it's not activated.
         */
        private var activationTime = -1f
        private val previousStates: RingBuffer<ShipParams> = RingBuffer<ShipParams>(
                determineRewindLength(ship) + 5,
                ActualShipParams.EmptyShipParams,
                ShipParams::class.java
        )

        @Volatile
        private var rewindCandidate: Optional<ShipParams> = Optional.empty()

        override fun getBaseInDuration(): Float = 1f

        override fun getBaseActiveDuration(): Float = PHASE_IN_DURATION

        override fun getBaseOutDuration(): Float = 0f

        override fun canActivate(): Boolean {
            // If less time has passed than we need to, we can't for sure
            if (timeElapsed < determineRewindLength(ship)) return false

            return findActivationCandidate().isPresent()
        }

        override fun getBaseCooldownDuration() = COOLDOWN * getNegativeMult(member, mods, exoticData)

        override fun getExtraInfoText(): String {
            val superText = super.getExtraInfoText()

            return if (superText == null) {
                if (canActivate()) {
                    superText
                } else {
                    StringUtils.getTranslation(stringKey, "cantActivate").toStringNoFormats()
                }
            } else {
                superText
            }
        }

        override fun onActivate() {
            super.onActivate()
            val candidate = findActivationCandidate()

            debugLog("--> onActivate()\tshould teleport from ${ship.location} to candidate ${candidate}\ttimeElapsed: ${timeElapsed}")
            debugLog("[${getClockTime()}] --> onActivate()\tshould teleport from ${ship.location} to candidate ${candidate}\ttimeElapsed: ${timeElapsed}", "Teleport")

            if (candidate.isPresent()) {
                rewindCandidate = candidate
                turnOnSystem()
                // (un)necessary kotlin drama
                candidate.get().let {
                    debugLog("onActivate()\tcandidate was present, candidate location: ${it.location}")
                    //part 1 - afterimage on ship
                    //note1: afterimage uses ship-relative coordinates, so anything other than 0,0 will paint it at some distance from the ship
                    //note2: using non-zero velocity will make the image "drift away" from the ship instead of staying on top of it

                    generatePreTeleportAfterimages()

                    // And the last one - two afterimages that glide off to the target location 'simulating' the ship going there
                    // If we use PHASE_IN_DURATION (as we should) here, the afterimage is kinda too slow and gets carried
                    // over with the teleported ship; so lets speed it up and use 1/PHASE_IN_DURATION or 0.5f
                    generatePreTeleportGlidingAfterimages(it.location, 1 / PHASE_IN_DURATION)

                    // The other parts (part 2 and part 3) will happen over time in advance()
                }
            } else {
                debugLog("onActivate()\tcandidate was NOT present !!!")
            }

            debugLog("<-- onActivate()\tnew ship location is now ${ship.location}")
        }

        override fun onFinished() {
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] --> onFinished()", "Teleport")
            super.onFinished()
            turnOffSystem()
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] <-- onFinished()", "Teleport")
        }

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

            // Instead of always searching the buffer, then checking a few eliminating constants (like before)
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
        private fun isActivationPossible(ship: ShipAPI, shipParams: ShipParams?): Boolean {
            // calculate the timestamp we're looking for in the past
            val pastTime = timeElapsed - determineRewindLength(ship)

            return if (shipParams is ActualShipParams) {
                // Check the timestamp for valid ActualShipParams implementations, and return false for EmptyShipParams
                when(shipParams) {
                    is ActualShipParams.ConcreteShipParams,
                    is ActualShipParams.MultiModuleShipParams -> (shipParams.timestamp - pastTime >= 0) && (shipParams.timestamp - pastTime <= 1)
                    is ActualShipParams.EmptyShipParams -> false
                }.exhaustive
            } else {
                // This one catches nulls
                false
            }
        }

        private fun findActivationCandidate(): Optional<ShipParams> {
            // Due to lack of synchronization, a data race or something happens here and shipParams can end up being null. yuck.
            val activationCandidate = previousStates.find { shipParams: ShipParams? ->
                isActivationPossible(ship, shipParams)
            }

            val retVal = if (activationCandidate == null) {
                Optional.empty()
            } else {
                when(activationCandidate) {
                    is ActualShipParams.ConcreteShipParams -> {
                        Optional.of(activationCandidate)
                    }
                    is ActualShipParams.MultiModuleShipParams -> {
                        Optional.of(activationCandidate)
                    }

                    else -> throw IllegalStateException("Encountered 'activationCandidate' of type ${activationCandidate.javaClass} in findActivationCandidate()! Add support for this !!!")
                }.exhaustive
            }
            debugLog("<-- findActivationCandidate() returning activationCandidate $activationCandidate (retVal = $retVal)", "ActivationCandidate")
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
                    if (ship.isThisAMultiModuleShipFast().not()) {
                        previousStates.put(ActualShipParams.ConcreteShipParams(ship, timeElapsed))
                    } else {
                        previousStates.put(ActualShipParams.MultiModuleShipParams(ship, timeElapsed))
                    }
                }

                if (spawnAfterTeleportAfterimages.get()) {
                    // part 4 - after teleport afterimages

                    // Finally, spawn the ghost images from last position before teleporting that will  home in
                    // on our current position and give a fake idea that the images that glided away have arrived
                    debugLog("[${getClockTime()}] performedTeleportImagePosition = ${performedTeleportImagePosition}", "Teleport")
                    performedTeleportImagePosition?.let {
                        generatePostTeleportGlidingAfterimages(
                                POST_TELEPORT_MAX_SHADOW_DISTANCE,
                                POST_TELEPORT_AFTERIMAGE_DURATION,
                                it
                        )
                    }
                    // And clear out the 'performedTeleportImagePosition'
                    performedTeleportImagePosition = null

                    spawnAfterTeleportAfterimages.compareAndSet(true, false)
                }

                // Do time-specific system stuff here
                // The following piece got somewhat complicated so here's a breakdown:
                // 1. if system activated, the ship gets an after image (part 1)
                // 2. then the fattening EMP arc starts showing (part 2)
                // 3. after the "active skill" aka baseIn + baseActive (1+2=3 seconds) passes, the skill turns off
                // 4. before the skill turns off, the teleport counter will have filled up and the ship would have teleported
                // after which it would have set the "teleportPerformed" flag (part 3)
                // 5. if the system deactivated / turned off but no teleportation happened, a fallback check will happen
                // in a subsequent frame, this is the "justTurnedOff && !performedTeleport" case which is when we
                // just teleport (part 3 again)
                // 6. immediatelly after performing teleportation, set a 'spawnAfterTeleportAfterimages' flag (part 4)
                // 7. in a subsequent frame, the "performedTeleport" is cleared (part 5)
                if (systemActivated.get()) {
                    arcTimer.advance(amount)
                    if (arcTimer.intervalElapsed()) {
                        // Draw increasingly-fat arc here
                        //part 2 - arc towards teleport location
                        if (rewindCandidate.isPresent()) {
                            rewindCandidate.get().let {
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
                                        15f * getActivationDuration(),
                                        Color.CYAN.brighter().brighter(),
                                        Color.CYAN.brighter()
                                )
                            }
                        }
                    }

                    // part 3 - actual teleportation to previous position
                    teleportTimer.advance(amount)
                    if (teleportTimer.intervalElapsed()) {
                        // And now, restore the ship!

                        if (rewindCandidate.isPresent()) {
                            performedTeleportImagePosition = ship.location.clone()
                            deploySavedState(rewindCandidate.get())
                            rewindCandidate = Optional.empty()
                            performedTeleport.compareAndSet(false, true)
                            spawnAfterTeleportAfterimages.compareAndSet(false, true)
                            playSound(SHIP_TELEPORTED_SOUND, ship)
                        } else {
                            // Log error only if teleport was not performed to avoid confusing myself since teleport
                            // timer can't be turned off.
                            if (performedTeleport.get().not()) {
                                logger.error("rewindCandidate was not present in teleportTimer part for deploying the rewind candidate!!!")
                            }
                        }
                    }
                } else if (justTurnedOff.get() && !performedTeleport.get()) {
                    // In case the system got turned off and the teleport timer somehow didn't fire, lets be sure to repeat
                    // the action here, just in case. Probably not necessary, but just to be sure. We will omit loading
                    // up the teleportTimer because [turnOffSystem] will zero it out - so it won't make sense to fill it.
                    logger.warn("In the fallback part - candidate: ${rewindCandidate}")
                    debugLog("In the fallback part - candidate: ${rewindCandidate}", "Teleport")
                    if (rewindCandidate.isPresent()) {
                        // part 3 - fallback in case it didn't happen properly
                        // Restore the ship if this is non-empty
                        logger.warn("Performing fallback teleportation!")
                        deploySavedState(rewindCandidate.get())
                        rewindCandidate = Optional.empty()
                        justTurnedOff.compareAndSet(true, false)
                        spawnAfterTeleportAfterimages.compareAndSet(false, true)
                        playSound(SHIP_TELEPORTED_SOUND, ship)
                    } else {
                        logger.error("rewindCandidate was not present in fallback part after justTurnedOff and not teleported!!!")
                    }
                } else if (performedTeleport.get()) {
                    // part 5 - performedTeleport bit
                    // If we performed teleport, just zero it out - this is the normal behaviour
                    // for one frame after teleporting back to original position
                    performedTeleport.compareAndSet(true, false)
                    // It would be ideal to use compareAndSet(true,false) here but we can't guarantee that here.
                    // Might just be safer to zero it out regardless of what it was.
                    justTurnedOff.set(false)
                }
            }
        }

        /**
         * Sets the [systemActivated] atomic boolean only if it's unset, and marks the activation time.
         * Also plays the "system_phase_teleporter" sound
         */
        private fun turnOnSystem() {
            systemActivated.compareAndSet(false, true)
            activationTime = timeElapsed
            playSound(SYSTEM_ACTIVATION_SOUND, ship)
        }

        /**
         * Unsets the [systemActivated] atomic boolean only if it's set, clears the activation time,
         * and also zeroes the [arcTimer] and [teleportTimer] [IntervalUtil] timers
         */
        private fun turnOffSystem() {
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] --> turnOffSystem()\t\tactivationTime: ${activationTime}\tactivation duration: ${getActivationDuration()}", "Teleport")
            systemActivated.compareAndSet(true, false)
            activationTime = -1f
            // reset timers as well
            arcTimer.elapsed = 0f
            teleportTimer.elapsed = 0f
            // Since I believe this may cause a data race, lets do one more thing to be sure
            justTurnedOff.compareAndSet(false, true)
        }
        private fun getActivationDuration(): Float = if (activationTime > -1f) { timeElapsed - activationTime } else { 0f }


        private fun deploySavedState(savedState: ShipParams) {
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] --> deploySavedState()\tDeploying stats and location from time ${savedState.timestamp}\t(X, Y): (${savedState.location.x}, ${savedState.location.y})\tcurrent loc: (${ship.location.x}, ${ship.location.y})\tship.isMultiModule ? ${ship.isThisAMultiModuleShipFast()}")

            when (savedState) {
                is ActualShipParams.ConcreteShipParams -> {
                    // The ConcreteShipParams version
                    applySavedStateToModule(ship, savedState)
                }

                is ActualShipParams.MultiModuleShipParams -> {
                    // The MultiModuleShipParams version, get all modules of the ship and apply it's saved params to each one of them
                    for (section in getAllShipSections(ship)) {
                        // Now, apply stuff to each section accordingly
                        val state = savedState.moduleParamsMap[section.id]
                        if (state != null) {
                            applySavedStateToModule(section, state)
                        } else {
                            logger.error("'state' was null in savedState.moduleParamsMap[section.id] for section.id ${section.id}")
                        }
                    }
                }

                else -> throw IllegalStateException("Encountered 'savedState' of type ${savedState.javaClass} in deploySavedState()! Add support for this !!!")
            }.exhaustive
        }

        private fun applySavedStateToModule(module: ShipAPI, savedState: ActualShipParams.ConcreteShipParams) {
            debugLog("--> applySavedStateToModule()\tmodule: ${module}, savedState: ${savedState}")
            module.velocity.set(savedState.velocity)
            module.angularVelocity = savedState.angularVelocity
            module.location.set(savedState.location.x, savedState.location.y)
            module.facing = savedState.facing
            module.maxHitpoints = savedState.maxHitpoints
            module.hitpoints = savedState.hitpoints
            if (module.system != null) {
                module.system.cooldownRemaining = savedState.systemCooldownRemaining
                module.system.ammo = savedState.systemAmmo
            }
            if (module.shield != null) {
                if (savedState.wasShieldOn) { module.shield.toggleOn() } else { module.shield.toggleOff() }
            }
            for ((index, savedStateWeaponData) in savedState.weaponsData.withIndex()) {
                val shipWeapon = module.allWeapons[index]
                if (shipWeapon == savedStateWeaponData.weapon) {
                    // If they weren't disabled/permanently disabled and they are now, restore them to good condition
                    if (shipWeapon.isDisabled && savedStateWeaponData.isDisabled.not()) {
                        shipWeapon.repair()
                    }
                    if (shipWeapon.isPermanentlyDisabled && savedStateWeaponData.isPermanentlyDisabled.not()) {
                        shipWeapon.repair()
                    }

                    // If they were disabled/permanently disabled and they aren't now (?!?), disable them
                    if (shipWeapon.isDisabled.not() && savedStateWeaponData.isDisabled) {
                        shipWeapon.disable()
                    }
                    if (shipWeapon.isPermanentlyDisabled.not() && savedStateWeaponData.isPermanentlyDisabled) {
                        shipWeapon.disable(true)
                    }

                    // Restore the rest of the parameters
                    shipWeapon.currHealth = savedStateWeaponData.currentHealth
                    shipWeapon.currAngle = savedStateWeaponData.currentAngle
                    shipWeapon.ammo = savedStateWeaponData.currentAmmo
                    shipWeapon.setRemainingCooldownTo(savedStateWeaponData.cooldownRemaining)
                } else {
                    logger.error("Weapon [${shipWeapon} - [${shipWeapon.displayName}] doesn't match the WeaponData's instance [${savedStateWeaponData.weapon} - ${savedStateWeaponData.weapon.displayName}] !!!")
                }
            }
        }

        private fun generatePreTeleportAfterimages() {
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] --> generatePreTeleportAfterimages()", "Teleport")
            // One afterimage above the ship
            addAfterimageToWholeShip(
                    ship,
                    AfterimageData(
                            color = Color.CYAN.brighter(),
                            locX = 0f,
                            locY = 0f,
                            velX = 0f,
                            velY = 0f,
                            maxJitter = MAX_TIME,
                            inDuration = 0f,
                            duration = PHASE_IN_DURATION,
                            outDuration = MAX_TIME - 1f,
                            additive = true,
                            combineWithSpriteColor = true,
                            aboveShip = true
                    )
            )

            // Another afterimage below the ship with added jitter
            addAfterimageToWholeShip(
                    ship,
                    AfterimageData(
                            color = Color.CYAN.brighter(),
                            locX = 0f,
                            locY = 0f,
                            velX = 0f,
                            velY = 0f,
                            maxJitter = 5*MAX_TIME,
                            inDuration = 0f,
                            duration = PHASE_IN_DURATION,
                            outDuration = MAX_TIME - 1f,
                            additive = true,
                            combineWithSpriteColor = false,
                            aboveShip = false
                    )
            )

            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] <-- generatePreTeleportAfterimages()\tgenerated AfterImages with inDuration: ${0f}, duration: ${PHASE_IN_DURATION}, outDuration: ${MAX_TIME - 1f}", "Teleport")
        }

        private fun generatePreTeleportGlidingAfterimages(destinationLocation: Vector2f, timeAfterimagesShouldTakeToReachDestination: Float) {
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] --> generatePreTeleportGlidingAfterimages()\tdestinationLocation=${destinationLocation}\ttimeAfterimagesShouldTakeToReachDestination=${timeAfterimagesShouldTakeToReachDestination}", "Teleport")
            val velocityVector = calculateVelocityVector(ship.location, destinationLocation, timeAfterimagesShouldTakeToReachDestination)
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] generatePreTeleportGlidingAfterimages()\tvelocityVector=${velocityVector}", "Teleport")
            addAfterimageToWholeShip(
                    ship,
                    AfterimageData(
                            color = Color.CYAN.brighter(),
                            locX = 0f,
                            locY = 0f,
                            velX = velocityVector.x,
                            velY = velocityVector.y,
                            maxJitter = MAX_TIME,
                            inDuration = 0f,
                            duration = PHASE_IN_DURATION,
                            outDuration = MAX_TIME - 1f,
                            additive = true,
                            combineWithSpriteColor = true,
                            aboveShip = true
                    )
            )
            addAfterimageToWholeShip(
                    ship,
                    AfterimageData(
                            color = Color.CYAN.brighter(),
                            locX = 0f,
                            locY = 0f,
                            velX = velocityVector.x,
                            velY = velocityVector.y,
                            maxJitter = 5*MAX_TIME,
                            inDuration = 0f,
                            duration = PHASE_IN_DURATION,
                            outDuration = MAX_TIME - 1f,
                            additive = true,
                            combineWithSpriteColor = false,
                            aboveShip = false
                    )
            )
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] <-- generatePreTeleportGlidingAfterimages()\tgenerated AfterImages with inDuration: ${0f}, duration: ${PHASE_IN_DURATION}, outDuration: ${MAX_TIME - 1f}", "Teleport")
        }

        private fun generatePostTeleportGlidingAfterimages(maxDistance: Float, afterimageDuration: Float, oldLocation: Vector2f) {
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] --> generatePostTeleportGlidingAfterimages()\tmaxDistance=${maxDistance}", "Teleport")
            // Due to the built-in limit on how far an Afterimage can draw away from the ship,
            // lets try using something much closer to our current position rather than the original one
            val newLocationVector = ship.location.clone()
            val fromOldPositionToCurrentPositionDirectionVector = newLocationVector.sub(oldLocation)
            val normalizedDirection = fromOldPositionToCurrentPositionDirectionVector.normalized()
            val shadowVector = newLocationVector.sub(normalizedDirection.mul(maxDistance))
            val newVelocityVector = calculateVelocityVector(shadowVector, newLocationVector, afterimageDuration)
            val shadowVectorRelativeToNewLocation = shadowVector.sub(newLocationVector)

            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] generatePostTeleportGlidingAfterimages()\tnewLocationVector = ${newLocationVector}\tfromOldPositionToCurrentPositionDirectionVector = ${fromOldPositionToCurrentPositionDirectionVector}\tnormalizedDirection = ${normalizedDirection}\tshadowVector = ${shadowVector}\tnewVelocityVector = ${newVelocityVector}", "Teleport")
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] generatePostTeleportGlidingAfterimages()\tCreating shadows from ${shadowVector} to ${newLocationVector}\tlast position = ${oldLocation}\tvelocity vector = ${newVelocityVector}", "Teleport")

            addAfterimageToWholeShip(
                    ship,
                    AfterimageData(
                            color = Color.CYAN.brighter(),
                            locX = shadowVectorRelativeToNewLocation.x,
                            locY = shadowVectorRelativeToNewLocation.y,
                            velX = newVelocityVector.x,
                            velY = newVelocityVector.y,
                            maxJitter = MAX_TIME,
                            inDuration = 0f,
                            duration = afterimageDuration,
                            outDuration = 0f,
                            additive = true,
                            combineWithSpriteColor = true,
                            aboveShip = true
                    )
            )
            addAfterimageToWholeShip(
                    ship,
                    AfterimageData(
                            color = Color.CYAN.brighter(),
                            locX = shadowVectorRelativeToNewLocation.x,
                            locY = shadowVectorRelativeToNewLocation.y,
                            velX = newVelocityVector.x,
                            velY = newVelocityVector.y,
                            maxJitter = MAX_TIME,
                            inDuration = 0f,
                            duration = afterimageDuration,
                            outDuration = 0f,
                            additive = true,
                            combineWithSpriteColor = false,
                            aboveShip = false
                    )
            )
            debugLog("[${getClockTime()} - ${getTimeFromSeconds(getActivationDuration())}] <-- generatePostTeleportGlidingAfterimages()\t\tgenerated AfterImages with inDuration: ${0f}, duration: ${afterimageDuration}, outDuration: ${0f}", "Teleport")
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

    private fun debugLog(log: String, @NonNull logTag: String) {
        if (DEBUG_LOGTAG_LIST.contains(logTag)) logger.info("[RewindSystem:$logTag] $log")
    }

    /**
     * Necessary interface workaround to be able to use a sealed class as a generic type
     */
    interface ShipParams {
        val location: Vector2f
        val timestamp: Float
    }

    sealed class ActualShipParams: ShipParams {

        data class ConcreteShipParams(val ship: ShipAPI, override val timestamp: Float) : ActualShipParams() {
            // Make an actual copy of all of the values instead of just reusing their reference
            // Primitives are assigned by-value, so they're automatically copied during assignment
            override val location: Vector2f = Vector2f(ship.location)
            val velocity: Vector2f = Vector2f(ship.velocity)
            val angularVelocity = ship.angularVelocity
            val facing = ship.facing
            val maxHitpoints: Float = ship.maxHitpoints
            val hitpoints: Float = ship.hitpoints
            val weaponsData: List<WeaponData> = ship.allWeapons.toList().map { weapon -> WeaponData(weapon) }
            val systemAmmo = ship.system?.let {it.ammo} ?: INVALID_INT_VALUE
            val systemCooldownRemaining = ship.system?.let { it.cooldownRemaining } ?: INVALID_FLOAT_VALUE
            val wasShieldOn: Boolean = ship.shield?.let { it.isOn } ?: false
        }

        data class MultiModuleShipParams(val ship: ShipAPI, override val timestamp: Float) : ActualShipParams() {
            override val location: Vector2f = Vector2f(ship.location)
            // now, since we have multiple modules, we will need to keep a track of all of them
            val moduleParamsMap = HashMap<String, ConcreteShipParams>()

            init {
                val shipSections = getAllShipSections(ship)
                shipSections.forEach { section ->
                    moduleParamsMap[section.id] = ConcreteShipParams(section, timestamp)

                    moduleParamsMap.toMap()
                }
            }
        }

        object EmptyShipParams : ActualShipParams() {
            override val location: Vector2f
                get() = throw IllegalStateException("EmptyShipParams does not have 'location'")
            override val timestamp: Float
                get() = throw IllegalStateException("EmptyShipParams does not have 'timestamp'")
        }
    }

    data class WeaponData(val weapon: WeaponAPI) {
        val currentHealth: Float = weapon.currHealth
        val currentAngle: Float = weapon.currAngle
        val currentAmmo: Int = weapon.ammo
        val cooldownRemaining: Float = weapon.cooldownRemaining
        val isDisabled: Boolean = weapon.isDisabled
        val isPermanentlyDisabled: Boolean = weapon.isPermanentlyDisabled
    }

    companion object {
        // To turn off logtag-based logging, make sure this list is empty
        // Possible values are "ActivationCandidate", "Teleport"
        private val DEBUG_LOGTAG_LIST: List<String> = listOf()
        private const val DEBUG = false

        private const val ITEM = "et_rewindchip"
        private const val MAX_TIME = 15f
        private const val COOLDOWN = 300
        private const val PHASE_IN_DURATION = 2f

        private const val SYSTEM_ACTIVATION_SOUND = "gigacannon_charge"
        private const val SHIP_TELEPORTED_SOUND = "tachyon_lance_fire"

        private const val POST_TELEPORT_AFTERIMAGE_DURATION = 1f
        private const val POST_TELEPORT_MAX_SHADOW_DISTANCE = 2500f

        private const val INVALID_INT_VALUE = -5
        private const val INVALID_FLOAT_VALUE = INVALID_INT_VALUE.toFloat()
    }

}