package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.IntervalUtil
import exoticatechnologies.combat.ExoticaCombatUtils
import exoticatechnologies.modifications.ShipModifications
import exoticatechnologies.modifications.exotics.Exotic
import exoticatechnologies.modifications.exotics.ExoticData
import exoticatechnologies.modifications.exotics.misc.ForceApplier
import exoticatechnologies.util.*
import exoticatechnologies.util.drawutils.LineUtils
import exoticatechnologies.util.drawutils.ParticleDrawMode
import org.apache.log4j.Logger
import org.json.JSONObject
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color
import kotlin.math.withSign
import kotlin.random.Random

class ShipFishingHookSystem(key: String, settings: JSONObject) : Exotic(key, settings) {
    private val logger: Logger = Logger.getLogger(ShipFishingHookSystem::class.java)
    private lateinit var originalShip: ShipAPI
    private lateinit var subsystem: FishingHookSystem


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
                .format("arc_width", getScaledArcWidth(member, mods, exoticData))
                .formatFloat("cooldown_time", getScaledCooldownDuration(member, mods, exoticData))
                .addToTooltip(tooltip, title)
        }
    }

    override fun applyToShip(id: String, member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        subsystem = FishingHookSystem(ship, member, mods, exoticData)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)
    }

    private fun getPullInStrength(member: FleetMemberAPI): Float {
        return PULL_IN_STRENGTH * getAllModulesVariantList(member).count()
    }

    private fun getPullInStrengthFactor(
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
            ShipAPI.HullSize.FRIGATE -> 1500f
            ShipAPI.HullSize.DESTROYER -> 2500f
            ShipAPI.HullSize.CRUISER -> 4500f
            ShipAPI.HullSize.CAPITAL_SHIP -> 7500f
        }.exhaustive
        return baseRadiusBasedOnShipSize * getPositiveMult(member, mods, exoticData)
    }

    private fun getScaledArcWidth(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        // Multiply by positive, and then divide by negative - so that a (negative:2, positive:1.5) doesn't just double
        // the width but also shrink it;
        // example: 30 * 2 / 1.5 = 40
        return BASE_CONE_WIDTH * getPositiveMult(member, mods, exoticData) / getNegativeMult(member, mods, exoticData)
    }

    private fun getScaledCooldownDuration(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return COOLDOWN_DURATION * getNegativeMult(member, mods, exoticData)
    }

    inner class FishingHookSystem(
        ship: ShipAPI,
        val member: FleetMemberAPI,
        val mods: ShipModifications,
        val exoticData: ExoticData
    ): MagicSubsystem(ship) {
        // check for activation every 3 seconds
        private val activationIntervalUtil = IntervalUtil(2.95f, 3.05f)
        private var visualArc: LineUtils.ArcSelection? = null
        private val targetsVisualsList = mutableListOf<LineUtils.StraightLine>()
        private var activationAngle: Float = ship.facing

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

        fun generateArc(
            center: Vector2f,
            userCentricFacing: Float,
            generateParticles: Boolean,
            particleSpacing: Float?,
            arcWidth: Float,
            fullRange: Float
        ): LineUtils.ArcSelection {
            val halfArc = arcWidth / 2
            return LineUtils.generateArc(
                origin = center,
                facing = userCentricFacing,
                // since we want to have things like (-15,15) we need to multiply by -1
                leftOffset = halfArc.withSign(-1f),
                rightOffset = halfArc,
                length = fullRange,
                degreeType = AngleDegreeType.USER_CENTRIC,
                generateParticles = generateParticles,
                particleSegments = null,
                particleSpacing = particleSpacing
            )
        }

        private fun getTarget(): ShipAPI? {
            val shipTarget: ShipAPI? = ship.shipTarget
            return shipTarget
        }

        /**
         * Method that either returns the facing to this ship's target, or fallbacks to [ShipAPI.getFacing] if there is no target
         */
        fun getFacingToTarget(): Float {
            val target = getTarget()

            return if (target != null) {
                ship.getFacingTo(target)
            } else {
                // With no target, fallback to ship.facing
                ship.facing
            }
        }

        private fun evaluateSituation(): Boolean {
            // If interval elapsed, we are going to do a few checks to determine if we should activate:
            // 1. if we have enemies within system range but outside of weighted effective weapon range (excluding wings) - activate
            // 2. majority of ships are moving away (excluding wings) - activate
            // 3. if there are vulnerable ships present (excluding wings) - activate
            // 4. allies outnumber enemies nearby (excluding wings) - activate
            // 5. there are enemies outside of weapon range, including PD   //TODO rethink this one
            // If any criteria is met, we will do an early return and avoid evaluating the rest of them
            // Otherwise - do nothing for this evaluation cycle

            val evaluator = if (ship.isRootModule()) {
                AIEvaluator.RootModuleEvaluator(
                    ship = ship,
                    system = this@ShipFishingHookSystem,
                    magicSubsystem = this@FishingHookSystem
                )
            } else {
                AIEvaluator.ChildModuleAimingEvaluator(
                    ship = ship,
                    system = this@ShipFishingHookSystem,
                    magicSubsystem = this@FishingHookSystem
                )
            }

            val evaluationResult = evaluator.evaluate()
            activationAngle = evaluationResult.angle
            return evaluationResult.shouldActivate
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

            //BEWARE - we initialize the ArcSelection used for filtering targets in showVisualFlair() and the method below
            // expects to directly use it to filter targets within radius; the calling order here matters !!
            showVisualFlair()
            pullInShipsWithinRadius()
            log("<-- onActivate()")
        }

        /**
         * Just returns all enemy ships within radius of [ship]
         */
        fun getPotentialTargets(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): List<ShipAPI> {
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

        fun onArcFinished(arcThatFinished: LineUtils.ArcSelection) {
            visualArc = null
            // And reset the smooth particle limit back to original
            EngineParticlePainter.ParticleLimits.resetParticleLimitForParticleType(ParticleType.SMOOTH_PARTICLE)
        }

        fun getShipDependantParticleSize(ship: ShipAPI): Float {
            // Coerce shipSize / 8 into [MIN_PARTICLE_SIZE, MAX_PARTICLE_SIZE] //16-64f
            return (ship.collisionRadius/8).coerceIn(MIN_PARTICLE_SIZE, MAX_PARTICLE_SIZE)
        }

        override fun advance(amount: Float, isPaused: Boolean) {
            if (isPaused.not()) {
                // If not paused, draw particles on the arc if we have it
                visualArc?.let { arc ->
                    arc.drawParticles(
                        amount = amount,
                        particleSize = getShipDependantParticleSize(ship = ship),
                        particleColors = listOf(
                            Color.WHITE.brighter().brighter(),
                            Color.WHITE,
                            Color.LIGHT_GRAY.brighter().brighter(),
                            Color.LIGHT_GRAY,
                            Color.DARK_GRAY,
                            Color.DARK_GRAY.darker().darker()
                        ),
                        particleDrawMode = ParticleDrawMode.WHOLE_ARM,
                        continuousDrain = null
                    )

                    // If we finished, get rid of this arc
                    if (arc.hasFinished()) {
                        onArcFinished(arc)
                    }
                }
                targetsVisualsList.forEach { visual ->
                    visual.drawParticles(
                        amount = amount,
                        particleSize = 24f,
                        particleDuration = 0.5f,
                        particlesToDrawPerInterval = 3,
                        particleColors = listOf(Color.WHITE),
                        particleDrawMode = ParticleDrawMode.ONE_AT_A_TIME,
                        continuousDrain = null
                    )
                }
            }
        }

        private fun showVisualFlair() {
            // We will allow the child modules to 'target' with their fishing hook system,
            // however the root (drivable) module will be forced to always shoot straight ahead
            val facingToUseForActivation = if(ship.isRootModule()) {
                // For root modules, which the player drives, we cannot 'angle' the system so always use facing
                ship.facing
            } else {
                // If this ship is not the root of the ship, then either use facing to target or this ship's facing
                getFacingToTarget()
            }

            visualArc = generateArc(
                center = ship.location,
                userCentricFacing = remapAngleToTrigonometricCoordinateSystem(facingToUseForActivation),
                generateParticles = true,
                particleSpacing = 100f,
                arcWidth = getScaledArcWidth(member, mods, exoticData),
                fullRange = getRadiusAmount(member, mods, exoticData)
            )
        }

        fun getReelInStrengthForShip(ship: ShipAPI): Float {
            // Idea is, PULL_IN_STRENGTH * ship.massSum, limited to max float
            val shipMassSum = ship.massWithModules
            val fishingShipsPullInStrength = getPullInStrength(member) * getPullInStrengthFactor(member)

            val actualStrength = (shipMassSum * fishingShipsPullInStrength).coerceAtMost(Float.MAX_VALUE)

            return actualStrength
        }

        fun pullInShip(pullInMomentum: Float, shipToPull: ShipAPI, destinationShip: ShipAPI, collisionPoint: Vector2f) {
            // First, apply momentum where needed
            ForceApplier.applyMomentum(
                entity = shipToPull,
                pointOfImpact = collisionPoint,
                direction = Vector2f.sub(destinationShip.location, shipToPull.location, null),
                momentum = pullInMomentum,
                elasticCollision = false,
                modifyAngularVelocity = false,
                applyImplicitMomentumScaling = true
            )
            // Now, generate a visual
            //TODO replace this with ArrowLines
            val pullInVisual = LineUtils.generateStraightLine(
                start = shipToPull.location,
                end = destinationShip.location,
                generateParticles = true,
                particleSegments = null,
                particleSpacing = 75f,
                particleDrawInterval = 0.25f
            )
            // And add it to the list of visuals to play
            targetsVisualsList.add(pullInVisual)
        }


        private fun pullInShipsWithinRadius() {
            log("--> pullInShipsWithinRadius()")
            // Look through all ships within radius, and apply momentum
            val radius: Float = getRadiusAmount(member, mods, exoticData)

            val potentiallyAffectedShips = getPotentialTargets(member, mods, exoticData)
            // Now, since we have an arc, lets also reduce the number of potentialTargets by filtering some more
            val shipsInArc = visualArc?.let { arc ->
                potentiallyAffectedShips.filter { arc.isWithinArc(it) }
            } ?: emptyList()

            for (nearbyShip in shipsInArc) {
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
                            val momentum = getReelInStrengthForShip(nearbyShip) * getPositiveMult(member, mods, exoticData)
                            pullInShip(
                                pullInMomentum = momentum,
                                shipToPull = nearbyShip,
                                destinationShip = ship,
                                collisionPoint = collision
                            )
                        } else {
                            // This is the "inverse" case, when we try pulling in an immovable object - so we should pull ourselves in a bit
                            // however, just using these 'normal' values as-is would be bad, so they need to be scaled.
                            // So we will scale the strength * factor with negative effect mult, perhaps too harsh but it is what it is
                            val negativeMomentum = getReelInStrengthForShip(ship.getRootModule()) * getNegativeMult(member, mods, exoticData)

                            pullInShip(
                                pullInMomentum = negativeMomentum,
                                shipToPull = ship.getRootModule(),
                                destinationShip = nearbyShip,
                                collisionPoint = collision
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

    data class EvaluationData(
        val angle: Float,
        val shouldActivate: Boolean
    )

    sealed class AIEvaluator(
        val evaluatingShip: ShipAPI,
        val system: ShipFishingHookSystem,
        val magicSubsystem: FishingHookSystem
    ) {
        abstract fun evaluate(): EvaluationData

        // classes first, class methods last; abstract will stay above so that they are clearly visible

        class RootModuleEvaluator(
            ship: ShipAPI,
            system: ShipFishingHookSystem,
            magicSubsystem: FishingHookSystem
        ) : AIEvaluator(ship, system, magicSubsystem) {
            override fun evaluate(): EvaluationData {
                // Since root module always shoots straight, his evaluation is easy - always ship.facing

                val evaluationArc = magicSubsystem.generateArc(
                    center = evaluatingShip.location,
                    userCentricFacing = remapAngleToTrigonometricCoordinateSystem(evaluatingShip.facing),
                    generateParticles = false,
                    particleSpacing = null,
                    arcWidth = system.getScaledArcWidth(magicSubsystem.member, magicSubsystem.mods, magicSubsystem.exoticData),
                    fullRange = system.getRadiusAmount(magicSubsystem.member, magicSubsystem.mods, magicSubsystem.exoticData)
                )

                val result = checkCriteria(evaluationArc) > 0

                return EvaluationData(
                    angle = evaluatingShip.facing,
                    shouldActivate = result
                )
            }

        }

        class ChildModuleAimingEvaluator(
            ship: ShipAPI,
            system: ShipFishingHookSystem,
            magicSubsystem: FishingHookSystem,
            val sweepingStep: Int = 15
        ) : AIEvaluator(ship, system, magicSubsystem) {

            /**
             * Just a little method that had the identical code shared between both loops in [evaluate] extracted into it (extracted here)
             * The method just generates the arc for [degree] angle, calls [checkCriteria] and returns a ```<degree, score>``` [Pair]
             */
            private fun loopArcEvaluate(degree: Int): Pair<Int, Int> {
                val angle = degree.toFloat()
                val evaluationArc = magicSubsystem.generateArc(
                    center = evaluatingShip.location,
                    userCentricFacing = remapAngleToTrigonometricCoordinateSystem(angle),
                    generateParticles = false,
                    particleSpacing = null,
                    arcWidth = system.getScaledArcWidth(magicSubsystem.member, magicSubsystem.mods, magicSubsystem.exoticData),
                    fullRange = system.getRadiusAmount(magicSubsystem.member, magicSubsystem.mods, magicSubsystem.exoticData)
                )

                // Calculate a score for this specific slice
                val score = checkCriteria(evaluationArc)

                return Pair(degree, score)
            }

            override fun evaluate(): EvaluationData {
                // Since child modules can aim, we need to scan and find the best activation angle,
                // returning the angle that scored best
                // So we will start from either ship.target or ship.facing and do a full circle.

                var bestAngle = magicSubsystem.getFacingToTarget()
                var highestScore = 0

                // Sweep the circle - after some thinking (and a test) I think 'until' is better than '..' due to not repeating the first step again
                // Flip a coin to decide whether to start evaluation from left-to-right or right-to-left - even though it makes no sense to do that
                // since the evaluation should return the **best** angle for activation...
                if (Random.nextInt() % 2 == 0 ) {
                    for (degree in 0 until 360 step sweepingStep) {
                        val (angle, score) = loopArcEvaluate(degree)
                        if (score > highestScore) {
                            highestScore = score
                            bestAngle = angle.toFloat()
                        }
                    }
                } else {
                    for (degree in 360 downUntil 0 step sweepingStep) {
                        val (angle, score) = loopArcEvaluate(degree)
                        if (score > highestScore) {
                            highestScore = score
                            bestAngle = angle.toFloat()
                        }
                    }
                }

                return EvaluationData(
                    angle = bestAngle,
                    shouldActivate = highestScore > 0f
                )
            }

        }

        fun checkCriteria(evaluationArc: LineUtils.ArcSelection): Int {
            var score = 0

            val shipsInArcRadius = magicSubsystem.getPotentialTargets(magicSubsystem.member, magicSubsystem.mods, magicSubsystem.exoticData)
                .filter { target -> target.isFighter.not() }
                .filter { evaluationArc.isWithinArc(it) }

            // Calculate our 'most damaging' range and see how many targets are within range but outside most damaging range
            val largestDamageRange = ExoticaCombatUtils.getLargestDamageContributingRange(evaluatingShip)
            val shipsWithinRangeOutsideOfBestRange = shipsInArcRadius.filter { targetShip ->
                val distanceToUs = targetShip.distanceToShip(evaluatingShip)

                return@filter distanceToUs > largestDamageRange
            }

            // Criteria 1 - have ships within range but outside of our most-damaging range
            val haveShipsOutsideOfMostDamagingRange = shipsWithinRangeOutsideOfBestRange.isNotEmpty()
            if (haveShipsOutsideOfMostDamagingRange) {
                // So, most important criteria starts with the biggest base, and each member contributing it is worth the most
                score += 50 + shipsWithinRangeOutsideOfBestRange.size * 5
            }

            // In case we did not return, lets start working on criteria 2 - majority of ships moving away
            val enemyShipCount = shipsInArcRadius.count()
            val enemyShipsRunningAway = shipsInArcRadius
                .map { enemyShip -> enemyShip.isMovingAwayFromShip(evaluatingShip) }
                .filter { it }
                .count()

            // Criteria 2 - majority of ships running away
            if (enemyShipsRunningAway > enemyShipCount / 2) {
                // Second criteria starts with a smaller base and each contributing member is worth *a bit less*
                score += 40 + enemyShipsRunningAway * 4
            }

            // In case we did not return, lets work on criteria 3 - vulnerable ships detected
            val vulnerableShipsInRange = shipsInArcRadius.filter { enemyShip -> magicSubsystem.isVulnerable(enemyShip) }

            // Criteria 3 - there are vulnerable ships present
            val anyVulnerableShipsInRange = vulnerableShipsInRange.isNotEmpty()
            if (anyVulnerableShipsInRange) {
                // Third criteria starts from 30, each member worth 3
                score += 30 + vulnerableShipsInRange.size * 3
            }

            // In case we did not return, try the last case - "more allies than enemies"
            val alliesInRange = CombatUtils.getShipsWithinRange(evaluatingShip.location, ALLIES_CHECK_RANGE)
                // make sure it only contains allies
                .filter { filterShip -> filterShip.owner == evaluatingShip.owner }
                // make sure we're not targetting ourselves
                .filter { module -> module.fleetMember != magicSubsystem.member && module.parentStation != evaluatingShip && module != evaluatingShip }
                // make sure we're not targetting child modules
                .filter { module -> module.parentStation == null }
                // and make sure we're not counting our own fighters
                .filter { target -> target.isFighter.not() }

            // Criteria 4 - there are more allied ships than potential pulled-in enemy ships
            val enemyShips = shipsInArcRadius.count()
            val allyShips = alliesInRange.count()
            if (allyShips >= enemyShips && enemyShips != 0) {
                // This is somewhat special, because both allies and enemies will count for it - each worth 2
                score += 20 + allyShips * 2 + enemyShips * 2
            }

            // Criteria 5 - there are enemies outside of weapon range, including PD
            val enemiesOutsideWeaponRange = shipsInArcRadius
                .map { shipInArc ->
                    evaluatingShip.distanceToShip(shipInArc) > ExoticaCombatUtils.getMaxWeaponRange(
                        ship = evaluatingShip,
                        includePD = true,
                        includeWeaponsOnAllModules = false
                    )
                }
                .count()
            if (enemiesOutsideWeaponRange >= 1) {
                // Last "bottom of the barrel" criteria, least amount of worth (1)
                score += 10 + enemiesOutsideWeaponRange
            }

            // Just return the score we accumulated so far, and if it ended up being zero that just means
            // that none of the criterias were fulfilled so far, so we should return "false" for this evaluation cycle
            return score
        }
    }

    companion object {
        private const val COST_CREDITS: Float = 250000f
        private const val ITEM = "et_laserhookcore"
        private const val LOGS_ENABLED = false

        private const val PULL_IN_STRENGTH = 1000f
        private const val COOLDOWN_DURATION = 30f
        private const val BASE_CONE_WIDTH = 30f
        private const val ALLIES_CHECK_RANGE = 2000f

        private const val MIN_PARTICLE_SIZE = 16f
        private const val MAX_PARTICLE_SIZE = 64f
    }
}
