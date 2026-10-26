package com.voxalarm.ui.ringing

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voxalarm.alarm.AlarmService
import com.voxalarm.alarm.RingingState
import com.voxalarm.container
import com.voxalarm.data.AppSettings
import com.voxalarm.ui.components.GradientButton
import com.voxalarm.ui.components.NeonOutlineButton
import com.voxalarm.ui.components.Pill
import com.voxalarm.ui.components.PulsingRings
import com.voxalarm.ui.components.SoundWave
import com.voxalarm.ui.home.rememberNow
import com.voxalarm.ui.theme.NeonPalette
import com.voxalarm.ui.theme.VoxBlack
import com.voxalarm.ui.theme.VoxTextDim
import com.voxalarm.ui.theme.VoxTheme
import com.voxalarm.util.TimeText
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Full-screen ringing UI. Shown over the lock screen via the full-screen intent. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        // The user must choose Snooze or Dismiss – back does nothing while ringing.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val settingsFlow = container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(AppSettings())
            val state by AlarmService.state.collectAsStateWithLifecycle()
            var seen by remember { mutableStateOf(false) }

            LaunchedEffect(state) {
                if (state != null) {
                    seen = true
                } else if (seen) {
                    finishAndRemoveTask()
                } else {
                    // Opened before the service published its state – give it a moment.
                    delay(5000)
                    if (AlarmService.state.value == null) finishAndRemoveTask()
                }
            }

            VoxTheme(settings.accentIndex) {
                Box(Modifier.fillMaxSize().background(VoxBlack)) {
                    state?.let { s ->
                        RingingScreen(
                            state = s,
                            use24 = settings.use24Hour,
                            onDismiss = { AlarmService.dismiss(this@AlarmActivity) },
                            onSnooze = { minutes -> AlarmService.snooze(this@AlarmActivity, minutes) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RingingScreen(
    state: RingingState,
    use24: Boolean,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
) {
    val alarm = state.alarm
    val neon = NeonPalette[alarm.colorIndex]
    val now = rememberNow()
    var snoozeMinutes by rememberSaveable(alarm.id) { mutableIntStateOf(alarm.snoozeMinutes) }

    val transition = rememberInfiniteTransition(label = "bg")
    val glow by transition.animateFloat(
        0.18f, 0.32f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(neon.color.copy(alpha = glow), neon.partner.copy(alpha = glow / 3), VoxBlack),
                    radius = 1400f,
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                style = MaterialTheme.typography.titleMedium, color = VoxTextDim,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    TimeText.formatDigits(now.hour, now.minute, use24),
                    style = MaterialTheme.typography.displayLarge.copy(
                        brush = Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color.White, neon.color))
                    ),
                    fontSize = 88.sp,
                )
                if (!use24) {
                    Text(
                        TimeText.amPm(now.hour),
                        style = MaterialTheme.typography.titleLarge, color = neon.color,
                        modifier = Modifier.padding(start = 6.dp, bottom = 18.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            PulsingRings(neon.color, neon.partner, Modifier.size(240.dp)) {
                val pulse by rememberInfiniteTransition(label = "icon").animateFloat(
                    0.9f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s"
                )
                Icon(
                    Icons.Filled.RecordVoiceOver, null, tint = neon.color,
                    modifier = Modifier.size(64.dp).scale(if (state.speaking) pulse else 1f),
                )
            }

            Text(
                alarm.label,
                style = MaterialTheme.typography.headlineMedium.copy(brush = NeonPalette.gradient(alarm.colorIndex)),
                textAlign = TextAlign.Center,
            )
            if (alarm.message.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "“${alarm.message.replace(Regex("\\{(time|day|date|name)\\}")) { m ->
                        when (m.groupValues[1]) {
                            "time" -> TimeText.formatDigits(now.hour, now.minute, use24)
                            "name" -> alarm.label
                            else -> m.value.trim('{', '}')
                        }
                    }}”",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = VoxTextDim,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                )
            }
            Spacer(Modifier.height(16.dp))
            SoundWave(state.speaking, neon.color, Modifier.fillMaxWidth(0.8f).height(44.dp))
            if (state.queued > 0) {
                Text(
                    "+${state.queued} more alarm${if (state.queued > 1) "s" else ""} waiting",
                    style = MaterialTheme.typography.labelMedium, color = neon.partner,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("Snooze for", style = MaterialTheme.typography.labelMedium, color = VoxTextDim)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20).forEach { m ->
                    Pill("$m min", snoozeMinutes == m, { snoozeMinutes = m }, neon.partner)
                }
            }
            Spacer(Modifier.height(18.dp))
            NeonOutlineButton(
                "SNOOZE · $snoozeMinutes MIN",
                onClick = { onSnooze(snoozeMinutes) },
                icon = Icons.Filled.Snooze,
                color = neon.partner,
                height = 64.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            GradientButton(
                "DISMISS",
                onClick = onDismiss,
                icon = Icons.Filled.AlarmOff,
                brush = NeonPalette.gradient(alarm.colorIndex),
                glowColor = neon.color,
                height = 72.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
