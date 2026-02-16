package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.IntervalUtil
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.modifications.exotics.misc.ForceApplier
import exoticatechnologies.util.*
import exoticatechnologies.util.drawutils.CircleUtils
import exoticatechnologies.util.drawutils.ContinuousDrainMode
import exoticatechnologies.util.drawutils.ParticleDrawMode
import org.apache.log4j.Logger
import org.json.JSONObject
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color

class ShipAttractorSystem(key: String, settings: JSONObject) : Exotic(key, settings) {
    private val logger: Logger = Logger.getLogger(ShipAttractorSystem::class.java)
    private lateinit var originalShip: ShipAPI
    private lateinit var subsystem: AttractorPullInSystem


    override var color: Color = Color(0xFFFFFF7F.toInt(), true)

    override fun getBasePrice(): Int = COST_CREDITS.toInt()

    override fun canAfford(fleet: CampaignFleetAPI, market: MarketAPI?): Boolean {
        return Utilities.hasItem(fleet.cargo, ITEM)
    }

    override fun removeItemsFromFleet(fleet: CampaignFleetAPI, member: FleetMemberAPI, market: MarketAPI?): Boolean {
        Utilities.takeItemQuantity(fleet.cargo, ITEM, 1f)
        return true
    }

    override fun modifyToolTip(tooltip: TooltipMakerAPI, title: UIComponentAPI, member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData, expand: Boolean) {
        if (expand) {
            StringUtils.getTranslation(key, "longDescription")
                .format("radius", getRadiusAmount(member, mods, exoticData))
                .format("pull_in_strength", formatFloatAsString(getScaledPullInEffectMomentumStrength(member, mods, exoticData), 2))
                .formatFloat("cooldown_time", getScaledCooldownDuration(member, mods, exoticData))
                .addToTooltip(tooltip, title)
        }
    }

    override fun applyToShip(id: String, member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        subsystem = AttractorPullInSystem(ship, member, mods, exoticData)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)
    }

    private fun getPullInStrength(member: FleetMemberAPI): Float {
        return PULL_IN_STRENGTH * getAllModulesVariantList(member).count()
    }

    private fun getScaledPullInEffectMomentumStrength(
        member: FleetMemberAPI,
        mods: ShipModifications,
        exoticData: ExoticData
    ): Float {
        return getPullInEffectMomentumFactor(
            member = member,
        ) * getPullInStrength(
            member = member
        ) * getPositiveMult(
            member = member,
            mods = mods,
            exoticData = exoticData
        )
    }

    private fun getPullInEffectMomentumFactor(
        member: FleetMemberAPI,
    ): Float {
        return when (member.hullSpec.hullSize) {
            null -> 0f
            ShipAPI.HullSize.DEFAULT -> 1f
            ShipAPI.HullSize.FIGHTER -> 1f
            ShipAPI.HullSize.FRIGATE -> 2f
            ShipAPI.HullSize.DESTROYER -> 2.5f
            ShipAPI.HullSize.CRUISER -> 3.5f
            ShipAPI.HullSize.CAPITAL_SHIP -> 5f
        }.exhaustive
    }


    private fun getRadiusAmount(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        val baseRadiusBasedOnShipSize = when(member.hullSpec.hullSize) {
            null -> 0f
            ShipAPI.HullSize.FIGHTER -> 350f
            ShipAPI.HullSize.DEFAULT -> 500f
            ShipAPI.HullSize.FRIGATE -> 750f
            ShipAPI.HullSize.DESTROYER -> 1000f
            ShipAPI.HullSize.CRUISER -> 1500f
            ShipAPI.HullSize.CAPITAL_SHIP -> 2000f
        }.exhaustive
        return baseRadiusBasedOnShipSize * getPositiveMult(member, mods, exoticData)
    }

    private fun getScaledCooldownDuration(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return COOLDOWN_DURATION * getNegativeMult(member, mods, exoticData)
    }


    inner class AttractorPullInSystem(
        ship: ShipAPI,
        val member: FleetMemberAPI,
        val mods: ShipModifications,
        val exoticData: ExoticData
    ): MagicSubsystem(ship) {
        // check for activation every 3 seconds
        private val activationIntervalUtil = IntervalUtil(2.95f, 3.05f)
        private var visualSwirl: CircleUtils.Swirl? = null

        override fun getBaseActiveDuration() = 1f

        override fun getBaseCooldownDuration() = getScaledCooldownDuration(member, mods, exoticData)


        override fun shouldActivateAI(amount: Float): Boolean {
            activationIntervalUtil.advance(amount)
            return if (activationIntervalUtil.intervalElapsed()) {
                evaluateSituation()
            } else {
                false
            }
        }

        private fun evaluateSituation(): Boolean {
            // If interval elapsed, we are going to do a few checks to determine if we should activate:
            // 1. if we have enemies within system range but outside of weighted effective weapon range (excluding wings) - activate
            // 2. majority of ships are moving away (excluding wings) - activate
            // 3. if there are vulnerable ships present (excluding wings) - activate
            // 4. allies outnumber enemies nearby (excluding wings) - activate
            // If any criteria is met, we will do an early return and avoid evaluating the rest of them
            // Otherwise - do nothing for this evaluation cycle

            val shipsInRadius = getPotentialTargets(member, mods, exoticData)
                .filter { target -> target.isFighter.not() }

            // Calculate our 'most damaging' range and see how many targets are within range but outside most damaging range
            val largestDamageRange = getLargestDamageContributingRange(ship)
            val shipsWithinRangeOutsideOfBestRange = shipsInRadius.filter { targetShip ->
                val distanceToUs = targetShip.distanceToShip(ship)

                return@filter distanceToUs > largestDamageRange
            }

            // Criteria 1 - have ships within range but outside of our most-damaging range
            val haveShipsOutsideOfMostDamagingRange = shipsWithinRangeOutsideOfBestRange.isNotEmpty()
            if (haveShipsOutsideOfMostDamagingRange) return true

            // In case we did not return, lets start working on criteria 2 - majority of ships moving away
            val enemyShipCount = shipsInRadius.count()
            val enemyShipsRunningAway = shipsInRadius
                .map { enemyShip -> enemyShip.isMovingAwayFromShip(ship) }
                .filter { it }
                .count()

            // Criteria 2 - majority of ships running away
            if (enemyShipsRunningAway > enemyShipCount / 2) return true

            // In case we did not return, lets work on criteria 3 - vulnerable ships detected
            val vulnerableShipsInRange = shipsInRadius.filter { enemyShip -> isVulnerable(enemyShip) }

            // Criteria 3 - there are vulnerable ships present
            val anyVulnerableShipsInRange = vulnerableShipsInRange.isNotEmpty()
            if (anyVulnerableShipsInRange) return true

            // In case we did not return, try the last case - "more allies than enemies"
            val alliesInRange = CombatUtils.getShipsWithinRange(ship.location, getRadiusAmount(member, mods, exoticData))
                // make sure it only contains allies
                .filter { filterShip -> filterShip.owner == ship.owner}
                // make sure we're not targetting ourselves
                .filter { module -> module.fleetMember != member && module.parentStation != ship && module != ship }
                // make sure we're not targetting child modules
                .filter { module -> module.parentStation == null }
                // and make sure we're not counting our own fighters
                .filter { target -> target.isFighter.not() }

            // Criteria 4 - there are more allied ships than enemy ships
            val enemyShips = shipsInRadius.count()
            val allyShips = alliesInRange.count()
            if (allyShips >= enemyShips) return true


            // None of the criterias were fulfilled so far, return false for this evaluation cycle
            return false
        }

        private fun getLargestDamageContributingRange(ship: ShipAPI): Float {
            // Create a range to "weapon damage potential" map
            val rangeDamageMap = mutableMapOf<Float, Float>()
            // For all weapons on installing ship, "calculate" it's DPS and derive potential damage over 10 seconds
            for (weapon in getAllShipWeapons(ship)) {
                // If weapon is broken, skip it
                if (weapon.isDisabled || weapon.isPermanentlyDisabled) continue
                // "sustainedDps" might make sense but not really because it evaluates over "ship fires for infinite amount of time"
//                weapon.derivedStats.sustainedDps
                // Otherwise, grab it's DPS, and calculate the "damage contribution" over 10 seconds
                val weaponDps = weapon.derivedStats.dps
                val weapon10secPotential = weaponDps * 10f

                val weaponRange = weapon.range
                // Add to range in the map
                val currentRangeDamageValue = rangeDamageMap[weaponRange] ?: 0f
                // Update map
                rangeDamageMap[weaponRange] = currentRangeDamageValue + weapon10secPotential
            }

            // After all weapons were processed, grab the key with the highest value
            val largestEntry = rangeDamageMap.maxByOrNull { it.value }
            return if (largestEntry != null) {
                largestEntry.key
            } else {
                // no maximum was found, fallback to 0
                0f
            }
        }

        /**
         * Determines if a ship is vulnerable. Excluding wings and not calling this method for wings is on the caller.
         *
         * **NOTE:** dead ships (and wings) are **not** considered vulnerable
         *
         * A ship is considered vulnerable if:
         * - hullLevel is below 0.3
         * - fluxLevel is above 0.9
         * - is overloaded or venting
         * - engines are flamed out
         * - is retreating, direct retreating or has defense disabled
         *
         * @param ship the ship to evaluate
         * @return true if the ship is vulnerable, false otherwise
         */
        fun isVulnerable(ship: ShipAPI): Boolean {
            if (ship.isFighter || !ship.isAlive) return false

            return ship.hullLevel < 0.3f ||
                ship.fluxLevel > 0.9f ||
                ship.fluxTracker.isOverloaded ||
                ship.fluxTracker.isVenting ||
                ship.engineController.isFlamedOut ||
                ship.isRetreating || ship.isDirectRetreat || ship.isDefenseDisabled
        }


        override fun getDisplayText() = "Ship Attractor System"

        override fun onActivate() {
            log("--> onActivate()")
            super.onActivate()

            showVisualFlair()
            pullInShipsWithinRadius()
            log("<-- onActivate()")
        }

        private fun getPotentialTargets(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): List<ShipAPI> {
            val radius: Float = getRadiusAmount(member, mods, exoticData)

            val potentiallyAffectedShips = CombatUtils.getShipsWithinRange(ship.location, radius)
                // make sure it only contains enemies and not enemies and neutrals
                .filter { filterShip -> ship.owner != filterShip.owner && filterShip.owner != 100 }
                // And ... then apply some more filtering
                // make sure we're not targetting ourselves
                .filter { module -> module.fleetMember != member && module.parentStation != ship && module != ship }
                // make sure we're not targetting child modules
                .filter { module -> module.parentStation == null }

            return potentiallyAffectedShips
        }

        fun onSwirlFinished(swirlThatFinished: CircleUtils.Swirl) {
            visualSwirl = null
            // And reset the smooth particle limit back to original
            EngineParticlePainter.ParticleLimits.resetParticleLimitForParticleType(ParticleType.SMOOTH_PARTICLE)
        }

        fun getShipDependantParticleSize(ship: ShipAPI): Float {
            // Coerce shipSize / 8 into [MIN_PARTICLE_SIZE, MAX_PARTICLE_SIZE] //16-64f
            return (ship.collisionRadius/8).coerceIn(MIN_PARTICLE_SIZE, MAX_PARTICLE_SIZE)
        }

        override fun advance(amount: Float, isPaused: Boolean) {
            if (isPaused.not()) {
                // If not paused, draw particles on the swirl if we have it
                visualSwirl?.let { swirl ->
                    swirl.drawParticles(
                        amount = amount,
                        particleSize = getShipDependantParticleSize(ship = ship),
                        particlesToDrawPerInterval = 6,
                        particleColors = listOf(
                            Color.WHITE.brighter().brighter(),
                            Color.WHITE,
                            Color.LIGHT_GRAY.brighter().brighter(),
                            Color.LIGHT_GRAY,
                            Color.DARK_GRAY,
                            Color.DARK_GRAY.darker().darker()
                        ),
                        particleDrawMode = ParticleDrawMode.WHOLE_ARM,
                        particleArmsToDraw = 9,
                        continuousDrain = ContinuousDrainMode.ITERATION_BASED_MODE
                    )
                    // If all arms have finished, get rid of visualSwirl
                    if (swirl.hasFinished()) {
                        onSwirlFinished(swirl)
                    }
                }
            }
        }

        private fun showVisualFlair() {
            // Bump the limit temporarily
            EngineParticlePainter.ParticleLimits.setParticleLimitForParticleType(ParticleType.SMOOTH_PARTICLE, 4000)
            // generate dots
            val center = ship.location
            val fullRange = getRadiusAmount(member, mods, exoticData)
            // Lets draw the first ring at 1.25x collision radius so it's more visible, 1x is kinda "too close"
            // Lets generate the swirl
            visualSwirl = CircleUtils.generateSwirl(
                center = center,
                rings = 6,
                pointsPerRing = 72,
                minRadius = ship.collisionRadius * 1.25f,
                maxRadius = fullRange,
                generateInwards = true,
                ringRotationsDegrees = listOf(0f, 30f, 60f, 90f, 120f, 150f),
                globalRotationDegrees = ship.facing,
                generateParticles = true,
                particleSegments = 16,
                particleGenerationWorkMode = CircleUtils.SwirlGenerationWorkMode.BEZIER,
                particleDrawInterval = 0.05f
            )
            // Draw the instantaneous part of the swirl
            // This causes a decent FPS drop, and are mainly EMP arcs, so i'll leave it here for the time being
//            visualSwirl?.draw(
//                ship = ship,
//                arcThickness = 12f,
//                arcColors = listOf(
//                    Color.BLUE.darker().darker().darker().darker().darker() to Color.WHITE,
//                    Color.BLUE.darker().darker().darker().darker() to Color.WHITE.darker(),
//                    Color.BLUE.darker().darker().darker() to Color.WHITE.darker().darker(),
//                    Color.BLUE.darker().darker() to Color.WHITE.darker().darker().darker(),
//                    Color.BLUE.darker() to Color.WHITE.darker().darker().darker().darker(),
//                    Color.BLUE to Color.WHITE.darker().darker().darker().darker().darker(),
//                ),
//                connectToCenter = true,
//            )
            // The "particle over time" part will be done separately in advance() ...
        }




        private fun pullInShipsWithinRadius() {
            log("--> pullInShipsWithinRadius()")
            // Look through all ships within radius, and apply momentum
            val radius: Float = getRadiusAmount(member, mods, exoticData)

            val potentiallyAffectedShips = getPotentialTargets(member, mods, exoticData)

            for (nearbyShip in potentiallyAffectedShips) {
                val distanceToShip = MathUtils.getDistance(nearbyShip.location, ship.location)
                val collisionRadius = nearbyShip.collisionRadius + radius
                if (distanceToShip < collisionRadius) {
                    // Calculate direction from ship to nearbyShip
                    val direction = Vector2f.sub(nearbyShip.location, ship.location, null)
                    val segmentLength = radius + nearbyShip.collisionRadius
                    direction.normalise()
                    direction.scale(segmentLength)
                    val endPoint = Vector2f(ship.location.x + direction.x, ship.location.y + direction.y)

                    val collisionPoint: Vector2f? = CollisionUtil.getShipCollisionPoint(ship.location, endPoint, nearbyShip)
                    collisionPoint?.let { collision ->
                        if (!nearbyShip.isStation && !(nearbyShip.isStationModule && nearbyShip.parentStation.isStation)) {
                            // This is the normal case, when we pull everyone to our ship, scaled with positive effect mult
                            // and finally we will also add the ship's summed mass so that it at least gets *some* push towards us
                            // regardless of how big it is
                            val momentum = getScaledPullInEffectMomentumStrength(member, mods, exoticData) + nearbyShip.massWithModules
                            ForceApplier.applyMomentum(
                                entity = nearbyShip,
                                pointOfImpact = collision,
                                direction = Vector2f.sub(ship.location, nearbyShip.location, null),
                                momentum = momentum,
                                elasticCollision = false,
                                modifyAngularVelocity = false,
                                applyImplicitMomentumScaling = true
                            )
                        } else {
                            // This is the "inverse" case, when we try pulling in an immovable object - so we should pull ourselves in a bit
                            // however, just using these 'normal' values as-is would be bad, so they need to be scaled.
                            // So we will scale the strength * factor with negative effect mult, perhaps too harsh but it is what it is
                            val negativeMomentum =
                                getPullInEffectMomentumFactor(member = member) *
                                    getPullInStrength(member = member) *
                                    getNegativeMult(member, mods, exoticData)

                            ForceApplier.applyMomentum(
                                entity = ship.getRootModule(),
                                pointOfImpact = collision,
                                direction = Vector2f.sub(nearbyShip.location, ship.location, null),
                                momentum = negativeMomentum,
                                elasticCollision = false,
                                modifyAngularVelocity = false,
                                applyImplicitMomentumScaling = true
                            )
                        }
                    }
                }
            }
            log("<-- pullInShipsWithinRadius()")
        }
    }

    private fun log(logMsg: String) {
        if (LOGS_ENABLED) logger.info(logMsg)
    }

    companion object {
        private const val COST_CREDITS: Float = 300000f
        private const val ITEM = "et_attractoritem"
        private const val LOGS_ENABLED = false

        private const val PULL_IN_STRENGTH = 1000f
        private const val COOLDOWN_DURATION = 30f

        private const val MIN_PARTICLE_SIZE = 16f
        private const val MAX_PARTICLE_SIZE = 64f
    }
}
