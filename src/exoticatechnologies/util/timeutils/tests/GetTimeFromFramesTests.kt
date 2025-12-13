package exoticatechnologies.util.timeutils.tests

import exoticatechnologies.util.timeutils.getTimeFromFrames
import org.junit.Test

class GetTimeFromFramesTests {

    @Test
    fun given_10_seconds_of_frames_the_function_returns_10_seconds() {
        val frames: Float = 10f * FRAMES_PER_SECOND
        val string = getTimeFromFrames(frames)

        assert(string == "00:00:10")
    }

    @Test
    fun given_70_seconds_of_frames_the_function_returns_1_minute_10_seconds() {
        val frames: Float = 70f * FRAMES_PER_SECOND
        val string = getTimeFromFrames(frames)

        assert(string == "00:01:10")
    }

    @Test
    fun given_3670_seconds_of_frames_the_function_returns_1_hour_1_minute_10_seconds() {
        val frames: Float = 3670f * FRAMES_PER_SECOND
        val string = getTimeFromFrames(frames)

        assert(string == "01:01:10")
    }

    @Test
    fun given_45296_seconds_of_frames_the_function_returns_12_hours_34_minutes_56_seconds() {
//        val frames: Float = 45396f * FRAMES_PER_SECOND    //fuck you chatgpt and your lousy math
        val frames: Float = 45296f * FRAMES_PER_SECOND
        val string = getTimeFromFrames(frames)

        assert(string == "12:34:56")
    }

    companion object {
        const val FRAMES_PER_SECOND = 60
    }
}