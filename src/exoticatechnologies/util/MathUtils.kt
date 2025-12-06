package exoticatechnologies.util

import kotlin.math.abs
import kotlin.math.withSign

object FastTrigUtils {

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


    fun sin(radians: Double): Double {
        // limits angle to between -PI/2 and +PI/2
        val reduced = reduceSinAngle(radians)
        return if (abs(reduced) <= Math.PI / 4.0) {
            kotlin.math.sin(reduced)
        } else {
            kotlin.math.cos(Math.PI / 2.0 - reduced)
        }

    }

    fun cos(radians: Double): Double {
        return sin(radians + Math.PI / 2.0)
    }

    /**
     * Returns arctangent of [tangentValue] as an angle in radians
     *
     * @param tangentValue the value to calculate atan for
     * @return angle in radians
     */
    fun atan(tangentValue: Double): Double {
        return (0.97239411 + -0.19194795 * tangentValue * tangentValue) * tangentValue;
    }

    fun atan2(y: Float, x: Float): Double {
        return atan2(y.toDouble(), x.toDouble())
    }

    fun atan2(y: Double, x: Double): Double {
        val ay = abs(y)
        val ax = abs(x)
        val invert = ay > ax
        // [0,1]
        val z = if (invert) ax / ay else ay / ax
        // [0,π/4]
        var th = atan(z)
        // [0,π/2]
        if (invert) th = Math.PI / 2.0 - th

        // [0,π]
        if (x < 0.0) th = Math.PI - th

        // [-π,π]
        return th.withSign(y)
    }
}
