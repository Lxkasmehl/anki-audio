package com.lxkasmehl.ankiaudio.content

/** Macht aus gerendertem Karten-HTML vorlesbaren Text. Reines Kotlin, damit es unit-testbar ist. */
object CardTextExtractor {

    private val ANSWER_SEPARATOR = Regex("""<hr[^>]*\bid\s*=\s*["']?answer["']?[^>]*>""", RegexOption.IGNORE_CASE)
    private val STYLE_OR_SCRIPT = Regex("""<(style|script)\b[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val SOUND = Regex("""\[sound:[^\]]*]""")
    private val TTS_TAG = Regex("""\[anki:tts[^\]]*].*?\[/anki:tts]""", RegexOption.DOT_MATCHES_ALL)
    private val CLOZE_BLANK = Regex("""\[(\.\.\.|…)]""")
    private val BLOCK_TAG = Regex("""<\s*(br|/div|/p|/li|/tr|/h[1-6]|hr|/ul|/ol|/table)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val ANY_TAG = Regex("""<[^>]+>""")
    private val NUMERIC_ENTITY = Regex("""&#(x?)([0-9a-fA-F]+);""")
    private val SENTENCE_END = setOf('.', '!', '?', ':', ';')

    private val NAMED_ENTITIES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&apos;" to "'", "&ndash;" to "-", "&mdash;" to "-",
        "&auml;" to "ä", "&ouml;" to "ö", "&uuml;" to "ü", "&Auml;" to "Ä",
        "&Ouml;" to "Ö", "&Uuml;" to "Ü", "&szlig;" to "ß", "&hellip;" to "…",
    )

    /** Die gerenderte Antwortseite enthält meist die Vorderseite + `<hr id=answer>`; nur den Teil danach behalten. */
    fun stripFrontFromAnswer(answerHtml: String): String {
        val match = ANSWER_SEPARATOR.find(answerHtml) ?: return answerHtml
        return answerHtml.substring(match.range.last + 1)
    }

    /**
     * HTML zu Sprechtext: Styles/Skripte/Sounds raus, Cloze-Lücken als [clozeWord],
     * Blöcke zu Sätzen, Entities dekodiert, Whitespace normalisiert.
     */
    fun toSpeech(html: String, clozeWord: String = "Lücke"): String {
        var s = html
        s = STYLE_OR_SCRIPT.replace(s, " ")
        s = COMMENT.replace(s, " ")
        s = TTS_TAG.replace(s, " ")
        s = SOUND.replace(s, " ")
        s = BLOCK_TAG.replace(s, "\n")
        s = ANY_TAG.replace(s, "")
        s = decodeEntities(s)
        s = CLOZE_BLANK.replace(s, clozeWord)

        val lines = s.lines()
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotEmpty() }
        return buildString {
            lines.forEachIndexed { i, line ->
                append(line)
                if (i < lines.lastIndex) append(if (line.last() in SENTENCE_END) " " else ". ")
            }
        }
    }

    /** Kürzt auf maxChars, möglichst an einem Satzende, sonst am letzten Leerzeichen. */
    fun truncate(text: String, maxChars: Int): String {
        if (maxChars <= 0 || text.length <= maxChars) return text
        val window = text.substring(0, maxChars)
        val sentenceCut = window.indexOfLast { it in SENTENCE_END }
        if (sentenceCut >= maxChars / 3) return window.substring(0, sentenceCut + 1).trim()
        val spaceCut = window.lastIndexOf(' ')
        return (if (spaceCut > 0) window.substring(0, spaceCut) else window).trim()
    }

    fun decodeEntities(input: String): String {
        var s = input
        NAMED_ENTITIES.forEach { (k, v) -> s = s.replace(k, v) }
        s = NUMERIC_ENTITY.replace(s) { m ->
            val radix = if (m.groupValues[1].isNotEmpty()) 16 else 10
            m.groupValues[2].toIntOrNull(radix)?.let { String(Character.toChars(it)) } ?: m.value
        }
        return s
    }
}
