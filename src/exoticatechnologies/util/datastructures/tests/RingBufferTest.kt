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

    @Test
    fun put() {
        ringBuffer.put(Integer(0))
        ringBuffer.put(Integer(1))
        ringBuffer.put(Integer(2))

//        val test = ringBuffer.get()
        assert(ringBuffer.get().equals(Integer(0)))
        assert(ringBuffer.get().equals(Integer(1)))
        assert(ringBuffer.get().equals(Integer(2)))
    }

    @Test
    fun test_if_works_with_kotlin_int() {
        val ring = RingBuffer(5, -1, Int::class.java)
    }
}