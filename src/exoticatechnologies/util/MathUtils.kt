package exoticatechnologies.util

import kotlin.math.abs
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
        val r2 = if (abs(r1) > Math.PI) r1 - twoPi else r1

        // put us in -PI/2 to +PI/2 space
        val r3 = if (abs(r2) > Math.PI / 2.0) Math.PI - r2 else r2

        return r3
    }

    /**
     * Calculates the sine of an angle using range reduction.
     *
     * This method reduces the input angle to the primary quadrant to maintain
     * precision and utilizes a hybrid approach: calling [kotlin.math.sin] for
     * small angles and [kotlin.math.cos] for angles closer to π/2.
     *
     * @param radians The angle, in radians.
     * @return The sine of the angle, in radians.
     */
    fun sin(radians: Double): Double {
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
     * @return The cosine of the angle, in radians.
     */
    fun cos(radians: Double): Double {
        return sin(radians + Math.PI / 2.0)
    }

    /**
     * Returns arctangent of [tangentValue] as an angle in radians, via magic.
     *
     * The actual magic in question
     *
     *      return (0.97239411 + -0.19194795 * tangentValue * tangentValue) * tangentValue
     *
     * @param tangentValue the value to calculate atan for
     * @return angle in radians
     */
    fun atan(tangentValue: Double): Double {
        return (0.97239411 + -0.19194795 * tangentValue * tangentValue) * tangentValue
    }

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
        val invert = ay > ax
        // [0,1]
        val z = if (invert) ax / ay else ay / ax
        // [0,π/4]
        var theta = atan(z)
        // [0,π/2]
        if (invert) theta = Math.PI / 2.0 - theta

        // [0,π]
        if (x < 0.0) theta = Math.PI - theta

        // [-π,π]
        return theta.withSign(y)
    }
}
