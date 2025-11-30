package exoticatechnologies.util

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
     * @param generateInwards whether the swirl should be generated inwards (from largest to smallest) or outwards (from smallest to largest). **Defaults to [false]**
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
    ): Swirl.Swirl2 {
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

        return Swirl.Swirl2(swirl)
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



    sealed class Swirl(private val rings: List<List<Vector2f>>) {

        class Swirl1(private val rings: List<List<Vector2f>>) : Swirl(rings) {

            /**
             * Draws the swirl by connecting points of successive rings.
             * Replace the drawing logic with your engine’s API calls.
             */
            fun draw(ship: ShipAPI, thickness: Float = 12f, color: Color = Color.CYAN) {
                val engine = Global.getCombatEngine()

                if (rings.size < 2) return

                val numPoints = rings[0].size

                for (i in 0 until numPoints) {
                    for (r in 0 until rings.size - 1) {
                        val p1 = rings[r][i]
                        val p2 = rings[r + 1][i]

                        // Example: persistent line backbone
                        engine.addSmoothParticle(
                            p1,
                            Vector2f(0f, 0f),
                            thickness,
                            1f,
                            0.3f,
                            color
                        )

                        // Example: flashy arc overlay
                        engine.spawnEmpArcVisual(
                            p1,
                            ship,
                            p2,
                            ship,
                            thickness,
                            color,
                            Color.WHITE
                        )
                    }
                }
            }
        }

        class Swirl2(private val rings: List<List<Vector2f>>) : Swirl(rings) {

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
                connectToCenter: Boolean = true
            ) {
                val engine = Global.getCombatEngine()
                if (rings.size < 2) return

                val numPoints = rings[0].size
                AnonymousLogger.log("numPoints: ${numPoints}", "SHARK-drawing")

                for (index in 0 until numPoints) {
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
//                            core,
//                            fringe
                            fringe,
                            core
                        )

                        // Optional persistent line overlay
                        if (drawParticles) {
                            val dx = (p2.x - p1.x) / particleSegments
                            val dy = (p2.y - p1.y) / particleSegments
                            var x = p1.x
                            var y = p1.y
                            for (seg in 0..particleSegments) {
                                engine.addSmoothParticle(
                                    Vector2f(x, y),
                                    Vector2f(0f, 0f),
                                    particleSize,
                                    1f,
                                    particleDuration,
//                                    core  //TODO
                                    Color.RED
                                )
                                x += dx
                                y += dy
                            }
                        }
                    }

                    // Optionally connect innermost ring to center
                    if (connectToCenter) {

                        val (core, fringe) = arcColors.last()

                        val innermost = rings.last()[index]
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

}
