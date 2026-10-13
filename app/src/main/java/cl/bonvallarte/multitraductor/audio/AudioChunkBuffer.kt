package cl.bonvallarte.multitraductor.audio

import java.util.ArrayDeque

/**
 * Continuous, non-blocking audio window queue. AudioRecord must never wait
 * for Whisper/ML Kit and must not silently discard read remainders.
 */
internal class AudioChunkBuffer(
    private val chunkSize: Int = 16_000 * 5,
    private val capacity: Int = 3,
    private val minVoicedSamples: Int = 16_000 / 2
) {
    init {
        require(chunkSize > 0 && capacity > 0 && minVoicedSamples in 0..chunkSize)
    }

    internal data class Chunk(val sequence: Long, val samples: FloatArray)

    private val lock = Any()
    private val pending = ArrayDeque<Chunk>()
    private var filling = FloatArray(chunkSize)
    private var used = 0
    private var voiced = 0
    private var sequence = 0L
    private var dropped = 0L

    /** Enqueue every complete voiced window; retain all partial window samples. */
    fun append(pcm: ShortArray, count: Int, dbfs: Double): Int = synchronized(lock) {
        val valid = count.coerceIn(0, pcm.size)
        var index = 0
        var enqueued = 0
        while (index < valid) {
            val take = minOf(chunkSize - used, valid - index)
            for (offset in 0 until take)
                filling[used + offset] = pcm[index + offset] / 32768.0f
            if (dbfs > -43.0) voiced += take
            index += take
            used += take
            if (used == chunkSize) {
                sequence++
                if (voiced >= minVoicedSamples) {
                    if (pending.size >= capacity) {
                        pending.removeFirst()
                        dropped++
                    }
                    pending.addLast(Chunk(sequence, filling))
                    enqueued++
                }
                filling = FloatArray(chunkSize)
                used = 0
                voiced = 0
            }
        }
        enqueued
    }

    fun poll(): Chunk? = synchronized(lock) {
        if (pending.isEmpty()) null else pending.removeFirst()
    }
    fun pendingCount(): Int = synchronized(lock) { pending.size }
    fun droppedCount(): Long = synchronized(lock) { dropped }
    fun clear() = synchronized(lock) {
        pending.clear()
        filling = FloatArray(chunkSize)
        used = 0
        voiced = 0
        dropped = 0
        sequence = 0L
    }
}
