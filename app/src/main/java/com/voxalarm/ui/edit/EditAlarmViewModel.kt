package com.voxalarm.ui.edit

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.voxalarm.container
import com.voxalarm.data.Alarm
import com.voxalarm.data.AppSettings
import com.voxalarm.tts.SpeechConfig
import com.voxalarm.tts.SpeechText
import com.voxalarm.tts.TtsState
import com.voxalarm.ui.theme.NeonPalette
import com.voxalarm.util.TimeText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Quick-start names with a matching personalised message. */
val LABEL_TEMPLATES: List<Pair<String, String>> = listOf(
    "Wake Up" to "Good morning! It's {time}. Time to wake up and get ready for a beautiful day!",
    "Take Medication" to "Hey, it's {time}. Time to take your medication. Don't forget a glass of water!",
    "Meeting Reminder" to "Heads up! Your meeting is coming up. Grab your notes and get ready.",
    "Time to Leave" to "Hey! It's time to leave for work. Don't forget your keys and your phone!",
    "Lunch Break" to "It's {time}. Time for a lunch break. Step away from the screen and recharge.",
    "Bedtime" to "It's {time}. Time to wind down and get ready for bed. Sleep well!",
)

class EditAlarmViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val container = app.container
    private val repo = container.repository
    private val tts = container.previewTts

    val alarmId: Long = savedState.get<Long>("id") ?: -1L
    val isNew: Boolean get() = alarmId <= 0

    private val _draft = MutableStateFlow<Alarm?>(null)
    val draft: StateFlow<Alarm?> = _draft.asStateFlow()

    val ttsState: StateFlow<TtsState> = tts.state
    val settings: StateFlow<AppSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    init {
        viewModelScope.launch {
            val s = container.settings.current()
            _draft.value = (if (!isNew) repo.getAlarm(alarmId) else null) ?: newAlarm(s)
        }
    }

    private fun newAlarm(s: AppSettings): Alarm {
        val start = LocalDateTime.now().plusHours(1)
        return Alarm(
            hour = start.hour,
            minute = 0,
            label = "Wake Up",
            message = LABEL_TEMPLATES.first().second,
            colorIndex = (System.currentTimeMillis() / 1000 % NeonPalette.colors.size).toInt(),
            voiceName = s.voiceName,
            languageTag = s.languageTag,
            speechRate = s.speechRate,
            pitch = s.pitch,
            volume = s.volume,
            toneMode = s.toneMode,
            repeatIntervalSec = s.repeatIntervalSec,
            vibrate = s.vibrate,
            gradualVolume = s.gradualVolume,
            speakTime = s.speakTime,
            snoozeMinutes = s.snoozeMinutes,
        )
    }

    fun update(transform: (Alarm) -> Alarm) {
        _draft.value = _draft.value?.let(transform)
    }

    fun setUse24Hour(use24: Boolean) {
        viewModelScope.launch { container.settings.update { it.copy(use24Hour = use24) } }
    }

    fun testVoice() {
        val a = _draft.value ?: return
        viewModelScope.launch {
            tts.stop()
            val ok = tts.speak(
                SpeechText.build(a, LocalDateTime.now(), a.speakTime),
                SpeechConfig(a.voiceName, a.languageTag, a.speechRate, a.pitch, a.volume / 100f),
            )
            if (!ok && !tts.state.value.speaking && tts.state.value.failed) {
                toast("Text-to-Speech is not available on this device")
            }
        }
    }

    fun stopVoice() = tts.stop()

    fun refreshTts() = tts.refresh()

    fun save(onSaved: () -> Unit) {
        val a = _draft.value ?: return
        viewModelScope.launch {
            tts.stop()
            val saved = repo.save(
                a.copy(
                    label = a.label.trim().ifBlank { "Alarm" },
                    message = a.message.trim(),
                    enabled = true,
                    isTemporary = false,
                )
            )
            toast("Alarm set ${TimeText.countdown(saved.nextTriggerAt)}")
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val a = _draft.value ?: return
        viewModelScope.launch {
            if (!isNew) repo.delete(a)
            onDeleted()
        }
    }

    override fun onCleared() {
        tts.stop()
    }

    private fun toast(text: String) = Toast.makeText(getApplication(), text, Toast.LENGTH_SHORT).show()
}
