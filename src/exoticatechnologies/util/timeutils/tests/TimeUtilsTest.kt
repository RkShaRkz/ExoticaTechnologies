package exoticatechnologies.util.timeutils.tests

import exoticatechnologies.util.timeutils.getClockTime
import exoticatechnologies.util.timeutils.getTimeFromFrames
import exoticatechnologies.util.timeutils.getTimeFromSeconds
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun test_that_getClockTime_works() {
        val clockTime = getClockTime()

        val regex = Regex("\\d{2}:\\d{2}:\\d{2}")

        assert(clockTime.matches(regex))
    }





    companion object {
        const val FRAMES_PER_SECOND = 60
    }
}