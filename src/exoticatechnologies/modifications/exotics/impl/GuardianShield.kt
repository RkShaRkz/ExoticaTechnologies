package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.mission.FleetSide
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.IntervalUtil
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.util.CollisionUtil
import exoticatechnologies.util.StringUtils
import exoticatechnologies.util.Utilities
import org.apache.log4j.Logger
import org.json.JSONObject
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.subsystems.MagicSubsystemsManager
import org.magiclib.subsystems.drones.MagicDroneSubsystem
import java.awt.Color
import kotlin.math.max
import kotlin.math.min


class GuardianShield(key: String, settings: JSONObject) : Exotic(key, settings) {
    override var color = Color(0xD93636)
    private lateinit var originalShip: ShipAPI
    private lateinit var originalOwnersShield: ShieldAPI

    private val logger: Logger = Logger.getLogger(GuardianShieldDrone::class.java)

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

    override fun applyToShip(id: String, member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        originalOwnersShield = ship.shield
        // First thing's first - disable the ship's shield
        ship.setShield(ShieldAPI.ShieldType.NONE, 0f, 0f, 0f)

        // Secondly, give the ship a new subsystem to summon a drone hosting the Guardian Shield that will follow the ship around
        val subsystem = GuardianShieldDrone(ship)
        subsystem.setDronesToSpawn(1)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)
    }

    override fun onDestroy(member: FleetMemberAPI) {
        super.onDestroy(member)

        originalShip.setShield(
                originalOwnersShield.type,
                originalOwnersShield.upkeep,
                originalOwnersShield.fluxPerPointOfDamage,
                originalOwnersShield.arc
        )
    }

    inner class GuardianShieldDrone(ship: ShipAPI) : MagicDroneSubsystem(ship) {

        var engine: CombatEngineAPI = Global.getCombatEngine()
        private var drone: ShipAPI? = null
        private val tracker = IntervalUtil(0.3f, 0.5f)
        private val shieldController = ShieldController()

        override fun onActivate() {
            super.onActivate()
            if (drone == null) {
                drone = spawnDrone()
                drone?.let { it.shield.toggleOn() }
            } else {
                drone?.let { it.shield.toggleOn() }
            }
        }


        override fun getBaseActiveDuration(): Float = 0f

        override fun getBaseCooldownDuration(): Float = 0f

        override fun getDisplayText(): String = "Guardian Shield"

        override fun getDroneVariant(): String = "exotica_guardianshield_standard"

        override fun targetOnlyEnemies(): Boolean = false

        override fun requiresTarget(): Boolean = false

        override fun shouldActivateAI(amount: Float): Boolean {
            if (engine.isPaused) {
                return false
            }
            if (drone == null) {
                drone = spawnDrone()
            }
            drone?.let {
                if (!it.isAlive && ship.isAlive) {
                    return false
                }
            }

            // Move the drone to center of ship
            drone?.let {
                it.fixedLocation = ship.location
                it.facing = ship.facing
            }

            return shieldController.assesSituation(amount)
        }

        override fun advance(amount: Float, isPaused: Boolean) {
            super.advance(amount, isPaused)

            if (!isPaused) {
                // Move the drone to center of ship
                drone?.let {
                    it.location.set(ship.location.x, ship.location.y)
                    it.facing = ship.facing
                }

                shieldController.assesSituation(amount)
                shieldController.controlShield(amount)
                shieldController.shieldPushOutEffect(amount)
                shieldController.transferFlux(amount, twoway = false, transfer = true)
            }
        }

        private fun systemOn() {
            drone?.let {
                it.shield.toggleOn()
                logger.info("systemOn()\tshield on ? ${it.shield.isOn}")
            }
        }

        private fun systemOff() {
            drone?.let {
                it.shield.toggleOff()
                logger.info("systemOff()\tshield off ? ${it.shield.isOff}")
            }
        }

        override fun spawnDrone(): ShipAPI {
            Global.getCombatEngine().getFleetManager(ship.owner).isSuppressDeploymentMessages = true

            val fleetSide = FleetSide.values()[ship.owner]
            val fighter = CombatUtils.spawnShipOrWingDirectly(
                    getDroneVariant(),
                    FleetMemberType.SHIP,
                    fleetSide,
                    1f,
                    ship.location,
                    ship.facing
            )
            activeWings[fighter] = getPIDController()
            fighter.shipAI = null
            fighter.giveCommand(ShipCommand.SELECT_GROUP, null, 99)
            Global.getCombatEngine().getFleetManager(ship.owner).isSuppressDeploymentMessages = false

            fighter.isForceHideFFOverlay = true
            // Make the shield-hosting drone invulnerable, so that it's not targetable.
            // Because the owning ship keeps shooting it down.
            fighter.mutableStats.hullDamageTakenMult.modifyMult(INVULNERABLE_SHIELD_DRONE, 0f)
            // Set it's collision channel to fighter - since they can fly over the ship and should still react to most of the things
            fighter.collisionClass = CollisionClass.FIGHTER
            logger.info("<-- spawnDrone()\tdrone shield: ${fighter.shield}, shield arc: ${fighter.shield.arc}, shield type: ${fighter.shield.type}, shield active ? ${fighter.shield.isOn}")
            return fighter
        }

        inner class ShieldController {

            fun assesSituation(amount: Float): Boolean {
                tracker.advance(amount)
                if (tracker.intervalElapsed()) {
                    val shipInDanger = ship.hullLevel < 0.15f

                    drone?.let {
                        val shieldInDanger = it.hullLevel < 1f
                        if (it.fluxTracker.fluxLevel > 0.96f && !shipInDanger && !shieldInDanger) {
                            logger.info("calling systemOff()\t\tif (it.fluxTracker.fluxLevel > 0.96f && !shipInDanger && !shieldInDanger)")
                            systemOff()
                            return false
                        }
                    }
                    val allies = AIUtils.getNearbyAllies(ship, 1500f)
                    // add shield to allies if it's non-null
                    if (drone != null) {
                        allies.add(drone)
                    }
                    // check if anybody needs help
                    for (ally in allies) {
                        if (ally.aiFlags != null
                                && (ally.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)
                                        || ally.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP))) {
                            logger.info("calling systemOn()\t\tallies need help condition")
                            systemOn()
                            return true
                        }
                    }
                    val enemies = AIUtils.getNearbyEnemies(ship, 2000f)
                    for (enemy in enemies) {
                        if ((enemy.aiFlags != null && enemy.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP))
                                || enemy.isRetreating) {
                            continue
                        }
                        logger.info("calling systemOn()\t\tnearby enemies condition")
                        systemOn()
                        return true
                    }
                    val projectiles: List<DamagingProjectileAPI> = engine.projectiles
                    for (proj in projectiles) {
                        if (proj.owner != ship.owner
                                && (MathUtils.getDistance(ship, proj) > 1500 || MathUtils.getDistance(drone, proj) > 1500)) {
                            logger.info("calling systemOn()\t\tprojectiles near ship or shield condition")
                            systemOn()
                            return true
                        }
                    }
                    val beams: List<BeamAPI> = engine.beams
                    for (beam in beams) {
                        if (beam.source.owner != ship.owner
                                && (MathUtils.isWithinRange(ship, beam.to, 1500f)
                                        || MathUtils.isWithinRange(drone, beam.to, 1500f))) {
                            logger.info("calling systemOn()\t\tbeam within range of ship or shield condition")
                            systemOn()
                            return true
                        }
                    }
                    logger.info("calling systemOff()\t\tno other conditions were met")
                    systemOff()
                    return false
                }

                // what do we do here? lets fallback to returning false, but don't touch the shield
                logger.info("just returning false, not touching shield")
                return false
            }

            fun controlShield(amount: Float) {
                drone?.let {
                    if (it.isHulk || it.shield.isOff) {
                        it.collisionClass = CollisionClass.FIGHTER
                        it.collisionRadius = 400f
                    } else if (it.shield.isOn) {
                        it.shield.activeArc = 360f
                        var radius = it.collisionRadius
                        if (radius < 1500) {
                            radius += 300 * amount
                        } else {
                            radius = 1500f
                        }
                        it.shield.radius = radius
                        it.collisionRadius = radius
                    }
                    if (it.fluxTracker.isOverloaded && Math.random() < 0.1) {
                        engine.applyDamage(ship, ship.location, MathUtils.getRandomNumberInRange(0f, 100f), DamageType.ENERGY, 0f, true, true, ship)
                    }
                }
            }

            fun shieldPushOutEffect(amount: Float) {
                drone?.let {
                    for (ship in AIUtils.getNearbyEnemies(it, it.collisionRadius)) {
                        if (MathUtils.getDistance(ship.location, it.location) < ship.collisionRadius + it.getCollisionRadius()) { //hmm, I needs to override the no negative result thing here.
                            val pointToTest = VectorUtils.clampLength(Vector2f.sub(it.getLocation(), ship.location, null), it.getShieldRadiusEvenIfNoShield())
                            val collisionPoint: Vector2f? = CollisionUtil.getShipCollisionPoint(it.getLocation(), pointToTest, ship)
                            collisionPoint?.let { collisionPoint -> //ugh, this shadowing is bad but i'm lazy
                                if (!ship.isStation && !(ship.isStationModule && ship.parentStation.isStation)) {
                                    ship.velocity.set(it.getVelocity())
                                    ForceApplier.applyMomentum(ship, collisionPoint, Vector2f.sub(it.getLocation(), ship.location, null), amount * 10f, true)
                                }
                                ForceApplier.applyMomentum(it.getParentStation(), collisionPoint, Vector2f.sub(ship.location, it.getLocation(), null), amount * -0.5f, true)
                            }
                        }
                    }
                }
            }

            fun transferFlux(amount: Float, twoway: Boolean = false, transfer: Boolean = false) {
                /**
                 * I don't know where i'm going with this two-way heat exchange
                 * the whole concept of working with percentages is kinda broken at it's core:
                 *  - the shield drone has 175k flux capacity
                 *  - the host ship has whatever, lets say 25k or 8k.
                 *  - 10% of shield drone's capacity (17.5k) will transform to 2.5k or 800 flux. 15k flux will have been lost.
                 */
                drone?.let {
                    // should be between 0-100%
                    val droneFluxTracker = it.fluxTracker
                    val droneSoftFluxPercentageLevel = (droneFluxTracker.currFlux - droneFluxTracker.hardFlux) / droneFluxTracker.maxFlux
                    val droneHardFluxPercentageLevel = droneFluxTracker.hardFlux / droneFluxTracker.maxFlux

                    val shipFluxTracker = ship.fluxTracker
                    val shipSoftFluxPercentageLevel =  (shipFluxTracker.currFlux - shipFluxTracker.hardFlux) / shipFluxTracker.maxFlux
                    val shipHardFluxPercentageLevel = shipFluxTracker.hardFlux / shipFluxTracker.maxFlux
                    // Check the host-ship's flux levels.
                    // If they're different from the shield-host's levels, apply the sharing code to both of them.
                    logger.info("droneSoftFluxPercentageLevel: ${droneSoftFluxPercentageLevel}, droneHardFluxPercentageLevel: ${droneHardFluxPercentageLevel}, drone hard flux: ${droneFluxTracker.hardFlux}, drone current flux: ${droneFluxTracker.currFlux}, drone max flux: ${droneFluxTracker.maxFlux}")
                    logger.info("shipSoftFluxPercentageLevel: ${shipSoftFluxPercentageLevel}, shipHardFluxPercentageLevel: ${shipHardFluxPercentageLevel}, ship hard flux: ${shipFluxTracker.hardFlux}, ship current flux: ${shipFluxTracker.currFlux}, ship max flux: ${shipFluxTracker.maxFlux}")

                    // Apply flux to the ship
                    // keep the difference clamped between 0-100% by doing max(diff,0) to keep it >=0, and min(that,100) to keep it <=100
                    val droneToShipSoftFluxDiff = min(max(droneSoftFluxPercentageLevel - shipSoftFluxPercentageLevel, 0f), 100f)
                    val droneToShipHardFluxDiff = min(max(droneHardFluxPercentageLevel - shipHardFluxPercentageLevel, 0f), 100f)
                    // because the diff isn't in (0, 1.00) range but rather (1,100), we need to divide to not end up having it 100 bigger
                    val droneToShipSoftFluxAmount = (droneToShipSoftFluxDiff * shipFluxTracker.maxFlux) / 100f
                    val droneToShipHardFluxAmount = (droneToShipHardFluxDiff * shipFluxTracker.maxFlux) / 100f
                    logger.info("droneToShipSoftFluxDiff: ${droneToShipSoftFluxDiff}, droneToShipSoftFluxAmount: ${droneToShipSoftFluxAmount}, droneToShipHardFluxDiff: ${droneToShipHardFluxDiff}, droneToShipHardFluxAmount: ${droneToShipHardFluxAmount}")
                    // If doing two-way, we need to calculate these things for the other direction too before we increase them
                    if (twoway) {
                        logger.info("Two-way flux exchange")
                        val shipToDroneSoftFluxDiff = min(max(shipSoftFluxPercentageLevel - droneSoftFluxPercentageLevel, 0f), 100f)
                        val shipToDroneHardFluxDiff = min(max(shipHardFluxPercentageLevel - droneHardFluxPercentageLevel, 0f), 100f)
                        // because the diff isn't in (0, 1.00) range but rather (0,100), we need to divide to not end up having it 100 bigger
                        val shipToDroneSoftFluxAmount = (shipToDroneSoftFluxDiff * droneFluxTracker.maxFlux) / 100f
                        val shipToDroneHardFluxAmount = (shipToDroneHardFluxDiff * droneFluxTracker.maxFlux) / 100f
                        logger.info("shipToDroneSoftFluxDiff: ${shipToDroneSoftFluxDiff}, shipToDroneSoftFluxAmount: ${shipToDroneSoftFluxAmount}, shipToDroneHardFluxDiff: ${shipToDroneHardFluxDiff}, shipToDroneHardFluxAmount: ${shipToDroneHardFluxAmount}")

                        // apply increases
                        shipFluxTracker.increaseFlux(droneToShipSoftFluxAmount, false)
                        shipFluxTracker.increaseFlux(droneToShipHardFluxAmount, true)
                        logger.info("Applied flux to ship! hard flux: ${droneToShipHardFluxAmount}, soft flux: ${droneToShipSoftFluxAmount}")
                        logger.info("shipSoftFluxPercentageLevel: ${(shipFluxTracker.currFlux - shipFluxTracker.hardFlux) / shipFluxTracker.maxFlux}, shipHardFluxPercentageLevel: ${shipFluxTracker.hardFlux / shipFluxTracker.maxFlux}, ship hard flux: ${shipFluxTracker.hardFlux}, ship current flux: ${shipFluxTracker.currFlux}, ship max flux: ${shipFluxTracker.maxFlux}")
                        droneFluxTracker.increaseFlux(shipToDroneSoftFluxAmount, false)
                        droneFluxTracker.increaseFlux(shipToDroneHardFluxAmount, true)
                        logger.info("Applied flux to drone! hard flux: ${shipToDroneHardFluxAmount}, soft flux: ${shipToDroneSoftFluxAmount}")
                        logger.info("droneToShipSoftFluxDiff: ${(droneFluxTracker.currFlux - droneFluxTracker.hardFlux) / droneFluxTracker.maxFlux}, droneToShipSoftFluxAmount: ${droneFluxTracker.hardFlux / droneFluxTracker.maxFlux}, droneToShipHardFluxDiff: ${droneToShipHardFluxDiff}, droneToShipHardFluxAmount: ${droneToShipHardFluxAmount}")
                    } else {
                        if(transfer.not()) {
                            logger.info("One-way flux exchange")
                            shipFluxTracker.increaseFlux(droneToShipSoftFluxAmount, false)
                            shipFluxTracker.increaseFlux(droneToShipHardFluxAmount, true)
                            logger.info("Applied flux to ship! hard flux: ${droneToShipHardFluxAmount}, soft flux: ${droneToShipSoftFluxAmount}")
                        } else {
                            logger.info("One-way flux transfer")
                            val droneSoftFluxAmount = (droneFluxTracker.currFlux - droneFluxTracker.hardFlux)//droneSoftFluxPercentageLevel * shipFluxTracker.maxFlux
                            val droneHardFluxAmount = droneFluxTracker.hardFlux//droneHardFluxPercentageLevel * shipFluxTracker.maxFlux
                            shipFluxTracker.increaseFlux(droneSoftFluxAmount, false)
                            shipFluxTracker.increaseFlux(droneHardFluxAmount, true)
                            logger.info("Applied flux to ship! hard flux: ${droneHardFluxAmount}, soft flux: ${droneSoftFluxAmount}")
                            droneFluxTracker.hardFlux = 0f
                            droneFluxTracker.currFlux = 0f
                            logger.info("Cleared drone flux! drone stats: hard flux: ${droneFluxTracker.hardFlux}, softFlux: ${droneFluxTracker.currFlux - droneFluxTracker.hardFlux}, currFlux: ${droneFluxTracker.currFlux}")
                        }
                    }

                    // share overload - it's done this way so that applying overload to one
                    // doesn't immediately reflect back to the other
                    val droneOverloaded = droneFluxTracker.isOverloaded
                    val droneOverloadTime = droneFluxTracker.overloadTimeRemaining
                    val shipOverloaded = shipFluxTracker.isOverloaded
                    val shipOverloadTime = shipFluxTracker.overloadTimeRemaining
                    logger.info("droneOverloaded: ${droneOverloaded}, droneOverloadTime: ${droneOverloadTime}, shipOverloaded: ${shipOverloaded}, shipOverloadTime: ${shipOverloadTime}")
                    if (droneOverloaded) {
                        // shield->ship overload transfer
                        shipFluxTracker.forceOverload(droneOverloadTime)
                        shipFluxTracker.setOverloadDuration(droneOverloadTime)
                        logger.info("Applied ${droneOverloadTime} of overload time from drone to ship!")
                    } else {
                        // actually, nothing here
                    }

                    if (shipOverloaded) {
                        // ship->shield overload transfer
                        droneFluxTracker.forceOverload(shipOverloadTime)
                        droneFluxTracker.setOverloadDuration(shipOverloadTime)
                        logger.info("Applied ${shipOverloadTime} of overload time from ship to drone!")
                    } else {
                        // actually, nothing here
                    }
                }
            }
        }
    }

    object ForceApplier {
        fun applyMomentum(entity: CombatEntityAPI?, pointOfImpact: Vector2f?, direction: Vector2f, momentum: Float, elasticCollision: Boolean) {
            // This whole thing is weird, but necessary since arguments are being reassigned for some reason
            var entity = entity
            var direction = direction
            var momentum = momentum

            if (entity == null) {
                return
            } else {
                // Filter out forces without a direction
                if (direction.lengthSquared() == 0f) {
                    return
                }
                // Avoid divide-by-zero errors...
                var mass = max(1.0, entity.mass.toDouble()).toFloat()
                // We should not move stations, right?
                if (entity is ShipAPI) {
                    val ship = entity
                    if (ship.isStation || (ship.isStationModule && ship.parentStation.isStation)) {
                        return
                    }
                    if (ship.isStationModule && ship.isShipWithModules) {
                        entity = ship.parentStation
                        mass = max(1.0, ship.massWithModules.toDouble()).toFloat()
                    }
                }
                // Momentum is far too weak otherwise
                momentum *= 100f
                // Doing some vector calculate
                val BPtoMC = entity?.let { Vector2f.sub(it.location, pointOfImpact, null) }
                        ?: throw RuntimeException("entity was null while assigning to BPtoMC -- this should not be happening. Look into GuardianShield -> ForceApplier::applyMomentum()")
                val forceV = Vector2f()
                direction.normalise(forceV)
                forceV.scale(momentum)
                // get force vector
                BPtoMC.normalise(BPtoMC)
                // calculate acceleration
                BPtoMC.scale(Vector2f.dot(forceV, BPtoMC) / mass)
                if (elasticCollision) {
                    // Apply velocity change
                    Vector2f.add(BPtoMC, entity.velocity, entity.velocity)
                } else {
                    // Apply velocity change
                    direction = Vector2f(forceV)
                    direction.scale(1 / mass)
                    Vector2f.add(direction, entity.velocity, entity.velocity)
                }
                // calculate moment change
                var angularAcc = VectorUtils.getCrossProduct(forceV, BPtoMC) / (0.5f * mass * entity.collisionRadius * entity.collisionRadius)
                angularAcc = Math.toDegrees(angularAcc.toDouble()).toFloat()
                // Apply angular velocity change
                if (elasticCollision) {
                    entity.angularVelocity = entity.angularVelocity + angularAcc
                } else {
                    entity.angularVelocity = entity.angularVelocity - angularAcc
                }
            }
        }
    }


    companion object {
        private const val ITEM = "et_ammospool"

        private const val INVULNERABLE_SHIELD_DRONE = "invulnerable_shield_drone"
        private const val BUFF_ID = "guardian_shield_buff"
        private const val SHIELD_DAMAGE_TAKEN_MULTIPLIER = 2f
    }
}