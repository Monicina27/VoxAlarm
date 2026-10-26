package com.voxalarm.data

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val voiceName: String = "",
    val languageTag: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Int = 80,
    val snoozeMinutes: Int = 10,
    val use24Hour: Boolean = false,
    val vibrate: Boolean = true,
    val toneMode: Int = ToneMode.TONE_THEN_VOICE,
    val accentIndex: Int = 0,
    val repeatIntervalSec: Int = 30,
    val gradualVolume: Boolean = true,
    val speakTime: Boolean = false,
    val upcomingReminder: Boolean = true,
    val reminderLeadMinutes: Int = 30,
    val missedNotifications: Boolean = true,
    val autoStopMinutes: Int = 10,
)

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        // Device-protected storage so settings are readable during Direct Boot.
        appContext.createDeviceProtectedStorageContext().preferencesDataStoreFile("settings")
    }

    private object K {
        val voiceName = stringPreferencesKey("voice_name")
        val language = stringPreferencesKey("language")
        val rate = floatPreferencesKey("rate")
        val pitch = floatPreferencesKey("pitch")
        val volume = intPreferencesKey("volume")
        val snooze = intPreferencesKey("snooze")
        val use24 = booleanPreferencesKey("use24")
        val vibrate = booleanPreferencesKey("vibrate")
        val toneMode = intPreferencesKey("tone_mode")
        val accent = intPreferencesKey("accent")
        val repeat = intPreferencesKey("repeat_interval")
        val gradual = booleanPreferencesKey("gradual")
        val speakTime = booleanPreferencesKey("speak_time")
        val upcoming = booleanPreferencesKey("upcoming")
        val lead = intPreferencesKey("lead")
        val missed = booleanPreferencesKey("missed")
        val autoStop = intPreferencesKey("auto_stop")
    }

    private val systemUses24 = DateFormat.is24HourFormat(appContext)

    val settings: Flow<AppSettings> = store.data.map { p -> currentFrom(p) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val s = transform(currentFrom(p))
            p[K.voiceName] = s.voiceName
            p[K.language] = s.languageTag
            p[K.rate] = s.speechRate
            p[K.pitch] = s.pitch
            p[K.volume] = s.volume
            p[K.snooze] = s.snoozeMinutes
            p[K.use24] = s.use24Hour
            p[K.vibrate] = s.vibrate
            p[K.toneMode] = s.toneMode
            p[K.accent] = s.accentIndex
            p[K.repeat] = s.repeatIntervalSec
            p[K.gradual] = s.gradualVolume
            p[K.speakTime] = s.speakTime
            p[K.upcoming] = s.upcomingReminder
            p[K.lead] = s.reminderLeadMinutes
            p[K.missed] = s.missedNotifications
            p[K.autoStop] = s.autoStopMinutes
        }
    }

    private fun currentFrom(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            voiceName = p[K.voiceName] ?: d.voiceName,
            languageTag = p[K.language] ?: d.languageTag,
            speechRate = p[K.rate] ?: d.speechRate,
            pitch = p[K.pitch] ?: d.pitch,
            volume = p[K.volume] ?: d.volume,
            snoozeMinutes = p[K.snooze] ?: d.snoozeMinutes,
            use24Hour = p[K.use24] ?: systemUses24,
            vibrate = p[K.vibrate] ?: d.vibrate,
            toneMode = p[K.toneMode] ?: d.toneMode,
            accentIndex = p[K.accent] ?: d.accentIndex,
            repeatIntervalSec = p[K.repeat] ?: d.repeatIntervalSec,
            gradualVolume = p[K.gradual] ?: d.gradualVolume,
            speakTime = p[K.speakTime] ?: d.speakTime,
            upcomingReminder = p[K.upcoming] ?: d.upcomingReminder,
            reminderLeadMinutes = p[K.lead] ?: d.reminderLeadMinutes,
            missedNotifications = p[K.missed] ?: d.missedNotifications,
            autoStopMinutes = p[K.autoStop] ?: d.autoStopMinutes,
        )
    }
}
