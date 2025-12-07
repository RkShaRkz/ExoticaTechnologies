package exoticatechnologies.util.tests

import exoticatechnologies.modifications.exotics.ExoticsHandler
import exoticatechnologies.modifications.exotics.types.ExoticType
import exoticatechnologies.util.InLogicalRangeWorkMode
import exoticatechnologies.util.StacktraceUtils
import exoticatechnologies.util.isInLogicalRange
import exoticatechnologies.util.wrapAroundMod
import org.junit.Assert
import org.junit.Test

class UtilTests {

    @Test
    fun test_that_ExoticType_stringifyTypes_works() {
        val stringifiedExoticTypes = ExoticType.stringifyTypes()

        println("Exotic types: "+stringifiedExoticTypes)

        assert(stringifiedExoticTypes.isNotEmpty())
    }

    @Test
    fun test_that_ExoticHandler_stringifyExotics_works() {
        val stringifiedExotics = ExoticsHandler.stringifyExotics()

        println("Exotics: "+stringifiedExotics)

        // IMPORTANT: This test is flaky, because the exotics are populated from
        // Global.getSettings().getMergedJSONForMod(...)
        // and as such, can never really work and show true results outside of the game!!!

        Assert.assertNotNull(stringifiedExotics)
    }

    @Test
    fun test_that_StackTraceUtils_unwindStackTrace_works() {
        val exception = Exception()
        val stringifiedStacktrace = StacktraceUtils.unwindStacktrace(exception.stackTrace)

        exception.printStackTrace()
        System.err.println("Stacktrace:\n"+stringifiedStacktrace)

        Assert.assertNotNull(stringifiedStacktrace)
    }

    @Test
    fun test_that_StackTraceUtils_unwindStacktraceFromException_works() {
        val exception = Exception()
        val stringifiedStacktrace = StacktraceUtils.unwindStacktraceFromException(exception)

        exception.printStackTrace()
        System.err.println(stringifiedStacktrace)

        Assert.assertNotNull(stringifiedStacktrace)
    }

    @Test
    fun test_that_StackTraceUtils_unwindStacktraceFromException_works_for_Throwables_too() {
        val throwable = Throwable("Hello from my test Throwable")
        val stringifiedStacktrace = StacktraceUtils.unwindStacktraceFromException(throwable)

        throwable.printStackTrace()
        System.err.println(stringifiedStacktrace)

        Assert.assertNotNull(stringifiedStacktrace)
    }

    @Test
    fun test_what_happens_when_a_outofbound_number_is_cast_to_Short() {
        val number1:Int = -65535
        val test1: Short = number1.toShort()
        val expected1: Short = 1.toShort()

        val number2: Double = 65536.toDouble()
        // Since we can't do this, lets go the roundabout way...
//        val test2 = number2.toShort()
        val test2: Short = number2.toInt().toShort()
        val expected2: Short = 0.toShort()

        Assert.assertEquals(expected1, test1)
        Assert.assertEquals(expected2, test2)
    }

    @Test
    fun test_what_happens_in_the_absurd_case_mentioned_in_isInLogicalRange_javadoc() {
        val number: Short = 123
        val left: Int = -65535
        val right: Double = 65536.toDouble()

        val test1 = number.isInLogicalRange(left, right, InLogicalRangeWorkMode.LESS_OR_EQUAL)
        val test2 = number.isInLogicalRange(left, right, InLogicalRangeWorkMode.GREATER_OR_EQUAL)

        Assert.assertEquals(false, test1)
        Assert.assertEquals(false, test2)
    }


    @Test
    fun test_when_decrementing_40_from_20_for_size_72_using_wraparoundmod_then_we_get_52() {
        val size = 72
        val start = 20
        val decrement = 40

        val raw = start - decrement       // 20 - 40 = -20
        val result = wrapAroundMod(raw, size)       // wrap into [0, 71]

        println("Start=$start, Decrement=$decrement, Raw=$raw, Wrapped=$result")

        // Expected: -20 wrapped into 52
        Assert.assertEquals(52, result)
    }

}
