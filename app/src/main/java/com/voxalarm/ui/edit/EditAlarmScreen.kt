package com.voxalarm.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voxalarm.alarm.AlarmScheduler
import com.voxalarm.data.Alarm
import com.voxalarm.data.ToneMode
import com.voxalarm.tts.SpeechText
import com.voxalarm.ui.components.ChoicePills
import com.voxalarm.ui.components.ColorSwatches
import com.voxalarm.ui.components.DayToggles
import com.voxalarm.ui.components.GradientButton
import com.voxalarm.ui.components.LabeledSlider
import com.voxalarm.ui.components.NeonCard
import com.voxalarm.ui.components.NeonOutlineButton
import com.voxalarm.ui.components.Pill
import com.voxalarm.ui.components.SectionTitle
import com.voxalarm.ui.components.SoundWave
import com.voxalarm.ui.components.SwitchRow
import com.voxalarm.ui.components.VoicePicker
import com.voxalarm.ui.theme.NeonPalette
import com.voxalarm.ui.theme.VoxBlack
import com.voxalarm.ui.theme.VoxCardHigh
import com.voxalarm.ui.theme.VoxOutline
import com.voxalarm.ui.theme.VoxText
import com.voxalarm.ui.theme.VoxTextDim
import com.voxalarm.ui.theme.VoxTextFaint
import com.voxalarm.util.TimeText

@Composable
fun EditAlarmScreen(onDone: () -> Unit, vm: EditAlarmViewModel = viewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val alarm = draft
    if (alarm == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    EditAlarmContent(alarm, vm, onDone)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditAlarmContent(alarm: Alarm, vm: EditAlarmViewModel, onDone: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tts by vm.ttsState.collectAsStateWithLifecycle()
    val neon = NeonPalette[alarm.colorIndex]
    val color = neon.color
    var confirmDelete by remember { mutableStateOf(false) }
    var keyboardInput by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.stopVoice(); onDone() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VoxText)
            }
            Text(
                if (vm.isNew) "New alarm" else "Edit alarm",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (!vm.isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, "Delete", tint = VoxTextDim)
                }
            }
            IconButton(onClick = { vm.save(onDone) }) {
                Icon(Icons.Filled.Check, "Save", tint = color)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- TIME ----------
            NeonCard(Modifier.fillMaxWidth(), accent = color, glow = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Time", icon = Icons.Filled.AccessTime, color = color, modifier = Modifier.weight(1f))
                    Pill("12h", !settings.use24Hour, { vm.setUse24Hour(false) }, color)
                    Spacer(Modifier.width(6.dp))
                    Pill("24h", settings.use24Hour, { vm.setUse24Hour(true) }, color)
                    IconButton(onClick = { keyboardInput = !keyboardInput }) {
                        Icon(
                            if (keyboardInput) Icons.Filled.Schedule else Icons.Filled.Keyboard,
                            "Toggle input mode", tint = VoxTextDim,
                        )
                    }
                }
                key(settings.use24Hour) {
                    val state = rememberTimePickerState(alarm.hour, alarm.minute, settings.use24Hour)
                    LaunchedEffect(state.hour, state.minute) {
                        vm.update { it.copy(hour = state.hour, minute = state.minute) }
                    }
                    val colors = TimePickerDefaults.colors(
                        clockDialColor = VoxBlack,
                        clockDialSelectedContentColor = VoxBlack,
                        clockDialUnselectedContentColor = VoxText,
                        selectorColor = color,
                        containerColor = VoxCardHigh,
                        periodSelectorBorderColor = VoxOutline,
                        periodSelectorSelectedContainerColor = color.copy(alpha = 0.25f),
                        periodSelectorUnselectedContainerColor = Color.Transparent,
                        periodSelectorSelectedContentColor = color,
                        periodSelectorUnselectedContentColor = VoxTextDim,
                        timeSelectorSelectedContainerColor = color.copy(alpha = 0.22f),
                        timeSelectorUnselectedContainerColor = VoxBlack,
                        timeSelectorSelectedContentColor = color,
                        timeSelectorUnselectedContentColor = VoxText,
                    )
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (keyboardInput) TimeInput(state = state, colors = colors)
                        else TimePicker(state = state, colors = colors)
                    }
                }
                val next = remember(alarm.hour, alarm.minute, alarm.repeatDays) {
                    AlarmScheduler.computeNextMillis(alarm)
                }
                Text(
                    "Rings ${TimeText.countdown(next)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            // ---------- REPEAT ----------
            NeonCard(Modifier.fillMaxWidth()) {
                SectionTitle("Repeat", icon = Icons.Filled.Repeat, color = color)
                ChoicePills(
                    options = listOf(false to "One-time", true to "Recurring"),
                    selected = alarm.isRepeating,
                    onSelect = { rec ->
                        vm.update { it.copy(repeatDays = if (rec) (if (it.repeatDays == 0) Alarm.WEEKDAYS else it.repeatDays) else 0) }
                    },
                    color = color,
                )
                AnimatedVisibility(alarm.isRepeating) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        DayToggles(alarm.repeatDays, { mask -> vm.update { it.copy(repeatDays = mask) } }, color)
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Weekdays", alarm.repeatDays == Alarm.WEEKDAYS, { vm.update { it.copy(repeatDays = Alarm.WEEKDAYS) } }, neon.partner)
                            Pill("Weekends", alarm.repeatDays == Alarm.WEEKENDS, { vm.update { it.copy(repeatDays = Alarm.WEEKENDS) } }, neon.partner)
                            Pill("Every day", alarm.repeatDays == Alarm.EVERY_DAY, { vm.update { it.copy(repeatDays = Alarm.EVERY_DAY) } }, neon.partner)
                        }
                    }
                }
            }

            // ---------- NAME ----------
            NeonCard(Modifier.fillMaxWidth()) {
                SectionTitle("Alarm name", icon = Icons.Filled.Label, color = color)
                OutlinedTextField(
                    value = alarm.label,
                    onValueChange = { v -> vm.update { it.copy(label = v.take(40)) } },
                    singleLine = true,
                    placeholder = { Text("e.g. Wake Up") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = fieldColors(color),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LABEL_TEMPLATES.forEach { (name, message) ->
                        Pill(name, alarm.label == name, {
                            vm.update { a ->
                                val isTemplateMsg = a.message.isBlank() || LABEL_TEMPLATES.any { it.second == a.message }
                                a.copy(label = name, message = if (isTemplateMsg) message else a.message)
                            }
                        }, neon.partner)
                    }
                }
            }

            // ---------- VOICE MESSAGE ----------
            NeonCard(Modifier.fillMaxWidth(), accent = color, glow = tts.speaking) {
                SectionTitle("Voice message", icon = Icons.Filled.EditNote, color = color)
                OutlinedTextField(
                    value = alarm.message,
                    onValueChange = { v -> vm.update { it.copy(message = v.take(500)) } },
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("Good morning, sunshine! It's {time}. Time to get up!") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = fieldColors(color),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Personalise with: {time} {day} {date} {name}",
                    style = MaterialTheme.typography.bodySmall, color = VoxTextDim,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeechText.TOKENS.forEach { token ->
                        Pill(token, false, { vm.update { it.copy(message = (it.message.trimEnd() + " " + token).trim()) } }, neon.partner)
                    }
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    "Announce the current time",
                    alarm.speakTime,
                    { v -> vm.update { it.copy(speakTime = v) } },
                    subtitle = "Starts with \"It is now six thirty in the morning…\"",
                    icon = Icons.Filled.AccessTime,
                    color = color,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Will say: “${SpeechText.build(alarm, java.time.LocalDateTime.now().withHour(alarm.hour).withMinute(alarm.minute))}”",
                    style = MaterialTheme.typography.bodySmall, color = VoxTextFaint,
                )
                Spacer(Modifier.height(14.dp))
                SoundWave(tts.speaking, color, Modifier.fillMaxWidth().height(36.dp))
                Spacer(Modifier.height(14.dp))
                if (tts.speaking) {
                    NeonOutlineButton("Stop", onClick = vm::stopVoice, icon = Icons.Filled.Stop, color = color, modifier = Modifier.fillMaxWidth(), height = 56.dp)
                } else {
                    GradientButton(
                        "Test Voice", onClick = vm::testVoice, icon = Icons.Filled.PlayArrow,
                        brush = NeonPalette.gradient(alarm.colorIndex), glowColor = color,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ---------- VOICE ----------
            NeonCard(Modifier.fillMaxWidth()) {
                SectionTitle("Voice", icon = Icons.Filled.RecordVoiceOver, color = color)
                VoicePicker(
                    tts = tts,
                    languageTag = alarm.languageTag,
                    voiceName = alarm.voiceName,
                    onLanguageChange = { t -> vm.update { it.copy(languageTag = t) } },
                    onVoiceChange = { n -> vm.update { it.copy(voiceName = n) } },
                    color = color,
                )
                Spacer(Modifier.height(10.dp))
                LabeledSlider(
                    "Speaking speed", alarm.speechRate, { v -> vm.update { it.copy(speechRate = v) } },
                    0.5f..2f, "%.1f×".format(alarm.speechRate), icon = Icons.Filled.Speed, color = color,
                )
                LabeledSlider(
                    "Voice pitch", alarm.pitch, { v -> vm.update { it.copy(pitch = v) } },
                    0.5f..2f, "%.1f×".format(alarm.pitch), icon = Icons.Filled.Tune, color = color,
                )
            }

            // ---------- SOUND ----------
            NeonCard(Modifier.fillMaxWidth()) {
                SectionTitle("Sound", icon = Icons.Filled.GraphicEq, color = color)
                LabeledSlider(
                    "Alarm volume", alarm.volume.toFloat(), { v -> vm.update { it.copy(volume = v.toInt()) } },
                    10f..100f, "${alarm.volume}%", icon = Icons.Filled.VolumeUp, color = color,
                )
                Text("Mode", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ChoicePills(
                    listOf(ToneMode.TONE_THEN_VOICE to "Gentle tone + voice", ToneMode.VOICE_ONLY to "Voice only"),
                    alarm.toneMode, { m -> vm.update { it.copy(toneMode = m) } }, color = color,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Repeat message until dismissed", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                ChoicePills(
                    REPEAT_OPTIONS, alarm.repeatIntervalSec, { s -> vm.update { it.copy(repeatIntervalSec = s) } }, color = color,
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow("Gradually increase volume", alarm.gradualVolume, { v -> vm.update { it.copy(gradualVolume = v) } },
                    subtitle = "Starts soft and rises over 45 seconds", icon = Icons.Filled.TrendingUp, color = color)
                SwitchRow("Vibrate", alarm.vibrate, { v -> vm.update { it.copy(vibrate = v) } },
                    icon = Icons.Filled.Vibration, color = color)
            }

            // ---------- SNOOZE ----------
            NeonCard(Modifier.fillMaxWidth()) {
                SectionTitle("Snooze", icon = Icons.Filled.Snooze, color = color)
                ChoicePills(SNOOZE_OPTIONS, alarm.snoozeMinutes, { m -> vm.update { it.copy(snoozeMinutes = m) } }, color = color)
            }

            // ---------- COLOUR ----------
            NeonCard(Modifier.fillMaxWidth()) {
                SectionTitle("Colour", icon = Icons.Filled.Palette, color = color)
                ColorSwatches(alarm.colorIndex, { i -> vm.update { it.copy(colorIndex = i) } })
            }

            GradientButton(
                if (vm.isNew) "Save alarm" else "Save changes",
                onClick = { vm.save(onDone) },
                icon = Icons.Filled.Check,
                brush = NeonPalette.gradient(alarm.colorIndex),
                glowColor = color,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = VoxCardHigh,
            title = { Text("Delete alarm?") },
            text = { Text("\"${alarm.label}\" will be removed.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

val REPEAT_OPTIONS = listOf(0 to "Once", 15 to "Every 15 s", 30 to "Every 30 s", 60 to "Every 60 s")
val SNOOZE_OPTIONS = listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 20 to "20 min")

@Composable
fun fieldColors(color: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = color,
    unfocusedBorderColor = VoxOutline,
    cursorColor = color,
    focusedContainerColor = VoxBlack,
    unfocusedContainerColor = VoxBlack,
    focusedTextColor = VoxText,
    unfocusedTextColor = VoxText,
    focusedPlaceholderColor = VoxTextFaint,
    unfocusedPlaceholderColor = VoxTextFaint,
)
