package exoticatechnologies.util.datastructures.tests

import exoticatechnologies.util.datastructures.SynchronizedRingBuffer
import org.junit.After
import org.junit.Before
import org.junit.Test

class SynchronizedRingBufferTest {

    private lateinit var ringBuffer: SynchronizedRingBuffer<Integer>

    @Before
    fun setUp() {
        ringBuffer = SynchronizedRingBuffer<Integer>(10, Integer(-1), Integer::class.java)
    }

    @After
    fun tearDown() {

    }

    /**
     * Basic numbers: byte, short, int, long
     */

    @Test
    fun test_if_works_with_kotlin_byte() {
        SynchronizedRingBuffer(5, -1, Byte::class.java)
    }

    @Test
    fun test_if_works_with_kotlin_short() {
        SynchronizedRingBuffer(5, -1, Short::class.java)
    }

    @Test
    fun test_if_works_with_kotlin_int() {
        SynchronizedRingBuffer(5, -1, Int::class.java)
    }

    @Test
    fun test_if_works_with_kotlin_long() {
        SynchronizedRingBuffer(5, -1, Long::class.java)
    }

    /**
     * IEEE754 numbers: float, double
     */

    @Test
    fun test_if_works_with_kotlin_float() {
        SynchronizedRingBuffer(5, -1.0f, Float::class.java)
    }

    @Test
    fun test_if_works_with_kotlin_double() {
        SynchronizedRingBuffer(5, -1.0, Double::class.java)
    }

    @Test(timeout = 1000L)
    fun more_tests_with_kotlin_double() {
        val ring = SynchronizedRingBuffer(5, -1.0, Double::class.java)
        ring.setWaitingTimeoutMillis(100)

        ring.put(1.0)
        ring.put(2.0)
        assert(ring.get() == 1.0)
        assert(ring.get() == 2.0)
        assert(ring.get() == -1.0)
        assert(ring.get() == -1.0)
    }

    /**
     * General-purpose generic tests
     */

    @Test
    fun put() {
        ringBuffer.put(Integer(0))

        assert(ringBuffer.get().equals(Integer(0)))
    }

    @Test(timeout = 1000)
    fun get_returns_default_element_when_empty() {
        val ring = SynchronizedRingBuffer<Int>(5, -1, Int::class.java)
        ring.setWaitingTimeoutMillis(100)

        assert(ring.get().equals(-1))
        assert(ring.get().equals(-1))
        assert(ring.get().equals(-1))
    }

    @Test
    fun after_putting_elements_followed_by_getting_them_THEN_they_are_in_same_order_as_they_were_put_in() {
        ringBuffer.put(Integer(0))
        ringBuffer.put(Integer(1))
        ringBuffer.put(Integer(2))

        assert(ringBuffer.get().equals(Integer(0)))
        assert(ringBuffer.get().equals(Integer(1)))
        assert(ringBuffer.get().equals(Integer(2)))
    }

    @Test(timeout = 1000)
    fun when_putting_more_than_capacity_THEN_oldest_elements_are_overwritten_first() {
        val ring = SynchronizedRingBuffer<Int>(2, -1, Int::class.java)
        ring.setWaitingTimeoutMillis(100)

        ring.put(0)
        ring.put(1)
        ring.put(2)
        ring.put(3)
        ring.put(4)
        ring.put(5)

        // should have only 4,5 in there now
        assert(ring.get() == 4)
        assert(ring.get() == 5)
    }

    @Test
    fun test_if_search_works() {
        val ring = SynchronizedRingBuffer<Int>(15, -1, Int::class.java)
        for (i in 1..15) {
            ring.put(i)
        }

        val test = ring.find { value -> value == 15 }

        assert(test != null)
        assert(test == 15)
    }

    @Test
    fun after_searching_then_get_order_isnt_broken() {
        val ring = SynchronizedRingBuffer<Int>(15, -1, Int::class.java)
        for (i in 1..15) {
            ring.put(i)
        }

        assert(ring.get() == 1)
        assert(ring.get() == 2)

        val test = ring.find { value -> value == 15 }

        assert(test != null)
        assert(test == 15)

        assert(ring.get() == 3)
        assert(ring.get() == 4)
    }

    @Test
    fun after_capacity_is_rolled_over_THEN_get_still_returns_oldest_result() {
        val ring = SynchronizedRingBuffer<Int>(10, -1, Int::class.java)
        ring.setWaitingTimeoutMillis(100)

        for (i in 1..10) {
            ring.put(i)
        }
        // Oldest is 1,2,3

        // put 20, rolling over 1
        ring.put(20)

        //oldest is 2,3,4..10, then 20
        for (i in 2..10) assert(ring.get() == i)
        assert(ring.get() == 20)
    }

    /**
     * Waiting tests
     */
    @Test(timeout = 1000)
    fun waiting_order_from_javadoc_is_respected_WHEN_testing_empty() {
        val ring = SynchronizedRingBuffer<Int>(10, -1, Int::class.java)
        ring.setWaitingTimeoutMillis(1000000)
        ring.setNotEmptyTimeoutMillis(0)

        assert(ring.get() == -1)
    }

    @Test
    fun waiting_order_from_javadoc_is_respected_WHEN_testing_full() {
        val ring = SynchronizedRingBuffer<Int>(10, -1, Int::class.java)
        ring.setWaitingTimeoutMillis(1000000)
        ring.setRollOverTimeoutMillis(0)

        for (i in 1..10) {
            ring.put(i)
        }
        // Oldest is 1,2,3

        // put 20, rolling over 1
        ring.put(20)

        //oldest is 2,3,4..10, then 20
        for (i in 2..10) assert(ring.get() == i)
        assert(ring.get() == 20)
    }

    /**
     * Multithreaded tests
     */

    @Test
    fun multithreaded_test1_producer_and_consumer() {
        val buffer = SynchronizedRingBuffer<Int>(10, -1, Int::class.java)
        buffer.setWaitingTimeoutMillis(2000)
        buffer.setRollOverTimeoutMillis(2000)

        // Producer thread
        val producerThread = Thread {
            for (i in 0 until 10) {
                buffer.put(i)
                println("Produced: $i")
            }
        }

        // Consumer thread
        val consumerThread = Thread {
            for (i in 0 until 10) {
                val item = buffer.get()
                println("Consumed: $item")
            }
        }

        // Searching for an item - this part is flaky
        // there is *very* little chances of starting this thread just when the producer has put it in
        // and before the consumer has pulled it out, because searching just searches through the
        // current state at time of calling. Nothing can be produced nor consumed while search is ongoing.
//        var foundItem: Int? = null
//        val searchThread = Thread {
//            foundItem = buffer.find { it == 3 }
//            if (foundItem != null) {
//                println("Found item: $foundItem")
//            } else {
//                println("Item not found")
//            }
//        }

        // Start them in reverse order so that the waiting gets to show
        // but also start searching last so that it doesn't find nothing before anything gets produced

        consumerThread.start()
        producerThread.start()

        // Wait a little bit
        Thread.sleep(500)

        // Confirm that the threads have eaten everything up by getting and receiving a 'default value'
        assert(buffer.get() == -1)
    }

    @Test
    fun multithreaded_test2_producer_and_searcher() {
        val buffer = SynchronizedRingBuffer<Int>(10, -1, Int::class.java)
        buffer.setWaitingTimeoutMillis(2000)
        buffer.setRollOverTimeoutMillis(2000)

        // Producer thread
        val producerThread = Thread {
            for (i in 0 until 10) {
                buffer.put(i)
                println("Produced: $i")
            }
        }

        var foundItem: Int? = null
        val searchThread = Thread {
            Thread.sleep(100)
            foundItem = buffer.find { it == 3 }
            if (foundItem != null) {
                println("Found item: $foundItem")
            } else {
                println("Item not found")
            }
        }

        // Start them in reverse order so that the waiting gets to show
        // but also start searching last so that it doesn't find nothing before anything gets produced

        producerThread.start()
        searchThread.start()

        // Wait a little bit
        Thread.sleep(500)

        // Confirm that the item was found and that the first get() will return the first item - 0
        assert(foundItem != null)
        assert(foundItem == 3)
        assert(buffer.get() == 0)
    }

}