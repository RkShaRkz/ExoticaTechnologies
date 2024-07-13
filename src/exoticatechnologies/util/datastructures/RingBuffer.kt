package exoticatechnologies.util.datastructures

/**
 * Generic fixed-size RingBuffer which uses a [Array] as it's backing buffer
 *
 * Parameters:<br>
 * [size] - size of the ring buffer's backing array
 * [defaultValue] - element to use instead of **null** / empty element
 * [clazz] - necessary hack for reified generic templates to work
 */
class RingBuffer<T : Any>(private val size: Int, private val defaultValue: T, private val clazz: Class<T>) {
    private val buffer: Array<T> = createArray(size, clazz)
    private var head = 0
    private var tail = 0
    private var isFull = false

    fun put(item: T) {
        buffer[tail] = item
        if (isFull) {
            head = (head + 1) % size
        }
        tail = (tail + 1) % size
        isFull = tail == head
    }

    /**
     * Returns null only when empty to avoid throwing
     */
    fun get(): T? {
        if (head == tail && !isFull) return null
        val item = buffer[head]
        buffer[head] = defaultValue // Clear the slot
        head = (head + 1) % size
        isFull = false
        return item
    }

    // Find an item that satisfies a given criterion
    fun find(predicate: (T) -> Boolean): T? {
        for (i in 0 until size) {
            val index = (head + i) % size
            if (predicate(buffer[index])) {
                return buffer[index]
            }
        }
        return null
    }

    private fun <T : Any> createArray(size: Int, clazz: Class<T>): Array<T> {
        @Suppress("UNCHECKED_CAST")
        return java.lang.reflect.Array.newInstance(clazz, size) as Array<T>
    }
}


