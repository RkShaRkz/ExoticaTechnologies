package exoticatechnologies.util.tests

import exoticatechnologies.modifications.exotics.ExoticsHandler
import exoticatechnologies.modifications.exotics.types.ExoticType
import exoticatechnologies.util.StacktraceUtils
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

        // IMPORTANT: This test is flaky, because the exotics are populated from
        // Global.getSettings().getMergedJSONForMod(...)
        // and as such, can never really work and show true results outside of the game!!!

        Assert.assertNotNull(stringifiedStacktrace)
    }
}