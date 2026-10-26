package com.voxalarm.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voxalarm.data.Alarm
import com.voxalarm.ui.components.GradientButton
import com.voxalarm.ui.components.NeonCard
import com.voxalarm.ui.components.PermissionChecklist
import com.voxalarm.ui.components.SectionTitle
import com.voxalarm.ui.components.neonGlow
import com.voxalarm.ui.components.neonSwitchColors
import com.voxalarm.ui.theme.LocalAccent
import com.voxalarm.ui.theme.NeonPalette
import com.voxalarm.ui.theme.VoxBlack
import com.voxalarm.ui.theme.VoxCardHigh
import com.voxalarm.ui.theme.VoxTextDim
import com.voxalarm.ui.theme.VoxTextFaint
import com.voxalarm.util.TimeText
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ticks every second, aligned to the wall clock. */
@Composable
fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    return now
}

fun greetingFor(hour: Int) = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

/** Short display name for a stored TTS voice name. */
fun voiceLabel(voiceName: String): String {
    if (voiceName.isBlank()) return "Default voice"
    return Regex("-x-([a-z0-9]+)-(local|network)$").find(voiceName)?.groupValues?.get(1)?.uppercase()
        ?: voiceName
}

@Composable
fun HomeScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now = rememberNow()
    val accent = LocalAccent.current
    var pendingDelete by remember { mutableStateOf<Alarm?>(null) }

    val nextAlarm = alarms.orEmpty()
        .filter { it.enabled }
        .minByOrNull { if (it.snoozeUntil > 0) it.snoozeUntil else it.nextTriggerAt }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "clock") { ClockHeader(now, settings.use24Hour) }
            item(key = "next") { NextAlarmCard(nextAlarm, settings.use24Hour, now) }
            item(key = "perm") { PermissionChecklist(color = accent.color) }
            item(key = "title") {
                SectionTitle(
                    if (alarms.isNullOrEmpty()) "Your alarms" else "Your alarms · ${alarms!!.size}",
                    icon = Icons.Filled.NotificationsActive,
                )
            }
            val list = alarms
            if (list != null && list.isEmpty()) {
                item(key = "empty") { EmptyState(onAddAlarm) }
            }
            items(list.orEmpty(), key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    use24 = settings.use24Hour,
                    onToggle = { vm.setEnabled(alarm, it) },
                    onEdit = { onEditAlarm(alarm.id) },
                    onDelete = { pendingDelete = alarm },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        // Floating "+" button
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
                .size(64.dp)
                .neonGlow(accent.color, radius = 20.dp, corner = 32.dp, alpha = 0.7f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent.color, accent.partner)))
                .clickable(onClick = onAddAlarm),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, "Add alarm", tint = VoxBlack, modifier = Modifier.size(32.dp))
        }
    }

    pendingDelete?.let { alarm ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = VoxCardHigh,
            title = { Text("Delete alarm?") },
            text = { Text("\"${alarm.label}\" at ${TimeText.format(alarm.hour, alarm.minute, settings.use24Hour)} will be removed.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(alarm); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ClockHeader(now: LocalDateTime, use24: Boolean) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 18.dp)) {
        Text(
            greetingFor(now.hour),
            style = MaterialTheme.typography.titleLarge,
            color = accent.color,
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
            style = MaterialTheme.typography.bodyLarge,
            color = VoxTextDim,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                TimeText.formatDigits(now.hour, now.minute, use24),
                style = MaterialTheme.typography.displayLarge.copy(
                    brush = Brush.linearGradient(listOf(Color.White, accent.color, accent.partner))
                ),
                fontSize = 84.sp,
            )
            Column(Modifier.padding(start = 8.dp, bottom = 16.dp)) {
                if (!use24) {
                    Text(TimeText.amPm(now.hour), style = MaterialTheme.typography.titleMedium, color = accent.partner)
                }
                AnimatedContent(
                    targetState = now.second,
                    transitionSpec = {
                        (slideInVertically { -it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut())
                    },
                    label = "seconds",
                ) { sec ->
                    Text("%02d".format(sec), style = MaterialTheme.typography.headlineMedium, color = accent.color)
                }
            }
        }
    }
}

@Composable
private fun NextAlarmCard(alarm: Alarm?, use24: Boolean, now: LocalDateTime) {
    val neon = alarm?.let { NeonPalette[it.colorIndex] } ?: LocalAccent.current
    NeonCard(Modifier.fillMaxWidth(), accent = neon.color, glow = alarm != null) {
        Text("NEXT ALARM", style = MaterialTheme.typography.labelSmall, color = neon.color)
        Spacer(Modifier.height(6.dp))
        if (alarm == null) {
            Text("No alarms scheduled", style = MaterialTheme.typography.titleLarge)
            Text("Tap + to create a voice alarm.", style = MaterialTheme.typography.bodyMedium, color = VoxTextDim)
        } else {
            val due = if (alarm.snoozeUntil > 0) alarm.snoozeUntil else alarm.nextTriggerAt
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        TimeText.formatMillisFixed(due, use24),
                        style = MaterialTheme.typography.displayMedium.copy(brush = NeonPalette.gradient(alarm.colorIndex)),
                    )
                    Text(
                        if (alarm.snoozeUntil > 0) "${alarm.label} · snoozed" else alarm.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                // `now` keeps this countdown live
                val countdown = remember(now.minute, due) { TimeText.countdown(due) }
                Text(
                    countdown,
                    style = MaterialTheme.typography.labelLarge,
                    color = VoxBlack,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(NeonPalette.gradient(alarm.colorIndex))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.AlarmOff, null, tint = VoxTextFaint, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("No alarms yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Create an alarm that greets you with your own words.",
            style = MaterialTheme.typography.bodyMedium, color = VoxTextDim,
        )
        Spacer(Modifier.height(18.dp))
        GradientButton(
            "Create voice alarm", onClick = onAdd, icon = Icons.Filled.Add,
            brush = Brush.horizontalGradient(listOf(accent.color, accent.partner)),
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    use24: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val neon = NeonPalette[alarm.colorIndex]
    val contentAlpha by animateFloatAsState(if (alarm.enabled) 1f else 0.5f, label = "alpha")
    NeonCard(modifier.fillMaxWidth(), accent = neon.color, glow = alarm.enabled, onClick = onEdit) {
        Row {
            // Colour stripe
            Box(
                Modifier
                    .width(5.dp)
                    .height(86.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(neon.color, neon.partner)))
                    .alpha(contentAlpha)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).alpha(contentAlpha)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        TimeText.formatDigits(alarm.hour, alarm.minute, use24),
                        style = MaterialTheme.typography.displayMedium.copy(
                            brush = if (alarm.enabled) NeonPalette.gradient(alarm.colorIndex)
                            else Brush.linearGradient(listOf(VoxTextDim, VoxTextDim))
                        ),
                        fontSize = 44.sp,
                    )
                    if (!use24) {
                        Text(
                            TimeText.amPm(alarm.hour),
                            style = MaterialTheme.typography.titleSmall,
                            color = neon.color,
                            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                        )
                    }
                }
                Text(alarm.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (alarm.message.isNotBlank()) {
                    Text(
                        "“${alarm.message}”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = VoxTextDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = neonSwitchColors(neon.color),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().alpha(contentAlpha), verticalAlignment = Alignment.CenterVertically) {
            InfoChip(Icons.Filled.Mic, voiceLabel(alarm.voiceName), neon.color)
            Spacer(Modifier.width(8.dp))
            InfoChip(Icons.Filled.Repeat, TimeText.repeatSummary(alarm.repeatDays), neon.partner)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit", tint = VoxTextDim) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, "Delete", tint = VoxTextDim) }
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp),
        )
    }
}
