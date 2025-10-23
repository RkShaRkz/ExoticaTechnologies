package exoticatechnologies.util.datastructures

import exoticatechnologies.util.reflect.JavaReflectionUtils
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * Slightly less generic fixed-size RingBuffer which uses a [Array] as it's backing buffer, and is also multi-producer
 * and multi-consumer capable while being thread-safe.
 *
 * **IMPORTANT NOTE:** when empty, it will wait for new elements to arrive before returning [defaultValue].
 * By default, it will wait **indefinetely** unless a timeout period is set (see [waitingTimeoutMillis])
 *
 * Unlike the [RingBuffer], this synchronized RingBuffer implementation doesn't like to neither rollover (overwrite old values)
 * nor underflow (return defaultValues when empty) and would rather wait for a value to be taken / inserted.
 *
 * Parameters:<br>
 * [size] - size of the ring buffer's backing array
 * [defaultValue] - element to use instead of **null** / empty element
 * [clazz] - necessary hack for reified generic templates to work
 */
class SynchronizedRingBuffer<T : Any>(private val size: Int, private val defaultValue: T, private val clazz: Class<T>) {

    private val buffer: Array<T> = createArray(size, clazz)
    private var head = 0
    private var tail = 0
    private var count = 0
    private var isFull = false

    private val lock = ReentrantLock()
    private val notFull: Condition = lock.newCondition()
    private val notEmpty: Condition = lock.newCondition()

    /**
     * General-purpose timeout value, in milliseconds.
     *
     * Will be used to wait for both removing-element-when-full before overwriting oldest element with a new one (in [put])
     * and inserting-element-when-empty before returning element of [defaultValue] (in [get])
     *
     * @see rollOverTimeoutMillis
     * @see notEmptyTimeoutMillis
     */
    private var waitingTimeoutMillis: Long? = null

    /**
     * Roll over timeout value, in milliseconds. How much to wait before overwriting oldest element when buffer is full.
     *
     * Will be used to wait for removing-element-when-full before overwriting oldest element with a new one (in [put])
     *
     * Takes precedence over [waitingTimeoutMillis] in case both are set
     */
    private var rollOverTimeoutMillis: Long? = null

    /**
     * Not empty timeout value, in milliseconds. How much to wait before returning an empty element when buffer is empty.
     *
     * Will be used to wait for inserting-element-when-empty before returning element of [defaultValue] (in [get])
     *
     * Takes precedence over [waitingTimeoutMillis] in case both are set
     */
    private var notEmptyTimeoutMillis: Long? = null

    /**
     * Puts an element into the buffer
     *
     * In case it has to roll over capacity, it will wait for [rollOverTimeoutMillis] or [waitingTimeoutMillis]
     * (in that order) before overwriting the oldest element
     *
     * @param item the item to put in
     */
    fun put(item: T) {
        lock.lock()
        try {
            waitingToAvoidOverflow@
            while (count == size) {
                var shouldBreak = false
                if (rollOverTimeoutMillis != null) {
                    rollOverTimeoutMillis?.let {
                        val nanos = TimeUnit.MILLISECONDS.toNanos(it)
                        val waitedTime = notFull.awaitNanos(nanos)
                        if (waitedTime <= 0) shouldBreak = true
                    }
                } else if (waitingTimeoutMillis != null) {
                    waitingTimeoutMillis?.let {
                        val nanos = TimeUnit.MILLISECONDS.toNanos(it)
                        val waitedTime = notFull.awaitNanos(nanos)
                        if (waitedTime <= 0) shouldBreak = true
                    }
                } else {
                    notFull.await()
                }

                if (shouldBreak) break@waitingToAvoidOverflow
            }
            buffer[tail] = item
            if (isFull) {
                head = (head + 1) % size
            }
            tail = (tail + 1) % size
            count++
            isFull = tail == head
            notEmpty.signal()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Retrieve and remove an item from the buffer
     *
     * In case it is currently empty, it will wait for [notEmptyTimeoutMillis] or [waitingTimeoutMillis]
     * (in that order) before returning the 'empty element' of [defaultValue] value.
     *
     * In case it's not empty, it simply returns the oldest element.
     */
    fun get(): T {
        lock.lock()
        try {
            waitingToAvoidStarvation@
            while (count == 0) {
                var shouldBreak = false
                if (notEmptyTimeoutMillis != null) {
                    notEmptyTimeoutMillis?.let {
                        val nanos = TimeUnit.MILLISECONDS.toNanos(it)
                        val waitedTime = notEmpty.awaitNanos(nanos)
                        if (waitedTime <= 0) shouldBreak = true
                    }
                } else if (waitingTimeoutMillis != null) {
                    waitingTimeoutMillis?.let {
                        val nanos = TimeUnit.MILLISECONDS.toNanos(it)
                        val waitedTime = notEmpty.awaitNanos(nanos)
                        if (waitedTime <= 0) shouldBreak = true
                    }
                } else {
                    notEmpty.await()
                }

                if (shouldBreak) break@waitingToAvoidStarvation
            }
            if (head == tail && !isFull) return defaultValue
            val item = buffer[head]
            buffer[head] = defaultValue
            head = (head + 1) % size
            count--
            isFull = false
            notFull.signal()
            return item
        } finally {
            lock.unlock()
        }
    }

    /**
     * Searches for the first item that satisfies a given criterion without messing the internal buffer order
     *
     * Doesn't touch [head] or [tail]
     *
     * **IMPORTANT NOTE:** It is noteworthy to mention that, due to synchronization, nothing can be produced
     * nor consumed while a search is ongoing, and as such - searching is locking, looking through the buffer
     * at time-of-calling, returning item or null, then unlocking the lock.
     *
     * @param predicate predicate that an item needs to satisfy to be returned
     * @return returns found item matching the [predicate] or *null* if no such item was found
     */
    fun find(predicate: (T) -> Boolean): T? {
        lock.lock()
        try {
            for (i in 0 until size) {
                val index = (head + i) % size
                if (predicate(buffer[index])) {
                    return buffer[index]
                }
            }
            return null
        } finally {
            lock.unlock()
        }
    }

    /**
     * Setter for [waitingTimeoutMillis]
     */
    fun setWaitingTimeoutMillis(newValue: Long) {
        waitingTimeoutMillis = newValue
    }

    /**
     * Setter for [rollOverTimeoutMillis]
     */
    fun setRollOverTimeoutMillis(newValue: Long) {
        rollOverTimeoutMillis = newValue
    }

    /**
     * Setter for [notEmptyTimeoutMillis]
     */
    fun setNotEmptyTimeoutMillis(newValue: Long) {
        notEmptyTimeoutMillis = newValue
    }

    private fun <T : Any> createArray(size: Int, clazz: Class<T>): Array<T> {
        if (clazz.equals(Int::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [I cannot be cast to [Ljava.lang.Object;
            return JavaReflectionUtils.newArrayInstance(java.lang.Integer::class.java, size) as Array<T>
        }
        if (clazz.equals(Float::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [F cannot be cast to [Ljava.lang.Object;
            return JavaReflectionUtils.newArrayInstance(java.lang.Float::class.java, size) as Array<T>
        }
        if (clazz.equals(Double::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [D cannot be cast to [Ljava.lang.Object;
            return JavaReflectionUtils.newArrayInstance(java.lang.Double::class.java, size) as Array<T>
        }
        if (clazz.equals(Long::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [J cannot be cast to [Ljava.lang.Object;
            return JavaReflectionUtils.newArrayInstance(java.lang.Long::class.java, size) as Array<T>
        }
        if (clazz.equals(Byte::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [B cannot be cast to [Ljava.lang.Object;
            return JavaReflectionUtils.newArrayInstance(java.lang.Byte::class.java, size) as Array<T>
        }
        if (clazz.equals(Short::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [S cannot be cast to [Ljava.lang.Object;
            return JavaReflectionUtils.newArrayInstance(java.lang.Short::class.java, size) as Array<T>
        }
        @Suppress("UNCHECKED_CAST")
        return JavaReflectionUtils.newArrayInstance(clazz, size) as Array<T>
    }
}
