package exoticatechnologies.util.tests

import com.fs.starfarer.api.combat.*
import exoticatechnologies.util.*
import exoticatechnologies.util.tests.utils.ShipAPIUtils
import exoticatechnologies.util.tests.utils.WeaponAPIUtils.createAnonymousWeaponAPI
import org.junit.Assert
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.lwjgl.util.vector.Vector2f
import kotlin.math.abs
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

    class VectorVelocityTests {
        @Test
        fun `compare velocity methods with 10 unit distance`() {
            val from = Vector2f(0f, 0f)
            val to = Vector2f(10f, 0f)
            val time = 2.0f

            // Standard method: displacement / time = (10, 0) / 2 = (5, 0)
            val result1 = getVelocityVector(from, to, time)

            // Flawed method: direction * (speed / distance)
            // direction = (1, 0), speed = 10 / 2 = 5, distance = 10
            // result = (1, 0) * (5 / 10) = (0.5, 0)
            val result2 = calculateVelocityVector(from, to, time)

            // Verifying standard physics (should be 5.0)
            Assert.assertEquals("getVelocityVector should result in x=5.0", 5.0f, result1.x)

            // Verifying the flaw in calculateVelocityVector (results in 0.5)
            // Google insists that `calculateVelocityVector()` is broken, and this test is there to prove that it's not broken
            // the fact that both methods return the exact same results is more worrying, but out of scope of this test
            Assert.assertNotEquals("calculateVelocityVector results in x=0.5 because it divides by distance twice", 0.5f, result2.x)

            // This assertion proves they are not equal
            // Except Google was wrong like I kept telling him, and the methods are exactly the same. Different kind of problem though.
            Assert.assertEquals(result1.x, result2.x)
        }
    }

    class AngleTests {
        @Test
        fun `compare whether angle remapping works between user-centric and trigonometric`() {
            // Trigonometric angles start at the "east" aka lies on the X-axis to the right
            val trigonometricZeroAngle = 0f
            // And that is considered 90-degrees in our 'user-centric' angle system
            val expectedConvertedAngle1 = 90f

            val remappedTrigonometricZeroAngle = remapAngleToTrigonometricCoordinateSystem(trigonometricZeroAngle)
            Assert.assertEquals("Trigonometric to UserCentric remapping broken - should have been 90", expectedConvertedAngle1, remappedTrigonometricZeroAngle)

            // User-centric angles start at the "north" aka lies on the Y-axis to the up
            val userCentricZeroAngle = 0f
            // And that is considered 90 degrees in regular trigonometric angle system
            val expectedConvertedAngle2 = 90f

            val remappedUserCentricZeroAngle = remapAngleToTrigonometricCoordinateSystem(userCentricZeroAngle)
            Assert.assertEquals("UserCentric to Trigonometric remapping broken - should have been 90", expectedConvertedAngle2, remappedUserCentricZeroAngle)
        }
    }

    class FacingTests {
        @Test
        fun `compare normal facing versus get facing towards vector`() {
            // Lets start off with a ship at (0,0), facing east (90 trig)
            // and check facing towards a target that is right (east) of us (10,0)

            val ourShipLocation = Vector2f(0f,0f)
            val ourShip = ShipAPIUtils.createAnonymousShipAPI(
                location = ourShipLocation,
                facing = 90f
            )

            val targetShip1Location = Vector2f(10f, 0f)
            val targetShip1 = ShipAPIUtils.createAnonymousShipAPI(
                location = targetShip1Location,
                facing = 0f
            )

            val facingTowardsTarget1 = calculateFacingTo(ourShipLocation, targetShip1Location)
            val shipFacingTowardsTarget1 = ourShip.getFacingTo(targetShip1)
            val angleFromOurToTargetShip1 = ourShip.getAngleDeltaToAnotherShip(targetShip1, true)
            val expectedFacingTowardsTarget1 = 0f
            val expectedRotationDeltaTowardsTarget1 = 90f

            // validate that our facing is 90 (trig) and facing towards target is 0 (trig)
            Assert.assertEquals("Our ship facing should have been 90", 90f, ourShip.facing)
            Assert.assertEquals("getFacingTo() doesn't work right, should have been 90 for this case", expectedFacingTowardsTarget1, facingTowardsTarget1)
            Assert.assertEquals("ShipAPI.getFacingTo() doesn't work right, should have been 90 for this case", expectedFacingTowardsTarget1, shipFacingTowardsTarget1)
            Assert.assertEquals("ShipAPI.getAngleDeltaToAnotherShip() doesn't work right, should have been 90 for this case", expectedRotationDeltaTowardsTarget1, angleFromOurToTargetShip1)

            //----------------------------------------------------------------------
            // now, lets see what is going on when the target is below (south of) us
            //----------------------------------------------------------------------

            val targetShip2Location = Vector2f(0f, -10f)
            val targetShip2 = ShipAPIUtils.createAnonymousShipAPI(
                location = targetShip2Location,
                facing = 0f
            )

            val facingTowardsTarget2 = calculateFacingTo(ourShipLocation, targetShip2Location)
            val shipFacingTowardsTarget2 = ourShip.getFacingTo(targetShip2)
            val angleFromOurToTargetShip2 = ourShip.getAngleDeltaToAnotherShip(targetShip2, true)
            val expectedFacingTowardsTarget2 = 270f //south is 270 trig
            val expectedRotationDeltaTowardsTarget2 = 180f  // since we're looking north and should turn south, that'd be 180

            // validate that our facing is 90 (trig) and facing towards target is 0 (trig)
            Assert.assertEquals("getFacingTo() doesn't work right, should have been 270 for this case", expectedFacingTowardsTarget2, facingTowardsTarget2)
            Assert.assertEquals("ShipAPI.getFacingTo() doesn't work right, should have been 270 for this case", expectedFacingTowardsTarget2, shipFacingTowardsTarget2)
            // Since the angle will turn out to be either 180 or -180, we will abs it so the test isn't flaky
            // and it doesn't really matter if the ship thinks he needs to turn to the left or to the right - only that it should flip around
            Assert.assertEquals("ShipAPI.getAngleDeltaToAnotherShip() doesn't work right, should have been 180 for this case", expectedRotationDeltaTowardsTarget2, abs(angleFromOurToTargetShip2))

            //----------------------------------------------------------------------
            // now, lets see what is going on when the target is left (west) of us
            //----------------------------------------------------------------------

            val targetShip3Location = Vector2f(-100f, 0f)
            val targetShip3 = ShipAPIUtils.createAnonymousShipAPI(
                location = targetShip3Location,
                facing = 0f
            )

            val facingTowardsTarget3 = calculateFacingTo(ourShipLocation, targetShip3Location)
            val shipFacingTowardsTarget3 = ourShip.getFacingTo(targetShip3)
            val angleFromOurToTargetShip3 = ourShip.getAngleDeltaToAnotherShip(targetShip3, true)
            val expectedFacingTowardsTarget3 = 180f //west is 180 trig
            val expectedRotationDeltaTowardsTarget3 = -90f  // since we're looking north and should turn west, that'd be -90

            // validate that facing towards target is 180 (trig)
            Assert.assertEquals("getFacingTo() doesn't work right, should have been 180 for this case", expectedFacingTowardsTarget3, facingTowardsTarget3)
            Assert.assertEquals("ShipAPI.getFacingTo() doesn't work right, should have been 180 for this case", expectedFacingTowardsTarget3, shipFacingTowardsTarget3)
            Assert.assertEquals("ShipAPI.getAngleDeltaToAnotherShip() doesn't work right, should have been -90 for this case", expectedRotationDeltaTowardsTarget3, angleFromOurToTargetShip3)

            //----------------------------------------------------------------------
            // now, lets see what is going on when the target is above (north of) us
            //----------------------------------------------------------------------

            val targetShip4Location = Vector2f(0f, 100f)
            val targetShip4 = ShipAPIUtils.createAnonymousShipAPI(
                location = targetShip4Location,
                facing = 0f
            )

            val facingTowardsTarget4 = calculateFacingTo(ourShipLocation, targetShip4Location)
            val shipFacingTowardsTarget4 = ourShip.getFacingTo(targetShip4)
            val angleFromOurToTargetShip4 = ourShip.getAngleDeltaToAnotherShip(targetShip4, true)
            val expectedFacingTowardsTarget4 = 90f //north is 90 trig
            val expectedRotationDeltaTowardsTarget4 = 0f  // since we're looking north and should turn north, that'd be -0

            // validate that facing towards target is 90 (trig)
            Assert.assertEquals("getFacingTo() doesn't work right, should have been 90 for this case", expectedFacingTowardsTarget4, facingTowardsTarget4)
            Assert.assertEquals("ShipAPI.getFacingTo() doesn't work right, should have been 90 for this case", expectedFacingTowardsTarget4, shipFacingTowardsTarget4)
            Assert.assertEquals("ShipAPI.getAngleDeltaToAnotherShip() doesn't work right, should have been 0 for this case", expectedRotationDeltaTowardsTarget4, angleFromOurToTargetShip4)
        }
    }

}
