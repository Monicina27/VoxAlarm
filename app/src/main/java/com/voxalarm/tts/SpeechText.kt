package com.voxalarm.tts

import com.voxalarm.data.Alarm
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Builds the sentence spoken when an alarm rings.
 *
 * Personalised messages may contain tokens that are filled in at ring time:
 *   {time}  → "six thirty in the morning"
 *   {day}   → "Monday"
 *   {date}  → "26 October"
 *   {name}  → the alarm name
 */
object SpeechText {

    val TOKENS = listOf("{time}", "{day}", "{date}", "{name}")

    fun build(alarm: Alarm, now: LocalDateTime = LocalDateTime.now(), speakTime: Boolean = alarm.speakTime): String {
        val message = fill(alarm.message, alarm, now).trim()
        val body = message.ifBlank { "${alarm.label}. It is ${spokenTime(now.hour, now.minute)}." }
        return if (speakTime && !alarm.message.contains("{time}")) {
            "It is now ${spokenTime(now.hour, now.minute)}. $body"
        } else body
    }

    fun fill(text: String, alarm: Alarm, now: LocalDateTime): String = text
        .replace("{time}", spokenTime(now.hour, now.minute))
        .replace("{day}", now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
        .replace("{date}", now.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())))
        .replace("{name}", alarm.label)

    /** "six thirty in the morning", "twelve noon", "midnight", "nine oh five at night". */
    fun spokenTime(hour: Int, minute: Int): String {
        if (hour == 0 && minute == 0) return "midnight"
        if (hour == 12 && minute == 0) return "twelve noon"
        val h12 = when (val h = hour % 12) { 0 -> 12; else -> h }
        val minutePart = when {
            minute == 0 -> "o'clock"
            minute < 10 -> "oh ${words(minute)}"
            else -> words(minute)
        }
        val period = when (hour) {
            in 5..11 -> "in the morning"
            in 12..16 -> "in the afternoon"
            in 17..20 -> "in the evening"
            else -> "at night"
        }
        return "${words(h12)} $minutePart $period"
    }

    private val ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty")

    fun words(n: Int): String = when {
        n < 20 -> ONES[n]
        n % 10 == 0 -> TENS[n / 10]
        else -> "${TENS[n / 10]}-${ONES[n % 10]}"
    }
}
