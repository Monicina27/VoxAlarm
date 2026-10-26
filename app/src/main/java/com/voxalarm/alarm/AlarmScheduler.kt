package com.voxalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.voxalarm.data.Alarm
import com.voxalarm.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Wraps AlarmManager. Ringing alarms use [AlarmManager.setAlarmClock], which:
 *  - fires exactly, even in Doze,
 *  - shows the alarm-clock icon in the status bar,
 *  - grants a temporary exemption to start our foreground service from the background.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Schedules [alarm] to ring at [triggerAt] (epoch millis). */
    fun scheduleAt(alarm: Alarm, triggerAt: Long) {
        val operation = ringPendingIntent(alarm.id)
        if (canScheduleExact()) {
            val info = AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent())
            alarmManager.setAlarmClock(info, operation)
        } else {
            // Best effort if the user revoked exact alarms (Android 12/12L only).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    /** Posts an "upcoming alarm" notification [leadMinutes] before [triggerAt]. */
    fun scheduleReminder(alarm: Alarm, triggerAt: Long, leadMinutes: Int) {
        val at = triggerAt - leadMinutes * 60_000L
        val pi = reminderPendingIntent(alarm.id)
        if (at <= System.currentTimeMillis()) {
            alarmManager.cancel(pi)
            return
        }
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(ringPendingIntent(alarmId))
        alarmManager.cancel(reminderPendingIntent(alarmId))
    }

    fun cancelReminder(alarmId: Long) {
        alarmManager.cancel(reminderPendingIntent(alarmId))
    }

    private fun ringPendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            data = Uri.parse("voxalarm://alarm/$alarmId")
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reminderPendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            data = Uri.parse("voxalarm://reminder/$alarmId")
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"

        /**
         * Next wall-clock occurrence strictly after [from], in the current time zone.
         * `atZone` resolves daylight-saving gaps by moving forward and overlaps by
         * picking the earlier offset, so alarms in a DST gap ring right after the jump.
         */
        fun computeNext(
            alarm: Alarm,
            from: ZonedDateTime = ZonedDateTime.now(),
        ): ZonedDateTime {
            val zone: ZoneId = from.zone
            var date: LocalDate = from.toLocalDate()
            repeat(9) {
                val candidate = date.atTime(alarm.hour, alarm.minute).atZone(zone)
                val dayOk = !alarm.isRepeating || alarm.repeatsOn(date.dayOfWeek)
                if (dayOk && candidate.isAfter(from)) return candidate
                date = date.plusDays(1)
            }
            // Unreachable for a valid mask; fall back to tomorrow.
            return from.toLocalDate().plusDays(1).atTime(alarm.hour, alarm.minute).atZone(zone)
        }

        fun computeNextMillis(alarm: Alarm, nowMillis: Long = System.currentTimeMillis()): Long =
            computeNext(alarm, Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()))
                .toInstant().toEpochMilli()
    }
}
