package exoticatechnologies.util

import org.lwjgl.util.vector.Vector2f
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.withSign

object FastTrigUtils {

    /**
     * Normalizes an angle to the range [-π/2, π/2] for trigonometric reduction.
     *
     * This helper maps any input angle to its equivalent value within the primary
     * quadrants of a sine wave, enabling more efficient or accurate periodic
     * calculations.
     *
     * @param radians The raw angle, in radians.
     * @return The reduced angle within the range [-π/2, π/2], in radians.
     */
    fun reduceSinAngle(radians: Double): Double {
        val twoPi = Math.PI * 2.0

        // put us in -2PI to +2PI space
        val r1 = radians % twoPi

        // put us in -PI to +PI space
        val r2 = if (abs(r1) > Math.PI) r1 - r1.sign * twoPi else r1

        // put us in -PI/2 to +PI/2 space
        val r3 = if (abs(r2) > Math.PI / 2.0) r2.sign * Math.PI - r2 else r2

        return r3
    }

    /**
     * Computes a fast approximation of the sine of an angle using a 7th-order Taylor polynomial.
     *
     * First normalizes [radians] into `[-π/2, +π/2]` via [reduceSinAngle], then evaluates the
     * Maclaurin series expansion `x - x³/3! + x⁵/5! - x⁷/7!` using Horner's method for speed
     * and numerical stability.
     *
     * - **Input domain:** All finite [Double] angles in radians `(-∞, +∞)` (returns [Double.NaN] for `NaN` or `±Infinity`).
     * - **Output range:** Approximately `[-1.0, +1.0]` (unitless ratio).
     * - **Accuracy:** Maximum absolute error is ~`0.000157` (`~1.57e-4`) at the `±π/2` boundaries.
     *
     * @param radians the angle in radians whose sine is to be approximated.
     * @return the approximate sine of [radians], or [Double.NaN] if [radians] is `NaN` or infinite.
     * @see reduceSinAngle
     * @see sin_hybrid
     */
    fun sin(radians: Double): Double {
        val x = reduceSinAngle(radians)
        val x2 = x * x
        return x * (1.0 - x2 * (1.0 / 6.0 - x2 * (1.0 / 120.0 - x2 / 5040.0)))
    }

    /**
     * Calculates the sine of an angle using range reduction.
     *
     * This method reduces the input angle to the primary quadrant to maintain
     * precision and utilizes a hybrid approach: calling [kotlin.math.sin] for
     * small angles and [kotlin.math.cos] for angles closer to π/2.
     *
     * This one is alot more accurate than [sin]
     *
     * @param radians The angle, in radians.
     * @return The sine of the angle, in radians.
     * @see sin
     */
    fun sin_hybrid(radians: Double): Double {
        // limits angle to between -PI/2 and +PI/2
        val reduced = reduceSinAngle(radians)
        return if (abs(reduced) <= Math.PI / 4.0) {
            kotlin.math.sin(reduced)
        } else {
            kotlin.math.cos(Math.PI / 2.0 - reduced)
        }

    }

    /**
     * Calculates the cosine of an angle by shifting it into a sine calculation.
     *
     * Leveraging the trigonometric identity cos(x) = sin(x + π/2), this method
     * ensures consistent range reduction and performance with the [sin] implementation.
     *
     * @param radians The angle, in radians.
     * @param useHybridSin whether we should use the "taylor" [sin] or the "hybrid" [sin_hybrid]
     * @return The cosine of the angle, in radians.
     * @see sin
     * @see sin_hybrid
     */
    fun cos(radians: Double, useHybridSin: Boolean = false): Double {
        return if (useHybridSin) sin_hybrid(radians + Math.PI / 2.0) else sin(radians + Math.PI / 2.0)
    }

    /**
     * Computes a fast approximation of the arc tangent of a value.
     *
     * Uses a minimax cubic polynomial approximation over `[-1.0, 1.0]` combined with
     * range reduction (`atan(z) = sign(z) * π/2 - atan(1/z)`) for `|z| > 1.0`.
     *
     * - **Input domain:** All finite [Double] values `(-∞, +∞)` (also handles `±Infinity`, returning `±π/2`).
     * - **Output range:** `[-π/2, +π/2]` radians (approximately `-1.5708` to `+1.5708`).
     * - **Accuracy:** Maximum absolute error is ~`0.00495` radians (~`0.28°`) across the entire domain.
     *
     * @param tangentValue the tangent value (slope ratio) whose arc tangent is to be returned.
     * @return the approximate angle in radians within `[-π/2, +π/2]`, or [Double.NaN] if [tangentValue] is `NaN`.
     * @see atan_internal
     */
    fun atan(tangentValue: Double): Double {
        return if (abs(tangentValue) <= 1.0) {
            atan_internal(tangentValue)
        } else {
            tangentValue.sign * kotlin.math.PI / 2.0 - atan_internal(1.0 / tangentValue)
        }
    }

    /**
     * Returns arctangent of [tangentValue] as an angle in radians, via minimax cubic polynomial approximation magic.
     *
     * The actual magic in question
     *
     *      return (0.97239411 + -0.19194795 * tangentValue * tangentValue) * tangentValue
     *
     * @param tangentValue the value to calculate atan for, **must be in [-1,1] range
     * @return angle in radians
     */
    private fun atan_internal(tangentValue: Double): Double {
        return (0.97239411 + -0.19194795 * tangentValue * tangentValue) * tangentValue
    }

//    private fun atan_internal(tangentValue: Double): Double {
//        val a = 0.372003
//        val b = 0.703384
//        val c = 0.043562
//
//        val xx = tangentValue * tangentValue
//        return (1.0 + a * xx) * tangentValue / (1.0 + xx * (b + c * xx))
//    }

    /**
     * Calculates the four-quadrant arctangent of y/x using [Float] inputs.
     * This is a convenience overload that delegates to the [Double] implementation.
     *
     * @param y The y-coordinate (numerator).
     * @param x The x-coordinate (denominator).
     * @return The angle within the range [-π, π], in radians.
     */
    fun atan2(y: Float, x: Float): Double {
        return atan2(y.toDouble(), x.toDouble())
    }

    /**
     * Calculates the four-quadrant arctangent of y/x.
     *
     * This implementation uses an approximation approach to determine the angle
     * between the positive x-axis and the point (x, y). It handles all four
     * quadrants by analyzing the signs and relative magnitudes of the inputs.
     *
     * This is the actual [Double] implementation of the [atan2] method
     *
     * @param y The y-coordinate (numerator).
     * @param x The x-coordinate (denominator).
     * @return The angle within the range [-π, π], in radians.
     */
    fun atan2(y: Double, x: Double): Double {
        val ay = abs(y)
        val ax = abs(x)
        // if they're both zero, bail out imediatelly to avoid NaN while calculating Z
        if (ax == 0.0 && ay == 0.0) return 0.0.withSign(y)
        val invert = ay > ax
        // [0,1]
        val z = if (invert) ax / ay else ay / ax
        // [0,π/4]
        var theta = atan_internal(z)
        // [0,π/2]
        if (invert) theta = Math.PI / 2.0 - theta

        // [0,π]
        if (x < 0.0) theta = Math.PI - theta

        // [-π,π]
        return theta.withSign(y)
    }
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
 * Gets a point on circumference of a circle centered at [center], with radius [radius] at angle [angle]
 * Or in other words, get a point on a line starting at [center], with length [radius] that is angled at [angle]
 * from either [AngleDegreeType.USER_CENTRIC] or [AngleDegreeType.TRIGONOMETRIC] 0-degree origin.
 * //TODO currently just always implies trigonometric angle type... i should really roll out my own Angle class...
 */
fun getPointOnCircumference(
    center: Vector2f = Vector2f(0f, 0f),
    radius: Float,
    angle: Float
): Vector2f {
    // There are 4 basic scenarios for 90-degree quadrant angles,
    // and the fifth typical scenario when the angle is whatever
    return when(angle) {
        0f -> {
            Vector2f(center.x + radius, center.y)
        }

        90f -> {
            Vector2f(center.x, center.y + radius)
        }

        180f -> {
            Vector2f(center.x - radius, center.y)
        }

        270f -> {
            Vector2f(center.x, center.y - radius)
        }

        else -> {
            // First get the radians of the angle
            val radians = Math.toRadians(angle.toDouble())
            // Calculate the X and Y components
            val x = FastTrigUtils.cos(radians) * radius + center.x
            val y = FastTrigUtils.sin(radians) * radius + center.y
            Vector2f(
                x.toFloat(),
                y.toFloat()
            )
        }
    }
}
