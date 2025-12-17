package exoticatechnologies.util.tests

import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.lwjgl.util.vector.Vector2f
import kotlin.math.sqrt
import kotlin.random.Random

@RunWith(Enclosed::class)
class RandomWhateverTests {

    class SqrtTests {


        @Test
        fun compare_regular_distance_vs_squared_distance_runtimes() {
//            val point = Vector2f(5f, 5f)
            val center = Vector2f(0f, 0f)
            val radius = 5f
            val iterations = 10
//            val iterations = 1_000
//            val iterations = 1_000_000
//            val iterations = 1_000_000_000
            val random = Random.Default

            val regular10kStart = System.nanoTime()
            for (i in 0 until iterations) {
//                regularDistanceCheck(point, center, radius)

                val point = Vector2f(random.nextFloat() * 10f, random.nextFloat() * 10f)
                regularDistanceCheck(point, center, radius)
            }
            val regular10kEnd = System.nanoTime()
            val regular10kDuration = regular10kEnd - regular10kStart
            val perItemRuntime = regular10kDuration / iterations
            val perItemMillis = nanosToMillis(perItemRuntime)

            println("Regular runs done, total runtime (nanos): ${regular10kDuration} (millis): ${nanosToMillis(regular10kDuration)}, per-item runtime (nanos): ${perItemRuntime}, per-item runtime (millis): ${perItemMillis}\tover ${iterations} iterations")

            val fast10kStart = System.nanoTime()
            for (i in 0 until iterations) {
//                regularDistanceCheck_fast(point, center, radius)

                val point = Vector2f(random.nextFloat() * 10f, random.nextFloat() * 10f)
                regularDistanceCheck_fast(point, center, radius)
            }
            val fast10kEnd = System.nanoTime()
            val fast10kDuration = fast10kEnd - fast10kStart
            val perItemRuntimeFast = fast10kDuration / iterations
            val perItemMillisFast = nanosToMillis(perItemRuntimeFast)

            println("Fast runs done, total runtime (nanos): ${fast10kDuration} (millis): ${nanosToMillis(fast10kDuration)}, per-item runtime (nanos): ${perItemRuntimeFast}, per-item runtime (millis): ${perItemMillisFast}\tover ${iterations} iterations")
            println("------------")
            val speedupRatio = regular10kDuration.toFloat() / fast10kDuration
            println("Speedup ratio: ${speedupRatio}")
        }

        fun regularDistanceCheck(point: Vector2f, center: Vector2f, radius: Float): Boolean {
            return sqrt((point.x.toDouble() - center.x) + (point.y.toDouble() - center.y)) <= radius
        }

        fun regularDistanceCheck_fast(point: Vector2f, center: Vector2f, radius: Float): Boolean {
            return (point.x - center.x) + (point.y - center.y) <= radius * radius
        }

        fun nanosToMillis(nanos: Long): Float {
            return nanos / 1000000f
        }
    }
}
