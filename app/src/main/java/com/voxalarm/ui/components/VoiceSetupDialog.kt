package com.voxalarm.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.voxalarm.tts.TtsManager
import com.voxalarm.tts.TtsState
import com.voxalarm.ui.theme.LocalAccent
import com.voxalarm.ui.theme.VoxCardHigh
import com.voxalarm.ui.theme.VoxTextDim

private const val PREFS = "voice_setup"
private const val KEY_DONT_ASK = "dont_ask_google_tts"

private fun Context.isGoogleTtsInstalled(): Boolean = runCatching {
    packageManager.getPackageInfo(TtsManager.GOOGLE_TTS, 0); true
}.getOrDefault(false)

private fun Context.setupPrefs() =
    createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

/**
 * Shown when the app opens and Google's speech engine isn't installed.
 * (Android doesn't let apps show anything during installation, so first launch is
 * the earliest point.) Closes by itself once the engine is installed.
 */
@Composable
fun VoiceSetupDialog(tts: TtsState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { resumeTick++; onRefresh() }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val googleInstalled = remember(resumeTick) { context.isGoogleTtsInstalled() }
    var hiddenThisSession by rememberSaveable { mutableStateOf(false) }
    val dontAsk = remember(resumeTick) { context.setupPrefs().getBoolean(KEY_DONT_ASK, false) }

    // Another engine works → recommend Google, but allow "don't ask again".
    // No engine at all → always ask (the app can only play a tone).
    val noEngine = tts.failed
    val otherEngineWorks = tts.ready && !tts.failed
    val show = !googleInstalled && !hiddenThisSession &&
        (noEngine || (otherEngineWorks && !dontAsk))
    if (!show) return

    val accent = LocalAccent.current
    AlertDialog(
        onDismissRequest = { hiddenThisSession = true },
        containerColor = VoxCardHigh,
        icon = { Icon(Icons.Filled.RecordVoiceOver, null, tint = accent.color, modifier = Modifier.size(36.dp)) },
        title = { Text(if (noEngine) "One step to hear your alarms" else "Get better alarm voices") },
        text = {
            Column {
                Text(
                    if (noEngine)
                        "VoxAlarm speaks your messages with Google's free voice engine, which isn't on this phone yet."
                    else
                        "For the most natural voices, VoxAlarm recommends Google's free voice engine.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "1. Tap Install below\n" +
                        "2. Install \"Speech Services by Google\" from the Play Store\n" +
                        "3. Come back to VoxAlarm – it sets up automatically",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (noEngine) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Until then, alarms still ring with a tone.",
                        style = MaterialTheme.typography.bodySmall, color = VoxTextDim,
                    )
                }
                Spacer(Modifier.height(18.dp))
                GradientButton(
                    "Install from Play Store",
                    onClick = { context.openGoogleTtsInPlayStore() },
                    icon = Icons.Filled.CloudDownload,
                    brush = Brush.horizontalGradient(listOf(accent.color, accent.partner)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { hiddenThisSession = true }) { Text("Not now") }
        },
        dismissButton = if (otherEngineWorks) {
            {
                TextButton(onClick = {
                    context.setupPrefs().edit().putBoolean(KEY_DONT_ASK, true).apply()
                    hiddenThisSession = true
                }) { Text("Don't ask again", color = VoxTextDim) }
            }
        } else null,
    )
}
