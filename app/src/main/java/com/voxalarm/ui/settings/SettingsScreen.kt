package com.voxalarm.ui.settings

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voxalarm.BuildConfigInfo
import com.voxalarm.container
import com.voxalarm.data.Alarm
import com.voxalarm.data.AppSettings
import com.voxalarm.data.ToneMode
import com.voxalarm.tts.SpeechConfig
import com.voxalarm.tts.SpeechText
import com.voxalarm.tts.TtsState
import com.voxalarm.ui.components.ChoicePills
import com.voxalarm.ui.components.ColorSwatches
import com.voxalarm.ui.components.GradientButton
import com.voxalarm.ui.components.LabeledSlider
import com.voxalarm.ui.components.NeonCard
import com.voxalarm.ui.components.NeonOutlineButton
import com.voxalarm.ui.components.PermissionChecklist
import com.voxalarm.ui.components.SectionTitle
import com.voxalarm.ui.components.SoundWave
import com.voxalarm.ui.components.SwitchRow
import com.voxalarm.ui.components.VoicePicker
import com.voxalarm.ui.components.appNotificationSettings
import com.voxalarm.ui.components.launchSafely
import com.voxalarm.ui.edit.REPEAT_OPTIONS
import com.voxalarm.ui.edit.SNOOZE_OPTIONS
import com.voxalarm.ui.theme.LocalAccent
import com.voxalarm.ui.theme.NeonPalette
import com.voxalarm.ui.theme.VoxTextDim
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private val tts = container.previewTts

    val settings: StateFlow<AppSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val ttsState: StateFlow<TtsState> = tts.state

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    /** Reminder settings change when every alarm's reminder should fire, so re-register them. */
    fun updateReminders(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            container.settings.update(transform)
            container.repository.rescheduleAll(detectMissed = false)
        }
    }

    fun testVoice() {
        val s = settings.value
        viewModelScope.launch {
            tts.stop()
            tts.speak(
                "Hello! This is your VoxAlarm voice. It is now ${SpeechText.spokenTime(LocalDateTime.now().hour, LocalDateTime.now().minute)}.",
                SpeechConfig(s.voiceName, s.languageTag, s.speechRate, s.pitch, s.volume / 100f),
            )
        }
    }

    fun stopVoice() = tts.stop()

    fun refreshTts() = tts.refresh()

    /** Creates a real, temporary alarm that rings in [seconds] using the default settings. */
    fun scheduleTestAlarm(seconds: Int) {
        val s = settings.value
        viewModelScope.launch {
            tts.stop()
            container.repository.createTestAlarm(
                Alarm(
                    label = "Test alarm",
                    message = "This is a test alarm. It is {time}. If you can hear this with the screen locked, your alarms are working.",
                    colorIndex = s.accentIndex,
                    voiceName = s.voiceName, languageTag = s.languageTag,
                    speechRate = s.speechRate, pitch = s.pitch, volume = s.volume,
                    toneMode = s.toneMode, repeatIntervalSec = s.repeatIntervalSec,
                    vibrate = s.vibrate, gradualVolume = s.gradualVolume,
                    speakTime = s.speakTime, snoozeMinutes = s.snoozeMinutes,
                ),
                seconds,
            )
            Toast.makeText(getApplication(), "Test alarm rings in $seconds seconds – lock your phone now", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCleared() { tts.stop() }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val tts by vm.ttsState.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    val color = accent.color
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.statusBarsPadding().padding(top = 18.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Defaults for new alarms and app preferences", style = MaterialTheme.typography.bodyMedium, color = VoxTextDim)
        }

        // ---------- VOICE ----------
        NeonCard(Modifier.fillMaxWidth(), accent = color, glow = tts.speaking) {
            SectionTitle("Default voice", icon = Icons.Filled.RecordVoiceOver)
            VoicePicker(
                tts = tts,
                languageTag = s.languageTag,
                voiceName = s.voiceName,
                onLanguageChange = { t -> vm.update { it.copy(languageTag = t) } },
                onVoiceChange = { n -> vm.update { it.copy(voiceName = n) } },
                color = color,
                onRefresh = vm::refreshTts,
            )
            Spacer(Modifier.height(10.dp))
            LabeledSlider("Speech speed", s.speechRate, { v -> vm.update { it.copy(speechRate = v) } },
                0.5f..2f, "%.1f×".format(s.speechRate), icon = Icons.Filled.Speed)
            LabeledSlider("Speech pitch", s.pitch, { v -> vm.update { it.copy(pitch = v) } },
                0.5f..2f, "%.1f×".format(s.pitch), icon = Icons.Filled.Tune)
            SoundWave(tts.speaking, color, Modifier.fillMaxWidth().height(28.dp))
            Spacer(Modifier.height(10.dp))
            if (tts.speaking) {
                NeonOutlineButton("Stop", onClick = vm::stopVoice, icon = Icons.Filled.Stop, modifier = Modifier.fillMaxWidth())
            } else {
                NeonOutlineButton("Test Voice", onClick = vm::testVoice, icon = Icons.Filled.PlayArrow, modifier = Modifier.fillMaxWidth())
            }
            if (tts.engineName.isNotBlank()) {
                Text(
                    "Speech engine: ${tts.engineName}",
                    style = MaterialTheme.typography.bodySmall, color = VoxTextDim,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // ---------- ALARM SOUND ----------
        NeonCard(Modifier.fillMaxWidth()) {
            SectionTitle("Alarm sound", icon = Icons.Filled.GraphicEq)
            LabeledSlider("Default alarm volume", s.volume.toFloat(), { v -> vm.update { it.copy(volume = v.toInt()) } },
                10f..100f, "${s.volume}%", icon = Icons.Filled.VolumeUp)
            Text("Mode", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            ChoicePills(
                listOf(ToneMode.TONE_THEN_VOICE to "Gentle tone + voice", ToneMode.VOICE_ONLY to "Voice only"),
                s.toneMode, { m -> vm.update { it.copy(toneMode = m) } },
            )
            Spacer(Modifier.height(14.dp))
            Label(Icons.Filled.Timer, "Default repeat interval")
            ChoicePills(REPEAT_OPTIONS, s.repeatIntervalSec, { v -> vm.update { it.copy(repeatIntervalSec = v) } })
            Spacer(Modifier.height(8.dp))
            SwitchRow("Gradually increase volume", s.gradualVolume, { v -> vm.update { it.copy(gradualVolume = v) } }, icon = Icons.Filled.TrendingUp)
            SwitchRow("Vibration", s.vibrate, { v -> vm.update { it.copy(vibrate = v) } }, icon = Icons.Filled.Vibration)
            SwitchRow(
                "Speak the current time", s.speakTime, { v -> vm.update { it.copy(speakTime = v) } },
                subtitle = "\"It is now six thirty in the morning…\"", icon = Icons.Filled.AccessTime,
            )
            Spacer(Modifier.height(8.dp))
            Label(Icons.Filled.TimerOff, "Stop ringing automatically after")
            ChoicePills(
                listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min"),
                s.autoStopMinutes, { v -> vm.update { it.copy(autoStopMinutes = v) } },
            )
            Text(
                "Defaults apply to new alarms. Each alarm can override them.",
                style = MaterialTheme.typography.bodySmall, color = VoxTextDim, modifier = Modifier.padding(top = 10.dp),
            )
        }

        // ---------- SNOOZE ----------
        NeonCard(Modifier.fillMaxWidth()) {
            SectionTitle("Default snooze", icon = Icons.Filled.Snooze)
            ChoicePills(SNOOZE_OPTIONS, s.snoozeMinutes, { v -> vm.update { it.copy(snoozeMinutes = v) } })
        }

        // ---------- DISPLAY ----------
        NeonCard(Modifier.fillMaxWidth()) {
            SectionTitle("Appearance", icon = Icons.Filled.Palette)
            Label(Icons.Filled.AccessTime, "Time format")
            ChoicePills(listOf(false to "12-hour", true to "24-hour"), s.use24Hour, { v -> vm.update { it.copy(use24Hour = v) } })
            Spacer(Modifier.height(16.dp))
            Label(Icons.Filled.Palette, "App accent colour – ${NeonPalette[s.accentIndex].name}")
            ColorSwatches(s.accentIndex, { i -> vm.update { it.copy(accentIndex = i) } })
        }

        // ---------- NOTIFICATIONS ----------
        NeonCard(Modifier.fillMaxWidth()) {
            SectionTitle("Notifications", icon = Icons.Filled.NotificationsActive)
            SwitchRow(
                "Upcoming alarm reminder", s.upcomingReminder, { v -> vm.updateReminders { it.copy(upcomingReminder = v) } },
                subtitle = "Lets you dismiss an alarm before it rings", icon = Icons.Filled.Alarm,
            )
            if (s.upcomingReminder) {
                ChoicePills(
                    listOf(10 to "10 min before", 30 to "30 min before", 60 to "1 hour before"),
                    s.reminderLeadMinutes, { v -> vm.updateReminders { it.copy(reminderLeadMinutes = v) } },
                    modifier = Modifier.padding(start = 36.dp, bottom = 6.dp),
                )
            }
            SwitchRow(
                "Missed alarm alerts", s.missedNotifications, { v -> vm.update { it.copy(missedNotifications = v) } },
                subtitle = "Tells you if an alarm couldn't ring (e.g. phone was off)", icon = Icons.Filled.NotificationsOff,
            )
            Spacer(Modifier.height(8.dp))
            NeonOutlineButton(
                "System notification settings", icon = Icons.Filled.Settings,
                onClick = { context.launchSafely(appNotificationSettings(context)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---------- RELIABILITY ----------
        PermissionChecklist(color = color, showWhenAllGranted = true)
        NeonCard(Modifier.fillMaxWidth()) {
            SectionTitle("Test your setup", icon = Icons.Filled.Shield)
            Text(
                "Schedules a real alarm with your default voice. Lock the phone or close the app and wait for it to ring.",
                style = MaterialTheme.typography.bodySmall, color = VoxTextDim,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(
                    "Ring in 15 s", onClick = { vm.scheduleTestAlarm(15) }, icon = Icons.Filled.Alarm,
                    brush = NeonPalette.gradient(s.accentIndex), modifier = Modifier.weight(1f),
                )
                NeonOutlineButton("In 1 min", onClick = { vm.scheduleTestAlarm(60) }, height = 56.dp)
            }
        }

        // ---------- ABOUT ----------
        NeonCard(Modifier.fillMaxWidth()) {
            SectionTitle("About", icon = Icons.Filled.Info)
            Text("VoxAlarm ${BuildConfigInfo.versionName(context)}", style = MaterialTheme.typography.titleSmall)
            Text(
                "Voices come from your device's Text-to-Speech engine and work offline when the voice is downloaded. " +
                    "No alarm can ring while the phone is powered off, and some manufacturers' battery savers may still interfere – " +
                    "use the checklist above for the best reliability.",
                style = MaterialTheme.typography.bodySmall, color = VoxTextDim,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Label(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalAccent.current.color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}
