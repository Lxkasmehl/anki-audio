package com.lxkasmehl.ankiaudio.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.lxkasmehl.ankiaudio.content.Lang
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

interface Speaker {
    /** Spricht den Text und kehrt zurück, wenn er fertig ist. Abbruch der Coroutine stoppt die Ausgabe. */
    suspend fun speak(text: String, lang: Lang)
    fun stop()
}

/** Android-Systemstimme (z.B. Google TTS). Fallback, solange es keine gecachte Cloud-Stimme gibt. */
class SystemTts(context: Context) : Speaker {
    private val ready = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
    }

    private fun finish(id: String?) {
        id ?: return
        pending.remove(id)?.let { if (it.isActive) it.resume(Unit) }
    }

    override suspend fun speak(text: String, lang: Lang) {
        if (text.isBlank()) return
        check(ready.await()) { "Sprachausgabe nicht verfügbar. Ist eine TTS-Engine installiert?" }
        tts.language = lang.locale
        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            pending[id] = cont
            cont.invokeOnCancellation {
                pending.remove(id)
                tts.stop()
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    override fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
