package cl.bonvallarte.multitraductor.audio

import kotlin.math.sqrt
import kotlin.math.log10

/** dBFS of mono PCM16; -90 dBFS is the display floor. */
object AudioLevelMeter {
    fun dbfs(samples: ShortArray, count: Int): Double {
        val n = count.coerceAtMost(samples.size)
        if (n <= 0) return -90.0
        var sum = 0.0
        for (i in 0 until n) {
            val v = samples[i].toDouble() / 32768.0
            sum += v * v
        }
        val rms = sqrt(sum / n)
        return if (rms <= 1e-9) -90.0 else (20.0 * log10(rms)).coerceAtLeast(-90.0)
    }
}
