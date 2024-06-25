package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.loading.WeaponGroupSpec
import com.fs.starfarer.api.loading.WeaponGroupType
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
                .format("flux_method", lazyFluxMethodString)
                .format("flux_effects", lazyFluxEffectString)
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

        private fun getDroneHullId(): String = "exotica_guardianshield"

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

            // If host ship or shield drone are overheated - don't turn on
            if (ship.fluxTracker.isOverloadedOrVenting) {
                return false
            }
            drone?.let {
                if (it.fluxTracker.isOverloadedOrVenting) {
                    return false
                }
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
                shieldController.transferFlux(amount, workingMode = FluxTransferMode.ONE_WAY, transfer = USE_TRANSFER)
            }
        }

        private fun systemOn() {
            // Enable turning the shield on *only* if neither the drone nor the host ship are overloaded or venting
            //
            // Had to be added because the shield was "flashing" / "flickering" (tried turning on before cancelling)
            // while the host ship was being overloaded
            drone?.let {
                if (it.fluxTracker.isOverloadedOrVenting.not() && ship.fluxTracker.isOverloadedOrVenting.not()) {
                    it.shield.toggleOn()
                    log("systemOn()\tshield on ? ${it.shield.isOn}\t\tship's flux dissipation: ${ship.mutableStats.fluxDissipation.modifiedValue}", "$LOGTAG:GuardianShieldDrone")
                } else {
                    log("systemOn()\taborted due to overload/venting", "$LOGTAG:GuardianShieldDrone")
                }
                if (APPLY_FLUX_DEBUFF) {
                    ship.mutableStats.fluxDissipation.modifyMult(GUARDIAN_SHIELD_FLUX_DISSIPATION_DEBUFF_ID, FLUX_DEBUFF_AMOUNT)
                }

                if (APPLY_HARDFLUX_DEBUFF) {
                    ship.mutableStats.hardFluxDissipationFraction.modifyMult(GUARDIAN_SHIELD_HARDFLUX_DISSIPATION_DEBUFF_ID, HARDFLUX_DEBUFF_AMOUNT)
                }
            }
        }

        private fun systemOff() {
            drone?.let {
                it.shield.toggleOff()
                log("systemOff()\tshield off ? ${it.shield.isOff}\t\tship's flux dissipation: ${ship.mutableStats.fluxDissipation.modifiedValue}", "$LOGTAG:GuardianShieldDrone")
                if (APPLY_FLUX_DEBUFF) {
                    ship.mutableStats.fluxDissipation.unmodify(GUARDIAN_SHIELD_FLUX_DISSIPATION_DEBUFF_ID)
                }

                if (APPLY_HARDFLUX_DEBUFF) {
                    ship.mutableStats.hardFluxDissipationFraction.unmodify(GUARDIAN_SHIELD_HARDFLUX_DISSIPATION_DEBUFF_ID)
                }
            }
        }

        override fun spawnDrone(): ShipAPI {

            val drone: ShipAPI = if(SPAWN_SHIELD_DRONE_USING_FXDRONE) {
                val spec = Global.getSettings().getHullSpec(getDroneHullId())
                val v = Global.getSettings().createEmptyVariant(getDroneVariant(), spec)
                val fxDrone: ShipAPI = Global.getCombatEngine().createFXDrone(v)
                fxDrone.layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
                fxDrone.owner = ship.originalOwner
                fxDrone.mutableStats.hullDamageTakenMult.modifyMult(INVULNERABLE_SHIELD_DRONE, 0f) // so it's non-targetable
                fxDrone.isDrone = true
                fxDrone.aiFlags.setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f, ship)
                fxDrone.collisionClass = CollisionClass.FIGHTER
                fxDrone.shipAI = null
                fxDrone.isForceHideFFOverlay = true
                fxDrone.setRenderBounds(false)
                fxDrone.giveCommand(ShipCommand.SELECT_GROUP, null, 0)
                Global.getCombatEngine().addEntity(fxDrone)

                fxDrone
            } else {
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

                fighter
            }

            log("<-- spawnDrone()\tdrone shield: ${drone.shield}, shield arc: ${drone.shield.arc}, shield type: ${drone.shield.type}, shield active ? ${drone.shield.isOn}", "$LOGTAG:GuardianShieldDrone")
            return drone
        }

        inner class ShieldController {

            fun assesSituation(amount: Float): Boolean {
                tracker.advance(amount)
                if (tracker.intervalElapsed()) {
                    // If host ship or shield drone are overheated - turn off shields
                    if (ship.fluxTracker.isOverloadedOrVenting) {
                        systemOff()
                    }
                    drone?.let {
                        if (it.fluxTracker.isOverloadedOrVenting) {
                            systemOff()
                        }
                    }

                    // Same for phasing
                    if (ship.isPhased) {
                        systemOff()
                    }

                    // Check for ships in danger - the host ship, or the shield drone
                    val shipInDanger = ship.hullLevel < 0.15f

                    drone?.let {
                        val shieldInDanger = it.hullLevel < 1f
                        if (it.fluxTracker.fluxLevel > 0.96f && !shipInDanger && !shieldInDanger) {
                            log("calling systemOff()\t\tif (it.fluxTracker.fluxLevel > 0.96f && !shipInDanger && !shieldInDanger)", "$LOGTAG:ShieldController")
                            systemOff()
                            return false
                        }
                    }

                    // Check for allies in radius of 1500 and whether they need help or have incoming damage
                    val allies = AIUtils.getNearbyAllies(ship, SHIELD_RADIUS.toFloat())
                    // add shield to allies if it's non-null
                    if (drone != null) {
                        allies.add(drone)
                    }
                    // check if anybody needs help
                    for (ally in allies) {
                        if (ally.aiFlags != null
                                && (ally.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)
                                        || ally.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP))) {
                            log("calling systemOn()\t\tallies need help condition", "$LOGTAG:ShieldController")
                            systemOn()
                            return true
                        }
                    }

                    // Check for nearby enemies within 2000, ignoring those that need help.
                    // Shield remains up if there are some.
                    val enemies = AIUtils.getNearbyEnemies(ship, 2000f)
                    for (enemy in enemies) {
                        if ((enemy.aiFlags != null && enemy.aiFlags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP))
                                || enemy.isRetreating) {
                            continue
                        }
                        log("calling systemOn()\t\tnearby enemies condition", "$LOGTAG:ShieldController")
                        systemOn()
                        return true
                    }

                    // Check for projectiles or and raise shields if there are some incoming
                    val projectiles: List<DamagingProjectileAPI> = engine.projectiles
                    for (proj in projectiles) {
                        if (proj.owner != ship.owner
                                && (MathUtils.getDistance(ship, proj) > SHIELD_RADIUS || nullSafeGetDistance(drone, proj) > SHIELD_RADIUS)) {
                            log("calling systemOn()\t\tprojectiles near ship or shield condition", "$LOGTAG:ShieldController")
                            systemOn()
                            return true
                        }
                    }
                    val beams: List<BeamAPI> = engine.beams
                    for (beam in beams) {
                        if (beam.source.owner != ship.owner
                                && (MathUtils.isWithinRange(ship, beam.to, SHIELD_RADIUS.toFloat())
                                        || nullSafeIsWithinRange(drone, beam.to, SHIELD_RADIUS.toFloat()))) {
                            log("calling systemOn()\t\tbeam within range of ship or shield condition", "$LOGTAG:ShieldController")
                            systemOn()
                            return true
                        }
                    }
                    log("calling systemOff()\t\tno other conditions were met", "$LOGTAG:ShieldController")
                    systemOff()
                    return false
                }

                // what do we do here? lets fallback to returning false, but don't touch the shield
                log("just returning false, not touching shield", "$LOGTAG:ShieldController")
                return false
            }

            private fun nullSafeGetDistance(ship: ShipAPI?, projectile: DamagingProjectileAPI) : Float {
                return ship?.let {
                    MathUtils.getDistance(it, projectile)
                } ?: Float.MIN_VALUE
            }

            private fun nullSafeIsWithinRange(ship: ShipAPI?, vector: Vector2f, range: Float) : Boolean {
                return ship?.let {
                    MathUtils.isWithinRange(it, vector, range)
                } ?: false
            }

            fun controlShield(amount: Float) {
                drone?.let {
                    if (it.isHulk || it.shield.isOff) {
                        it.collisionClass = CollisionClass.FIGHTER
                        it.collisionRadius = 400f
                    } else if (it.shield.isOn) {
                        it.shield.activeArc = 360f
                        var radius = it.collisionRadius
                        if (radius < SHIELD_RADIUS) {
                            radius += 300 * amount
                        } else {
                            radius = SHIELD_RADIUS.toFloat()
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
                    val momentumFactor: Float = when (ship.hullSize) {
                        null -> 0f
                        ShipAPI.HullSize.DEFAULT -> 1f
                        ShipAPI.HullSize.FIGHTER -> 1f
                        ShipAPI.HullSize.FRIGATE -> 2f
                        ShipAPI.HullSize.DESTROYER -> 2.5f
                        ShipAPI.HullSize.CRUISER -> 3.5f
                        ShipAPI.HullSize.CAPITAL_SHIP -> 5f
                    }

                    for (ship in AIUtils.getNearbyEnemies(it, it.collisionRadius)) {
                        if (MathUtils.getDistance(ship.location, it.location) < ship.collisionRadius + it.getCollisionRadius()) { //hmm, I needs to override the no negative result thing here.
                            val pointToTest = VectorUtils.clampLength(Vector2f.sub(it.getLocation(), ship.location, null), it.getShieldRadiusEvenIfNoShield())
                            val collisionPoint: Vector2f? = CollisionUtil.getShipCollisionPoint(it.getLocation(), pointToTest, ship)
                            collisionPoint?.let { collision ->
                                if (!ship.isStation && !(ship.isStationModule && ship.parentStation.isStation)) {
                                    ship.velocity.set(it.getVelocity())
                                    val momentum = amount * 10f * momentumFactor
                                    ForceApplier.applyMomentum(ship, collision, Vector2f.sub(it.getLocation(), ship.location, null), momentum, true)
                                } else {
                                    val momentum = amount * -0.5f * 1 / momentumFactor
                                    ForceApplier.applyMomentum(it.getParentStation(), collision, Vector2f.sub(ship.location, it.getLocation(), null), momentum, true)
                                }
                            }
                        }
                    }
                }
            }

            fun transferFlux(amount: Float, workingMode: FluxTransferMode = FluxTransferMode.ONE_WAY, transfer: Boolean = false) {
                /**
                 * I don't know where i'm going with this two-way heat exchange
                 * the whole concept of working with percentages is kinda broken at it's core:
                 *  - the shield drone has 175k flux capacity
                 *  - the host ship has whatever, lets say 25k or 8k.
                 *  - 10% of shield drone's capacity (17.5k) will transform to 2.5k or 800 flux. 15k flux will have been lost.
                 *
                 *  on the other hand, if the ship has amassed 90% flux:
                 *  - the 90% of let's say 25k is 22.5 will transform to 153k flux. 130.5k flux will have been magically created
                 *
                 *  I think the two-way exchange does kinda work, but exchange is the wrong word for it.
                 */
                drone?.let {
                    log("--> transferFlux(amount=$amount, workingMode=$workingMode, transfer=$transfer)", "$LOGTAG:FluxTransfer")
                    // should be between 0-100%
                    val droneFluxTracker = it.fluxTracker
                    val droneSoftFluxPercentageLevel = (droneFluxTracker.currFlux - droneFluxTracker.hardFlux) / droneFluxTracker.maxFlux
                    val droneHardFluxPercentageLevel = droneFluxTracker.hardFlux / droneFluxTracker.maxFlux

                    val shipFluxTracker = ship.fluxTracker
                    val shipSoftFluxPercentageLevel = (shipFluxTracker.currFlux - shipFluxTracker.hardFlux) / shipFluxTracker.maxFlux
                    val shipHardFluxPercentageLevel = shipFluxTracker.hardFlux / shipFluxTracker.maxFlux
                    // Check the host-ship's flux levels.
                    // If they're different from the shield-host's levels, apply the sharing code to both of them.
                    log("droneSoftFluxPercentageLevel: ${droneSoftFluxPercentageLevel}, droneHardFluxPercentageLevel: ${droneHardFluxPercentageLevel}, drone hard flux: ${droneFluxTracker.hardFlux}, drone current flux: ${droneFluxTracker.currFlux}, drone max flux: ${droneFluxTracker.maxFlux}", "$LOGTAG:FluxTransfer")
                    log("shipSoftFluxPercentageLevel: ${shipSoftFluxPercentageLevel}, shipHardFluxPercentageLevel: ${shipHardFluxPercentageLevel}, ship hard flux: ${shipFluxTracker.hardFlux}, ship current flux: ${shipFluxTracker.currFlux}, ship max flux: ${shipFluxTracker.maxFlux}", "$LOGTAG:FluxTransfer")

                    // Apply flux to the ship
                    // keep the difference clamped between 0-1.00% by doing max(diff,0) to keep it >=0, and min(that,1.00) to keep it <=1.00
                    val droneToShipSoftFluxDiff = min(max(droneSoftFluxPercentageLevel - shipSoftFluxPercentageLevel, 0f), 1.00f)
                    val droneToShipHardFluxDiff = min(max(droneHardFluxPercentageLevel - shipHardFluxPercentageLevel, 0f), 1.00f)

                    val droneToShipSoftFluxAmount = (droneToShipSoftFluxDiff * shipFluxTracker.maxFlux) / 1.00f
                    val droneToShipHardFluxAmount = (droneToShipHardFluxDiff * shipFluxTracker.maxFlux) / 1.00f
                    log("droneToShipSoftFluxDiff: ${droneToShipSoftFluxDiff}, droneToShipSoftFluxAmount: ${droneToShipSoftFluxAmount}, droneToShipHardFluxDiff: ${droneToShipHardFluxDiff}, droneToShipHardFluxAmount: ${droneToShipHardFluxAmount}", "$LOGTAG:FluxTransfer")
                    // If doing two-way, we need to calculate these things for the other direction too before we increase them
                    when (workingMode) {
                        FluxTransferMode.TWO_WAY -> {
                            log("Two-way flux exchange", "$LOGTAG:FluxTransfer")
                            val shipToDroneSoftFluxDiff = min(max(shipSoftFluxPercentageLevel - droneSoftFluxPercentageLevel, 0f), 1.00f)
                            val shipToDroneHardFluxDiff = min(max(shipHardFluxPercentageLevel - droneHardFluxPercentageLevel, 0f), 1.00f)

                            val shipToDroneSoftFluxAmount = (shipToDroneSoftFluxDiff * droneFluxTracker.maxFlux) / 1.00f
                            val shipToDroneHardFluxAmount = (shipToDroneHardFluxDiff * droneFluxTracker.maxFlux) / 1.00f
                            log("shipToDroneSoftFluxDiff: ${shipToDroneSoftFluxDiff}, shipToDroneSoftFluxAmount: ${shipToDroneSoftFluxAmount}, shipToDroneHardFluxDiff: ${shipToDroneHardFluxDiff}, shipToDroneHardFluxAmount: ${shipToDroneHardFluxAmount}", "$LOGTAG:FluxTransfer")

                            // apply increases
                            shipFluxTracker.increaseFlux(droneToShipSoftFluxAmount, false)
                            shipFluxTracker.increaseFlux(droneToShipHardFluxAmount, true)
                            log("Applied flux to ship! hard flux: ${droneToShipHardFluxAmount}, soft flux: ${droneToShipSoftFluxAmount}", "$LOGTAG:FluxTransfer")
                            log("shipSoftFluxPercentageLevel: ${(shipFluxTracker.currFlux - shipFluxTracker.hardFlux) / shipFluxTracker.maxFlux}, shipHardFluxPercentageLevel: ${shipFluxTracker.hardFlux / shipFluxTracker.maxFlux}, ship hard flux: ${shipFluxTracker.hardFlux}, ship current flux: ${shipFluxTracker.currFlux}, ship max flux: ${shipFluxTracker.maxFlux}", "$LOGTAG:FluxTransfer")
                            log("[AFTER] ship stats: hard flux: ${shipFluxTracker.hardFlux}, softFlux: ${shipFluxTracker.currFlux - shipFluxTracker.hardFlux}, currFlux: ${shipFluxTracker.currFlux}", "$LOGTAG:FluxTransfer")
                            droneFluxTracker.increaseFlux(shipToDroneSoftFluxAmount, false)
                            droneFluxTracker.increaseFlux(shipToDroneHardFluxAmount, true)
                            log("Applied flux to drone! hard flux: ${shipToDroneHardFluxAmount}, soft flux: ${shipToDroneSoftFluxAmount}", "$LOGTAG:FluxTransfer")
                            log("droneToShipSoftFluxDiff: ${(droneFluxTracker.currFlux - droneFluxTracker.hardFlux) / droneFluxTracker.maxFlux}, droneToShipSoftFluxAmount: ${droneFluxTracker.hardFlux / droneFluxTracker.maxFlux}, droneToShipHardFluxDiff: ${droneToShipHardFluxDiff}, droneToShipHardFluxAmount: ${droneToShipHardFluxAmount}", "$LOGTAG:FluxTransfer")
                            log("[AFTER] drone stats: hard flux: ${droneFluxTracker.hardFlux}, softFlux: ${droneFluxTracker.currFlux - droneFluxTracker.hardFlux}, currFlux: ${droneFluxTracker.currFlux}", "$LOGTAG:FluxTransfer")
                        }

                        FluxTransferMode.ONE_WAY -> {
                            if (transfer.not()) {
                                log("One-way flux exchange", "$LOGTAG:FluxTransfer")
                                shipFluxTracker.increaseFlux(droneToShipSoftFluxAmount, false)
                                shipFluxTracker.increaseFlux(droneToShipHardFluxAmount, true)
                                log("Applied flux to ship! hard flux: ${droneToShipHardFluxAmount}, soft flux: ${droneToShipSoftFluxAmount}", "$LOGTAG:FluxTransfer")
                                log("[AFTER] drone stats: hard flux: ${droneFluxTracker.hardFlux}, softFlux: ${droneFluxTracker.currFlux - droneFluxTracker.hardFlux}, currFlux: ${droneFluxTracker.currFlux}", "$LOGTAG:FluxTransfer")
                                log("[AFTER] ship stats: hard flux: ${shipFluxTracker.hardFlux}, softFlux: ${shipFluxTracker.currFlux - shipFluxTracker.hardFlux}, currFlux: ${shipFluxTracker.currFlux}", "$LOGTAG:FluxTransfer")
                            } else {
                                log("One-way flux transfer", "$LOGTAG:FluxTransfer")
                                val droneSoftFluxAmount = (droneFluxTracker.currFlux - droneFluxTracker.hardFlux)
                                val droneHardFluxAmount = droneFluxTracker.hardFlux
                                shipFluxTracker.increaseFlux(droneSoftFluxAmount, false)
                                shipFluxTracker.increaseFlux(droneHardFluxAmount, true)
                                log("Applied flux to ship! hard flux: ${droneHardFluxAmount}, soft flux: ${droneSoftFluxAmount}", "$LOGTAG:FluxTransfer")
                                droneFluxTracker.hardFlux = 0f
                                droneFluxTracker.currFlux = 0f
                                log("Cleared drone flux!", "$LOGTAG:FluxTransfer")
                                log("[AFTER] drone stats: hard flux: ${droneFluxTracker.hardFlux}, softFlux: ${droneFluxTracker.currFlux - droneFluxTracker.hardFlux}, currFlux: ${droneFluxTracker.currFlux}", "$LOGTAG:FluxTransfer")
                                log("[AFTER] ship stats: hard flux: ${shipFluxTracker.hardFlux}, softFlux: ${shipFluxTracker.currFlux - shipFluxTracker.hardFlux}, currFlux: ${shipFluxTracker.currFlux}", "$LOGTAG:FluxTransfer")
                            }
                        }

                        FluxTransferMode.NEITHER -> {
                            // Do nothing, leave the shield drone's (shield) flux economy decoupled from host ship's (shooting) flux economy
                            // It will deal damage to the host ship when the drone overloads
                        }
                    }

                    // share overload - it's done this way so that applying overload to one
                    // doesn't immediately reflect back to the other
                    val droneOverloaded = droneFluxTracker.isOverloaded
                    val droneOverloadTime = droneFluxTracker.overloadTimeRemaining
                    val shipOverloaded = shipFluxTracker.isOverloaded
                    val shipOverloadTime = shipFluxTracker.overloadTimeRemaining
                    log("droneOverloaded: ${droneOverloaded}, droneOverloadTime: ${droneOverloadTime}, shipOverloaded: ${shipOverloaded}, shipOverloadTime: ${shipOverloadTime}", "$LOGTAG:FluxTransfer")
                    if (droneOverloaded && !shipOverloaded) {
                        // shield->ship overload transfer
                        shipFluxTracker.forceOverload(droneOverloadTime)
                        // We don't want to apply drone's overload time, since drone will have
                        // the shortest overload time due to it's Fighter profile
//                        shipFluxTracker.setOverloadDuration(droneOverloadTime)
                        log("Applied ${droneOverloadTime} of overload time from drone to ship!", "$LOGTAG:FluxTransfer")
                    } else {
                        // actually, nothing here
                    }

                    if (shipOverloaded && !droneOverloaded) {
                        // ship->shield overload transfer
                        droneFluxTracker.forceOverload(shipOverloadTime)
                        droneFluxTracker.setOverloadDuration(shipOverloadTime)
                        log("Applied ${shipOverloadTime} of overload time from ship to drone!")
                    } else {
                        // actually, nothing here
                    }

                    log("<-- transferFlux()", "$LOGTAG:FluxTransfer")
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

    private fun log(text: String, logtag: String = LOGTAG) {
        if (LOGS_ENABLED) {
            logger.info("[${logtag}]: ${text}")
        }
    }

    /**
     * Descriptor for how the flux economy coupling should work between the shield drone and it's host ship
     *
     * @see [FluxTransferMode.ONE_WAY]
     * @see [FluxTransferMode.TWO_WAY]
     * @see [FluxTransferMode.NEITHER]
     */
    enum class FluxTransferMode {
        /**
         * Whether the ship and the drone will use percentage-based system to influence each other's flux amount.
         *
         * 10% of shield drone's flux capacity will transform to 10% of host ship's flux capacity and vice-versa
         * <code>
         *
         *                     val droneFluxTracker = drone.fluxTracker
         *                     val droneSoftFluxPercentageLevel = (droneFluxTracker.currFlux - droneFluxTracker.hardFlux) / droneFluxTracker.maxFlux
         *                     val droneHardFluxPercentageLevel = droneFluxTracker.hardFlux / droneFluxTracker.maxFlux
         *
         *                     val shipFluxTracker = ship.fluxTracker
         *                     val shipSoftFluxPercentageLevel = (shipFluxTracker.currFlux - shipFluxTracker.hardFlux) / shipFluxTracker.maxFlux
         *                     val shipHardFluxPercentageLevel = shipFluxTracker.hardFlux / shipFluxTracker.maxFlux
         *                     // Check the host-ship's flux levels.
         *                     // If they're different from the shield-host's levels, apply the sharing code to both of them.
         *
         *                     // Apply flux to the ship
         *                     // keep the difference clamped between 0-1.00% by doing max(diff,0) to keep it >=0, and min(that,1.00) to keep it <=1.00
         *                     val droneToShipSoftFluxDiff = min(max(droneSoftFluxPercentageLevel - shipSoftFluxPercentageLevel, 0f), 1.00f)
         *                     val droneToShipHardFluxDiff = min(max(droneHardFluxPercentageLevel - shipHardFluxPercentageLevel, 0f), 1.00f)
         *
         *                     val droneToShipSoftFluxAmount = (droneToShipSoftFluxDiff * shipFluxTracker.maxFlux) / 1.00f
         *                     val droneToShipHardFluxAmount = (droneToShipHardFluxDiff * shipFluxTracker.maxFlux) / 1.00f
         *
         *                     val shipToDroneSoftFluxDiff = min(max(shipSoftFluxPercentageLevel - droneSoftFluxPercentageLevel, 0f), 1.00f)
         *                     val shipToDroneHardFluxDiff = min(max(shipHardFluxPercentageLevel - droneHardFluxPercentageLevel, 0f), 1.00f)
         *
         *                     val shipToDroneSoftFluxAmount = (shipToDroneSoftFluxDiff * droneFluxTracker.maxFlux) / 1.00f
         *                     val shipToDroneHardFluxAmount = (shipToDroneHardFluxDiff * droneFluxTracker.maxFlux) / 1.00f
         *
         *                     // apply increases
         *                     shipFluxTracker.increaseFlux(droneToShipSoftFluxAmount, false)
         *                     shipFluxTracker.increaseFlux(droneToShipHardFluxAmount, true)
         *
         *                     droneFluxTracker.increaseFlux(shipToDroneSoftFluxAmount, false)
         *                     droneFluxTracker.increaseFlux(shipToDroneHardFluxAmount, true)
         * </code>
         */
        TWO_WAY,

        /**
         * Whether the drone will use percentage-based system to influence the host ship's flux amount.
         * 10% of shield drone's flux capacity will transform to 10% of host ship's flux capacity
         *
         * See [TWO_WAY] since the same (drone->ship direction only) mechanism mentioned there is used unless [USE_TRANSFER] is toggled
         *
         * @see [TWO_WAY]
         * @see [USE_TRANSFER]
         */
        ONE_WAY,

        /**
         * Neither, leaving the shield drone's flux economy completely decoupled from host ship's flux economy.
         */
        NEITHER
    }

    companion object {
        private const val ITEM = "et_shieldcrystal"

        private const val INVULNERABLE_SHIELD_DRONE = "invulnerable_shield_drone"
        private const val GUARDIAN_SHIELD_FLUX_DISSIPATION_DEBUFF_ID = "guardian_shield_debuff_flux-dissipation"
        private const val GUARDIAN_SHIELD_HARDFLUX_DISSIPATION_DEBUFF_ID = "guardian_shield_debuff_hardflux-dissipation"

        private const val SHIELD_RADIUS = 1500
        private const val APPLY_FLUX_DEBUFF = true
        private const val APPLY_HARDFLUX_DEBUFF = true
        private const val FLUX_DEBUFF_AMOUNT = 0.75f
        private const val HARDFLUX_DEBUFF_AMOUNT = 0.25f

        /**
         * Should the shield-hosting drone be an FXDrone or regular Drone
         */
        private const val SPAWN_SHIELD_DRONE_USING_FXDRONE = true

        /**
         * Which mode should the flux transfer work in
         *
         * @see [FluxTransferMode]
         */
        private val WORKING_MODE: FluxTransferMode = FluxTransferMode.ONE_WAY

        /**
         * Whether the flux of the 'donor' will be zeroed after the flux has been increased on the receiver.
         *
         * Changes how [FluxTransferMode.ONE_WAY] works to using full amounts instead of percentage-based scaling.
         *
         * Used only when [WORKING_MODE] is [FluxTransferMode.ONE_WAY]
         */
        private const val USE_TRANSFER = true

        // These three need to end with space because the string looks like "... , =${flux_effects}=and ..."
        private val CASE_BOTH = "but =reduces flux dissipation to ${Math.round(FLUX_DEBUFF_AMOUNT * 100)}%% and hard flux dissipation to ${Math.round(HARDFLUX_DEBUFF_AMOUNT * 100)}%%= "
        private val CASE_FLUX = "but =reduces flux dissipation to ${Math.round(FLUX_DEBUFF_AMOUNT * 100)}%%= "
        private val CASE_HARDFLUX = "but =reduces hard flux dissipation to ${Math.round(HARDFLUX_DEBUFF_AMOUNT * 100)}%%= "
        private val lazyFluxEffectString: String by lazy {
            "${
                if (APPLY_FLUX_DEBUFF && APPLY_HARDFLUX_DEBUFF) {
                    "${CASE_BOTH}"
                } else if (APPLY_FLUX_DEBUFF && !APPLY_HARDFLUX_DEBUFF) {
                    "${CASE_FLUX}"
                } else if (!APPLY_FLUX_DEBUFF && APPLY_HARDFLUX_DEBUFF) {
                    "${CASE_HARDFLUX}"
                } else {
                    "${""}"
                }
            }"
        }

        private const val CASE_SHARE = "=shares it's flux status= with the host ship, *sharing overload status*"
        private const val CASE_TRANSFER = "=transfers it's flux status= to the host ship, *sharing overload status*"
        private const val CASE_ONEWAY = "=applies a percentage-based portion of it's flux status= to the host ship, *sharing overload status*"
        private const val CASE_NEITHER = "=applies damage to the host ship when the shield overloads=, *sharing overload status*"
        private val lazyFluxMethodString: String by lazy {
            "${
                if (WORKING_MODE == FluxTransferMode.TWO_WAY) {
                    "${CASE_SHARE}"
                } else if (USE_TRANSFER) {
                    "${CASE_TRANSFER}"
                } else if (WORKING_MODE == FluxTransferMode.ONE_WAY) {
                    // not two-way and not transfer, so it's just oneway
                    "${CASE_ONEWAY}"
                } else {
                    // must be neither
                    "${CASE_NEITHER}"
                }
            }"
        }

        private const val LOGTAG = "GuardianShield"
        private const val LOGS_ENABLED = false
    }
}