package com.lxkasmehl.ankiaudio.session

import android.view.KeyEvent

/**
 * Tastenbelegung bei eingeschaltetem Bildschirm.
 *
 * Vorderseite: Leertaste/Enter/Play/Lautstärke = aufdecken
 * Rückseite:   Lautstärke hoch = Good, Lautstärke runter = Again, Leertaste/Enter/Play = Good
 * Immer:       1/A = Again, 2/H = Hard, 3/G = Good, 4/E = Easy, R = wiederholen, S = überspringen
 */
object KeyMapper {

    private val REVEAL_KEYS = setOf(
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
    )

    fun map(keyCode: Int, phase: Phase): Command? {
        when (keyCode) {
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_A -> return Command.Rate(1)
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_H -> return Command.Rate(2)
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_G -> return Command.Rate(3)
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_E -> return Command.Rate(4)
            KeyEvent.KEYCODE_R -> return Command.Repeat
            KeyEvent.KEYCODE_S -> return Command.Skip
        }
        return when (phase) {
            Phase.FRONT -> when (keyCode) {
                in REVEAL_KEYS, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> Command.Reveal
                else -> null
            }
            Phase.BACK -> when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> Command.Rate(3)
                KeyEvent.KEYCODE_VOLUME_DOWN -> Command.Rate(1)
                in REVEAL_KEYS -> Command.Rate(3)
                else -> null
            }
            else -> null
        }
    }

    /** Nur während einer laufenden Karte schlucken wir Tasten (sonst normale Lautstärkeregelung). */
    fun isSessionPhase(phase: Phase) = phase == Phase.FRONT || phase == Phase.BACK
}
