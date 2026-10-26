package com.voxalarm.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.voxalarm.alarm.AlarmScheduler
import com.voxalarm.alarm.Notifications
import com.voxalarm.ui.theme.VoxTextDim

data class PermissionStatus(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val requiredOk get() = notifications && exactAlarms && fullScreen
}

fun readPermissionStatus(context: Context) = PermissionStatus(
    notifications = Notifications.canPost(context),
    exactAlarms = AlarmScheduler(context).canScheduleExact(),
    fullScreen = Notifications.canUseFullScreenIntent(context),
    batteryUnrestricted = context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName),
)

/** Re-reads permission state every time the screen resumes. */
@Composable
fun rememberPermissionStatus(): PermissionStatus {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return remember(tick) { readPermissionStatus(context) }
}

/**
 * Lists what the app needs for reliable alarms, with a working button for each
 * missing item. Set [showWhenAllGranted] to also render the "all good" state.
 */
@Composable
fun PermissionChecklist(
    color: Color,
    showWhenAllGranted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val status = rememberPermissionStatus()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.launchSafely(appNotificationSettings(context))
    }
    if (!showWhenAllGranted && status.requiredOk && status.batteryUnrestricted) return

    NeonCard(modifier.fillMaxWidth(), accent = color, glow = !status.requiredOk) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, null, tint = color)
            Spacer(Modifier.width(10.dp))
            Text(
                if (status.requiredOk) "Alarm reliability" else "Finish setup for reliable alarms",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChecklistItem(
                Icons.Filled.NotificationsActive, "Notifications",
                "Needed to show the ringing alarm and reminders", status.notifications, color,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else context.launchSafely(appNotificationSettings(context))
            }
            ChecklistItem(
                Icons.Filled.AlarmOn, "Exact alarms",
                "Lets alarms ring on the exact minute", status.exactAlarms, color,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.launchSafely(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
            ChecklistItem(
                Icons.Filled.Fullscreen, "Full-screen alarms",
                "Shows the alarm over the lock screen", status.fullScreen, color,
            ) {
                if (Build.VERSION.SDK_INT >= 34) {
                    context.launchSafely(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
            ChecklistItem(
                Icons.Filled.BatteryChargingFull, "Unrestricted battery (recommended)",
                "Stops aggressive battery savers from delaying alarms", status.batteryUnrestricted, color,
            ) {
                context.launchSafely(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                )
            }
        }
    }
}

fun appNotificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

@Composable
private fun ChecklistItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    color: Color,
    onFix: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (granted) Icons.Filled.CheckCircle else icon, null, tint = if (granted) Color(0xFFA6FF00) else color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VoxTextDim)
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            NeonOutlineButton("Allow", onClick = onFix, color = color, height = 36.dp)
        }
    }
}
