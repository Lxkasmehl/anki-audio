package com.lxkasmehl.ankiaudio.anki

import android.net.Uri

/**
 * Spalten und URIs des AnkiDroid-ContentProviders (FlashCardsContract).
 * Bewusst selbst definiert statt über die JitPack-Bibliothek, um eine Abhängigkeit zu sparen.
 * Quelle: AnkiDroid api/src/main/java/com/ichi2/anki/FlashCardsContract.kt
 */
object AnkiContract {
    const val PACKAGE = "com.ichi2.anki"
    const val AUTHORITY = "com.ichi2.anki.flashcards"
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    const val FIELD_SEPARATOR = '\u001f'

    private val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")

    object Deck {
        val CONTENT_ALL_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")
        const val DECK_ID = "deck_id"
        const val DECK_NAME = "deck_name"
        /** JSON-Array: [learn, review, new] */
        const val DECK_COUNTS = "deck_count"
    }

    object ReviewInfo {
        val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "schedule")
        const val NOTE_ID = "note_id"
        const val CARD_ORD = "ord"
        const val BUTTON_COUNT = "button_count"
        const val EASE = "answer_ease"
        const val TIME_TAKEN = "time_taken"
    }

    object Note {
        val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes")
        const val MID = "mid"
        const val FLDS = "flds"
    }

    object Card {
        const val CARD_NAME = "card_name"
        const val QUESTION = "question"
        const val ANSWER = "answer"
        const val ANSWER_PURE = "answer_pure"
    }

    object Model {
        val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "models")
        const val NAME = "name"
        const val FIELD_NAMES = "field_names"
    }

    fun noteUri(noteId: Long): Uri = Uri.withAppendedPath(Note.CONTENT_URI, noteId.toString())

    fun modelUri(mid: Long): Uri = Uri.withAppendedPath(Model.CONTENT_URI, mid.toString())

    fun cardUri(noteId: Long, ord: Int): Uri =
        noteUri(noteId).buildUpon()
            .appendPath("cards")
            .appendPath(ord.toString())
            .build()
}
