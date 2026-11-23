package com.lxkasmehl.ankiaudio

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lxkasmehl.ankiaudio.anki.AnkiContract
import com.lxkasmehl.ankiaudio.session.Command
import com.lxkasmehl.ankiaudio.session.Phase
import com.lxkasmehl.ankiaudio.session.SessionState

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(vm) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0 && vm.onKey(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Lautstärke-Tasten während der Session komplett schlucken, sonst piept das System.
        val phase = vm.session.state.value.phase
        if ((phase == Phase.FRONT || phase == Phase.BACK) &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) return true
        return super.onKeyUp(keyCode, event)
    }

    fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val home by vm.home.collectAsStateWithLifecycle()
    val session by vm.session.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refresh()
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Text("Anki Audio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        when {
            !home.ankiInstalled -> Text("AnkiDroid ist nicht installiert. Bitte zuerst AnkiDroid installieren.")
            !home.hasPermission -> {
                Text("Anki Audio braucht Zugriff auf deine AnkiDroid-Sammlung, um Karten zu lesen und zu bewerten.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(AnkiContract.PERMISSION) }) {
                    Text("Zugriff erlauben")
                }
            }
            session.phase == Phase.IDLE -> DeckList(home, onPick = vm::start)
            else -> SessionScreen(session, onCommand = vm::send, onStop = vm::stop)
        }
    }
}

@Composable
private fun DeckList(home: HomeState, onPick: (com.lxkasmehl.ankiaudio.anki.DeckInfo) -> Unit) {
    home.error?.let { Text("Fehler: $it", color = MaterialTheme.colorScheme.error) }
    if (home.loading) Text("Lade Decks…")
    Text("Deck wählen", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    LazyColumn {
        items(home.decks, key = { it.id }) { deck ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(deck) }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(deck.name, Modifier.weight(1f))
                Text(if (deck.due > 0) "${deck.due} fällig" else "", color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun TtsHelp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun open(vararg intents: Intent) {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Installiere eine Sprachausgabe und stelle sie als Standard ein. Am besten \"Sprachausgabe von Google\" " +
            "(Speech Services by Google). Ohne Play Store gehen z.B. RHVoice oder eSpeak NG aus F-Droid. " +
            "Danach hier auf Beenden tippen und das Deck neu starten.",
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = {
        open(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.tts")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts")),
        )
    }, Modifier.fillMaxWidth()) { Text("Google-Sprachausgabe installieren") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = {
        open(Intent("com.android.settings.TTS_SETTINGS"), Intent(android.provider.Settings.ACTION_SETTINGS))
    }, Modifier.fillMaxWidth()) { Text("Sprachausgabe-Einstellungen öffnen") }
}

@Composable
private fun SessionScreen(s: SessionState, onCommand: (Command) -> Unit, onStop: () -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? MainActivity
    LaunchedEffect(s.phase) {
        activity?.keepScreenOn(s.phase == Phase.FRONT || s.phase == Phase.BACK)
    }

    Column(Modifier.fillMaxSize()) {
        Text(s.deckName, style = MaterialTheme.typography.titleMedium)
        Text(
            "Gelernt: ${s.reviewed} · Übersprungen: ${s.skipped}",
            style = MaterialTheme.typography.bodySmall,
        )
        s.lastSkipReason?.let {
            Text("Zuletzt übersprungen: $it", style = MaterialTheme.typography.bodySmall)
        }
        if (s.phase != Phase.ERROR) s.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (s.phase) {
                Phase.LOADING -> Text("Lade Karten…")
                Phase.FINISHED -> Text("Fertig für heute in diesem Deck.", style = MaterialTheme.typography.titleLarge)
                Phase.ERROR -> {
                    Text("Fehler: ${s.message}", color = MaterialTheme.colorScheme.error)
                    if (s.ttsMissing) TtsHelp()
                }
                else -> {
                    Text(s.front, style = MaterialTheme.typography.titleLarge)
                    if (s.phase == Phase.BACK) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Text(s.back, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        when (s.phase) {
            Phase.FRONT -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCommand(Command.Reveal) }, Modifier.weight(1f)) { Text("Aufdecken") }
            }
            Phase.BACK -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "Again", 2 to "Hard", 3 to "Good", 4 to "Easy").forEach { (ease, label) ->
                    Button(onClick = { onCommand(Command.Rate(ease)) }, Modifier.weight(1f)) { Text(label) }
                }
            }
            else -> {}
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.phase == Phase.FRONT || s.phase == Phase.BACK) {
                OutlinedButton(onClick = { onCommand(Command.Repeat) }, Modifier.weight(1f)) { Text("Wiederholen") }
                OutlinedButton(onClick = { onCommand(Command.Skip) }, Modifier.weight(1f)) { Text("Überspringen") }
            }
            OutlinedButton(onClick = onStop, Modifier.weight(1f)) { Text("Beenden") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tasten: Leertaste/Lautstärke = aufdecken · danach laut = Good, leise = Again · 1-4 bzw. A/H/G/E bewerten · R wiederholen · S überspringen",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
