package com.lxkasmehl.ankiaudio.tts

import android.media.AudioManager
import android.media.ToneGenerator

/** Kurze Signaltöne, damit man ohne Bildschirm weiß, was passiert ist. */
class Feedback {
    private val tones = ToneGenerator(AudioManager.STREAM_MUSIC, 70)

    fun rated(ease: Int) {
        val tone = if (ease == 1) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_ACK
        tones.startTone(tone, 150)
    }

    fun skipped() {
        tones.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
    }

    fun release() {
        tones.release()
    }
}
