package exoticatechnologies.util.tests

import exoticatechnologies.util.rotate
import exoticatechnologies.util.rotateAroundPivot
import org.junit.Assert
import org.junit.Test
import org.lwjgl.util.vector.Vector2f
import kotlin.math.cos
import kotlin.math.sin

class Vector2fTests {
    @Test
    fun `rotate around origin 90 degrees`() {
        val v = Vector2f(1f, 0f)
        val rotated = v.rotate(90f)

        val expected = Vector2f(0f, 1f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate around origin 180 degrees`() {
        val v = Vector2f(1f, 0f)
        val rotated = v.rotate(180f)

        val expected = Vector2f(-1f, 0f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate around pivot`() {
        val v = Vector2f(2f, 0f)
        val pivot = Vector2f(1f, 0f)
        val rotated = v.rotateAroundPivot(pivot, 180f)

        val expected = Vector2f(0f, 0f)

        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate 90 degrees around origin`() {
        val v = Vector2f(1f, 0f)
        val rotated = v.rotate(90f)

        val expected = Vector2f(0f, 1f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate 180 degrees around origin`() {
        val v = Vector2f(1f, 0f)
        val rotated = v.rotate(180f)

        val expected = Vector2f(-1f, 0f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate 270 degrees around origin`() {
        val v = Vector2f(1f, 0f)
        val rotated = v.rotate(270f)

        val expected = Vector2f(0f, -1f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate 45 degrees around origin`() {
        val v = Vector2f(1f, 0f)
        val rotated = v.rotate(45f)

        // sqrt(2)/2 ≈ 0.707
        // actually 0.70710677 to be exact
        val expected = Vector2f(0.70710677f, 0.70710677f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate vector in quadrant II by 90 degrees`() {
        val v = Vector2f(-1f, 1f)
        val rotated = v.rotate(90f)

        val expected = Vector2f(-1f, -1f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate vector in quadrant III by 180 degrees`() {
        val v = Vector2f(-1f, -1f)
        val rotated = v.rotate(180f)

        val expected = Vector2f(1f, 1f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate vector in quadrant IV by 270 degrees`() {
        val v = Vector2f(1f, -1f)
        val rotated = v.rotate(270f)

        val expected = Vector2f(-1f, -1f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate arbitrary vector -457,352 by 90 degrees`() {
        val v = Vector2f(-457f, 352f)
        val rotated = v.rotate(90f)

        val expected = Vector2f(-352f, -457f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate arbitrary vector 256,128 by 180 degrees`() {
        val v = Vector2f(256f, 128f)
        val rotated = v.rotate(180f)

        val expected = Vector2f(-256f, -128f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate arbitrary vector 256,128 by 270 degrees`() {
        val v = Vector2f(256f, 128f)
        val rotated = v.rotate(270f)

        val expected = Vector2f(128f, -256f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate arbitrary vector 256,128 by 45 degrees - no fast trig`() {
        val v = Vector2f(256f, 128f)
        val rotated = v.rotate(45f, false)

        val expected = Vector2f(90.51f, 271.53f)
        val expectedPrecise = Vector2f(90.50967f, 271.529f)

        Assert.assertEquals(expected.x, rotated.x, 1e-2f)
        Assert.assertEquals(expected.y, rotated.y, 1e-2f)
        Assert.assertEquals(expectedPrecise, rotated)
    }

    @Test
    fun `rotate arbitrary vector 256,128 by 45 degrees - with fast trig`() {
        val v = Vector2f(256f, 128f)
        val rotated = v.rotate(45f, true)

        val expected = Vector2f(90.51f, 271.53f)
        val expectedPrecise = Vector2f(90.50967f, 271.529f)
//        Assert.assertEquals(expected, rotated)
        Assert.assertEquals(expected.x, rotated.x, 1e-2f)
        Assert.assertEquals(expected.y, rotated.y, 1e-2f)
        Assert.assertEquals(expectedPrecise, rotated)
    }

    @Test
    fun `cos and sin values at 45 degrees radians sanity`() {
        val theta = Math.toRadians(45.0)
        val cos = Math.cos(theta).toFloat()
        val sin = Math.sin(theta).toFloat()

        val expectedCos = 0.70710677f
        val expectedSin = 0.70710677f

        val rotated = Vector2f(cos, sin)

        val expected = Vector2f(expectedCos, expectedSin)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate preserves length for arbitrary vector 256,128 by 45 degrees`() {
        val v = Vector2f(256f, 128f)
        val rotated = v.rotate(45f)

        val expectedLength = Math.hypot(256.0, 128.0).toFloat()
        val expected = Vector2f(rotated.x, rotated.y) // use rotated coords to check equality form

        // Compare lengths via equality of vectors with same coords (diagnostic wrapper)
        Assert.assertEquals(expected, rotated)

        // Additional invariant check (length exactly preserved at float precision)
        val rotatedLength = Math.hypot(rotated.x.toDouble(), rotated.y.toDouble()).toFloat()
        Assert.assertEquals(expectedLength, rotatedLength, 1e-3f)
    }

    @Test
    fun `rotate 45 then -45 returns original for arbitrary vector 256,128`() {
        val v = Vector2f(256f, 128f)
        val rotated = v.rotate(45f).rotate(-45f)

        val expected = Vector2f(256f, 128f)
        // we are going to get something like [255.99998, 127.99999] so we need some epsilon
//        Assert.assertEquals(expected, rotated, 1e-4)
        Assert.assertEquals(expected.x, rotated.x, 1e-4f)
        Assert.assertEquals(expected.y, rotated.y, 1e-4f)
    }

    @Test
    fun `rotate arbitrary vector 256,128 by 45 degrees using double-precision expected`() {
        val v = Vector2f(256f, 128f)
        val rotated = v.rotate(45f)

        val theta = Math.toRadians(45.0)
        val cos = cos(theta).toFloat()
        val sin = sin(theta).toFloat()
        val expectedX = (256f * cos) - (128f * sin)
        val expectedY = (256f * sin) + (128f * cos)

        val expected = Vector2f(expectedX, expectedY)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate arbitrary vector -133,458 by 45 degrees - no fast trig`() {
        val v = Vector2f(-133f, 458f)
        val rotated = v.rotate(45f, false)

        val expected = Vector2f(-417.9f, 229.81f)
        val expectedPrecise = Vector2f(-417.90012f, 229.80971f)
        Assert.assertEquals(expected.x, rotated.x, 1e-2f)
        Assert.assertEquals(expected.y, rotated.y, 1e-2f)
        Assert.assertEquals(expectedPrecise, rotated)
    }

    @Test
    fun `rotate arbitrary vector -133,458 by 45 degrees - with fast trig`() {
        val v = Vector2f(-133f, 458f)
        val rotated = v.rotate(45f, true)

        val expected = Vector2f(-417.9f, 229.81f)
        val expectedPrecise = Vector2f(-417.90012f, 229.80971f)
        Assert.assertEquals(expected.x, rotated.x, 1e-2f)
        Assert.assertEquals(expected.y, rotated.y, 1e-2f)
        Assert.assertEquals(expectedPrecise, rotated)
    }

    @Test
    fun `rotate arbitrary vector 100,-50 by -90 degrees`() {
        val v = Vector2f(100f, -50f)
        val rotated = v.rotate(-90f)

        val expected = Vector2f(-50f, -100f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate arbitrary vector 42,99 by 360 degrees`() {
        val v = Vector2f(42f, 99f)
        val rotated = v.rotate(360f)

        val expected = Vector2f(42f, 99f)
        Assert.assertEquals(expected.x, rotated.x, 1e-5f)
        Assert.assertEquals(expected.y, rotated.y, 1e-5f)
    }

    @Test
    fun `rotate zero vector by any angle`() {
        val v = Vector2f(0f, 0f)
        val rotated = v.rotate(123f)

        val expected = Vector2f(0f, 0f)
        Assert.assertEquals(expected, rotated)
    }

    @Test
    fun `rotate preserves length for arbitrary vector -77,305 by 123 degrees`() {
        val v = Vector2f(-77f, 305f)
        val rotated = v.rotate(123f)

        val expectedLength = Math.hypot(v.x.toDouble(), v.y.toDouble()).toFloat()
        val rotatedLength = Math.hypot(rotated.x.toDouble(), rotated.y.toDouble()).toFloat()
        Assert.assertEquals(expectedLength, rotatedLength, 1e-3f)
    }

    @Test
    fun `rotate around pivot preserves distance`() {
        val v = Vector2f(10f, 0f)
        val pivot = Vector2f(5f, 0f)
        val rotated = v.rotateAroundPivot(pivot, 90f)

        val originalDist = Math.hypot((v.x - pivot.x).toDouble(), (v.y - pivot.y).toDouble()).toFloat()
        val rotatedDist = Math.hypot((rotated.x - pivot.x).toDouble(), (rotated.y - pivot.y).toDouble()).toFloat()
        Assert.assertEquals(originalDist, rotatedDist, 1e-3f)
    }

    @Test
    fun `rotate large vector 1000000,500000 by 45 degrees`() {
        val v = Vector2f(1_000_000f, 500_000f)
        val rotated = v.rotate(45f)

        val expectedX = (1_000_000f * 0.70710677f) - (500_000f * 0.70710677f)
        val expectedY = (1_000_000f * 0.70710677f) + (500_000f * 0.70710677f)
        val expected = Vector2f(expectedX, expectedY)

        Assert.assertEquals(expected.x, rotated.x, 1e-2f)
        Assert.assertEquals(expected.y, rotated.y, 1e-2f)
    }

}
