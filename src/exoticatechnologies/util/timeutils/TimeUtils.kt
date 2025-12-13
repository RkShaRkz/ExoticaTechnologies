package exoticatechnologies.util.timeutils

import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.floor

/**
 * Returns locale-dependant local time by calling [System.currentTimeMillis]
 * and formatting it in a "HH:mm:ss" format, ex. 12:34:56
 */
fun getClockTime() : String {
    val currentMillis = System.currentTimeMillis()
    val currentTime = Date(currentMillis)

    val timeFormatter = SimpleDateFormat("HH:mm:ss")
    return timeFormatter.format(currentTime)
}

/**
 * If frames are being used anywhere, and the game counts 1 second
 * as 60 frames - this method will return a formatted time extracted
 * from those frames in a "HH:mm:ss" format.
 *
 * Internally calls [getTimeFromSeconds]
 */
fun getTimeFromFrames(frames: Float): String {
    // The system runs in constant 60FPS so 60 frames is equal to 1 second.
    // Having that in mind, we just need to divide frames by 60 to get the number of seconds
    val FRAMES_PER_SECOND = 60

    val baseSeconds = floor(frames / FRAMES_PER_SECOND)
    return getTimeFromSeconds(baseSeconds = baseSeconds)
}

/**
 * Returns a "HH:mm:ss" format of duration represented by [baseSeconds]
 */
fun getTimeFromSeconds(baseSeconds: Float): String {
    val SECONDS_IN_MINUTE = 60
    val SECONDS_IN_HOUR = 60 * SECONDS_IN_MINUTE

    // hours is just base seconds divided by number of seconds in an hour
    val hours = (baseSeconds / SECONDS_IN_HOUR).toInt()
    // get the seconds leftover after calculating hours, then divide that into minutes
    val minutes = ((baseSeconds % SECONDS_IN_HOUR) / SECONDS_IN_MINUTE).toInt()
    // and finally, the seconds are what's left over from hours and minutes
    val seconds = ((baseSeconds % SECONDS_IN_HOUR) % SECONDS_IN_MINUTE).toInt()

    // then, return the hh:mm:ss patterned string
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}