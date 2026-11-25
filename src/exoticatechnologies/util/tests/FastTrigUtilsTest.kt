package exoticatechnologies.util.tests

import exoticatechnologies.util.FastTrigUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan as stdAtan
import kotlin.math.atan2 as stdAtan2
import kotlin.math.cos as stdCos
import kotlin.math.sin as stdSin
import kotlin.system.measureNanoTime

class FastTrigUtilsTest {

    // Max expected error for the cubic minimax atan/atan2 approximation (~0.00495 rad)
    private val atanTolerance = 0.0055
    // Near-exact tolerance for your hybrid sin/cos (delegates to kotlin.math)
    private val exactTolerance = 1e-12
    // Max expected error for the 7th-order Taylor sin on [-π/2, π/2] (~0.000157)
    private val taylorSinTolerance = 0.0002

    // =========================================================================
    // 1. POSITIVE TESTS (Standard Domains, All 4 Quadrants, Multi-Rotations)
    // =========================================================================

    @Test
    fun `reduceSinAngle maps standard and multi-rotation angles into -PI_2 to PI_2`() {
        val testAngles = listOf(
            0.0, PI / 6, PI / 4, PI / 3, PI / 2,
            2 * PI / 3, 3 * PI / 4, PI,
            4 * PI / 3, 3 * PI / 2, 7 * PI / 4, 2 * PI,
            5.0 * PI + 0.3, 12.0 * PI - 0.7
        )

        for (angle in testAngles) {
            val reduced = FastTrigUtils.reduceSinAngle(angle)
            assertTrue(
                "Reduced angle $reduced out of [-π/2, π/2] for input $angle",
                reduced in (-PI / 2 - 1e-12)..(PI / 2 + 1e-12)
            )
            assertEquals(stdSin(angle), stdSin(reduced), exactTolerance)
        }
    }

    @Test
    fun `sin and cos match standard library across positive angles`() {
        for (deg in 0..720 step 5) {
            val rad = Math.toRadians(deg.toDouble())
            // Just like before, since the original sin() got renamed to sin_hybrid and was replaced with sin_taylor
            // we have to adjust the expectancies here a little bit ...
            assertEquals("sin mismatch at $deg°", stdSin(rad), FastTrigUtils.sin_hybrid(rad), exactTolerance)
            assertEquals("cos mismatch at $deg°", stdCos(rad), FastTrigUtils.cos(rad, useHybridSin = true), exactTolerance)
            assertEquals("sin mismatch at $deg°", stdSin(rad), FastTrigUtils.sin(rad), taylorSinTolerance)
            assertEquals("cos mismatch at $deg°", stdCos(rad), FastTrigUtils.cos(rad, useHybridSin = false), taylorSinTolerance)
        }
    }

    @Test
    fun `atan matches standard library within minimax error across positive slopes`() {
        val slopes = listOf(0.0, 0.1, 0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 5.0, 10.0, 100.0, 10_000.0)
        for (z in slopes) {
            val expected = stdAtan(z)
            val actual = FastTrigUtils.atan(z)
            assertEquals("atan mismatch at z=$z", expected, actual, atanTolerance)
        }
    }

    @Test
    fun `atan2 matches standard library across all four quadrants`() {
        for (deg in -179..180 step 5) {
            val rad = Math.toRadians(deg.toDouble())
            val y = stdSin(rad) * 3.5
            val x = stdCos(rad) * 3.5

            assertEquals("Double atan2 mismatch at $deg°", stdAtan2(y, x), FastTrigUtils.atan2(y, x), atanTolerance)
            assertEquals(
                "Float atan2 mismatch at $deg°",
                stdAtan2(y.toFloat(), x.toFloat()).toDouble(),
                FastTrigUtils.atan2(y.toFloat(), x.toFloat()),
                atanTolerance
            )
        }
    }

    // =========================================================================
    // 2. NEGATIVE & EDGE-CASE TESTS (Negative Angles, Zeros, NaN, Infinity)
    // =========================================================================

    @Test
    fun `reduceSinAngle handles negative angles and multiple negative rotations`() {    // close but not quite 1.0
        val negativeAngles = listOf(
            -0.0, -PI / 6, -PI / 4, -PI / 2,
            -2.0, -2.5, -3.0, -PI,
            -4.0, -3 * PI / 2, -2 * PI,
            -7.0 * PI - 0.4, -15.0 * PI + 0.9
        )

        for (angle in negativeAngles) {
            val reduced = FastTrigUtils.reduceSinAngle(angle)
            assertTrue(
                "Reduced angle $reduced out of [-π/2, π/2] for negative input $angle",
                reduced in (-PI / 2 - 1e-12)..(PI / 2 + 1e-12)
            )
            // Since the original "hybrid" sin() was replaced with taylor sin() and the original has been renamed to sin_hybrid()
            // we have to change the allowed epsilon we expect in here, which is now taylorTolerance
//            assertEquals("sin mismatch at $angle", stdSin(angle), FastTrigUtils.sin(angle), exactTolerance)
//            assertEquals("cos mismatch at $angle", stdCos(angle), FastTrigUtils.cos(angle), exactTolerance)
            assertEquals("sin mismatch at $angle", stdSin(angle), FastTrigUtils.sin(angle), taylorSinTolerance)
            assertEquals("cos mismatch at $angle", stdCos(angle), FastTrigUtils.cos(angle), taylorSinTolerance)
        }
    }

    @Test
    fun `atan handles negative slopes including values below -1`() {
        val negativeSlopes = listOf(-0.0, -0.25, -0.5, -1.0, -1.5, -2.0, -10.0, -500.0)
        for (z in negativeSlopes) {
            assertEquals("atan mismatch at negative z=$z", stdAtan(z), FastTrigUtils.atan(z), atanTolerance)
        }
    }

    @Test
    fun `atan handles NaN and Infinity properly`() {
        assertTrue(FastTrigUtils.atan(Double.NaN).isNaN())
        assertEquals(PI / 2.0, FastTrigUtils.atan(Double.POSITIVE_INFINITY), exactTolerance)
        assertEquals(-PI / 2.0, FastTrigUtils.atan(Double.NEGATIVE_INFINITY), exactTolerance)
    }

    @Test
    fun `sin and cos return NaN for NaN and Infinity inputs`() {
        assertTrue(FastTrigUtils.sin(Double.NaN).isNaN())
        assertTrue(FastTrigUtils.cos(Double.NaN).isNaN())
        assertTrue(FastTrigUtils.sin(Double.POSITIVE_INFINITY).isNaN())
        assertTrue(FastTrigUtils.sin(Double.NEGATIVE_INFINITY).isNaN())
        assertTrue(FastTrigUtils.cos(Double.POSITIVE_INFINITY).isNaN())
    }

    @Test
    fun `atan2 handles axis-aligned inputs, origin zeros, and NaN`() {
        // Origin (0, 0) should safely return 0.0 instead of NaN
        assertEquals(0.0, FastTrigUtils.atan2(0.0, 0.0), exactTolerance)
        assertEquals(-0.0, FastTrigUtils.atan2(-0.0, 0.0), exactTolerance)

        // Cardinal axes
        assertEquals(0.0, FastTrigUtils.atan2(0.0, 5.0), exactTolerance)          // +X axis
        assertEquals(PI / 2.0, FastTrigUtils.atan2(5.0, 0.0), exactTolerance)     // +Y axis
        assertEquals(PI, FastTrigUtils.atan2(0.0, -5.0), exactTolerance)          // -X axis (y = +0.0)
        assertEquals(-PI, FastTrigUtils.atan2(-0.0, -5.0), exactTolerance)        // -X axis (y = -0.0)
        assertEquals(-PI / 2.0, FastTrigUtils.atan2(-5.0, 0.0), exactTolerance)   // -Y axis

        // NaN propagation
        assertTrue(FastTrigUtils.atan2(Double.NaN, 1.0).isNaN())
        assertTrue(FastTrigUtils.atan2(1.0, Double.NaN).isNaN())
    }

    // =========================================================================
    // 3. COMPARISON TESTS: Your Hybrid sin() vs. 7th-Order Taylor sin()
    // =========================================================================

    @Test
    fun `compare accuracy of hybrid sin vs Taylor sin across full range`() {
        var maxHybridError = 0.0
        var maxTaylorError = 0.0

        val steps = 10_000
        for (i in 0..steps) {
            val rad = -4.0 * PI + (8.0 * PI * i / steps)
            val truth = stdSin(rad)

            val hybridVal = FastTrigUtils.sin_hybrid(rad)
            val taylorVal = FastTrigUtils.sin(rad)

            val hybridErr = abs(truth - hybridVal)
            val taylorErr = abs(truth - taylorVal)

            if (hybridErr > maxHybridError) maxHybridError = hybridErr
            if (taylorErr > maxTaylorError) maxTaylorError = taylorErr

            assertEquals("Taylor sin exceeded tolerance at $rad", truth, taylorVal, taylorSinTolerance)
        }

        println("Max Hybrid sin() error: $maxHybridError")
        println("Max Taylor sin() error: $maxTaylorError")

        assertTrue(maxHybridError < 1e-14)
        assertTrue(maxTaylorError < taylorSinTolerance)
    }

    @Test
    fun `benchmark execution time of hybrid sin vs Taylor sin vs kotlin math sin`() {
        val iterations = 2_000_000
        val inputs = DoubleArray(1024) { i -> -2 * PI + (4 * PI * i / 1024.0) }

        // Warmup JVM JIT compiler
        var sink = 0.0
        repeat(500_000) { i ->
            val x = inputs[i and 1023]
            sink += stdSin(x) + FastTrigUtils.sin_hybrid(x) + FastTrigUtils.sin(x)
        }

        val stdTimeNs = measureNanoTime {
            for (i in 0 until iterations) sink += stdSin(inputs[i and 1023])
        }
        val hybridTimeNs = measureNanoTime {
            for (i in 0 until iterations) sink += FastTrigUtils.sin_hybrid(inputs[i and 1023])
        }
        val taylorTimeNs = measureNanoTime {
            for (i in 0 until iterations) sink += FastTrigUtils.sin(inputs[i and 1023])
        }

        println(
            """
            |=== sin() Benchmark ($iterations iterations) ===
            |kotlin.math.sin : ${stdTimeNs / 1_000_000.0} ms
            |Hybrid sin()    : ${hybridTimeNs / 1_000_000.0} ms
            |Taylor sin()    : ${taylorTimeNs / 1_000_000.0} ms
            |(sink=$sink)
            """.trimMargin()
        )

        assertTrue(sink.isFinite())
    }

    @Test
    fun `compare accuracy of FastTrigUtils atan vs Zarowski atanApprox`() {
        var maxCurrentError = 0.0
        var maxZarowskiError = 0.0

        // Sample 10,000 tangent values from -20.0 to +20.0 (covers both |z| <= 1 and |z| > 1)
        val steps = 10_000
        for (i in 0..steps) {
            val z = -20.0 + (40.0 * i / steps)
            val truth = stdAtan(z)

            val currentVal = FastTrigUtils.atan(z, useZarowski = false)
            val zarowskiVal = FastTrigUtils.atan(z, useZarowski = true)

            val currentErr = abs(truth - currentVal)
            val zarowskiErr = abs(truth - zarowskiVal)

            if (currentErr > maxCurrentError) maxCurrentError = currentErr
            if (zarowskiErr > maxZarowskiError) maxZarowskiError = zarowskiErr

            assertEquals("Zarowski atan exceeded tolerance at z=$z", truth, zarowskiVal, 0.0001)
        }

        println("Max Cubic atan() error    : $maxCurrentError rad (~${Math.toDegrees(maxCurrentError)}°)")
        println("Max Zarowski atan() error : $maxZarowskiError rad (~${Math.toDegrees(maxZarowskiError)}°)")

        // Zarowski's rational approximation is ~90x more accurate than the cubic polynomial
        assertTrue(
            "Expected Zarowski error ($maxZarowskiError) to be smaller than cubic error ($maxCurrentError)",
            maxZarowskiError < maxCurrentError / 50.0
        )
    }

    @Test
    fun `benchmark execution time of FastTrigUtils atan vs Zarowski atan vs kotlin math atan`() {
        val iterations = 2_000_000
        val inputs = DoubleArray(1024) { i -> -10.0 + (20.0 * i / 1024.0) }

        // Warmup JVM JIT compiler
        var sink = 0.0
        repeat(500_000) { i ->
            val z = inputs[i and 1023]
            sink += stdAtan(z) + FastTrigUtils.atan(z, useZarowski = false) + FastTrigUtils.atan(z, useZarowski = true)
        }

        val stdTimeNs = measureNanoTime {
            for (i in 0 until iterations) sink += stdAtan(inputs[i and 1023])
        }
        val cubicTimeNs = measureNanoTime {
            for (i in 0 until iterations) sink += FastTrigUtils.atan(inputs[i and 1023], useZarowski = false)
        }
        val zarowskiTimeNs = measureNanoTime {
            for (i in 0 until iterations) sink += FastTrigUtils.atan(inputs[i and 1023], useZarowski = true)
        }

        println(
            """
            |=== atan() Benchmark ($iterations iterations) ===
            |kotlin.math.atan : ${stdTimeNs / 1_000_000.0} ms
            |Cubic atan()     : ${cubicTimeNs / 1_000_000.0} ms
            |Zarowski atan()  : ${zarowskiTimeNs / 1_000_000.0} ms
            |(sink=$sink)
            """.trimMargin()
        )

        assertTrue(sink.isFinite())
    }

    /**
     * Reference Kotlin port of libGDX's branchless Möbius-reduced atanUnchecked().
     */
    private fun atanLibGdx(i: Double): Double {
        val n = abs(i)
        val c = (n - 1.0) / (n + 1.0)
        val c2 = c * c
        val c3 = c * c2
        val c5 = c3 * c2
        val c7 = c5 * c2
        val c9 = c7 * c2
        val c11 = c9 * c2
        return kotlin.math.sign(i) * (
            (PI * 0.25) +
                (0.99997726 * c - 0.33262347 * c3 + 0.19354346 * c5 - 0.11643287 * c7 + 0.05265332 * c9 - 0.0117212 * c11)
            )
    }

    /**
     * Reference Kotlin port of libGDX's atan2() built on top of atanUnchecked().
     */
    private fun atan2LibGdx(yInput: Double, xInput: Double): Double {
        var x = xInput
        var n = yInput / x
        if (n != n) {
            n = if (yInput == x) 1.0 else -1.0
        } else if (n - n != n - n) {
            x = 0.0
        }
        return if (x > 0.0) {
            atanLibGdx(n)
        } else if (x < 0.0) {
            if (yInput >= 0.0) atanLibGdx(n) + PI else atanLibGdx(n) - PI
        } else if (yInput > 0.0) {
            x + PI / 2.0
        } else if (yInput < 0.0) {
            x - PI / 2.0
        } else {
            x + yInput
        }
    }

    @Test
    fun `benchmark atan and atan2 against libGDX branchless implementation`() {
        val iterations = 2_000_000
        val rng = java.util.Random(42L)

        // Random inputs spanning [-10, +10] so |z| <= 1 and |z| > 1 branches are unpredictable
        val zInputs = DoubleArray(1024) { (rng.nextDouble() * 20.0) - 10.0 }
        val yInputs = DoubleArray(1024) { (rng.nextDouble() * 20.0) - 10.0 }
        val xInputs = DoubleArray(1024) { (rng.nextDouble() * 20.0) - 10.0 }

        // 1. Measure max error across [-20, +20] for atan() and full 360° circle for atan2()
        var maxCubicAtanErr = 0.0
        var maxZarowskiAtanErr = 0.0
        var maxLibGdxAtanErr = 0.0

        for (i in 0..10_000) {
            val z = -20.0 + (40.0 * i / 10_000.0)
            val truth = stdAtan(z)
            maxCubicAtanErr = maxOf(maxCubicAtanErr, abs(truth - FastTrigUtils.atan(z, useZarowski = false)))
            maxZarowskiAtanErr = maxOf(maxZarowskiAtanErr, abs(truth - FastTrigUtils.atan(z, useZarowski = true)))
            maxLibGdxAtanErr = maxOf(maxLibGdxAtanErr, abs(truth - atanLibGdx(z)))
        }

        var maxCubicAtan2Err = 0.0
        var maxZarowskiAtan2Err = 0.0
        var maxLibGdxAtan2Err = 0.0

        for (i in 0..10_000) {
            val angle = -PI + (2.0 * PI * i / 10_000.0)
            val y = stdSin(angle) * 5.0
            val x = stdCos(angle) * 5.0
            val truth = stdAtan2(y, x)
            maxCubicAtan2Err = maxOf(maxCubicAtan2Err, abs(truth - FastTrigUtils.atan2(y, x, useZarowski = false)))
            maxZarowskiAtan2Err = maxOf(maxZarowskiAtan2Err, abs(truth - FastTrigUtils.atan2(y, x, useZarowski = true)))
            maxLibGdxAtan2Err = maxOf(maxLibGdxAtan2Err, abs(truth - atan2LibGdx(y, x)))
        }

        // 2. Warmup JVM JIT compiler
        var sink = 0.0
        repeat(500_000) { i ->
            val idx = i and 1023
            val z = zInputs[idx]
            val y = yInputs[idx]
            val x = xInputs[idx]
            sink += stdAtan(z) + FastTrigUtils.atan(z, true) + FastTrigUtils.atan(z, false) + atanLibGdx(z)
            sink += stdAtan2(y, x) + FastTrigUtils.atan2(y, x, true) + FastTrigUtils.atan2(y, x, false) + atan2LibGdx(y, x)
        }

        // 3. Benchmark standalone atan()
        val stdAtanNs = measureNanoTime {
            for (i in 0 until iterations) sink += stdAtan(zInputs[i and 1023])
        }
        val cubicAtanNs = measureNanoTime {
            for (i in 0 until iterations) sink += FastTrigUtils.atan(zInputs[i and 1023], useZarowski = false)
        }
        val zarowskiAtanNs = measureNanoTime {
            for (i in 0 until iterations) sink += FastTrigUtils.atan(zInputs[i and 1023], useZarowski = true)
        }
        val libGdxAtanNs = measureNanoTime {
            for (i in 0 until iterations) sink += atanLibGdx(zInputs[i and 1023])
        }

        // 4. Benchmark four-quadrant atan2()
        val stdAtan2Ns = measureNanoTime {
            for (i in 0 until iterations) {
                val idx = i and 1023
                sink += stdAtan2(yInputs[idx], xInputs[idx])
            }
        }
        val cubicAtan2Ns = measureNanoTime {
            for (i in 0 until iterations) {
                val idx = i and 1023
                sink += FastTrigUtils.atan2(yInputs[idx], xInputs[idx], useZarowski = false)
            }
        }
        val zarowskiAtan2Ns = measureNanoTime {
            for (i in 0 until iterations) {
                val idx = i and 1023
                sink += FastTrigUtils.atan2(yInputs[idx], xInputs[idx], useZarowski = true)
            }
        }
        val libGdxAtan2Ns = measureNanoTime {
            for (i in 0 until iterations) {
                val idx = i and 1023
                sink += atan2LibGdx(yInputs[idx], xInputs[idx])
            }
        }

        println(
            """
            |=== atan() Accuracy (Max Error on [-20, 20]) ===
            |Cubic atan()    : $maxCubicAtanErr rad (~${Math.toDegrees(maxCubicAtanErr)}°)
            |Zarowski atan() : $maxZarowskiAtanErr rad (~${Math.toDegrees(maxZarowskiAtanErr)}°)
            |libGDX atan()   : $maxLibGdxAtanErr rad (~${Math.toDegrees(maxLibGdxAtanErr)}°)
            |
            |=== atan2() Accuracy (Max Error across [-π, π]) ===
            |Cubic atan2()    : $maxCubicAtan2Err rad (~${Math.toDegrees(maxCubicAtan2Err)}°)
            |Zarowski atan2() : $maxZarowskiAtan2Err rad (~${Math.toDegrees(maxZarowskiAtan2Err)}°)
            |libGDX atan2()   : $maxLibGdxAtan2Err rad (~${Math.toDegrees(maxLibGdxAtan2Err)}°)
            |
            |=== Standalone atan() Benchmark ($iterations random calls) ===
            |kotlin.math.atan : ${stdAtanNs / 1_000_000.0} ms
            |Cubic atan()     : ${cubicAtanNs / 1_000_000.0} ms
            |Zarowski atan()  : ${zarowskiAtanNs / 1_000_000.0} ms
            |libGDX atan()    : ${libGdxAtanNs / 1_000_000.0} ms
            |
            |=== Four-Quadrant atan2() Benchmark ($iterations random calls) ===
            |kotlin.math.atan2 : ${stdAtan2Ns / 1_000_000.0} ms
            |Cubic atan2()     : ${cubicAtan2Ns / 1_000_000.0} ms
            |Zarowski atan2()  : ${zarowskiAtan2Ns / 1_000_000.0} ms
            |libGDX atan2()    : ${libGdxAtan2Ns / 1_000_000.0} ms
            |(sink=$sink)
            """.trimMargin()
        )

        assertTrue(sink.isFinite())
    }



}
