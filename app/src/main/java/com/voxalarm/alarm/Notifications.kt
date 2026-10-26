package com.voxalarm.alarm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.voxalarm.R
import com.voxalarm.data.Alarm
import com.voxalarm.ui.MainActivity
import com.voxalarm.ui.ringing.AlarmActivity
import com.voxalarm.ui.theme.NeonPalette
import com.voxalarm.util.TimeText

object Notifications {
    const val CH_RINGING = "ringing"
    const val CH_UPCOMING = "upcoming"
    const val CH_MISSED = "missed"

    const val ID_RINGING = 1001
    private const val ID_UPCOMING_BASE = 20_000
    private const val ID_MISSED_BASE = 40_000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ringing = NotificationChannel(
            CH_RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_ringing_desc)
            setSound(null, null) // the service plays the tone and voice itself
            enableVibration(false) // the service vibrates itself
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val upcoming = NotificationChannel(
            CH_UPCOMING, context.getString(R.string.channel_upcoming), NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_upcoming_desc)
            setSound(null, null)
        }
        val missed = NotificationChannel(
            CH_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.channel_missed_desc) }
        nm.createNotificationChannels(listOf(ringing, upcoming, missed))
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun canUseFullScreenIntent(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Notification used for the ringing foreground service. */
    fun buildRinging(context: Context, alarm: Alarm?): Notification {
        val fullScreen = PendingIntent.getActivity(
            context, 1,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CH_RINGING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)

        if (alarm == null) {
            return builder.setContentTitle("Alarm").setContentText("Starting…").build()
        }
        builder
            .setContentTitle("${TimeText.format(context, alarm.hour, alarm.minute)} · ${alarm.label}")
            .setContentText(alarm.message.ifBlank { "Tap to open the alarm" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(alarm.message.ifBlank { alarm.label }))
            .setColor(NeonPalette.argb(alarm.colorIndex))
            .setColorized(true)
            .addAction(0, "Snooze ${alarm.snoozeMinutes} min", servicePendingIntent(context, AlarmService.ACTION_SNOOZE, 2))
            .addAction(0, "Dismiss", servicePendingIntent(context, AlarmService.ACTION_DISMISS, 3))
        return builder.build()
    }

    private fun servicePendingIntent(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            context, code,
            Intent(context, AlarmService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    @SuppressLint("MissingPermission")
    fun showUpcoming(context: Context, alarm: Alarm, triggerAt: Long) {
        if (!canPost(context)) return
        val skip = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_SKIP
                data = Uri.parse("voxalarm://skip/${alarm.id}")
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CH_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Upcoming: ${alarm.label}")
            .setContentText("Rings at ${TimeText.formatMillis(context, triggerAt)}")
            .setColor(NeonPalette.argb(alarm.colorIndex))
            .setContentIntent(mainPendingIntent(context))
            .setAutoCancel(true)
            .setTimeoutAfter((triggerAt - System.currentTimeMillis()).coerceAtLeast(1000L))
            .addAction(0, "Dismiss now", skip)
            .build()
        NotificationManagerCompat.from(context).notify(ID_UPCOMING_BASE + alarm.id.toInt(), n)
    }

    fun cancelUpcoming(context: Context, alarmId: Long) {
        NotificationManagerCompat.from(context).cancel(ID_UPCOMING_BASE + alarmId.toInt())
    }

    @SuppressLint("MissingPermission")
    fun showMissed(context: Context, alarm: Alarm, scheduledAt: Long) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Missed alarm: ${alarm.label}")
            .setContentText("Was due at ${TimeText.formatMillis(context, scheduledAt)} but the phone could not ring.")
            .setColor(NeonPalette.argb(alarm.colorIndex))
            .setContentIntent(mainPendingIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_MISSED_BASE + alarm.id.toInt(), n)
    }

    private fun mainPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
