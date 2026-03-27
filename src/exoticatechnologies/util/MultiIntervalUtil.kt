package exoticatechnologies.util

import com.fs.starfarer.api.util.IntervalUtil

/**
 * Very similar to [IntervalUtil] except this one is meant to be used with very small intervals,
 * and does not allow for intervals to be "lost" in a case of a FPS drop when the 'amount' we get
 * in [advance] is very big, and is big enough for one-or-more intervals to have passed.
 *
 * In a case that more than one interval has passed, the [onIntervalElapsed] lambda will be executed
 * as many times as the number of intervals that have passed
 */
class MultiIntervalUtil(private var currInterval: Float) {
    private var elapsed: Float = 0f
    private var intervalsPassed: Int = 0

    private var onIntervalElapsed: (() -> Unit)? = null

    fun forceCurrInterval(value: Float) {
        currInterval = value
    }

    fun getElapsed(): Float = elapsed

    fun advance(amount: Float) {
        elapsed += amount

        // Deduct intervals and increment counter
        while (elapsed >= currInterval) {
            elapsed -= currInterval
            intervalsPassed++
        }

        // Consume the counter: run callback exactly that many times
        val counter = intervalsPassed
        for (i in 0 until counter) {
            onIntervalElapsed?.invoke()
            intervalsPassed--
        }
        // At this point intervalsPassed == 0, elapsed holds leftover time
    }

    fun intervalElapsed(): Boolean = intervalsPassed > 0

    fun onIntervalElapsed(action: () -> Unit) {
        this.onIntervalElapsed = action
    }

    fun getIntervalDuration(): Float = currInterval

    fun setElapsed(elapsed: Float) {
        this.elapsed = elapsed
    }
}
