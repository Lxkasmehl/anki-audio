package com.lxkasmehl.ankiaudio.session

import com.lxkasmehl.ankiaudio.anki.AnkiRepository
import com.lxkasmehl.ankiaudio.anki.CardKey
import com.lxkasmehl.ankiaudio.anki.DeckInfo
import com.lxkasmehl.ankiaudio.content.CardPreparer
import com.lxkasmehl.ankiaudio.content.Lang
import com.lxkasmehl.ankiaudio.content.PreparedCard
import com.lxkasmehl.ankiaudio.content.ReadingSettings
import com.lxkasmehl.ankiaudio.tts.Feedback
import com.lxkasmehl.ankiaudio.tts.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Phase { IDLE, LOADING, FRONT, BACK, FINISHED, ERROR }

data class SessionState(
    val phase: Phase = Phase.IDLE,
    val deckName: String = "",
    val front: String = "",
    val back: String = "",
    val reviewed: Int = 0,
    val skipped: Int = 0,
    val lastSkipReason: String? = null,
    val message: String? = null,
    /** Fehler liegt an fehlender Sprachausgabe: UI bietet Links zu Einstellungen/Installation an. */
    val ttsMissing: Boolean = false,
)

sealed interface Command {
    data object Reveal : Command
    data object Repeat : Command
    data object Skip : Command
    /** ease 1 = Again, 2 = Hard, 3 = Good, 4 = Easy */
    data class Rate(val ease: Int) : Command
}

/**
 * Ablauf einer Audio-Session:
 * Vorderseite vorlesen -> warten auf "aufdecken" -> Rückseite vorlesen -> warten auf Bewertung -> nächste Karte.
 * Grafische Karten werden übersprungen und in AnkiDroid nicht angefasst (bleiben fällig).
 */
class SessionController(
    private val repo: AnkiRepository,
    private val speaker: Speaker,
    private val feedback: Feedback,
    private val scope: CoroutineScope,
    var settings: ReadingSettings = ReadingSettings(),
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var job: Job? = null
    private var commands = Channel<Command>(Channel.UNLIMITED)

    fun start(deck: DeckInfo) {
        job?.cancel()
        commands = Channel(Channel.UNLIMITED)
        val inbox = commands
        job = scope.launch {
            try {
                run(deck, inbox)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = Phase.ERROR,
                        message = e.message ?: e.toString(),
                        ttsMissing = e is com.lxkasmehl.ankiaudio.tts.TtsUnavailableException,
                    )
                }
            } finally {
                speaker.stop()
            }
        }
    }

    fun send(command: Command) {
        commands.trySend(command)
    }

    fun stop() {
        job?.cancel()
        job = null
        speaker.stop()
        _state.update { it.copy(phase = Phase.IDLE) }
    }

    private suspend fun run(deck: DeckInfo, inbox: Channel<Command>) = coroutineScope {
        _state.value = SessionState(phase = Phase.LOADING, deckName = deck.name)
        val skippedKeys = mutableSetOf<CardKey>()

        while (isActive) {
            // Immer frisch beim Scheduler nachfragen; übersprungene Karten stehen weiter vorne in der Queue.
            val due = repo.dueCards(deck.id, skippedKeys.size + 1).firstOrNull { it.key !in skippedKeys }
            if (due == null) {
                val s = _state.value
                _state.update { it.copy(phase = Phase.FINISHED, front = "", back = "") }
                speaker.speak(
                    "Fertig. ${s.reviewed} Karten gelernt, ${s.skipped} übersprungen.",
                    settings.defaultLang,
                )
                return@coroutineScope
            }

            val content = repo.content(due.key)
            val card = content?.let { CardPreparer.prepare(it, settings) }
            if (card == null || card.skipReason != null) {
                skippedKeys += due.key
                _state.update {
                    it.copy(skipped = it.skipped + 1, lastSkipReason = card?.skipReason ?: "Karte nicht lesbar")
                }
                feedback.skipped()
                continue
            }

            when (val outcome = playCard(card, due.buttonCount, inbox)) {
                is Outcome.Rated -> {
                    _state.update { it.copy(reviewed = it.reviewed + 1, message = null) }
                    feedback.rated(outcome.ease)
                }
                Outcome.Skipped -> {
                    skippedKeys += card.key
                    _state.update { it.copy(skipped = it.skipped + 1, lastSkipReason = "Von dir übersprungen") }
                    feedback.skipped()
                }
            }
        }
    }

    private sealed interface Outcome {
        data class Rated(val ease: Int) : Outcome
        data object Skipped : Outcome
    }

    private suspend fun playCard(card: PreparedCard, buttonCount: Int, inbox: Channel<Command>): Outcome =
        coroutineScope {
            val startedAt = System.currentTimeMillis()
            var speech: Job? = null
            fun say(text: String, lang: Lang) {
                speech?.cancel()
                speech = launch { speaker.speak(text, lang) }
            }
            suspend fun rate(ease: Int): Outcome {
                speech?.cancel()
                val ok = repo.answer(card.key, mapEase(ease, buttonCount), System.currentTimeMillis() - startedAt)
                if (!ok) _state.update { it.copy(message = "Bewertung wurde von AnkiDroid nicht angenommen") }
                return Outcome.Rated(ease)
            }

            // Vorderseite
            _state.update { it.copy(phase = Phase.FRONT, front = card.front, back = "") }
            say(card.front, card.frontLang)
            while (true) {
                when (val c = inbox.receive()) {
                    Command.Reveal -> break
                    Command.Repeat -> say(card.front, card.frontLang)
                    Command.Skip -> { speech?.cancel(); return@coroutineScope Outcome.Skipped }
                    is Command.Rate -> return@coroutineScope rate(c.ease)
                }
            }

            // Rückseite
            _state.update { it.copy(phase = Phase.BACK, back = card.back) }
            say(card.back, card.backLang)
            while (true) {
                when (val c = inbox.receive()) {
                    Command.Reveal, Command.Repeat -> say(card.back, card.backLang)
                    Command.Skip -> { speech?.cancel(); return@coroutineScope Outcome.Skipped }
                    is Command.Rate -> return@coroutineScope rate(c.ease)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Outcome.Skipped
        }

    companion object {
        /** Ältere Scheduler zeigen bei Lernkarten nur 2 oder 3 Knöpfe; die 4er-Skala darauf abbilden. */
        fun mapEase(ease: Int, buttonCount: Int): Int = when (buttonCount) {
            2 -> if (ease == 1) 1 else 2
            3 -> when (ease) { 1 -> 1; 4 -> 3; else -> 2 }
            else -> ease.coerceIn(1, 4)
        }
    }
}
