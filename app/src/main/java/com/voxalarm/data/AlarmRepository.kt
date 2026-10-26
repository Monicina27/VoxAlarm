package com.voxalarm.data

import com.voxalarm.alarm.AlarmScheduler
import com.voxalarm.alarm.Notifications
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for alarms. Every write goes through here so the
 * AlarmManager schedule always matches what is stored in Room.
 */
class AlarmRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: AlarmScheduler,
    private val settingsRepository: SettingsRepository,
) {
    private val alarmDao = db.alarmDao()
    private val historyDao = db.historyDao()
    private val mutex = Mutex()

    val alarms: Flow<List<Alarm>> = alarmDao.observeAll()
    val history: Flow<List<HistoryEntry>> = historyDao.observeAll()

    fun observeAlarm(id: Long): Flow<Alarm?> = alarmDao.observeById(id)
    suspend fun getAlarm(id: Long): Alarm? = alarmDao.getById(id)

    /** Inserts or updates an alarm and (re)schedules it. Returns the stored alarm. */
    suspend fun save(alarm: Alarm): Alarm = mutex.withLock {
        val base = alarm.copy(snoozeUntil = 0)
        val id = if (base.id == 0L) alarmDao.insert(base) else base.id.also { alarmDao.update(base) }
        val stored = base.copy(id = id)
        applySchedule(stored)
    }

    suspend fun setEnabled(alarm: Alarm, enabled: Boolean): Alarm = mutex.withLock {
        applySchedule(alarm.copy(enabled = enabled, snoozeUntil = 0))
    }

    suspend fun delete(alarm: Alarm) = mutex.withLock {
        scheduler.cancel(alarm.id)
        Notifications.cancelUpcoming(context, alarm.id)
        alarmDao.delete(alarm)
    }

    /** Creates a temporary alarm that rings in [seconds] seconds – used by "Test alarm". */
    suspend fun createTestAlarm(template: Alarm, seconds: Int): Alarm = mutex.withLock {
        val at = System.currentTimeMillis() + seconds * 1000L
        val cal = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
        val alarm = template.copy(
            id = 0, hour = cal.hour, minute = cal.minute, repeatDays = 0,
            enabled = true, isTemporary = true, nextTriggerAt = at, snoozeUntil = 0,
        )
        val id = alarmDao.insert(alarm)
        val stored = alarm.copy(id = id)
        scheduler.scheduleAt(stored, at)
        stored
    }

    /** Called when the user dismisses (or the alarm times out). */
    suspend fun onDismissed(alarmId: Long, action: String = HistoryAction.DISMISSED) = mutex.withLock {
        val alarm = alarmDao.getById(alarmId) ?: return@withLock
        record(alarm, action, alarm.snoozeUntil.takeIf { it > 0 } ?: alarm.nextTriggerAt)
        when {
            alarm.isTemporary -> {
                scheduler.cancel(alarm.id)
                alarmDao.delete(alarm)
            }
            alarm.isRepeating -> applySchedule(alarm.copy(snoozeUntil = 0))
            else -> applySchedule(alarm.copy(enabled = false, snoozeUntil = 0))
        }
    }

    suspend fun onSnoozed(alarmId: Long, minutes: Int) = mutex.withLock {
        val alarm = alarmDao.getById(alarmId) ?: return@withLock
        val until = System.currentTimeMillis() + minutes * 60_000L
        record(alarm, HistoryAction.SNOOZED, alarm.snoozeUntil.takeIf { it > 0 } ?: alarm.nextTriggerAt, minutes)
        val updated = alarm.copy(snoozeUntil = until, enabled = true)
        alarmDao.update(updated)
        scheduler.cancelReminder(alarm.id)
        scheduler.scheduleAt(updated, until)
    }

    /** Skips the next occurrence (from the "upcoming alarm" notification). */
    suspend fun skipNext(alarmId: Long) = mutex.withLock {
        val alarm = alarmDao.getById(alarmId) ?: return@withLock
        record(alarm, HistoryAction.SKIPPED, alarm.nextTriggerAt)
        if (alarm.isRepeating) {
            val next = AlarmScheduler.computeNextMillis(alarm, alarm.nextTriggerAt + 60_000L)
            val updated = alarm.copy(nextTriggerAt = next, snoozeUntil = 0)
            alarmDao.update(updated)
            scheduler.scheduleAt(updated, next)
            scheduleReminderIfWanted(updated, next)
        } else {
            applySchedule(alarm.copy(enabled = false, snoozeUntil = 0))
        }
    }

    /**
     * Re-registers every alarm with AlarmManager. Called after reboot, app update,
     * time/time-zone changes and when exact-alarm permission is granted.
     * Optionally detects alarms whose ring time passed while they could not fire.
     */
    suspend fun rescheduleAll(detectMissed: Boolean) = mutex.withLock {
        val now = System.currentTimeMillis()
        val settings = settingsRepository.current()
        for (alarm in alarmDao.getAll()) {
            if (!alarm.enabled) { scheduler.cancel(alarm.id); continue }
            if (com.voxalarm.alarm.AlarmService.isActive(alarm.id)) continue

            if (alarm.snoozeUntil > now) {
                scheduler.scheduleAt(alarm, alarm.snoozeUntil)
                continue
            }
            val due = if (alarm.snoozeUntil > 0) alarm.snoozeUntil else alarm.nextTriggerAt
            val missed = detectMissed && due in 1 until (now - 60_000L)
            if (missed) {
                record(alarm, HistoryAction.MISSED, due)
                if (settings.missedNotifications) Notifications.showMissed(context, alarm, due)
                if (alarm.isTemporary) { alarmDao.delete(alarm); continue }
                if (!alarm.isRepeating) {
                    applySchedule(alarm.copy(enabled = false, snoozeUntil = 0)); continue
                }
            }
            applySchedule(alarm.copy(snoozeUntil = 0))
        }
    }

    suspend fun clearHistory() = historyDao.clear()

    /** Persists [alarm] with a fresh nextTriggerAt and registers/cancels it. Caller holds mutex. */
    private suspend fun applySchedule(alarm: Alarm): Alarm {
        return if (alarm.enabled) {
            val next = AlarmScheduler.computeNextMillis(alarm)
            val updated = alarm.copy(nextTriggerAt = next)
            alarmDao.update(updated)
            scheduler.scheduleAt(updated, next)
            scheduleReminderIfWanted(updated, next)
            updated
        } else {
            scheduler.cancel(alarm.id)
            Notifications.cancelUpcoming(context, alarm.id)
            val updated = alarm.copy(nextTriggerAt = 0)
            alarmDao.update(updated)
            updated
        }
    }

    private suspend fun scheduleReminderIfWanted(alarm: Alarm, triggerAt: Long) {
        val s = settingsRepository.current()
        if (s.upcomingReminder && !alarm.isTemporary) {
            scheduler.scheduleReminder(alarm, triggerAt, s.reminderLeadMinutes)
        } else {
            scheduler.cancelReminder(alarm.id)
        }
    }

    private suspend fun record(alarm: Alarm, action: String, scheduledAt: Long, detail: Int = 0) {
        historyDao.insert(
            HistoryEntry(
                alarmId = alarm.id,
                label = alarm.label,
                message = alarm.message,
                colorIndex = alarm.colorIndex,
                scheduledAt = scheduledAt,
                action = action,
                detail = detail,
            )
        )
    }
}
