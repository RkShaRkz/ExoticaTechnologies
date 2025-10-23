package exoticatechnologies.util.datastructures.tests

import exoticatechnologies.util.datastructures.RingBuffer
import org.junit.After
import org.junit.Before
import org.junit.Test

class RingBufferTest {

    private lateinit var ringBuffer: RingBuffer<Integer>

    @Before
    fun setUp() {
        ringBuffer = RingBuffer<Integer>(10, Integer(-1), Integer::class.java)
    }

    @After
    fun tearDown() {

    }

    /**
     * Basic numbers: byte, short, int, long
     */

    @Test
    fun test_if_works_with_kotlin_byte() {
        RingBuffer(5, -1, Byte::class.java)
    }

    @Test
    fun test_if_works_with_kotlin_short() {
        RingBuffer(5, -1, Short::class.java)
    }

    @Test
    fun test_if_works_with_kotlin_int() {
        RingBuffer(5, -1, Int::class.java)
    }

    @Test
    fun test_if_works_with_kotlin_long() {
        RingBuffer(5, -1, Long::class.java)
    }

    /**
     * IEEE754 numbers: float, double
     */

    @Test
    fun test_if_works_with_kotlin_float() {
        RingBuffer(5, -1.0f, Float::class.java)
    }

    @Test
    fun test_if_works_with_kotlin_double() {
        RingBuffer(5, -1.0, Double::class.java)
    }

    @Test
    fun more_tests_with_kotlin_double() {
        val ring = RingBuffer(5, -1.0, Double::class.java)

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

    @Test
    fun get_returns_default_element_when_empty() {
        val ring = RingBuffer<Int>(5, -1, Int::class.java)

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

    @Test
    fun when_putting_more_than_capacity_THEN_oldest_elements_are_overwritten_first() {
        val ring = RingBuffer<Int>(2, -1, Int::class.java)
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
        val ring = RingBuffer<Int>(15, -1, Int::class.java)
        for(i in 1..15) {
            ring.put(i)
        }

        val test = ring.find { value -> value == 15 }

        assert(test != null)
        assert(test == 15)
    }

    @Test
    fun after_searching_then_get_order_isnt_broken() {
        val ring = RingBuffer<Int>(15, -1, Int::class.java)
        for(i in 1..15) {
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
        val ring = RingBuffer<Int>(10, -1, Int::class.java)

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
}