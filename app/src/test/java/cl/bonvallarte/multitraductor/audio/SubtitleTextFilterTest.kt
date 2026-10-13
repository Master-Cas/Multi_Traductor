package cl.bonvallarte.multitraductor.audio

import org.junit.Assert.*
import org.junit.Test

class SubtitleTextFilterTest {
    @Test fun removesPathologicalRepeatedSpanishPhrases() {
        val text = ("Mi padre ".repeat(18) + "volvió hoy.")
        assertEquals("Mi padre volvió hoy.", SubtitleTextFilter.clean(text))
    }

    @Test fun removesRepeatedSourceWordsBeforeTranslation() {
        assertEquals("My father arrived.", SubtitleTextFilter.clean(
            "My father My father My father My father arrived."))
    }

    @Test fun keepsNaturalDistinctSentences() {
        assertEquals("Hola, ¿cómo estás? Estoy bien.",
            SubtitleTextFilter.clean("Hola, ¿cómo estás? Estoy bien."))
    }

    @Test fun languageAgnosticAndBounded() {
        assertEquals("안녕하세요 세계", SubtitleTextFilter.clean("안녕하세요 세계"))
        assertTrue(SubtitleTextFilter.clean("padre ".repeat(1000)).length <= 500)
    }

    @Test fun signaturesIgnorePunctuationButNotDifferentWords() {
        assertEquals(SubtitleTextFilter.signature("Hola, mundo!"),
            SubtitleTextFilter.signature("HOLA mundo"))
        assertNotEquals(SubtitleTextFilter.signature("Hola mundo"),
            SubtitleTextFilter.signature("Hola Chile"))
    }
}
