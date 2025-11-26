package exoticatechnologies.modifications.exotics.impl

import com.fs.starfarer.api.Global
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
import org.apache.log4j.Logger
import org.json.JSONObject
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.subsystems.MagicSubsystem
import org.magiclib.subsystems.MagicSubsystemsManager
import java.awt.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ShipRepulsorSystem(key: String, settings: JSONObject) : Exotic(key, settings) {
    private val logger: Logger = Logger.getLogger(ShipRepulsorSystem::class.java)
    private lateinit var originalShip: ShipAPI
    private lateinit var subsystem: RepulsorPushOutSystem


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
                    .format("target_ships", getWorkModeString(member, mods, exoticData))
                    .format("push_out_strength", formatFloatAsString(getScaledPushOutEffectMomentumStrength(member, mods, exoticData), 2))
                    .formatFloat("debilitating_factor", getScaledAllowCoefficient(member, mods, exoticData) * 100f)
                    .formatFloat("cooldown_time", getScaledCooldownDuration(member, mods, exoticData))
                    .addToTooltip(tooltip, title)
        }
    }

    override fun applyToShip(id: String, member: FleetMemberAPI, ship: ShipAPI, mods: ShipModifications, exoticData: ExoticData) {
        super.applyToShip(id, member, ship, mods, exoticData)

        originalShip = ship
        subsystem = RepulsorPushOutSystem(ship, member, mods, exoticData)
        MagicSubsystemsManager.addSubsystemToShip(ship, subsystem)
    }

    private fun getScaledPushOutEffectMomentumStrength(
            member: FleetMemberAPI,
            mods: ShipModifications,
            exoticData: ExoticData
    ): Float {
        return getPushOutEffectMomentumFactor(
                member = member,
        ) * getPushOutStrength(
                member = member,
                ship = getInstalledOnShipIfAvailable()
        ) * getPositiveMult(
                member = member,
                mods = mods,
                exoticData = exoticData
        )
    }

    private fun getInstalledOnShipIfAvailable(): ShipAPI? {
        return if(::originalShip.isInitialized) {
            originalShip
        } else {
            null
        }
    }

    private fun getPushOutEffectMomentumFactor(
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

    private fun getPushOutStrength(
            member: FleetMemberAPI,
            ship: ShipAPI?
    ): Float {
        // For now, lets just try with actual hull HP and see how to go from there...
        // if ship is null (as it will be during preview) fallback to FMAPI hitpoints.
        return if (ship != null) {
            val allShipSectionsMaxHitpoints = getAllShipSections(ship)
                    .map { section -> section.maxHitpoints }
                    .sum()

            // Return the sum of all shipAPI's sections' hitpoints
            allShipSectionsMaxHitpoints
        } else {
            // fallback to FMAPI
            val childModules = getChildModuleVariantList(member)
            val childHP = childModules
                    .map { childVariant -> childVariant.hullSpec.hitpoints }
                    .sum()

            // Return the sum of all member's children's hitpoints
            member.hullSpec.hitpoints + childHP
        }
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

    private fun getScaledAllowCoefficient(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): Float {
        return ALLOW_COEF * getNegativeMult(member, mods, exoticData)
    }

    private fun getWorkMode(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): RepulsorWorkMode {
        val negativeMult = getNegativeMult(member, mods, exoticData)
        // When negative mult is less than 1.5, it's enemies only
        // when it's in 1.5-3 range, it's both; for 3+ it's allies only
        return when {
            negativeMult.isInLogicalRange(null, 1.5f, InLogicalRangeWorkMode.LESS_OR_EQUAL) -> RepulsorWorkMode.ENEMIES_ONLY
            negativeMult.isInLogicalRange(1.5f, 3f, InLogicalRangeWorkMode.LESS_THAN) -> RepulsorWorkMode.ENEMIES_AND_ALLIES
            negativeMult.isInLogicalRange(3f, null, InLogicalRangeWorkMode.LESS_OR_EQUAL) -> RepulsorWorkMode.ALLIES_ONLY
            else -> throw IllegalStateException("negativeMult wasn't in any of the expected ranges, the obscene value that caused this crash was ${negativeMult}")
        }
    }

    private fun getWorkModeString(member: FleetMemberAPI, mods: ShipModifications, exoticData: ExoticData): String {
        val workMode = getWorkMode(member, mods, exoticData)

        return when (workMode) {
            RepulsorWorkMode.ENEMIES_ONLY -> "enemy"
            RepulsorWorkMode.ENEMIES_AND_ALLIES -> "enemy and ally"
            RepulsorWorkMode.ALLIES_ONLY -> "ally"
        }.exhaustive
    }



    enum class RepulsorWorkMode {
        ENEMIES_ONLY, ENEMIES_AND_ALLIES, ALLIES_ONLY
    }

    inner class RepulsorPushOutSystem(
            ship: ShipAPI,
            val member: FleetMemberAPI,
            val mods: ShipModifications,
            val exoticData: ExoticData
    ): MagicSubsystem(ship) {
        // check for activation every 3 seconds
        private val activationIntervalUtil = IntervalUtil(2.95f, 3.05f)

        override fun getBaseActiveDuration() = 1f

        override fun getBaseCooldownDuration() = getScaledCooldownDuration(member, mods, exoticData)

        override fun shouldActivateAI(amount: Float): Boolean {
            //TODO i dont know what to do here so just say 'no' for now
            activationIntervalUtil.advance(amount)
            if (activationIntervalUtil.intervalElapsed()) {
                // If interval elapsed, we are going to do a few checks to determine if we should activate:
                // 1. if we have enemies closer than any weapon's minimum range - if yes, activate
                // 2. if we have enemies within 250 range of us - activate
                // 3. if our flux is at 80+% of flux capacity - activate

            }
            return false
        }

        override fun getDisplayText() = "Ship Repulsor System"

        override fun onActivate() {
            log("--> onActivate()")
            super.onActivate()

            showVisualFlair()
            pushOutShipsWithinRadius()
            log("<-- onActivate()")
        }

        private fun showVisualFlair() {
            // generate dots
            val center = ship.location
            val fullRange = getRadiusAmount(member, mods, exoticData)
            // Lets draw the first ring at 1.5x collision radius so it's more visible, 1x is kinda "too close"
            val stage1distance = ship.collisionRadius * 1.5f
            val stage1DotsPair = generateDots(center, stage1distance)

            val stage1left = stage1DotsPair.first
            val stage1right = stage1DotsPair.second
            // the first stage will draw emp arcs from left/right start to end
            // start of stage1
            for (index in 0 until stage1left.size -1) {
                val fromL = stage1left[index]
                val toL = stage1left[index+1]
                Global
                        .getCombatEngine()
                        .spawnEmpArcVisual(
                                fromL,
                                ship,
                                toL,
                                ship,
                                6f,
                                Color.BLUE.darker().darker(),
                                Color.WHITE
                        )

                val fromR = stage1right[index]
                val toR = stage1right[index+1]
                Global
                        .getCombatEngine()
                        .spawnEmpArcVisual(
                                fromR,
                                ship,
                                toR,
                                ship,
                                6f,
                                Color.BLUE.darker().darker(),
                                Color.WHITE
                        )
            }
            // end of stage1
            val stage2distance = fullRange / 2
            val stage2dotsPair = generateDots(center, stage2distance)

            val stage2left = stage2dotsPair.first
            val stage2LeftReversed = stage2left.asReversed()
            val stage2right = stage2dotsPair.second
            val stage2CW = stage2right + stage2LeftReversed
            val stage2CCW = stage2CW.asReversed()
            val stage1CW = stage1right + stage1left.asReversed()

            // stage2 will draw a full circle going from index0-35 and index35-0
            // along with stage1[i] to stage2[i]
            // start of stage2
            for (index in 0 until stage2CW.size - 1) {
                val CW1 = stage2CW[index]
                val CW2 = stage2CW[index+1]
                Global
                        .getCombatEngine()
                        .spawnEmpArcVisual(
                                CW1,
                                ship,
                                CW2,
                                ship,
                                6f,
                                Color.BLUE.darker(),
                                Color.WHITE.darker()
                        )

                val CCW1 = stage2CCW[index]
                val CCW2 = stage2CCW[index+1]
                Global
                        .getCombatEngine()
                        .spawnEmpArcVisual(
                                CCW1,
                                ship,
                                CCW2,
                                ship,
                                6f,
                                Color.BLUE.darker(),
                                Color.WHITE.darker()
                        )
            }
            for (index in 0 until stage2CW.size) {
                val from = stage1CW[index]
                val to = stage2CW[index]
                Global
                        .getCombatEngine()
                        .spawnEmpArcVisual(
                                from,
                                ship,
                                to,
                                ship,
                                6f,
                                Color.BLUE.darker(),
                                Color.WHITE.darker()
                        )
            }
            // end of stage2

            // stage3 will just do CCW arcs between stage2ccw and stage3ccw
            val stage3distance = fullRange
            val stage3dotsPair = generateDots(center, stage3distance)
            val stage3CCW = stage3dotsPair.second + stage3dotsPair.first.asReversed()
            for (index in 0 until stage3CCW.size) {
                val from = stage2CCW[index]
                val to = stage3CCW[index]
                Global
                        .getCombatEngine()
                        .spawnEmpArcVisual(
                                from,
                                ship,
                                to,
                                ship,
                                6f,
                                Color.BLUE.darker(),
                                Color.WHITE.darker()
                        )
            }
        }

        /**
         * Generates a pair of lists, going from 0-360 degrees with 10-degree increments, clockwise.
         * The left list is reversed so that it represents CCW rotation from 0 to 180
         *
         * @return a pair of lists, first one being the 0-180 "right" list, second one being the reversed 180-360 "left" list
         *
         * @param center the center from which dots should diverge
         * @param distance how far from the center should the dots be
         */
        private fun generateDots(center: Vector2f, distance: Float): Pair<List<Vector2f>, List<Vector2f>> {
            val leftDots = mutableListOf<Vector2f>()
            val rightDots = mutableListOf<Vector2f>()
            val numDots = 36
            for (i in 0 until numDots) {
//            for (i in 0 ..numDots) {
                val angleDeg = i * 10f
                val angleRad = angleDeg * PI.toFloat() / 180f
                val x = center.x + distance * cos(angleRad)
                val y = center.y + distance * sin(angleRad)
//                dots.add(Vector2f(x, y))
                if (i < numDots / 2) {
                    rightDots.add(Vector2f(x,y))
                } else {
                    leftDots.add(Vector2f(x,y))
                }
            }
            // Now, since rightDots go from top to bottom and leftDots go from bottom to top, reverse the leftDots
            leftDots.reverse()

            return Pair(leftDots, rightDots)
        }

        private fun pushOutShipsWithinRadius() {
            log("--> pushOutShipsWithinRadius()")
            // Look through all ships within radius, and apply momentum
            val momentumFactor: Float = getPushOutEffectMomentumFactor(member)
            val momentumStrength: Float = getPushOutStrength(member, ship)
            val radius: Float = getRadiusAmount(member, mods, exoticData)
            val workMode = getWorkMode(member, mods, exoticData)

            // Depending on the workmode, grab ships within radius with some prefiltering ...
            val potentiallyAffectedShips = when (workMode) {
                RepulsorWorkMode.ENEMIES_ONLY -> CombatUtils.getShipsWithinRange(ship.location, radius)
                        // make sure it only contains enemies and not enemies and neutrals
                        .filter { filterShip -> ship.owner != filterShip.owner && filterShip.owner != 100 }

                RepulsorWorkMode.ENEMIES_AND_ALLIES -> CombatUtils.getShipsWithinRange(ship.location, radius)
                        // make sure it only contains non-neutrals
                        .filter { filterShip -> filterShip.owner != 100 }

                RepulsorWorkMode.ALLIES_ONLY -> CombatUtils.getShipsWithinRange(ship.location, radius)
                        // make sure it only contains allies and not enemies and neutrals
                        .filter { filterShip -> filterShip.owner == ship.owner && filterShip.owner != 100 }
            }.exhaustive
                    // And ... then apply some more filtering
                    // make sure we're not targetting ourselves
                    .filter { module -> module.fleetMember != member && module.parentStation != ship && module != ship }
                    // make sure we're not targetting child modules
                    .filter { module -> module.parentStation == null }

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
                            // This is the normal case, when we push everyone away from our ship, scaled with positive effect mult
                            nearbyShip.velocity.set(ship.velocity)
                            val momentum = getScaledPushOutEffectMomentumStrength(member, mods, exoticData)
                            ForceApplier.applyMomentum(
                                    entity = nearbyShip,
                                    pointOfImpact = collision,
//                                    direction = Vector2f.sub(ship.location, nearbyShip.location, null),   //this attracts
                                    direction = Vector2f.sub(nearbyShip.location, ship.location, null),
                                    momentum = momentum,
                                    elasticCollision = true,
                                    // We will modify the angular velocity after just pushing it back
                                    modifyAngularVelocity = false
                            )

                            // Add the "paralyzing"/debilitating effect as well - effect being just also spinning the target
                            // besides just launching it straight away from us along the vector direction between the two of us

                            // Calculate the "difference in mass ratio" by taking the enemy ship's total mass divided by
                            // our ship's total mass - we do it this way because we will be dividing the momentumStrength
                            // with it; so lighter ships should end up increasing the momentumStrength while heavier ships
                            // will end up decreasing it
                            // NOTE: we will not be using "ShipAPI.massWithModules()" because it literally does the exact same thing
                            val enemyShipTotalMass = getAllShipSections(nearbyShip).map { module -> module.mass }.sum()
                            val myShipTotalMass = getAllShipSections(ship).map { module -> module.mass }.sum()
                            val enemyShipTotalHitpoints = getAllShipSections(nearbyShip).map { module -> module.maxHitpoints}.sum()
                            val myShipTotalHitpoints = getAllShipSections(ship).map { module -> module.maxHitpoints}.sum()
                            val differenceInMassRatio = enemyShipTotalMass / myShipTotalMass
                            val rotationalDirection = if (Math.random() < 0.5) { 1 } else { -1 }
                            // Once we have the rotational momentum calculated, we need to 'clamp' it between
                            // -10mil and 10mil so we can scale it further
                            val myMassHitpoints = (myShipTotalHitpoints * myShipTotalMass)
                            val enemyMassHitpoints = (enemyShipTotalHitpoints * enemyShipTotalMass)
                            val diffMassHitpoints = myMassHitpoints - enemyMassHitpoints
                            var avoidScaling = false
                            val rotationalMomentum = if(diffMassHitpoints > 0) {
                                momentumFactor * (diffMassHitpoints / differenceInMassRatio) * rotationalDirection
                            } else {
                                // If we're in the ALLOW_COEF, let it go, otherwise just return 0 os we can't spin up ships far out of our league.
                                // We need to abs() it because it's already negative, so whatever the ratio comes out, it's certainly going
                                // to be less than a small positive number ...
                                val diffMassHitpointRatio = abs(diffMassHitpoints / myMassHitpoints)
                                if (diffMassHitpointRatio <= getScaledAllowCoefficient(member, mods, exoticData)) {
                                    if (diffMassHitpointRatio != 0f) {
                                        // Special case 1 - if we're not exactly the same, but are within allowed ratio,
                                        // use identical formula as above regardless of diffMassHitpoints being positive or negative
                                        momentumFactor * (diffMassHitpoints / differenceInMassRatio) * rotationalDirection
                                    } else {
                                        // Special case 2 - If we are exactly the same, do not use the formula at all.
                                        // Just apply a small random rotation and call it a day. Also avoid scaling in this case
                                        avoidScaling = true

                                        (Math.random() * ZERO_DIFF_ROTATION_VALUE).toFloat() * rotationalDirection
                                    }
                                } else {
                                    0f
                                }
                            }
                            val scaledRotationalMomentum = (rotationalMomentum * getPositiveMult(member, mods, exoticData)).coerceIn(MIN_MOMENTUM_CLAMP, MAX_MOMENTUM_CLAMP)
                            // once it has been clamped, we will apply the scaling factor of 0.00216 to bring it into [-360*3, 360*3] range
                            val finalRotationalMomentum = if (avoidScaling.not()) {
                                scaledRotationalMomentum * SCALING_FACTOR
                            } else {
                                rotationalMomentum
                            }

                            // And finally, apply the scaled rotational momentum to the enemy ship
                            nearbyShip.angularVelocity += finalRotationalMomentum
                        } else {
                            // This is the "inverse" case, when we try pushing out an immovable object - so we should push ourselves back a bit
                            // however, just using these 'normal' values as-is would be bad, so they need to be scaled.
                            // And finally, we will scale the strength * factor with negative effect mult
                            val momentum = (-momentumStrength / 2f) * (1 / momentumFactor) * getNegativeMult(member, mods, exoticData)
                            ForceApplier.applyMomentum(
                                    entity = ship.parentStation,
                                    pointOfImpact = collision,
//                                    direction = Vector2f.sub(nearbyShip.location, ship.location, null),  //this probably repulses?
                                    direction = Vector2f.sub(ship.location, nearbyShip.location, null),   //this attracts
                                    momentum = momentum,
                                    elasticCollision = true,
                            )
                        }
                    }
                }
            }
            log("<-- pushOutShipsWithinRadius()")
        }
    }

    private fun log(logMsg: String) {
        if (LOGS_ENABLED) logger.info(logMsg)
    }

    companion object {
        private const val COST_CREDITS: Float = 300000f
        private const val ITEM = "et_repulsorcrystal"
        private const val LOGS_ENABLED = false

        private const val MIN_MOMENTUM_CLAMP = -10000000f
        private const val MAX_MOMENTUM_CLAMP = 10000000f
        private const val SCALING_FACTOR = 0.000108f

        private const val COOLDOWN_DURATION = 30f
        private const val ALLOW_COEF = 0.33f
        private const val ZERO_DIFF_ROTATION_VALUE = 120f
    }
}
