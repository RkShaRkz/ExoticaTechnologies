package exoticatechnologies.util.timeutils.tests

import exoticatechnologies.util.timeutils.getTimeFromSeconds
import org.junit.Test

class GetTimeFromSecondsTests {
    @Test
    fun given_10_seconds_the_function_returns_10_seconds() {
        val string = getTimeFromSeconds(10f)

        assert(string == "00:00:10")
    }

    @Test
    fun given_70_seconds_the_function_returns_1_minute_10_seconds() {
        val string = getTimeFromSeconds(70f)

        assert(string == "00:01:10")
    }

    @Test
    fun given_3670_seconds_the_function_returns_1_hour_1_minute_10_seconds() {
        val string = getTimeFromSeconds(3670f)

        assert(string == "01:01:10")
    }

    @Test
    fun given_45296_seconds_the_function_returns_12_hours_34_minutes_56_seconds() {
        val string = getTimeFromSeconds(45296f)

        assert(string == "12:34:56")
    }
}