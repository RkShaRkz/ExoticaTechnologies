package exoticatechnologies.util

import org.lwjgl.util.vector.Vector2f
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
        val angleIncrement = 360/numDots
        for (i in 0 until numDots) {
            val angleDeg = i * angleIncrement
            val angleRad = angleDeg * PI.toFloat() / 180f
            val x = center.x + distance * cos(angleRad)
            val y = center.y + distance * sin(angleRad)
            dots.add(Vector2f(x,y))
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
            maxRadius: Float
    ): List<List<Vector2f>> {
        val swirl = mutableListOf<List<Vector2f>>()

        // Step sizes
        val radiusStep = if (rings > 1) (maxRadius - minRadius) / (rings - 1) else 0f
        val angleStepDegrees = 360.0 / pointsPerRing

        for (ring in 0 until rings) {
            val radius = minRadius + ring * radiusStep
            val angleOffset = Math.toRadians(angleStepDegrees * ring)
            val ringPoints = mutableListOf<Vector2f>()

            for (i in 0 until pointsPerRing) {
                val angle = (2.0 * Math.PI * i / pointsPerRing) + angleOffset
                val x = center.x + radius * cos(angle).toFloat()
                val y = center.y + radius * sin(angle).toFloat()
                ringPoints.add(Vector2f(x, y))
            }
            swirl.add(ringPoints)
        }

        return swirl
    }

    fun generateSwirlWithRotation(
            center: Vector2f,
            rings: Int,
            pointsPerRing: Int,
            minRadius: Float,
            maxRadius: Float,
            globalRotationDegrees: Float = 0f
    ): List<List<Vector2f>> {
        val swirl = mutableListOf<List<Vector2f>>()

        // Step sizes
        val radiusStep = if (rings > 1) (maxRadius - minRadius) / (rings - 1) else 0f
        val angleStepDegrees = 360.0 / pointsPerRing
        val globalRotation = Math.toRadians(globalRotationDegrees.toDouble())

        for (ring in 0 until rings) {
            val radius = minRadius + ring * radiusStep
            val angleOffset = Math.toRadians(angleStepDegrees * ring) + globalRotation
            val ringPoints = mutableListOf<Vector2f>()

            for (i in 0 until pointsPerRing) {
                val angle = (2.0 * Math.PI * i / pointsPerRing) + angleOffset
                val x = center.x + radius * cos(angle).toFloat()
                val y = center.y + radius * sin(angle).toFloat()
                ringPoints.add(Vector2f(x, y))
            }
            swirl.add(ringPoints)
        }

        return swirl
    }


}
