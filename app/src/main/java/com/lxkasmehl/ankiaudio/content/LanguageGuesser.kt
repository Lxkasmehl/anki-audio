package com.lxkasmehl.ankiaudio.content

import java.util.Locale

enum class Lang(val locale: Locale, val clozeWord: String) {
    DE(Locale.GERMANY, "Lücke"),
    EN(Locale.US, "blank"),
}

/**
 * Einfache Deutsch/Englisch-Erkennung über Umlaute und häufige Wörter.
 * Platzhalter bis ML Kit Language ID (Schritt 5 im Plan); reicht für typische Kartentexte.
 */
object LanguageGuesser {

    private val GERMAN_WORDS = setOf(
        "der", "die", "das", "und", "ist", "nicht", "ein", "eine", "einen", "einem", "mit", "von", "zu",
        "auf", "für", "wie", "was", "wer", "wo", "welche", "welcher", "welches", "im", "den", "dem", "des",
        "sich", "auch", "oder", "hauptstadt", "land", "sind", "wird", "werden", "nach", "bei", "aus", "kein",
    )
    private val ENGLISH_WORDS = setOf(
        "the", "and", "is", "of", "to", "in", "with", "what", "which", "who", "where", "how", "capital",
        "country", "a", "an", "are", "does", "do", "for", "on", "this", "that", "it", "be", "by", "from", "not",
    )
    private val WORD = Regex("""[\p{L}']+""")

    fun guess(text: String, default: Lang): Lang {
        if (text.isBlank()) return default
        var de = text.count { it in "äöüÄÖÜß" } * 2
        var en = 0
        WORD.findAll(text.lowercase()).forEach { m ->
            if (m.value in GERMAN_WORDS) de++
            if (m.value in ENGLISH_WORDS) en++
        }
        return when {
            de > en -> Lang.DE
            en > de -> Lang.EN
            else -> default
        }
    }
}
