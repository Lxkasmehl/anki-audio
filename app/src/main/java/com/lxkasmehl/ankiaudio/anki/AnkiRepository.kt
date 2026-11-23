package com.lxkasmehl.ankiaudio.anki

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class DeckInfo(val id: Long, val name: String, val due: Int)

/** Eine Karte, eindeutig über Notiz-ID + Kartentyp-Index. */
data class CardKey(val noteId: Long, val ord: Int)

data class DueCard(val key: CardKey, val buttonCount: Int)

data class CardContent(
    val key: CardKey,
    val cardName: String,
    val modelName: String,
    val fieldNames: List<String>,
    val fields: List<String>,
    val questionHtml: String,
    /** Antwortseite; kann die Vorderseite noch enthalten (wird im Extractor abgeschnitten). */
    val answerHtml: String,
)

class AnkiRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val modelCache = mutableMapOf<Long, Pair<String, List<String>>>()

    fun isAnkiDroidInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(AnkiContract.PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(AnkiContract.PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun decks(): List<DeckInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DeckInfo>()
        resolver.query(AnkiContract.Deck.CONTENT_ALL_URI, null, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.long(AnkiContract.Deck.DECK_ID) ?: continue
                val name = c.string(AnkiContract.Deck.DECK_NAME) ?: continue
                result += DeckInfo(id, name, parseDue(c.string(AnkiContract.Deck.DECK_COUNTS)))
            }
        }
        result.sortedBy { it.name.lowercase() }
    }

    /** Fällige Karten des Decks laut AnkiDroid-Scheduler, in Lernreihenfolge. */
    suspend fun dueCards(deckId: Long, limit: Int): List<DueCard> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DueCard>()
        resolver.query(
            AnkiContract.ReviewInfo.CONTENT_URI,
            null,
            "limit=?,deckID=?",
            arrayOf(limit.toString(), deckId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val noteId = c.long(AnkiContract.ReviewInfo.NOTE_ID) ?: continue
                val ord = c.long(AnkiContract.ReviewInfo.CARD_ORD)?.toInt() ?: continue
                val buttons = c.long(AnkiContract.ReviewInfo.BUTTON_COUNT)?.toInt() ?: 4
                result += DueCard(CardKey(noteId, ord), buttons)
            }
        }
        result
    }

    suspend fun content(key: CardKey): CardContent? = withContext(Dispatchers.IO) {
        var question = ""
        var answer = ""
        var answerPure: String? = null
        var cardName = ""
        val found = resolver.query(AnkiContract.cardUri(key.noteId, key.ord), null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use false
            question = c.string(AnkiContract.Card.QUESTION).orEmpty()
            answer = c.string(AnkiContract.Card.ANSWER).orEmpty()
            answerPure = c.string(AnkiContract.Card.ANSWER_PURE)
            cardName = c.string(AnkiContract.Card.CARD_NAME).orEmpty()
            true
        } ?: false
        if (!found) return@withContext null

        var mid = 0L
        var fields = emptyList<String>()
        resolver.query(AnkiContract.noteUri(key.noteId), null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                mid = c.long(AnkiContract.Note.MID) ?: 0L
                fields = c.string(AnkiContract.Note.FLDS).orEmpty().split(AnkiContract.FIELD_SEPARATOR)
            }
        }
        val (modelName, fieldNames) = model(mid)

        CardContent(
            key = key,
            cardName = cardName,
            modelName = modelName,
            fieldNames = fieldNames,
            fields = fields,
            questionHtml = question,
            answerHtml = answerPure ?: answer,
        )
    }

    /** Bewertet die Karte: ease 1 = Again, 2 = Hard, 3 = Good, 4 = Easy. */
    suspend fun answer(key: CardKey, ease: Int, timeTakenMs: Long): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(AnkiContract.ReviewInfo.NOTE_ID, key.noteId)
            put(AnkiContract.ReviewInfo.CARD_ORD, key.ord)
            put(AnkiContract.ReviewInfo.EASE, ease)
            put(AnkiContract.ReviewInfo.TIME_TAKEN, timeTakenMs)
        }
        resolver.update(AnkiContract.ReviewInfo.CONTENT_URI, values, null, null) > 0
    }

    private fun model(mid: Long): Pair<String, List<String>> = modelCache.getOrPut(mid) {
        if (mid == 0L) return@getOrPut "" to emptyList()
        resolver.query(AnkiContract.modelUri(mid), null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.string(AnkiContract.Model.NAME).orEmpty() to
                    c.string(AnkiContract.Model.FIELD_NAMES).orEmpty().split(AnkiContract.FIELD_SEPARATOR)
            } else {
                null
            }
        } ?: ("" to emptyList())
    }

    private fun parseDue(json: String?): Int = try {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).sumOf { arr.optInt(it) }
    } catch (_: Exception) {
        0
    }
}

private fun Cursor.string(column: String): String? {
    val i = getColumnIndex(column)
    return if (i >= 0 && !isNull(i)) getString(i) else null
}

private fun Cursor.long(column: String): Long? {
    val i = getColumnIndex(column)
    return if (i >= 0 && !isNull(i)) getLong(i) else null
}
