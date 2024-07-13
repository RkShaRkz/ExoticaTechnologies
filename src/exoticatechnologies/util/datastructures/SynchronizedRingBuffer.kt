package exoticatechnologies.util.datastructures

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

/**
 * Slightly less generic fixed-size RingBuffer which uses a [Array] as it's backing buffer, and is also multi-producer
 * and multi-consumer capable while being thread-safe.
 *
 * Parameters:<br>
 * [size] - size of the ring buffer's backing array
 * [defaultValue] - element to use instead of **null** / empty element
 * [clazz] - necessary hack for reified generic templates to work
 */
class SynchronizedRingBuffer<T: Any>(private val size: Int, private val defaultValue: T, private val clazz: Class<T>) {

    private val buffer: Array<T> = createArray(size, clazz)
    private var head = 0
    private var tail = 0
    private var count = 0

    private val lock = ReentrantLock()
    private val notFull: Condition = lock.newCondition()
    private val notEmpty: Condition = lock.newCondition()

    // Add an item to the buffer
    fun put(item: T) {
        lock.lock()
        try {
            while (count == size) {
                notFull.await()
            }
            buffer[tail] = item
            tail = (tail + 1) % size
            count++
            notEmpty.signal()
        } finally {
            lock.unlock()
        }
    }

    // Retrieve and remove an item from the buffer
    fun take(): T {
        lock.lock()
        try {
            while (count == 0) {
                notEmpty.await()
            }
            val item = buffer[head]
            buffer[head] = defaultValue
            head = (head + 1) % size
            count--
            notFull.signal()
            @Suppress("UNCHECKED_CAST")
            return item as T
        } finally {
            lock.unlock()
        }
    }

    // Find an item that satisfies a given criterion
    fun find(predicate: (T) -> Boolean): T? {
        lock.lock()
        try {
            for (i in 0 until count) {
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

    private fun <T : Any> createArray(size: Int, clazz: Class<T>): Array<T> {
        @Suppress("UNCHECKED_CAST")
        return java.lang.reflect.Array.newInstance(clazz, size) as Array<T>
    }
}
