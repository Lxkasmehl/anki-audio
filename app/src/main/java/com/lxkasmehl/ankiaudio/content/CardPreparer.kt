package com.lxkasmehl.ankiaudio.content

import com.lxkasmehl.ankiaudio.anki.CardContent
import com.lxkasmehl.ankiaudio.anki.CardKey

data class ReadingSettings(
    /** Rückseiten länger als das werden gekürzt (lange Notizen). 0 = nie kürzen. */
    val maxBackChars: Int = 200,
    val defaultLang: Lang = Lang.DE,
)

data class PreparedCard(
    val key: CardKey,
    val front: String,
    val frontLang: Lang,
    val back: String,
    val backLang: Lang,
    /** Gesetzt, wenn die Karte nicht vorlesbar ist und übersprungen wird. */
    val skipReason: String?,
)

/** Macht aus dem Karteninhalt die beiden Sprechtexte. Reine Funktion, unit-testbar. */
object CardPreparer {

    fun prepare(card: CardContent, settings: ReadingSettings): PreparedCard {
        val answerHtml = CardTextExtractor.stripFrontFromAnswer(card.answerHtml)

        val frontPlain = CardTextExtractor.toSpeech(card.questionHtml, clozeWord = "")
        val backPlain = CardTextExtractor.toSpeech(answerHtml)
        val frontLang = LanguageGuesser.guess(frontPlain, settings.defaultLang)
        val backLang = LanguageGuesser.guess(backPlain, frontLang)

        return PreparedCard(
            key = card.key,
            front = CardTextExtractor.toSpeech(card.questionHtml, frontLang.clozeWord),
            frontLang = frontLang,
            back = CardTextExtractor.truncate(backPlain, settings.maxBackChars),
            backLang = backLang,
            skipReason = VisualCardDetector.skipReason(
                questionHtml = card.questionHtml,
                answerHtml = answerHtml,
                modelName = card.modelName,
                questionText = frontPlain,
                answerText = backPlain,
            ),
        )
    }
}
