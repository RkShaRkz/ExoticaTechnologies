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
     * @return a list of [Vector2f] dots along the circle circumference of a [distance] radius
     *
     * @param center the center from which dots should diverge
     * @param distance how far from the center should the dots be
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

    fun rotatePoints(points: List<Vector2f>, center: Vector2f, angleDegrees: Float): List<Vector2f> {
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


    fun generateSwirl(
        center: Vector2f,
        rings: Int,
        pointsPerRing: Int,
        minRadius: Float,
        maxRadius: Float,
        ringRotationsDegrees: List<Float>
    ): Swirl.Swirl2 {
        val swirl = mutableListOf<List<Vector2f>>()

        // Step sizes
        val radiusStep = if (rings > 1) (maxRadius - minRadius) / (rings - 1) else 0f
        val angleStepDegrees = 360.0 / pointsPerRing

        for (ring in 0 until rings) {
            val radius = minRadius + ring * radiusStep

            // base rotation offset for this ring
            val ringRotation = Math.toRadians(
                ringRotationsDegrees.getOrNull(ring)?.toDouble() ?: 0.0
            )

            val ringPoints = mutableListOf<Vector2f>()

            for (i in 0 until pointsPerRing) {
                // base spacing in degrees, plus global rotation, plus per-ring rotation
                val angle = Math.toRadians(angleStepDegrees * i) + ringRotation
                val x = center.x + radius * cos(angle).toFloat()
                val y = center.y + radius * sin(angle).toFloat()
                ringPoints.add(Vector2f(x, y))
            }
            swirl.add(ringPoints)
        }

        //TODO decide on Swirl1 or Swirl2
        return Swirl.Swirl2(swirl)
    }

    fun generateSwirlWithRotation(
        center: Vector2f,
        rings: Int,
        pointsPerRing: Int,
        minRadius: Float,
        maxRadius: Float,
        ringRotationsDegrees: List<Float>,
        globalRotationDegrees: Float = 0f
    ): Swirl.Swirl2 {
        val swirl = mutableListOf<List<Vector2f>>()

        // Step sizes
        val radiusStep = if (rings > 1) (maxRadius - minRadius) / (rings - 1) else 0f
        val angleStepDegrees = 360.0 / pointsPerRing
        val globalRotation = Math.toRadians(globalRotationDegrees.toDouble())

        for (ring in 0 until rings) {
            val radius = minRadius + ring * radiusStep

            // base rotation offset for this ring
            val ringRotation = Math.toRadians(
                ringRotationsDegrees.getOrNull(ring)?.toDouble() ?: 0.0
            )

            val ringPoints = mutableListOf<Vector2f>()

            for (i in 0 until pointsPerRing) {
                // base spacing in degrees, plus global rotation, plus per-ring rotation
                val angle = Math.toRadians(angleStepDegrees * i) + globalRotation + ringRotation
                val x = center.x + radius * cos(angle).toFloat()
                val y = center.y + radius * sin(angle).toFloat()
                ringPoints.add(Vector2f(x, y))
            }
            swirl.add(ringPoints)
        }

        //TODO decide on Swirl1 or Swirl2
        return Swirl.Swirl2(swirl)
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
             * @param arcColors List of (coreColor, fringeColor) per stage connection.
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
                            core,
                            fringe
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
                                    core
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
