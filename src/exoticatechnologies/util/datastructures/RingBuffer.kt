package exoticatechnologies.util.datastructures

/**
 * Generic fixed-size RingBuffer which uses a [Array] as it's backing buffer.
 *
 * A FIFO (First In First Out) "endless" data structure that rolls over and overwrites
 * oldest members when capacity is reached instead of throwing like default Kotlin's
 * [kotlin.collections.RingBuffer] implementation
 *
 * Parameters:
 * [size] - size of the ring buffer's backing array
 * [defaultValue] - element to use instead of **null** / empty element
 * [clazz] - necessary hack for reified generic templates to work
 */
class RingBuffer<T : Any>(private val size: Int, private val defaultValue: T, private val clazz: Class<T>) {
    private val buffer: Array<T> = createArray(size, clazz)
    private var head = 0
    private var tail = 0
    private var isFull = false

    /**
     * Puts an element into the buffer
     *
     * In case it has to roll over capacity, it will overwrite the oldest element
     *
     * @param item the item to put in
     */
    fun put(item: T) {
        buffer[tail] = item
        if (isFull) {
            head = (head + 1) % size
        }
        tail = (tail + 1) % size
        isFull = tail == head
    }

    /**
     * Gets the firstly put (oldest) element
     *
     * Returns [defaultValue] only when empty to avoid throwing
     */
    fun get(): T {
        if (head == tail && !isFull) return defaultValue
        val item = buffer[head]
        buffer[head] = defaultValue // Clear the slot
        head = (head + 1) % size
        isFull = false
        return item
    }

    /**
     * Searches for the first item that satisfies a given criterion without messing the internal buffer order
     *
     * Doesn't touch [head] or [tail]
     *
     * @param predicate predicate that an item needs to satisfy to be returned
     * @return returns found item matching the [predicate] or *null* if no such item was found
     */
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
        if (clazz.equals(Int::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [I cannot be cast to [Ljava.lang.Object;
            return java.lang.reflect.Array.newInstance(java.lang.Integer::class.java, size) as Array<T>
        }
        if (clazz.equals(Float::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [F cannot be cast to [Ljava.lang.Object;
            return java.lang.reflect.Array.newInstance(java.lang.Float::class.java, size) as Array<T>
        }
        if (clazz.equals(Double::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [D cannot be cast to [Ljava.lang.Object;
            return java.lang.reflect.Array.newInstance(java.lang.Double::class.java, size) as Array<T>
        }
        if (clazz.equals(Long::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [J cannot be cast to [Ljava.lang.Object;
            return java.lang.reflect.Array.newInstance(java.lang.Long::class.java, size) as Array<T>
        }
        if (clazz.equals(Byte::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [B cannot be cast to [Ljava.lang.Object;
            return java.lang.reflect.Array.newInstance(java.lang.Byte::class.java, size) as Array<T>
        }
        if (clazz.equals(Short::class.java)) {
            // These need to be boxed to avoid java.lang.ClassCastException: [S cannot be cast to [Ljava.lang.Object;
            return java.lang.reflect.Array.newInstance(java.lang.Short::class.java, size) as Array<T>
        }
        @Suppress("UNCHECKED_CAST")
        return java.lang.reflect.Array.newInstance(clazz, size) as Array<T>
    }
}


