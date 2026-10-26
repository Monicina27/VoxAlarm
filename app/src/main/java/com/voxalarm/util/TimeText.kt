package com.voxalarm.util

import android.content.Context
import android.text.format.DateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object TimeText {

    fun format(hour: Int, minute: Int, use24: Boolean): String =
        if (use24) "%02d:%02d".format(hour, minute)
        else "%d:%02d %s".format(hour12(hour), minute, if (hour < 12) "AM" else "PM")

    /** Time without the AM/PM suffix (for large displays). */
    fun formatDigits(hour: Int, minute: Int, use24: Boolean): String =
        if (use24) "%02d:%02d".format(hour, minute) else "%d:%02d".format(hour12(hour), minute)

    fun amPm(hour: Int): String = if (hour < 12) "AM" else "PM"

    fun hour12(hour: Int): Int = when (val h = hour % 12) { 0 -> 12; else -> h }

    fun format(context: Context, hour: Int, minute: Int): String =
        format(hour, minute, DateFormat.is24HourFormat(context))

    fun formatMillis(context: Context, millis: Long): String {
        val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return format(context, t.hour, t.minute)
    }

    fun formatMillisFixed(millis: Long, use24: Boolean): String {
        val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return format(t.hour, t.minute, use24)
    }

    /** "in 7 h 23 min", "in 45 min", "in less than a minute". */
    fun countdown(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val totalMin = ((targetMillis - nowMillis) / 60_000L).coerceAtLeast(0)
        val days = totalMin / (60 * 24)
        val hours = (totalMin / 60) % 24
        val mins = totalMin % 60
        return when {
            targetMillis - nowMillis < 60_000L -> "in less than a minute"
            days > 0 -> "in ${days} d ${hours} h"
            hours > 0 -> "in ${hours} h ${mins} min"
            else -> "in ${mins} min"
        }
    }

    /** Human description of a repeat mask, e.g. "Weekdays", "Mon, Wed, Fri", "Once". */
    fun repeatSummary(mask: Int): String = when (mask) {
        0 -> "Once"
        0b1111111 -> "Every day"
        0b0011111 -> "Weekdays"
        0b1100000 -> "Weekends"
        else -> DayOfWeek.entries
            .filter { mask and (1 shl (it.value - 1)) != 0 }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}
