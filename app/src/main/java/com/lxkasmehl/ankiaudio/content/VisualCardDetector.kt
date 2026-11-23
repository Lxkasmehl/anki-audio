package com.lxkasmehl.ankiaudio.content

/**
 * Erkennt Karten, die nur grafisch funktionieren (Flaggen, Karten anklicken, Image Occlusion).
 * Heuristik für den Fall, dass für den Notiztyp keine eigene Leseregel existiert.
 */
object VisualCardDetector {

    private val IMAGE = Regex("""<img\b""", RegexOption.IGNORE_CASE)
    /** Elemente, die praktisch nie Deko sind, sondern Inhalt bzw. Interaktion (Weltkarte zum Anklicken etc.). */
    private val INTERACTIVE = Regex("""<(svg|canvas|iframe|object|embed|video)\b""", RegexOption.IGNORE_CASE)

    /** Bis zu so vielen Zeichen gilt Text neben einem Bild als bloße Beschriftung ("Welches Land?"). */
    const val CAPTION_MAX_CHARS = 25

    /** @return Grund fürs Überspringen, oder null wenn die Karte vorlesbar ist. */
    fun skipReason(
        questionHtml: String,
        answerHtml: String,
        modelName: String,
        questionText: String = CardTextExtractor.toSpeech(questionHtml),
        answerText: String = CardTextExtractor.toSpeech(answerHtml),
    ): String? {
        if (modelName.contains("image occlusion", ignoreCase = true)) return "Image Occlusion"
        if (INTERACTIVE.containsMatchIn(questionHtml)) return "Grafik/Interaktion auf der Vorderseite"
        if (IMAGE.containsMatchIn(questionHtml) && questionText.length <= CAPTION_MAX_CHARS) {
            return "Bild auf der Vorderseite"
        }
        if (questionText.isBlank()) return "Vorderseite ohne Text"
        if ((IMAGE.containsMatchIn(answerHtml) || INTERACTIVE.containsMatchIn(answerHtml)) && answerText.isBlank()) {
            return "Antwort ist nur eine Grafik"
        }
        return null
    }
}
