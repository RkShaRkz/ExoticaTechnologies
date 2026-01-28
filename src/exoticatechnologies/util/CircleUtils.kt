import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.combat.CombatEngine
import exoticatechnologies.util.*
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.*

object CircleUtils {


    /**
     * Generates a list of [Vector2f] dots, going from 0-360 degrees with 360/[numDots] degree increments, clockwise.
     *
     * @param center the center from which dots should diverge
     * @param distance how far from the center should the dots be
     * @param numDots how many dots should be in the circle
     *
     * @return a list of [Vector2f] dots along the circle circumference of a [distance] radius
     */
    fun generateDots(center: Vector2f, distance: Float, numDots: Int): List<Vector2f> {
        val dots = mutableListOf<Vector2f>()
        val angleIncrement = 360 / numDots
        for (i in 0 until numDots) {
            val angleDeg = i * angleIncrement
            val angleRad = angleDeg * PI.toFloat() / 180f
            val x = center.x + distance * cos(angleRad)
            val y = center.y + distance * sin(angleRad)
            dots.add(Vector2f(x, y))
        }

        return dots
    }

    /**
     * Rotates a circle of [points] from the given [center] by a given [angleDegrees].
     * Given a list of 360 points along some center, calling the method with `angleDegrees` of 30 would make the
     * first point (at zero degrees) change to 30, the next point would be 31 instead of 1 and so on...
     *
     * @param points list of points along a circle
     * @param center the center of the circle
     * @param angleDegrees the angle degrees to add to their "rotation"
     *
     * @return a list of original [points] rotated by [angleDegrees] along the circle centered at [center]
     */
    fun rotatePointsAlongCircle(points: List<Vector2f>, center: Vector2f, angleDegrees: Float): List<Vector2f> {
        val angle = Math.toRadians(angleDegrees.toDouble())
        val cos = Math.cos(angle)
        val sin = Math.sin(angle)

        return points.map { p ->
            val dx = p.x - center.x
            val dy = p.y - center.y

            val rotatedX = (dx * cos - dy * sin) + center.x
            val rotatedY = (dx * sin + dy * cos) + center.y

            Vector2f(rotatedX.toFloat(), rotatedY.toFloat())
        }
    }

    /**
     * Generates a [Swirl]
     *
     * @param center the center of the swirl
     * @param rings the number of rings the swirl will have
     * @param pointsPerRing how many points per ring
     * @param minRadius the min radius, or rather the radius of the smallest concentric ring in the swirl
     * @param maxRadius the max radius, or rather the radius of the largest concentric ring in the swirl
     * @param generateInwards whether the swirl should be generated inwards (from largest to smallest) for [true] or outwards (from smallest to largest) for [false]. **Defaults to [false]**
     * @param ringRotationsDegrees list of per-ring **CLOCKWISE** rotations. Ideally a list of the same size as [rings] because otherwise it defaults to 0.
     * The rotations should be in degrees, where the following user-favored coordinate system is in place:
     * 0 degrees = north, 90 degrees = east, 180 degrees = south, 270 degrees = west.
     * Which is completely different from the geometric defaults of:
     * 0 degrees = east, 90 degrees = north, 180 degrees = west, 270 degrees = south.
     * @param ringRotationDegreesListAngleType which kind of angles ([AngleDegreeType]) does the [ringRotationsDegrees] contain and represent. Defaults to [AngleDegreeType.USER_CENTRIC]
     * @param globalRotationDegrees the global rotation to add to every point.
     * E.g. using ship's facing here will always make the first generated point be in same relative location/angle to the ship rather than always starting at zero degrees. **Defaults to 0**
     * **NOTE:** since ship.facing is already coming in geometric coordinate system, or rather 0 being right, up being 90, 180 being left this parameter will not be treated
     * as being in "user-centric" coordinate system like [ringRotationsDegrees] will be.
     * @param generateParticles whether this swirl should also generate particles or not
     * @param particleSegments how many 'line segments' should there be for each ring-to-ring connection. Disregarded if [generateParticles] is [false]
     * @param particleGenerationWorkMode how to generate the particles. See [SwirlGenerationWorkMode]
     * @param particleDrawInterval the default interval used for the [Swirl.intervalUtil]. Defaults to 0.1
     *
     * @return an instance of a [Swirl]. See [Swirl.draw] and [Swirl.drawParticles]
     */
    //TODO add support for "anchored swirl" capability in here by having an anchor: Optional<CombatEntityAPI>
    // and then decide whether we generate a "fixed swirl" or "anchored swirl" depending on if it's empty or not
    fun generateSwirl(
        center: Vector2f,
        rings: Int,
        pointsPerRing: Int,
        minRadius: Float,
        maxRadius: Float,
        generateInwards: Boolean = false,
        ringRotationsDegrees: List<Float>,
        ringRotationDegreesListAngleType: AngleDegreeType = AngleDegreeType.USER_CENTRIC,
        globalRotationDegrees: Float = 0f,
        generateParticles: Boolean = false,
        particleSegments: Int,
        particleGenerationWorkMode: SwirlGenerationWorkMode = SwirlGenerationWorkMode.LOGARITHMIC,
        particleDrawInterval: Float = 0.1f
    ): Swirl {
        val swirl = mutableListOf<List<Vector2f>>()

        // First thing's first - lets remap our user-centric degrees from our intuitive coord system
        // (north = 0deg, east = 90deg, south = 180deg, west = 270deg)
        // into actual geometric angles which is
        // (east = 0deg, north = 90deg, west = 180deg, south = 270deg)
        val remappedRingRotations = when (ringRotationDegreesListAngleType) {
            AngleDegreeType.USER_CENTRIC -> {
                // If user-centric, we need to remap
                ringRotationsDegrees.map { userCentricAngle ->
                    remapAngleToTrigonometricCoordinateSystem(userCentricAngle)
                }
            }
            AngleDegreeType.TRIGONOMETRIC -> {
                // If trigonometric, just accept them as-is
                ringRotationsDegrees
            }
        }.exhaustive

        // Step sizes
        val radiusStep = if (rings > 1) (maxRadius - minRadius) / (rings - 1) else 0f
        val angleStepDegrees = 360.0 / pointsPerRing

        for (ring in 0 until rings) {
            val radius = if (generateInwards) {
                maxRadius - ring * radiusStep
            } else {
                minRadius + ring * radiusStep
            }

            // base rotation offset for this ring
            val ringRotation = remappedRingRotations.getOrNull(ring)?.toDouble() ?: 0.0

            val ringPoints = mutableListOf<Vector2f>()

            for (i in 0 until pointsPerRing) {
                // base spacing in degrees, plus global rotation, plus per-ring rotation
                val angleDegrees = (angleStepDegrees * i) + globalRotationDegrees + ringRotation
                // After summing in degrees, convert to radians
                val angleRadians = Math.toRadians(angleDegrees)
                val x = center.x + radius * cos(angleRadians).toFloat()
                val y = center.y + radius * sin(angleRadians).toFloat()
                ringPoints.add(Vector2f(x, y))
            }
            swirl.add(ringPoints)
        }

        return Swirl(
            rings = swirl,
            swirlType = if (generateInwards) {
                Swirl.SwirlType.OUTWARD
            } else {
                Swirl.SwirlType.INWARD
            },
            generateParticles = generateParticles,
            particleSegments = particleSegments,
            particleGenerationWorkMode = particleGenerationWorkMode,
            particleGenerationCenter = center,
            particleDrawInterval = particleDrawInterval
        )
    }

    /**
     * Enum class describing the angle/degree type as either [USER_CENTRIC] or [TRIGONOMETRIC]
     *
     * The [USER_CENTRIC] system is a system where 0 is north, 90 is east, 180 is south, 270 is west and rotations increase **counter-clockwise**.
     *
     * The [TRIGONOMETRIC] system is a system where 0 is east, 90 is north, 180 is west, 270 is south and rotations increase **clockwise**.
     *
     * @see remapAngleToTrigonometricCoordinateSystem
     * @see USER_CENTRIC
     * @see TRIGONOMETRIC
     */
    enum class AngleDegreeType {
        /**
         * Denotes this angle to be in user-centric ("azimuth" / "bearing") system where:
         * - 0 is north
         * - 90 is east
         * - 180 is south
         * - 270 is west
         */
        USER_CENTRIC,

        /**
         * Denotes this angle to be in trigonometric ("standard position" / "cartesian") system where:
         * - 0 is east
         * - 90 is north
         * - 180 is west
         * - 270 is south
         */
        TRIGONOMETRIC
    }

    /**
     * Method for converting caller's user-intuitive ("Azimuth" / "Bearing") expected system of
     * 0 degrees being north,
     * 90 degrees being east,
     * 180 degrees being south
     * 270 degrees being west
     * where rotations increase **clockwise**
     *
     * into actual mathematically correct *actual* trigonometric ("Standard Position" / "Cartesian") angle coordinate system which is
     * east being 0 degrees,
     * north being 90 degrees
     * west being 180 degrees
     * south being 270 degrees
     * where rotations increase **couter-clockwise**
     *
     * Essentially remapping the upper-right Q1, lower-right Q2, lower-left Q3, upper-left Q4 into actual
     * upper-right Q1, upper-left Q2, lower-left Q3, lower-right Q4
     *
     * @param degrees the user-intuitive degree based in north being 0-degrees, east being 90-degrees, south being 180-degrees system
     *
     * @return the actual trigonometric correct degree based in east being 0-degrees, north being 90-degrees, west being 180-degrees system
     */
    fun remapAngleToTrigonometricCoordinateSystem(degrees: Float): Float {
        // Convert caller's "north=0" system into trig's "east=0" system
        return (90f - degrees + 360f) % 360f
    }

    /**
     * Normalizes an angular difference to the canonical range [-π, π].
     *
     * This function ensures that any raw angular delta (which may be outside
     * the standard interval) is wrapped to represent the shortest signed
     * rotation between two angles. Positive results indicate a counterclockwise
     * difference, while negative results indicate a clockwise difference.
     *
     * Internally, it uses the identity atan2(sin(Δθ), cos(Δθ)) to guarantee
     * the result lies within [-π, π].
     *
     * Example:
     * ```
     * normalizeAngularDelta(Math.PI + 0.1)   // ≈ -3.04159 (just under -π)
     * normalizeAngularDelta(-4.0)            // ≈ 2.28319 (wrapped into [-π, π])
     * ```
     *
     * Use this method for **relative rotations** where the "shortest path" to a target is required.
     * If you need an **absolute rotation**, use [normalizeRawAngularDelta]
     *
     *
     * This method is suitable to use for "turn X degrees" situations, when we need to rotate (displace) a certain amount
     * of degrees to reach some state; e.g. if we're facing North (0-degrees) and want to face West (270-degrees) we have
     * two choices - turn 270 degrees (clockwise), or turn -90 degrees (counter-clockwise) - this method returns the
     * **shortest signed path (angular delta)** to achieve a desired state/rotation, in radians
     *
     * @param dTheta The raw angular difference in radians (may be outside [-π, π]).
     * @return The normalized angular difference in radians, guaranteed to be within [-π, π].
     */
    fun normalizeAngularDelta(dTheta: Double): Double {
        // returns in [-π, π]
        return Math.atan2(Math.sin(dTheta), Math.cos(dTheta))
    }

    /**
     * Normalizes the angular difference between two angles to the range [0, 2π).
     *
     * This method calculates the angular distance from thetaInner to thetaOuter
     * in a strictly counterclockwise direction, ensuring the result is always
     * non-negative. It is typically used for absolute orientations or
     * compass-style headings where negative values are not desired.
     *
     *
     * Example:
     * ```
     * normalizeRawAngularDelta(0.0, 0.5 * Math.PI) // ≈ 1.5π (wrapped positively)
     * normalizeRawAngularDelta(0.5 * Math.PI, 0.0) // ≈ 0.5π
     * ```
     *
     * Use this method for **absolute rotations** where a consistent, positive representation of a heading is required.
     * If you need an **relative rotation**, use [normalizeAngularDelta].
     *
     *
     * This method is suitable to use for "face/facing X direction" situations, when we need to face (or are facing)
     * an absolute orientation, represented as an absolute "compass" value.
     *
     * @param thetaOuter The target or outer angle in radians.
     * @param thetaInner The reference or inner angle in radians.
     * @return The normalized difference in radians within the interval [0, 2π).
     */
    fun normalizeRawAngularDelta(thetaOuter: Double, thetaInner: Double): Double {
        val twoPi = (2 * Math.PI)
        var delta = thetaOuter - thetaInner
        // normalize into [0, 2π) instead of (–π, π]
        delta = (delta % twoPi + twoPi) % twoPi
        return delta
    }


    /**
     * Generates a list of sampled points forming a "swirl" curve between two [Vector2f] points.
     *
     * Depending on [workMode] the curve is either:
     * - **BEZIER**: A chord-based cubic Bézier curve between [pInner] and [pOuter], with control points rotated by [bezierBendAngleRadians] to
     * cause curvature.
     * - **LOGARITHMIC**: A logarithmic spiral segment between [pInner] and [pOuter], computed in polar coordinates using `r = a * e^(bθ)`.
     *
     *
     * @param pInner the starting point of the curve (innermost ring point)
     * @param pOuter the ending point of the curve (outer ring point)
     * @param particleSegments number of segments to sample along the curve; higher values produce smoother curves but more points
     * @param center the actual 'center' to pivot around; if points are already relative to some center, leave as null - depending on this rotations will either be done "in place" or "around pivot", unused when [workMode] is [SwirlGenerationWorkMode.BEZIER]
     * @param bezierTightnessConstant the optional "tightness constant"" for bezier control points to control swirl intensity, unused if [workMode] isn't [SwirlGenerationWorkMode.BEZIER].
     * Lesser is "flatter", higher is more "loopy" or "circular". **Defaults to 1/3f**
     * @param bezierBendAngleRadians the optional "bend angle" for bezier curves, unused if [workMode] isn't [SwirlGenerationWorkMode.BEZIER]
     * Lesser is "flatter", higher is more "loopy" or "circular". Try to stick in the [0.3, 0.6] range. **Defaults to 0.45f**
     * @param bezierAdaptiveTightnessDampeningFactor the optional "bezier tightness dampening factor" for bezier chords/handles, unused if [workMode] isn't [SwirlGenerationWorkMode.BEZIER]
     * The idea with this one is that the longer the handle gets, the smaller the fraction of [bezierTightnessConstant] will be applied to it.
     * This is used so that longer chords do not end up looking "hilly"/"humpy" and become 'flatter' due to using a smaller tightness constant.
     * **Defaults to 0.00007f**
     * @param workMode whether to use bezier curving or logarithmic curving. See [SwirlGenerationWorkMode]
     *
     * @return a list of points along the curve
     */
    //TODO add support for "anchored swirl" capability in here by having an anchor: Optional<CombatEntityAPI>
    // and then decide whether we generate a "fixed swirl" or "anchored swirl" depending on if it's empty or not
    fun generateSwirlPoints(
        pInner: Vector2f,
        pOuter: Vector2f,
        particleSegments: Int,
        center: Vector2f? = null,
        bezierTightnessConstant: Float = 1/3f,
        bezierBendAngleRadians: Float = 0.45f,
        bezierAdaptiveTightnessDampeningFactor: Float = 0.00007f,
        workMode: SwirlGenerationWorkMode,
    ): List<Vector2f> {
        val points = mutableListOf<Vector2f>()

        when (workMode) {
            SwirlGenerationWorkMode.BEZIER -> {
                //TODO should I plug the center back in here ?

                // 1. Basic vectors
                val chordX = pOuter.x - pInner.x
                val chordY = pOuter.y - pInner.y
                val chordLen = sqrt(chordX * chordX + chordY * chordY)

                // 2. The "Swirl Direction"
                // Instead of polar math, we find the direction from Inner to Outer
                // and "bend" it.
                val dirX = chordX / chordLen
                val dirY = chordY / chordLen

                // 3. The "Bend" (The secret sauce)
                // To turn a straight line into a swirl, we rotate the direction vector
                // by a small amount. 0.3 to 0.5 radians is usually perfect.
                val cosB = cos(bezierBendAngleRadians)
                val sinB = sin(bezierBendAngleRadians)

                // Rotate the direction to get the handle directions
                val ctrlDir1X = dirX * cosB - dirY * sinB
                val ctrlDir1Y = dirX * sinB + dirY * cosB

                val ctrlDir2X = dirX * cosB + dirY * sinB
                val ctrlDir2Y = -dirX * sinB + dirY * cosB

                // 4. Handle Length with Adaptive Tightness
                // We use a small dampening factor (0.001) so it really kicks in on those long outer segments.
                // Example:
                // With factor of 0.00007:
                // - chord of length 100 will use (1 / (1 + 100 * 0.00007)) ≈ 0.993 factor of original tightness (~0.331)
                // - chord of length 1000 will use (1 / 1 + 1000 * 0.00007) ≈ 0.934 factor of original tightness (~0.311)
                // - chord of length 2500 will use (1 / 1 + 2500 * 0.00007) ≈ 0.851 factor of original tightness (~0.283)
                // As a reminder: the lesser the 'tightness constant', the flatter the line will be
                val adaptiveTightness = bezierTightnessConstant * (1.0f / (1.0f + chordLen * bezierAdaptiveTightnessDampeningFactor))
                val hLen = chordLen * adaptiveTightness

                val c1 = Vector2f(pInner.x + ctrlDir1X * hLen, pInner.y + ctrlDir1Y * hLen)
                val c2 = Vector2f(pOuter.x - ctrlDir2X * hLen, pOuter.y - ctrlDir2Y * hLen)

                // 5. Cubic Bezier sampling using formula: (1-t)^3*P0 + 3(1-t)^2*t*P1 + 3(1-t)*t^2*P2 + t^3*P3
                for (i in 0..particleSegments) {
                    val t = i.toFloat() / particleSegments
                    val invT = 1f - t

                    val x = invT.pow(3) * pInner.x +
                        3 * invT.pow(2) * t * c1.x +
                        3 * invT * t.pow(2) * c2.x +
                        t.pow(3) * pOuter.x

                    val y = invT.pow(3) * pInner.y +
                        3 * invT.pow(2) * t * c1.y +
                        3 * invT * t.pow(2) * c2.y +
                        t.pow(3) * pOuter.y

                    points.add(Vector2f(x, y))
                }
            }
            SwirlGenerationWorkMode.LOGARITHMIC -> {
                // Since center won't be 0,0 we need to translate to be relative to actual center
                // HOWEVER since most of the time the center of the swirl will be in the swirl's center and that pInner and pOuter
                // are already relative to the (swirl's) center,

                // If center is non-null, use it's coords, otherwise fallback to 0
                val cx = center?.let { it.x } ?: 0f
                val cy = center?.let { it.y } ?: 0f

                // And now keep on moving regardless whether we're pivoting around (0,0) or some (x,y)
                val dxInner = pInner.x - cx
                val dyInner = pInner.y - cy
                val rInner = sqrt(dxInner * dxInner + dyInner * dyInner)
                val thetaInner = FastTrigUtils.atan2(dyInner, dxInner)

                val dxOuter = pOuter.x - cx
                val dyOuter = pOuter.y - cy
                val rOuter = sqrt(dxOuter * dxOuter + dyOuter * dyOuter)
                val thetaOuter = FastTrigUtils.atan2(dyOuter, dxOuter)

                // Spiral parameters: r = a * e^(bθ)
                val a = rInner
                // Calculate and normalize the thetaOuter - thetaInner to be in [-pi, pi] range
                val dTheta = normalizeAngularDelta(thetaOuter - thetaInner)

                val b = ln(rOuter / rInner) / dTheta

                for (i in 0..particleSegments) {
                    val t = i.toFloat() / particleSegments
                    val theta = thetaInner + t * dTheta
                    val r = a * exp(b * (theta - thetaInner))
                    val x = r * FastTrigUtils.cos(theta)
                    val y = r * FastTrigUtils.sin(theta)
                    // In case we translated the center of the coord system at the start, we should translate back
                    val xTrans = x + cx
                    val yTrans = y + cy
                    points.add(Vector2f(xTrans.toFloat(), yTrans.toFloat()))
                }
            }
        }.exhaustive

        return points
    }

    /**
     * Enum class describing how to generate the swirl points.
     *
     * @see BEZIER
     * @see LOGARITHMIC
     */
    enum class SwirlGenerationWorkMode {
        /**
         * Use a chord-based Bezier curve rotated with some angle around points
         */
        BEZIER,

        /**
         * Use a natural logarithm curve
         */
        LOGARITHMIC
    }

    /**
     * Enum describing how particles should be drawn when using [Swirl.drawParticles] and [Swirl.SwirlArmParticles.draw]
     *
     * @see ONE_AT_A_TIME
     * @see WHOLE_ARM
     */
    enum class ParticleDrawMode {
        /**
         * Enum signifying the "one dot at a time" mode, which draws one dot and removes it from the arm
         */
        ONE_AT_A_TIME,

        /**
         * Enum signifying the "whole arm at a time" mode, which draws the whole arm and removes a number of dots from it
         */
        WHOLE_ARM
    }

    /**
     * Enum class describing the "continuous drain" and how many particles should be pre-drained before drawing
     */
    enum class ContinuousDrainMode {
        /**
         * Pre-drain based on drawing iteration, first draw iteration pre-draining 0, second draw-iteration pre-draining 1 and so on...
         */
        ITERATION_BASED_MODE,

        /**
         * Pre-drain based on previous arm's post-draw size, so that the next arm's draw state begins from the size of the last arm's drawing state
         */
        //TODO test this out
        PREVIOUS_ARM_SIZE_MODE
    }

    //TODO right now, the Swirl is really just a fixed-location "instantaneous" swirl
    // Add capability for anchored swirls, which will store "displacement from anchor" rather than absolute points in worldspace
    // which will then just calculate their *real* location by adding themselves to the anchor
    data class Swirl(
        private val rings: List<List<Vector2f>>,
        private val swirlType: SwirlType,
        private val generateParticles: Boolean,
        private val particleSegments: Int,
        private val particleGenerationWorkMode: SwirlGenerationWorkMode,
        private val particleGenerationCenter: Vector2f,
        private val particleDrawInterval: Float
    ) {
        private val particlePoints: List<SwirlArmParticles>
        private val intervalUtil: MultiIntervalUtil
        private var drawIterations: Int = 0
        private var previousArmPostDrawIndex: Int = 0

        // Used only when drawing in WHOLE_ARM mode
        private var lastDrawnArm: Int = 0

        init {
            particlePoints = if (generateParticles) {
                // If we should generate particles, we need to iterate through all rings, generate particles
                // and then return that list of SwirlArmParticles
                val center = particleGenerationCenter
                val numPoints = rings[0].size
                // prepare SwirlArm list
                val swirlArms = mutableListOf<SwirlArmParticles>()
                // Start iterating through rings and their points ...
                for (index in 0 until numPoints) {
                    // Add swirl arm
                    swirlArms.add(
                        SwirlArmParticles()
                    )
                    // Move on to further generate the whole arm
                    for (ring in 0 until rings.size - 1) {
                        val p1 = rings[ring][index]
                        val p2 = rings[ring + 1][index]

                        // Determine the 'inner' and 'outer' points, or whether p1/p2 is inner or outer
                        val (inner, outer) = when (swirlType) {
                            SwirlType.INWARD -> {
                                // For inward swirls, the biggest index is closest to center
                                // p1 is 'ring', p2 is 'ring+1'
                                p2 to p1
                            }

                            SwirlType.OUTWARD -> {
                                // For outward swirls, the smallest index is closest to center
                                // p1 is 'ring', p2 is 'ring+1'
                                p1 to p2
                            }
                        }.exhaustive
                        val swirlPoints = generateSwirlPoints(
                            pInner = inner,
                            pOuter = outer,
                            particleSegments = particleSegments,
                            // we should not pivot around the center again since all points are already relative to the ship-location
                            // even if they are in absolute world-location units - at least for bezier at least
                            center = when (particleGenerationWorkMode) {
                                SwirlGenerationWorkMode.BEZIER -> null
                                SwirlGenerationWorkMode.LOGARITHMIC -> center
                            }.exhaustive,
                            bezierBendAngleRadians = 0.35f,
                            workMode = particleGenerationWorkMode
                        )
                        // And add them to the arm
                        swirlArms[index].addMorePoints(swirlPoints)
                    }
                }

                // And finally, return the list of SwirlArms
                swirlArms.toList()
            } else {
                // If we should not generate particles, just return an empty list
                emptyList()
            }

            // After generating the particles, instantiate the intervalUtil
            intervalUtil = MultiIntervalUtil(particleDrawInterval)
        }

        /**
         * Enum class denoting whether the swirl is an inward swirl (first ring is outermost) or an outward swirl (first ring is innermost)
         *
         * @see INWARD
         * @see OUTWARD
         */
        enum class SwirlType {
            /**
             * Enum value denoting that this is an INWARD swirl, meaning that it's first ring is the outermost ring
             */
            INWARD,

            /**
             * Enum value denoting that this is an OUTWARD swirl, meaning that it's first ring is the innermost ring
             */
            OUTWARD
        }

        /**
         * Draws the swirl with full control over visuals. This just draws out EMP arcs
         *
         * @param ship The ship entity (needed for arc visuals).
         * @param arcThickness Thickness of EMP arcs.
         * @param arcColors List of (coreColor, fringeColor) per stage connection. Defaults to [Color.WHITE] core and [Color.CYAN] fringe
         */
        fun draw(
            ship: ShipAPI,
            arcThickness: Float = 6f,
            arcColors: List<Pair<Color, Color>> = emptyList(),
            connectToCenter: Boolean = true,
        ) {
            val engine = Global.getCombatEngine()
            if (rings.size < 2) return

            val numPoints = rings[0].size

            for (index in 0 until numPoints) {
                // We cannot use "in rings.indices" here because then we will hit an OOB when p2 tries to access ring+1
                for (ring in 0 until rings.size - 1) {
                    val p1 = rings[ring][index]
                    val p2 = rings[ring + 1][index]

                    // EMP arc visual
                    val (core, fringe) = if (arcColors.isNotEmpty() && ring < arcColors.size) {
                        arcColors[ring]
                    } else {
                        Color.CYAN to Color.WHITE
                    }

                    EngineParticlePainter.spawnEmpArc(
                        engine = engine,
                        arcType = EmpArcType.VISUAL,
                        arcParams = EmpArcParams.VisualArc(
                            from = p1,
                            fromAnchor = ship,
                            to = p2,
                            toAnchor = ship,
                            thickness = arcThickness,
                            fringe = fringe,
                            core = core
                        )
                    )
                }

                // After messing with the ring loop, optionally connect innermost ring to center
                if (connectToCenter) {
                    // Figure out the colors and which point to connect to the center (ship.location)
                    val (core, fringe) = arcColors.last()
                    val innermost = when(swirlType) {
                        SwirlType.INWARD -> rings.last()[index]
                        SwirlType.OUTWARD -> rings.first()[index]
                    }.exhaustive
                    // Draw the emp visual

                    EngineParticlePainter.spawnEmpArc(
                        engine = engine,
                        arcType = EmpArcType.VISUAL,
                        arcParams = EmpArcParams.VisualArc(
                            from = innermost,
                            fromAnchor = ship,
                            to = ship.location,
                            toAnchor = ship,
                            thickness = arcThickness,
                            fringe = fringe,
                            core = core
                        )
                    )
                }
            }
        }

        /**
         * Method that draws particles over time, and should be called repeatedly until all arms are finished.
         *
         * @param amount the amount of time that has passed, used to feed into [intervalUtil]
         * @param particleSize the particle size
         * @param particleDuration the lifetime of each particle
         * @param particlesToDrawPerInterval depending on the [particleDrawMode] it will either:
         * - For [ParticleDrawMode.ONE_AT_A_TIME] it will call [SwirlArmParticles.draw] this many times
         * - For [ParticleDrawMode.WHOLE_ARM] it will call [SwirlArmParticles.draw] followed by [SwirlArmParticles.removePoints] with this number
         * @param particleColors a **non-empty** list of [Color]s to use for the particles depending on which segment of the arm they belong to.
         * Needs at least one color, **list cannot be empty**
         * @param particleDrawMode the [ParticleDrawMode] to use
         * @param particleArmsToDraw how many swirl arms to draw, relevant only when [particleDrawMode] is [ParticleDrawMode.WHOLE_ARM]
         * @param continuousDrain optional parameter determining whether to pre-drain arms before drawing. See [ContinuousDrainMode]
         */
        fun drawParticles(
            amount: Float,
            particleSize: Float = 12f,
            particleDuration: Float = 0.25f,
            particlesToDrawPerInterval: Int = 1,
            particleColors: List<Color>,
            particleDrawMode: ParticleDrawMode,
            particleArmsToDraw: Int,
            continuousDrain: ContinuousDrainMode?
        ) {
            // Validate color list
            if (particleColors.isEmpty()) throw IllegalArgumentException("particleColors list must NOT be empty")
            // Drawing particles is rather simple. Feed the amount into the interval util, if amount has passed -
            // call draw on each SwirlArmParticles instance. They will automatically remove the drawn point.
            intervalUtil.advance(amount)
            intervalUtil.onIntervalElapsed {
                // Depending on the particle draw mode, we will either:
                // draw 'particlesToDrawPerInterval' points from each arm at the same time
                // draw whole arms one at a time and remove points from them until they all drain out
                when(particleDrawMode) {
                    ParticleDrawMode.ONE_AT_A_TIME -> {
                        particlePoints.forEach { swirlArm ->
                            // Pre-drain if we should
                            continuousDrain?.let {
                                when(it) {
                                    ContinuousDrainMode.ITERATION_BASED_MODE -> {
                                        swirlArm.removePoints(drawIterations)
                                    }
                                    ContinuousDrainMode.PREVIOUS_ARM_SIZE_MODE -> {
                                        swirlArm.removePoints(previousArmPostDrawIndex)
                                    }
                                }.exhaustive
                            }

                            // If we should draw more particles, do so
                            repeat(particlesToDrawPerInterval) {
                                // decode color based on where we are in the list
                                // Each 'particleSegments' number of items should belong to the same color,
                                // after which we switch to the next color index
                                val colorIndex = swirlArm.getCurrentPointIndex() / particleSegments
                                val color = if (colorIndex < particleColors.size) {
                                    particleColors[colorIndex]
                                } else {
                                    particleColors.last()
                                }

                                // And finally draw the arm
                                swirlArm.draw(
                                    particleSize = particleSize,
                                    particleDuration = particleDuration,
                                    particleColors = listOf(color),
                                    particleDrawMode = particleDrawMode
                                )

                                // It makes sense to do this in the 'repeat' after drawing, because if we drew only one point
                                // we want the next arm to pre-drain one point;
                                // If we drew 5 points, we want the next arm to pre-drain 5 points before drawing as well.
                                drawIterations++

                                // And return nothing
                                Unit
                            }
                            previousArmPostDrawIndex = swirlArm.getCurrentPointIndex()
                        }
                    }

                    ParticleDrawMode.WHOLE_ARM -> {
                        // For whole arm, we do not need to decode the color to use, the arm will do that itself.
                        // Idea is, we will keep on drawing arms one at a time, or many at a time depending on particleArmsToDraw
                        val stride = ceil(
                            particlePoints.size.toDouble() / particleArmsToDraw
                        ).toInt()

                        for (i in 0 until particleArmsToDraw) {
                            // calculate proper index, deducting so it goes in the right direction but i guess it doesn't matter
                            val armIndex = wrapAroundMod(lastDrawnArm - i * stride, particlePoints.size)

                            // Pre-drain if we should (will drain once on a per-arm basis)
                            continuousDrain?.let {
                                when(it) {
                                    ContinuousDrainMode.ITERATION_BASED_MODE -> {
                                        particlePoints[armIndex].removePoints(drawIterations)
                                    }
                                    ContinuousDrainMode.PREVIOUS_ARM_SIZE_MODE -> {
                                        particlePoints[armIndex].removePoints(previousArmPostDrawIndex)
                                    }
                                }.exhaustive
                            }

                            // draw the arm
                            particlePoints[armIndex].draw(
                                particleSize = particleSize,
                                particleDuration = particleDuration,
                                particleColors = particleColors,
                                particleDrawMode = particleDrawMode
                            )
                            // deduct points from that arm
                            particlePoints[armIndex].removePoints(particlesToDrawPerInterval)
                        }

                        // now, decrement lastDrawnArm for next iteration
                        lastDrawnArm = wrapAroundMod(lastDrawnArm - 1, particlePoints.size)
                        // And bump the drawIterations and previousArmPostDrawIndex
                        drawIterations++
                        previousArmPostDrawIndex = particlePoints[lastDrawnArm].getCurrentPointIndex()

                        // And return nothing
                        Unit
                    }
                }.exhaustive
            }
        }

        fun debugParticles() {
            // Might as well let this method sit here I suppose
//            setSmoothParticleLimit(newLimit = 4000)
            (Global.getCombatEngine() as CombatEngine).smoothParticles.limit = 64000
            for (swirlArm in particlePoints) {
                swirlArm.draw(
                    particleSize = 64f,
                    particleDuration = 5f,
                    particleColors = listOf(
                        Color.WHITE.brighter().brighter(),
                        Color.WHITE,
                        Color.LIGHT_GRAY.brighter().brighter(),
                        Color.LIGHT_GRAY,
                        Color.DARK_GRAY,
                        Color.DARK_GRAY.darker().darker()
                    ),
                    particleDrawMode = CircleUtils.ParticleDrawMode.WHOLE_ARM
                )
            }
        }

        /**
         * Method for checking whether all of this [Swirl]'s particle arms ([SwirlArmParticles]) have finished or not
         *
         * @return whether all particle arms have finished or not
         */
        fun hasFinished(): Boolean {
            return particlePoints.all { it.hasFinished() }
        }

        inner class SwirlArmParticles(
            private val particlePoints: MutableList<Vector2f> = mutableListOf()
        ) {
            private var armSize: Int = 0
            private var isFinished = false

            /**
             * Draws the arm depending on the [particleDrawMode]
             *
             * For [ParticleDrawMode.ONE_AT_A_TIME], it draws the first point in the list and then removes it from the list of points
             * For [ParticleDrawMode.WHOLE_ARM], it draws the whole arm and **DOESN'T** remove any points from it.
             * **NOTE:** In this mode, the points have to be externally removed, because otherwise the arm will **NEVER** finish.
             *
             * @param particleSize the particle size to use. Defaults to 12f.
             * @param particleDuration the lifetime of a single particle, in seconds. Defaults to 0.25f
             * @param particleColors depending on [particleDrawMode], it should either be a one-element list for [ParticleDrawMode.ONE_AT_A_TIME]
             * or a list of colors for [ParticleDrawMode.WHOLE_ARM]
             * @param particleBrightness particle brightness to use. Defaults to 1f
             * @param particleDrawMode the mode in which to draw particles. See [ParticleDrawMode]
             */
            fun draw(
                particleSize: Float = 12f,
                particleDuration: Float = 0.25f,
                particleColors: List<Color>,
                particleBrightness: Float = 1f,
                particleDrawMode: ParticleDrawMode
            ) {
                if (particlePoints.isNotEmpty()) {
                    when(particleDrawMode) {
                        ParticleDrawMode.ONE_AT_A_TIME -> {
                            val point = particlePoints.removeAt(0)
                            val engine = Global.getCombatEngine()
                            val particleColor = particleColors.first()
                            EngineParticlePainter.addParticle(
                                engine = engine,
                                particleType = ParticleType.SMOOTH_PARTICLE,
                                particleParams = ParticleParams.Smooth.Basic(
                                    location = point,
                                    velocity = Vector2f(0f, 0f),
                                    size = particleSize,
                                    brightness = particleBrightness,
                                    duration = particleDuration,
                                    color = particleColor
                                )
                            )
                        }
                        ParticleDrawMode.WHOLE_ARM -> {
                            // Fetch the original smooth particle limit
                            val engine = Global.getCombatEngine()
                            for (point in particlePoints) {
                                val colorIndex = getCurrentPointIndex() / particleSegments
                                val color = if (colorIndex < particleColors.size) {
                                    particleColors[colorIndex]
                                } else {
                                    particleColors.last()
                                }

                                EngineParticlePainter.addParticle(
                                    engine = engine,
                                    particleType = ParticleType.SMOOTH_PARTICLE,
                                    particleParams = ParticleParams.Smooth.Basic(
                                        location = point,
                                        velocity = Vector2f(0f, 0f),
                                        size = particleSize,
                                        brightness = particleBrightness,
                                        duration = particleDuration,
                                        color = color
                                    )
                                )
                            }
                        }
                    }.exhaustive
                } else {
                    isFinished = true
                }
            }

            // adding more points should just add them
            fun addMorePoints(points: List<Vector2f>) {
                particlePoints.addAll(points)
                armSize += points.size
            }

            /**
             * Method for returning the "arm size". This is **not** the number of points remaining in the arm.
             * This is just a hacky representation of how many points the [particlePoints] contained after we added
             * all points to it.
             *
             * @return the initial size of this [SwirlArmParticles] collection before we started draining it via [draw]
             */
            fun getArmSize(): Int {
                return armSize
            }

            /**
             * Gets the current point "index" or rather the difference between [armSize] and size of [particlePoints]
             *
             * Better call this before [draw]ing because drawing will mutate it.
             *
             * @return the "next drawing point" index
             */
            fun getCurrentPointIndex(): Int {
                return getArmSize() - particlePoints.size
            }

            /**
             * Method that checks whether this particle arm has finished or not.
             * A particle arm is considered "finished" when it has exhausted all of it's points
             *
             * @return whether this arm has finished or not
             */
            fun hasFinished(): Boolean {
                return isFinished
            }

            /**
             * Method that removes [pointsToRemove] first points from this arm.
             * If the method tries to remove more points than we have in [particlePoints] it will remove as many
             * as it can and then do nothing.
             */
            fun removePoints(pointsToRemove: Int) {
                repeat(pointsToRemove) {
                    if (particlePoints.isNotEmpty()) {
                        particlePoints.removeAt(0)
                    }
                }
            }
        }
    }


    /**
     * Method for generating a [ConcentricCircles]
     *
     * @param center the center of all concentric circles.
     * @param radii a list of growing (or shrinking) radiuses to use for circles. See [generateInwards]
     * @param pointsPerRing how many points per ring should there be. Angle between the points is very dependant on this.
     * @param generateInwards whether the circles should generate "inwards" (shrinking circles) or "outwards" (expanding circles).
     * This should align with how you generated the [radii] list. There are no checks for growing/shrinking lists for appropriate types.
     * @param globalRotationDegrees the "global" rotation degrees of the circle, defaults to 0.
     * @param generateParticles whether particles should also be generated or not, defaults to [false].
     * @param particleDrawInterval the default interval used for the [ConcentricCircles.intervalUtil]. Defaults to 0.1
     * @param particleDrawDuration how long should the whole particle "drawing" of all of these concentric circles last, in seconds. Defaults to 1
     * This parameter is also in charge of "how fast should particles move between rings" as well as the lifetime of each particle.
     * **NOTE**: particleDrawDuration of 1 for 5 rings will end up being 0.25 for each particle due to internal calculations. See [ConcentricCircles.particleDrawingDuration]
     */
    //TODO add support for "anchored concentric circles" capability in here by having an anchor: Optional<CombatEntityAPI>
    // and then decide whether we generate a "fixed concentric circles" or "anchored concentric circles" depending on if it's empty or not
    fun generateConcentricCircles(
        center: Vector2f,
        radii: List<Float>,
        pointsPerRing: Int,
        generateInwards: Boolean = false,
        globalRotationDegrees: Float = 0f,
        generateParticles: Boolean = false,
        particleDrawInterval: Float = 0.1f,
        particleDrawDuration: Float = 1f,
        particlesUseDelay: Boolean = false
    ): ConcentricCircles {
        val circles = mutableListOf<List<Vector2f>>()
        val angleStepDegrees = 360.0 / pointsPerRing

        for (radius in radii) {
            val ringPoints = mutableListOf<Vector2f>()
            for (i in 0 until pointsPerRing) {
                val angleDegrees = (angleStepDegrees * i) + globalRotationDegrees
                val angleRadians = Math.toRadians(angleDegrees)
                val x = center.x + radius * cos(angleRadians).toFloat()
                val y = center.y + radius * sin(angleRadians).toFloat()
                ringPoints.add(Vector2f(x, y))
            }
            circles.add(ringPoints)
        }

        val dotPairs = circles
            .map { dotList ->
                val midSize = dotList.size / 2

                pairOf(dotList.take(midSize), dotList.drop(midSize))
            }

        return ConcentricCircles(
            rings = dotPairs,
            generateInwards = generateInwards,
            globalRotationDegrees = globalRotationDegrees,
            generateParticles = generateParticles,
            particleDrawInterval = particleDrawInterval,
            particleDrawingDuration = particleDrawDuration,
            particleRingsUseDelay = particlesUseDelay
        )
    }

    /**
     * Container for concentric circle geometry used for visual effects.
     *
     * Holds an arbitrary number of rings (each a pair of left/right point lists),
     * and provides draw methods for EMP arcs (debug) and particles (production).
     */
    data class ConcentricCircles(
        private val rings: List<Pair<List<Vector2f>, List<Vector2f>>>,
        private val generateInwards: Boolean,
        private val globalRotationDegrees: Float,
        private val generateParticles: Boolean,
//        private val particleSegments: Int,    //TODO delete
        private val particleDrawInterval: Float,
        private val particleDrawingDuration: Float,
        private val particleRingsUseDelay: Boolean
    ) {
        private val circleParticles: List<CircleParticles>
        private val intervalUtil: MultiIntervalUtil
        @Volatile
        private var particleAccumulator: Float = 0f

        init {
            circleParticles = if (generateParticles) {
                // Lets assume each ring has an equal duration - but only counting "gaps".
                // E.g. five concentric circles only have 4 gaps in between them
                val ringDuration = particleDrawingDuration / (rings.size - 1)

                // Just throw ring[i] to ring[i++] particle
                // We cannot use "in rings.indices" here because then we will hit an OOB when p2 tries to access ring+1
                val mutableCircleList = mutableListOf<CircleParticles>()
                for (ring in 0 until rings.size - 1) {
                    val mutableCircleParticlesList = mutableListOf<CircleParticle>()
                    val p1 = rings[ring]
                    val p2 = rings[ring + 1]

                    val (leftDots1, rightDots1) = p1
                    val concentricCircle1 = leftDots1 + rightDots1
                    val (leftDots2, rightDots2) = p2
                    val concentricCircle2 = leftDots2 + rightDots2

                    // And generate moving particles from cc1 to cc2
                    for (pointIndex in concentricCircle1.indices) {
                        val point1 = concentricCircle1[pointIndex]
                        val point2 = concentricCircle2[pointIndex]

                        val velocityVector = calculateVelocityVector(
                            fromVector = point1,
                            toVector = point2,
                            time = ringDuration
                        )
                        mutableCircleParticlesList.add(
                            CircleParticle(
                                fromVector = point1,
                                toVector = point2,
                                velocityVector = velocityVector
                            )
                        )
                    }
                    // And transform the mutable list into a concrete CircleParticle class
                    val circleParticles = CircleParticles(
                        particlePoints = mutableCircleParticlesList,
                        // While this might seem odd, the real explanation is this:
                        // If we have 4 rings which should last 1 second total, and each ring lasts 0.25sec
                        // the first ring should have 0 delay, the second ring should have 0.25sec delay,
                        // third should have 0.5sec and fourth should have 0.75sec - so using the ring index checks out.
                        delayInSec = if (particleRingsUseDelay) { ring * ringDuration } else { 0f }
                    )

                    mutableCircleList.add(circleParticles)
                }

                // And after we have generated all CircleParticle instances, return an immutable list of them
                mutableCircleList.toList()
            } else {
                // If no particles should be generated, return empty list
                emptyList<CircleParticles>()
            }
            intervalUtil = MultiIntervalUtil(particleDrawInterval)
        }


        fun draw(ship: ShipAPI) {
            TODO("Implement EMP arc drawing based on rings")
        }


        fun drawParticles(
            amount: Float,
            particleSizeList: List<Float> = listOf(12f),
            particleDuration: Float = particleDrawingDuration / (rings.size - 1),
            particleColors: List<Color>,
            particleBrightness: Float = 1f
        ) {
            // Bump the accumulator, the intervalUtil and draw if anything is drawable
            particleAccumulator += amount
            intervalUtil.advance(amount)
            intervalUtil.onIntervalElapsed {
                // Filter circles that are not finished and that have waited enough
                val drawableCircles = circleParticles.filter {
                    it.hasFinished().not() && it.getDelay() <= particleAccumulator
                }
                // Then, draw them out
                drawableCircles.forEachIndexed { index, circleParticles ->
                    val particleSize = particleSizeList.getOrElse(index) { _ -> 12f }
                    circleParticles.draw(
                        particleSize = particleSize,
                        particleDuration = particleDuration,
                        particleColors = particleColors,
                        particleBrightness = particleBrightness
                    )
                }
            }
        }

        /**
         * Method for checking whether all of this [ConcentricCircles]'s particle circles ([CircleParticles]) have finished or not
         *
         * @return whether all particle circles have finished or not
         */
        fun hasFinished(): Boolean {
            return circleParticles.all { it.hasFinished() }
        }

        inner class CircleParticles(
            private val particlePoints: MutableList<CircleParticle>,
            private val delayInSec: Float
        ) {
            private var isFinished = false

            fun getDelay(): Float = delayInSec

            /**
             * Draws the circle
             *
             * @param particleSize the particle size to use. Defaults to 12f.
             * @param particleDuration the lifetime of a single particle, in seconds. Defaults to 0.25f
             * @param particleColors a one-element list of colors
             * @param particleBrightness particle brightness to use. Defaults to 1f
             */
            fun draw(
                particleSize: Float = 12f,
                particleDuration: Float = 0.25f,
                particleColors: List<Color>,
                particleBrightness: Float = 1f,
            ) {
                if (particlePoints.isNotEmpty()) {
                    val engine = Global.getCombatEngine()
                    for (point in particlePoints) {
                        val color = particleColors.last()

                        EngineParticlePainter.addParticle(
                            engine = engine,
                            particleType = ParticleType.SMOOTH_PARTICLE,
                            particleParams = ParticleParams.Smooth.Basic(
                                location = point.fromVector,
                                velocity = point.velocityVector,
                                size = particleSize,
                                brightness = particleBrightness,
                                duration = particleDuration,
                                color = color
                            )
                        )
                    }
                    // And drain all particle elements from this CircleParticles instance
                    particlePoints.clear()
                } else {
                    isFinished = true
                }
            }

            /**
             * Method that checks whether this particle arm has finished or not.
             * A particle arm is considered "finished" when it has exhausted all of it's points
             *
             * @return whether this arm has finished or not
             */
            fun hasFinished(): Boolean {
                return isFinished
            }

            /**
             * Method that removes [pointsToRemove] first points from this arm.
             * If the method tries to remove more points than we have in [particlePoints] it will remove as many
             * as it can and then do nothing.
             */
            fun removePoints(pointsToRemove: Int) {
                repeat(pointsToRemove) {
                    if (particlePoints.isNotEmpty()) {
                        particlePoints.removeAt(0)
                    }
                }
            }

        }
        data class CircleParticle(
            val fromVector: Vector2f,
            val toVector: Vector2f,
            val velocityVector: Vector2f
        )
    }

}
