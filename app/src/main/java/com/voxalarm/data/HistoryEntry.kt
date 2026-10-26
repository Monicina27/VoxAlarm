package com.voxalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object HistoryAction {
    const val DISMISSED = "DISMISSED"
    const val SNOOZED = "SNOOZED"
    const val MISSED = "MISSED"
    const val TIMED_OUT = "TIMED_OUT"
    const val SKIPPED = "SKIPPED"
}

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val label: String,
    val message: String,
    val colorIndex: Int,
    /** When the alarm was supposed to ring. */
    val scheduledAt: Long,
    /** When the action happened. */
    val eventAt: Long = System.currentTimeMillis(),
    val action: String,
    /** Extra detail, e.g. snooze length in minutes. */
    val detail: Int = 0,
)
