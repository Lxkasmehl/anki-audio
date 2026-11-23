package com.lxkasmehl.ankiaudio.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.lxkasmehl.ankiaudio.content.Lang
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

interface Speaker {
    /** Spricht den Text und kehrt zurück, wenn er fertig ist. Abbruch der Coroutine stoppt die Ausgabe. */
    suspend fun speak(text: String, lang: Lang)
    fun stop()
}

class TtsUnavailableException(message: String) : IllegalStateException(message)

/**
 * Android-Systemstimme. Nimmt die Standard-Engine; klappt das nicht, wird jede installierte Engine
 * der Reihe nach probiert (manche Geräte haben eine Engine, aber keinen Standard gesetzt).
 */
class SystemTts(context: Context) : Speaker {
    private val appContext = context.applicationContext
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private val initLock = Mutex()
    private var tts: TextToSpeech? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = finish(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
    }

    private fun finish(id: String?) {
        id ?: return
        pending.remove(id)?.let { if (it.isActive) it.resume(Unit) }
    }

    /** Startet eine Engine (null = Standard) und wartet auf ihr Ergebnis. */
    private suspend fun create(engine: String?): TextToSpeech? {
        val status = CompletableDeferred<Int>()
        val instance = TextToSpeech(appContext, { status.complete(it) }, engine)
        val result = withTimeoutOrNull(8_000) { status.await() }
        if (result == TextToSpeech.SUCCESS) return instance
        instance.shutdown()
        return null
    }

    private suspend fun obtain(): TextToSpeech = initLock.withLock {
        tts?.let { return@withLock it }

        val default = create(null)
        val engines = default?.engines ?: TextToSpeech(appContext, {}).let { probe ->
            probe.engines.also { probe.shutdown() }
        }
        val chosen = default ?: engines.firstNotNullOfOrNull { create(it.name) }
            ?: throw TtsUnavailableException(
                if (engines.isEmpty()) {
                    "Auf dem Handy ist keine Sprachausgabe (TTS-Engine) installiert."
                } else {
                    "Sprachausgabe gefunden (${engines.joinToString { it.label }}), lässt sich aber nicht starten."
                },
            )

        chosen.setOnUtteranceProgressListener(listener)
        tts = chosen
        chosen
    }

    override suspend fun speak(text: String, lang: Lang) {
        if (text.isBlank()) return
        val engine = obtain()
        engine.language = lang.locale
        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            pending[id] = cont
            cont.invokeOnCancellation {
                pending.remove(id)
                engine.stop()
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    override fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
