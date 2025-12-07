import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
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
    fun generateSwirl(
        center: Vector2f,
        rings: Int,
        pointsPerRing: Int,
        minRadius: Float,
        maxRadius: Float,
        generateInwards: Boolean = false,
        ringRotationsDegrees: List<Float>,
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
        val remappedRingRotations = ringRotationsDegrees.map {userCentricAngle ->
            remapAngleToGeomericCoordinateSystem(userCentricAngle)
        }

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
     * Method for converting caller's user-intuitive expected system of
     * 0 degrees being north,
     * 90 degrees being east,
     * 180 degrees being south
     * 270 degrees being west
     *
     * into actual mathematically correct *actual* geometric angle coordinate system which is
     * east being 0 degrees,
     * north being 90 degrees
     * west being 180 degrees
     * south being 270 degrees.
     *
     * Essentially remapping the upper-right Q1, lower-right Q2, lower-left Q3, upper-left Q4 into actual
     * upper-right Q1, upper-left Q2, lower-left Q3, lower-right Q4
     *
     * @param degrees the user-intuitive degree based in north being 0-degrees, east being 90-degrees, south being 180-degrees system
     *
     * @return the actual geometrically correct degree based in east being 0-degrees, north being 90-degrees, west being 180-degrees system
     */
    fun remapAngleToGeomericCoordinateSystem(degrees: Float): Float {
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
     * @param dTheta The raw angular difference in radians (may be outside [-π, π]).
     * @return The normalized angular difference in radians, guaranteed to be within [-π, π].
     *
     * Example:
     * ```
     * normalizeAngularDelta(Math.PI + 0.1)   // ≈ -3.04159 (just under -π)
     * normalizeAngularDelta(-4.0)            // ≈ 2.28319 (wrapped into [-π, π])
     * ```
     */
    fun normalizeAngularDelta(dTheta: Double): Double {
        // returns in [-π, π]
        return Math.atan2(Math.sin(dTheta), Math.cos(dTheta))
    }

    fun normalizeRawAngularDelta(thetaOuter: Double, thetaInner: Double): Double {
        val twoPi = (2 * Math.PI).toFloat()
        var delta = thetaOuter - thetaInner
        // normalize into [0, 2π) instead of (–π, π]
        delta = (delta % twoPi + twoPi) % twoPi
        return delta
    }



    /**
     * Generates a list of sampled points forming a "swirl" curve between two [Vector2f] points.
     *
     * Depending on [workMode] the curve is either:
     * - **BEZIER**: A cubic Bézier curve between [pInner] and [pOuter], with control points rotated by [bezierAngle] to
     * cause curvature.
     * - **LOGARITHMIC**: A logarithmic spiral segment between [pInner] and [pOuter], computed in polar coordinates using `r = a * e^(bθ)`.
     *
     *
     * @param pInner the starting point of the curve (innermost ring point)
     * @param pOuter the ending point of the curve (outer ring point)
     * @param particleSegments number of segments to sample along the curve; higher values produce smoother curves but more points
     * @param center the actual 'center' to pivot around; if points are already relative to some center, leave as null - depending on this rotations will either be done "in place" or "around pivot"
     * @param bezierAngle the optional angle to use for bezier control points to control swirl intensity, unused if [workMode] isn't [SwirlGenerationWorkMode.BEZIER]. **IF SET TO ZERO** the curve will degenerate to a straight line
     * @param workMode whether to use bezier curving or logarithmic curving. See [SwirlGenerationWorkMode]
     *
     * @return a list of points along the curve
     */
    fun generateSwirlPoints(
        pInner: Vector2f,
        pOuter: Vector2f,
        particleSegments: Int,
        center: Vector2f? = null,
        bezierAngle: Float = 30f,
        workMode: SwirlGenerationWorkMode,
    ): List<Vector2f> {
        val points = mutableListOf<Vector2f>()

        when (workMode) {
            SwirlGenerationWorkMode.BEZIER -> {
                val c1 = Vector2f(
                    pInner.x + (pOuter.x - pInner.x) * 0.25f,
                    pInner.y + (pOuter.y - pInner.y) * 0.25f
                )
                val c2 = Vector2f(
                    pInner.x + (pOuter.x - pInner.x) * 0.75f,
                    pInner.y + (pOuter.y - pInner.y) * 0.75f
                )
                // Tangential offset to induce swirl by rotating control point around center
                // quick and dirty hack but meh
                // oh shit, the center won't necessarily be 0,0, we need to rotate around pivot aaargh
                // UPDATE: ok, since all of these vectors are ship.location relative, we don't need to rotate around pivot...
                val c1Rot: Vector2f
                val c2Rot: Vector2f
//                val c1Rot = c1.rotateAroundPivot(pivotPoint = center, angle = bezierAngle)
//                val c2Rot = c2.rotateAroundPivot(pivotPoint = center, angle = -bezierAngle)
                if (center != null) {
                    c1Rot = c1.rotateAroundPivot(pivotPoint = center, angle = bezierAngle)
                    c2Rot = c2.rotateAroundPivot(pivotPoint = center, angle = -bezierAngle)
                } else {
                    c1Rot = c1.rotate(angle = bezierAngle)
                    c2Rot = c2.rotate(angle = -bezierAngle)
                }

                for (i in 0..particleSegments) {
                    val t = i.toFloat() / particleSegments
                    val x = (1 - t).pow(3) * pInner.x +
                        3 * (1 - t).pow(2) * t * c1Rot.x +
                        3 * (1 - t) * t.pow(2) * c2Rot.x +
                        t.pow(3) * pOuter.x
                    val y = (1 - t).pow(3) * pInner.y +
                        3 * (1 - t).pow(2) * t * c1Rot.y +
                        3 * (1 - t) * t.pow(2) * c2Rot.y +
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
                // Calculate and normalize the thetaOuter - thetaInner
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
         * Use a Bezier curve rotated with some angle around points
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
                            bezierAngle = 30f,
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

                    engine.spawnEmpArcVisual(
                        p1,
                        ship,
                        p2,
                        ship,
                        arcThickness,
                        fringe,
                        core
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
                    engine.spawnEmpArcVisual(
                        innermost,
                        ship,
                        ship.location,
                        ship,
                        arcThickness,
                        fringe,
                        core
                    )
                }
            }

            // Now that we're done with the emp arcs - draw the particles if allowed
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
         */
        fun drawParticles(
            amount: Float,
            particleSize: Float = 12f,
            particleDuration: Float = 0.25f,
            particlesToDrawPerInterval: Int = 1,
            particleColors: List<Color>,
            particleDrawMode: ParticleDrawMode,
            particleArmsToDraw: Int
        ) {
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
                                swirlArm.draw(
                                    particleSize = particleSize,
                                    particleDuration = particleDuration,
                                    particleColors = listOf(color),
                                    particleDrawMode = particleDrawMode
                                )
                            }
                        }
                    }

                    /*
                    ParticleDrawMode.WHOLE_ARM -> {
                        // For whole arm, we do not need to decode the color to use, the arm will do that itself.
                        // Idea is, we will keep on drawing arms one at a time
                        particlePoints[lastDrawnArm].draw(
                            particleSize = particleSize,
                            particleDuration = particleDuration,
                            particleColors = particleColors,
                            particleDrawMode = particleDrawMode
                        )
                        // now remove points from that arm
                        particlePoints[lastDrawnArm].removePoints(particlesToDrawPerInterval)
                        // now bump up the last drawn arm for next iteration, and ensure it is within range
                        lastDrawnArm++
                        lastDrawnArm %= particlePoints.size
                        // Eventually they will drain out ...
                    }
                     */
                    ParticleDrawMode.WHOLE_ARM -> {
                        // For whole arm, we do not need to decode the color to use, the arm will do that itself.
                        // Idea is, we will keep on drawing arms one at a time, or many at a time depending on particleArmsToDraw
                        val stride = ceil(
                            particlePoints.size.toDouble() / particleArmsToDraw
                        ).toInt()

                        for (i in 0 until particleArmsToDraw) {
                            // calculate proper index, deducting so it goes in the right direction but i guess it doesn't matter
                            val armIndex = wrapAroundMod(lastDrawnArm - i * stride, particlePoints.size)
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
                    }
                }.exhaustive
            }
        }

        /**
         * Method for checking whether all of this [Swirl]'s particle arms ([SwirlArmParticles] have finished or not
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
                            // Fetch the original smooth particle limit
//                            val originalLimit = (engine as CombatEngine).smoothParticles.limit
                            // bump limit so they all fit
//                            (engine as CombatEngine).smoothParticles.limit = particlePoints.size
                            val particleColor = particleColors.first()
                            engine.addSmoothParticle(
                                point,
                                Vector2f(0f, 0f),
                                particleSize,
                                particleBrightness,
                                particleDuration,
                                particleColor
                            )
                            // Revert limit after drawing
//                            (engine as CombatEngine).smoothParticles.limit = originalLimit
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

                                engine.addSmoothParticle(
                                    point,
                                    Vector2f(0f, 0f),
                                    particleSize,
                                    particleBrightness,
                                    particleDuration,
                                    color
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

}
