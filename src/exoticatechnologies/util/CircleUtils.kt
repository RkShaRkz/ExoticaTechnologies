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
     */
    fun generateSwirl(
        center: Vector2f,
        rings: Int,
        pointsPerRing: Int,
        minRadius: Float,
        maxRadius: Float,
        generateInwards: Boolean = false,
        ringRotationsDegrees: List<Float>,
        globalRotationDegrees: Float = 0f
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
            }
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
                // While both of these should be in the [-Pi, Pi] range, using them raw like this will collapse
                // the sign and make it think it went a whole circle rather than just a tiny bit.
                // E.g. One point at +179*, other point a bit past -179*, delta will turn out to be -358*. Instead of 2.
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


    class Swirl(
        private val rings: List<List<Vector2f>>,
        private val swirlType: SwirlType
    ) {

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
         * Draws the swirl with full control over visuals.
         *
         * @param ship The ship entity (needed for arc visuals).
         * @param arcThickness Thickness of EMP arcs.
         * @param arcColors List of (coreColor, fringeColor) per stage connection. Defaults to [Color.WHITE] core and [Color.CYAN] fringe
         * @param drawParticles Whether to also draw persistent particle lines.
         * @param particleSize Size of particles for persistent lines.
         * @param particleDuration Lifetime of particles for persistent lines.
         * @param particleSegments Number of particles per line segment.
         */
        fun draw(
            ship: ShipAPI,
            arcThickness: Float = 6f,
            arcColors: List<Pair<Color, Color>> = emptyList(),
            drawParticles: Boolean = false,
            particleSize: Float = 12f,
            particleDuration: Float = 0.25f,
            particleSegments: Int = 16,
            connectToCenter: Boolean = true,
            workMode: SwirlGenerationWorkMode = SwirlGenerationWorkMode.BEZIER
        ) {
            val engine = Global.getCombatEngine()
            if (rings.size < 2) return

            val numPoints = rings[0].size
            //TODO get rid of this stuff below
            AnonymousLogger.log("numPoints: ${numPoints}", "SHARK-drawing")
            for (ringNum in rings.indices) {
                AnonymousLogger.log("rings[${ringNum}].size: ${rings[ringNum].size}", "SHARK-drawing")
            }

            for (index in 0 until numPoints) {
                // We cannot use "in rings.indices" here because then we will hit an OOB when p2 tries to access ring+1
                for (ring in 0 until rings.size - 1) {
//                for (ring in rings.indices) {
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
//                            core,
//                            fringe
                        fringe,
                        core
                    )

                    // Optional persistent line overlay
                    if (drawParticles) {
//                            val dx = (p2.x - p1.x) / particleSegments
//                            val dy = (p2.y - p1.y) / particleSegments
//                            var x = p1.x
//                            var y = p1.y
                        val center = ship.location
                        val swirlPoints = generateSwirlPoints(
                            //TODO this will differ for SwirlType.INWARD and OUTWARD
                            pInner = p1,
                            pOuter = p2,
                            particleSegments = particleSegments,
//                            center = center,
                            // we should not pivot around the center again since all points are already relative to the ship-location
                            // even if they are in absolute world-location units - at least for bezier at least
                            center = when(workMode) {
                                SwirlGenerationWorkMode.BEZIER -> null
                                SwirlGenerationWorkMode.LOGARITHMIC -> center
                            }.exhaustive,
                            bezierAngle = 30f,
                            workMode = workMode
                        )
//                            for (seg in 0 until particleSegments) {
                        AnonymousLogger.log("swirlPoints.size: ${swirlPoints.size}", "SHARK-drawing")
                        for (point in swirlPoints) {
                            engine.addSmoothParticle(
                                point,
                                Vector2f(0f, 0f),
                                particleSize,
                                1f,
                                particleDuration,
//                                    core  //TODO
                                Color.RED
                            )
//                            x += dx
//                            y += dy
                        }
                    }
                }

                // After messing with the ring loop, optionally connect innermost ring to center
                if (connectToCenter) {

                    val (core, fringe) = arcColors.last()

                    val innermost = when(swirlType) {
                        SwirlType.INWARD -> rings.last()[index]
                        SwirlType.OUTWARD -> rings.first()[index]
                    }.exhaustive
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
        }
    }

}
