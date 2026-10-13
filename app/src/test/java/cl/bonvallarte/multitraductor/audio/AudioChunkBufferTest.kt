package cl.bonvallarte.multitraductor.audio

import org.junit.Assert.*
import org.junit.Test

class AudioChunkBufferTest {
    @Test fun retainsRemainderAtEveryBoundary() {
        val b = AudioChunkBuffer(chunkSize = 10, capacity = 3, minVoicedSamples = 1)
        val first = ShortArray(12) { (it + 1).toShort() }
        assertEquals(1, b.append(first, first.size, -10.0))
        val c1 = b.poll()!!
        assertEquals(1L, c1.sequence)
        assertEquals(10, c1.samples.size)
        assertEquals(10 / 32768f, c1.samples.last(), 0.000001f)
        val next = ShortArray(8) { (13 + it).toShort() }
        assertEquals(1, b.append(next, next.size, -10.0))
        val c2 = b.poll()!!
        assertEquals(2L, c2.sequence)
        assertEquals(11 / 32768f, c2.samples.first(), 0.000001f)
        assertEquals(20 / 32768f, c2.samples.last(), 0.000001f)
        assertEquals(0L, b.droppedCount())
    }

    @Test fun captureKeepsEnqueuingWhileRecognitionIsBusy() {
        val b = AudioChunkBuffer(chunkSize = 10, capacity = 3, minVoicedSamples = 1)
        b.append(ShortArray(10) { 10 }, 10, -12.0)
        val inProgress = b.poll()!! // Reader is processing this chunk.
        b.append(ShortArray(20) { 20 }, 20, -12.0)
        assertEquals(2, b.pendingCount())
        assertEquals(10 / 32768f, inProgress.samples[0], 0.000001f)
        assertEquals(20 / 32768f, b.poll()!!.samples[0], 0.000001f)
        assertEquals(20 / 32768f, b.poll()!!.samples[0], 0.000001f)
        assertEquals(0L, b.droppedCount())
    }

    @Test fun overloadDropsOnlyOldestPendingWindow() {
        val b = AudioChunkBuffer(chunkSize = 5, capacity = 3, minVoicedSamples = 1)
        repeat(5) { n -> b.append(ShortArray(5) { (n + 1).toShort() }, 5, -10.0) }
        assertEquals(3, b.pendingCount())
        assertEquals(2L, b.droppedCount())
        assertEquals(3L, b.poll()!!.sequence)
        assertEquals(4L, b.poll()!!.sequence)
        assertEquals(5L, b.poll()!!.sequence)
    }

    @Test fun silenceIsNotSentToWhisperAndPartialDataIsCleared() {
        val b = AudioChunkBuffer(chunkSize = 10, capacity = 2, minVoicedSamples = 4)
        b.append(ShortArray(10), 10, -90.0)
        assertNull(b.poll())
        b.append(ShortArray(10) { 9 }, 10, -12.0)
        assertEquals(2L, b.poll()!!.sequence)
        b.append(ShortArray(4) { 9 }, 4, -12.0)
        b.clear()
        assertEquals(0, b.pendingCount())
        b.append(ShortArray(10) { 1 }, 10, -12.0)
        assertEquals(1L, b.poll()!!.sequence)
    }

    @Test fun clampsCountToAvailableSamplesWithoutDropping() {
        val b = AudioChunkBuffer(chunkSize = 3, capacity = 3, minVoicedSamples = 1)
        assertEquals(1, b.append(shortArrayOf(4,5,6), 10, -12.0))
        assertEquals(3, b.poll()!!.samples.size)
    }
}
