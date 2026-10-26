package com.voxalarm.ui.history

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voxalarm.container
import com.voxalarm.data.AppSettings
import com.voxalarm.data.HistoryAction
import com.voxalarm.data.HistoryEntry
import com.voxalarm.ui.components.NeonCard
import com.voxalarm.ui.components.neonGlow
import com.voxalarm.ui.theme.NeonPalette
import com.voxalarm.ui.theme.VoxCardHigh
import com.voxalarm.ui.theme.VoxTextDim
import com.voxalarm.ui.theme.VoxTextFaint
import com.voxalarm.util.TimeText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = app.container.repository
    val history: StateFlow<List<HistoryEntry>?> =
        repo.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings: StateFlow<AppSettings> =
        app.container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun clear() = viewModelScope.launch { repo.clearHistory() }
}

private data class ActionStyle(val label: String, val icon: ImageVector, val color: Color)

private fun styleFor(action: String) = when (action) {
    HistoryAction.DISMISSED -> ActionStyle("Dismissed", Icons.Filled.CheckCircle, Color(0xFFA6FF00))
    HistoryAction.SNOOZED -> ActionStyle("Snoozed", Icons.Filled.Snooze, Color(0xFF2D9CFF))
    HistoryAction.MISSED -> ActionStyle("Missed", Icons.Filled.ErrorOutline, Color(0xFFFF2E97))
    HistoryAction.TIMED_OUT -> ActionStyle("Auto-stopped", Icons.Filled.TimerOff, Color(0xFFFF8A00))
    HistoryAction.SKIPPED -> ActionStyle("Skipped", Icons.Filled.SkipNext, Color(0xFFB967FF))
    else -> ActionStyle(action, Icons.Filled.History, VoxTextDim)
}

@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel()) {
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val list = history.orEmpty()
    val zone = ZoneId.systemDefault()
    val grouped = remember(list) {
        list.groupBy { Instant.ofEpochMilli(it.eventAt).atZone(zone).toLocalDate() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("History", style = MaterialTheme.typography.headlineMedium)
                    Text("Everything your alarms did", style = MaterialTheme.typography.bodyMedium, color = VoxTextDim)
                }
                if (list.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteSweep, "Clear history", tint = VoxTextDim)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(HistoryAction.DISMISSED, HistoryAction.SNOOZED, HistoryAction.MISSED).forEach { a ->
                    val st = styleFor(a)
                    StatTile(st.label, list.count { it.action == a }, st.color, Modifier.weight(1f))
                }
            }
        }
        if (history != null && list.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AlarmOff, null, tint = VoxTextFaint, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No alarm activity yet", style = MaterialTheme.typography.titleMedium)
                    Text("Dismissed, snoozed and missed alarms appear here.", style = MaterialTheme.typography.bodySmall, color = VoxTextDim)
                }
            }
        }
        grouped.forEach { (date, entries) ->
            item(key = "h_$date") {
                Text(
                    dayLabel(date),
                    style = MaterialTheme.typography.labelMedium,
                    color = VoxTextDim,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            items(entries, key = { it.id }) { e -> HistoryRow(e, settings.use24Hour) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = VoxCardHigh,
            title = { Text("Clear history?") },
            text = { Text("This removes all alarm history. Your alarms are not affected.") },
            confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())).uppercase()
    }
}

@Composable
private fun StatTile(label: String, count: Int, color: Color, modifier: Modifier) {
    NeonCard(modifier, accent = color, glow = count > 0) {
        Text("$count", style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = VoxTextDim)
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry, use24: Boolean) {
    val st = styleFor(e.action)
    val neon = NeonPalette[e.colorIndex]
    NeonCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp)
                    .neonGlow(st.color, radius = 10.dp, corner = 20.dp, alpha = 0.4f)
                    .clip(CircleShape)
                    .background(st.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(st.icon, null, tint = st.color, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(neon.color))
                    Spacer(Modifier.width(6.dp))
                    Text(e.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val detail = if (e.action == HistoryAction.SNOOZED) "${st.label} for ${e.detail} min" else st.label
                Text(
                    "$detail at ${TimeText.formatMillisFixed(e.eventAt, use24)}" +
                        if (e.scheduledAt > 0) " · due ${TimeText.formatMillisFixed(e.scheduledAt, use24)}" else "",
                    style = MaterialTheme.typography.bodySmall, color = VoxTextDim,
                )
            }
            Text(
                st.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = st.color,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(st.color.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
