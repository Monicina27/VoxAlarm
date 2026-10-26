package com.voxalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek

/** How sound is produced when an alarm fires. */
object ToneMode {
    const val VOICE_ONLY = 0
    const val TONE_THEN_VOICE = 1
}

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "Wake Up",
    val message: String = "",
    val enabled: Boolean = true,
    /** Bitmask: bit 0 = Monday … bit 6 = Sunday. 0 = one-time alarm. */
    val repeatDays: Int = 0,
    /** Index into [com.voxalarm.ui.theme.NeonPalette]. */
    val colorIndex: Int = 0,
    /** Name of a TextToSpeech Voice, or blank for the engine default. */
    val voiceName: String = "",
    /** BCP-47 language tag, or blank for the device default. */
    val languageTag: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    /** 0..100 – percentage of the alarm stream's maximum. */
    val volume: Int = 80,
    val toneMode: Int = ToneMode.TONE_THEN_VOICE,
    /** Seconds between repetitions of the spoken message. 0 = speak once. */
    val repeatIntervalSec: Int = 30,
    val vibrate: Boolean = true,
    val gradualVolume: Boolean = true,
    val speakTime: Boolean = false,
    val snoozeMinutes: Int = 10,
    /** Epoch millis of the next scheduled ring (0 when not scheduled). */
    val nextTriggerAt: Long = 0,
    /** Epoch millis of a pending snooze (0 when not snoozed). */
    val snoozeUntil: Long = 0,
    /** Temporary test alarms are deleted after they ring. */
    val isTemporary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isRepeating: Boolean get() = repeatDays != 0

    fun repeatsOn(day: DayOfWeek): Boolean = repeatDays and dayBit(day) != 0

    companion object {
        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)
        const val WEEKDAYS = 0b0011111
        const val WEEKENDS = 0b1100000
        const val EVERY_DAY = 0b1111111
    }
}
