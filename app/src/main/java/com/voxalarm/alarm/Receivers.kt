package com.voxalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.voxalarm.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive. */
private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (t: Throwable) {
            Log.e("VoxAlarm", "Receiver work failed", t)
        } finally {
            pending.finish()
        }
    }
}

/** Fired by AlarmManager at ring time. Hands off to the foreground [AlarmService]. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (id < 0) return
        try {
            AlarmService.ring(context, id)
        } catch (t: Throwable) {
            // Should not happen for setAlarmClock() alarms, which are exempt from
            // background-start restrictions; record it so the user is told.
            Log.e("VoxAlarm", "Could not start alarm service", t)
            launchAsync {
                val alarm = context.container.repository.getAlarm(id) ?: return@launchAsync
                Notifications.showMissed(context, alarm, System.currentTimeMillis())
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.voxalarm.action.FIRE"
    }
}

/** Shows the "upcoming alarm" notification and handles its "Dismiss now" action. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (id < 0) return
        val repo = context.container.repository
        when (intent.action) {
            ACTION_REMIND -> launchAsync {
                val alarm = repo.getAlarm(id) ?: return@launchAsync
                val due = if (alarm.snoozeUntil > 0) alarm.snoozeUntil else alarm.nextTriggerAt
                if (alarm.enabled && due > System.currentTimeMillis()) {
                    Notifications.showUpcoming(context, alarm, due)
                }
            }
            ACTION_SKIP -> launchAsync {
                Notifications.cancelUpcoming(context, id)
                repo.skipNext(id)
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.voxalarm.action.REMIND"
        const val ACTION_SKIP = "com.voxalarm.action.SKIP"
    }
}

/**
 * Reboot, app update, clock/time-zone change and exact-alarm permission changes all
 * clear or invalidate AlarmManager entries, so every alarm is re-registered here.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        launchAsync {
            context.container.repository.rescheduleAll(detectMissed = isBoot)
        }
    }
}
