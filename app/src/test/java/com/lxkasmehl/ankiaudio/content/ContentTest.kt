package com.lxkasmehl.ankiaudio.content

import com.lxkasmehl.ankiaudio.anki.CardContent
import com.lxkasmehl.ankiaudio.anki.CardKey
import com.lxkasmehl.ankiaudio.session.SessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextExtractorTest {

    @Test
    fun `strips tags, styles, sounds and decodes entities`() {
        val html = """<style>.card{color:red}</style><div>Was ist die Hauptstadt von <b>Peru</b>?</div>[sound:peru.mp3]&nbsp;"""
        assertEquals("Was ist die Hauptstadt von Peru?", CardTextExtractor.toSpeech(html))
    }

    @Test
    fun `block elements become sentences`() {
        val html = "<div>Lima</div><div>gegründet 1535</div>"
        assertEquals("Lima. gegründet 1535", CardTextExtractor.toSpeech(html))
    }

    @Test
    fun `cloze blank is spoken as word`() {
        val html = """Die Hauptstadt von Peru ist <span class=cloze>[...]</span>."""
        assertEquals("Die Hauptstadt von Peru ist Lücke.", CardTextExtractor.toSpeech(html, "Lücke"))
    }

    @Test
    fun `answer keeps only part after hr id answer`() {
        val html = """Frage<hr id=answer>Antwort"""
        assertEquals("Antwort", CardTextExtractor.stripFrontFromAnswer(html))
        assertEquals("ohne trenner", CardTextExtractor.stripFrontFromAnswer("ohne trenner"))
    }

    @Test
    fun `numeric entities`() {
        assertEquals("Ä & é", CardTextExtractor.decodeEntities("&#196; &amp; &#xe9;"))
    }

    @Test
    fun `truncate cuts at sentence end`() {
        val text = "Lima. Die Stadt wurde 1535 von Pizarro gegründet und ist heute sehr groß."
        // Satzende zu früh (< 1/3 des Limits) -> am Leerzeichen schneiden
        assertEquals("Lima. Die Stadt wurde 1535", CardTextExtractor.truncate(text, 30))
        assertEquals("Lima. Die Stadt wurde 1535 von Pizarro gegründet und ist heute sehr groß.", CardTextExtractor.truncate(text, 80))
        assertEquals(text, CardTextExtractor.truncate(text, 0))
        val noSentence = "eins zwei drei vier fünf sechs sieben"
        assertEquals("eins zwei drei", CardTextExtractor.truncate(noSentence, 16))
    }
}

class VisualCardDetectorTest {

    @Test
    fun `flag card is skipped`() {
        val q = """<div class="flag"><img src="ug-flag-germany.svg"></div>"""
        assertNotNull(VisualCardDetector.skipReason(q, "Deutschland", "Ultimate Geography"))
    }

    @Test
    fun `flag with short caption is skipped`() {
        val q = """Welches Land?<br><img src="flag.png">"""
        assertNotNull(VisualCardDetector.skipReason(q, "Deutschland", "Basic"))
    }

    @Test
    fun `interactive world map is skipped even with long text`() {
        val q = """Klicke auf der Weltkarte auf das Land Deutschland<svg viewBox="0 0 10 10"></svg>"""
        assertNotNull(VisualCardDetector.skipReason(q, "", "Map Click"))
    }

    @Test
    fun `answer that is only a map is skipped`() {
        assertNotNull(VisualCardDetector.skipReason("Wo liegt Peru?", """<img src="map-peru.png">""", "Basic"))
    }

    @Test
    fun `image occlusion is skipped`() {
        assertNotNull(VisualCardDetector.skipReason("Was ist das hier für ein Organ?", "Leber", "Image Occlusion Enhanced"))
    }

    @Test
    fun `plain text card is read`() {
        assertNull(VisualCardDetector.skipReason("Hauptstadt von Peru?", "Lima", "Basic"))
    }

    @Test
    fun `text card with decorative image and long question is read`() {
        val q = """Welcher Kanzler führte die Agenda 2010 ein? <img src="deko.png">"""
        assertNull(VisualCardDetector.skipReason(q, "Gerhard Schröder", "Basic"))
    }
}

class LanguageGuesserTest {

    @Test
    fun german() {
        assertEquals(Lang.DE, LanguageGuesser.guess("Was ist die Hauptstadt von Österreich?", Lang.EN))
    }

    @Test
    fun english() {
        assertEquals(Lang.EN, LanguageGuesser.guess("What is the capital of Austria?", Lang.DE))
    }

    @Test
    fun `single unknown word falls back to default`() {
        assertEquals(Lang.DE, LanguageGuesser.guess("Lima", Lang.DE))
        assertEquals(Lang.EN, LanguageGuesser.guess("Lima", Lang.EN))
    }
}

class CardPreparerTest {

    private fun card(q: String, a: String, model: String = "Basic") = CardContent(
        key = CardKey(1, 0), cardName = "Card 1", modelName = model,
        fieldNames = emptyList(), fields = emptyList(), questionHtml = q, answerHtml = a,
    )

    @Test
    fun `back side drops repeated front and long notes are cut`() {
        val notes = "Lima ist die Hauptstadt. " + "Sehr lange Notiz. ".repeat(30)
        val p = CardPreparer.prepare(
            card("Hauptstadt von Peru?", "Hauptstadt von Peru?<hr id=answer>$notes"),
            ReadingSettings(maxBackChars = 60),
        )
        assertNull(p.skipReason)
        assertEquals("Hauptstadt von Peru?", p.front)
        assertTrue(p.back.startsWith("Lima ist die Hauptstadt."))
        assertTrue(p.back.length <= 60)
    }

    @Test
    fun `english card gets english voice and cloze word`() {
        val p = CardPreparer.prepare(
            card("""The capital of Peru is <span class=cloze>[...]</span>""", "The capital of Peru is Lima"),
            ReadingSettings(defaultLang = Lang.DE),
        )
        assertEquals(Lang.EN, p.frontLang)
        assertEquals("The capital of Peru is blank", p.front)
    }
}

class EaseMappingTest {
    @Test
    fun mapping() {
        assertEquals(3, SessionController.mapEase(3, 4))
        assertEquals(2, SessionController.mapEase(2, 3))
        assertEquals(3, SessionController.mapEase(4, 3))
        assertEquals(2, SessionController.mapEase(4, 2))
        assertEquals(1, SessionController.mapEase(1, 2))
    }
}
