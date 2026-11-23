package com.lxkasmehl.ankiaudio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lxkasmehl.ankiaudio.anki.AnkiRepository
import com.lxkasmehl.ankiaudio.anki.DeckInfo
import com.lxkasmehl.ankiaudio.session.Command
import com.lxkasmehl.ankiaudio.session.KeyMapper
import com.lxkasmehl.ankiaudio.session.SessionController
import com.lxkasmehl.ankiaudio.tts.Feedback
import com.lxkasmehl.ankiaudio.tts.SystemTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val ankiInstalled: Boolean = true,
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    val decks: List<DeckInfo> = emptyList(),
    val error: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AnkiRepository(app)
    private val tts = SystemTts(app)
    private val feedback = Feedback()
    val session = SessionController(repo, tts, feedback, viewModelScope)

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    fun refresh() {
        val installed = repo.isAnkiDroidInstalled()
        val permitted = installed && repo.hasPermission()
        _home.update { it.copy(ankiInstalled = installed, hasPermission = permitted, error = null) }
        if (!permitted) return
        viewModelScope.launch {
            _home.update { it.copy(loading = true) }
            try {
                val decks = repo.decks()
                _home.update { it.copy(decks = decks, loading = false) }
            } catch (e: Exception) {
                _home.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun start(deck: DeckInfo) = session.start(deck)

    fun stop() {
        session.stop()
        refresh()
    }

    fun send(command: Command) = session.send(command)

    /** @return true, wenn die Taste verbraucht wurde. */
    fun onKey(keyCode: Int): Boolean {
        val phase = session.state.value.phase
        if (!KeyMapper.isSessionPhase(phase)) return false
        val command = KeyMapper.map(keyCode, phase) ?: return false
        session.send(command)
        return true
    }

    override fun onCleared() {
        session.stop()
        tts.shutdown()
        feedback.release()
    }
}
