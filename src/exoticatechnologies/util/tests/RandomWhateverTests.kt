package exoticatechnologies.util.tests

import com.fs.starfarer.api.combat.*
import exoticatechnologies.util.tests.utils.WeaponAPIUtils.createAnonymousWeaponAPI
import org.junit.Assert
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

    class FakeShipWeaponTests {
        @Test
        fun validate_whether_getLargestDamageContributingRange_works() {
            val expectedMostDamagingRange = 400f
            // Create a list of 4x400 range strong guns and 1x2000 range weak gun
            val weaponList = getListOfFourShortrangeStrongGunsAndOneLongrangeWeakGun()

            // Evaluate
            val mostDamagingRange = fakeGetLargestDamageContributingRange(weaponList)

            // Assert
            Assert.assertEquals(expectedMostDamagingRange, mostDamagingRange)
        }

        //DUPLICATED FROM ShipAttractorSystem !!!
        fun fakeGetLargestDamageContributingRange(weaponList: List<WeaponAPI>): Float{
            // Create a range to "weapon damage potential" map
            val rangeDamageMap = mutableMapOf<Float, Float>()
            // For all weapons on installing ship
            for (weapon in weaponList) {
                // If weapon is broken, skip it
                if (weapon.isDisabled || weapon.isPermanentlyDisabled) continue
//                weapon.derivedStats.sustainedDps
                // Otherwise, grab it's DPS, and calculate the "damage contribution" over 10 seconds
                val weaponDps = weapon.derivedStats.dps
                val weapon10secPotential = weaponDps * 10f

                val weaponRange = weapon.range
                // Add to range in the map
                val currentRangeDamageValue = rangeDamageMap[weaponRange] ?: 0f
                // Update map
                rangeDamageMap[weaponRange] = currentRangeDamageValue + weapon10secPotential
            }

            // After all weapons were processed, grab the key with the highest value
            val largestEntry = rangeDamageMap.maxByOrNull { it.value }
            return if (largestEntry != null) {
                largestEntry.key
            } else {
                // no maximum was found, fallback to 0
                0f
            }
        }

        fun getListOfFourShortrangeStrongGunsAndOneLongrangeWeakGun(): List<WeaponAPI> {

            return listOf(
                createAnonymousWeaponAPI(
                    range = 400f,
                    dps = 200f
                ),
                createAnonymousWeaponAPI(
                    range = 400f,
                    dps = 200f
                ),
                createAnonymousWeaponAPI(
                    range = 400f,
                    dps = 200f
                ),
                createAnonymousWeaponAPI(
                    range = 400f,
                    dps = 200f
                ),
                createAnonymousWeaponAPI(
                    range = 2000f,
                    dps = 75f
                )
            )
        }
    }
}
