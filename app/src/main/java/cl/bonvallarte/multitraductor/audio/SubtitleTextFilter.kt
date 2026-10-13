package cl.bonvallarte.multitraductor.audio

import java.util.Locale

/** Protects subtitles against Whisper's well-known looping hallucinations. */
internal object SubtitleTextFilter {
    fun clean(raw: String): String {
        val tokens = raw.trim()
            .replace(Regex("[\\p{Cc}\\p{Cf}]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(90)
        if (tokens.isEmpty()) return ""
        val normalized = tokens.map { token ->
            token.trim { !it.isLetterOrDigit() }.lowercase(Locale.ROOT)
        }
        val output = ArrayList<String>()
        var index = 0
        while (index < tokens.size) {
            var bestSize = 0
            var bestRepeat = 0
            val maxWords = minOf(8, (tokens.size - index) / 3)
            for (size in 1..maxWords) {
                if (normalized.subList(index, index + size).any { it.isBlank() }) continue
                var repeats = 1
                while (index + (repeats + 1) * size <= tokens.size &&
                    normalized.subList(index, index + size) ==
                    normalized.subList(index + repeats * size, index + (repeats + 1) * size)
                ) repeats++
                if (repeats >= 3 && size * repeats > bestSize * bestRepeat) {
                    bestSize = size
                    bestRepeat = repeats
                }
            }
            if (bestSize > 0) {
                output.addAll(tokens.subList(index, index + bestSize))
                index += bestSize * bestRepeat
            } else {
                output.add(tokens[index++])
            }
        }
        return output.joinToString(" ").take(500).trim()
    }

    fun signature(raw: String): String = clean(raw)
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() || it.isWhitespace() }
        .replace(Regex("\\s+"), " ")
        .trim()
}
