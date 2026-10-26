package com.voxalarm

import android.app.Application
import android.content.Context
import com.voxalarm.alarm.AlarmScheduler
import com.voxalarm.alarm.Notifications
import com.voxalarm.data.AlarmRepository
import com.voxalarm.data.AppDatabase
import com.voxalarm.data.SettingsRepository
import com.voxalarm.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Minimal manual dependency container. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = SettingsRepository(appContext)
    val scheduler = AlarmScheduler(appContext)
    val repository = AlarmRepository(appContext, AppDatabase.get(appContext), scheduler, settings)

    /** Scope for writes that must outlive a screen or the ringing service. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** TTS instance for previews in the UI (the ringing service owns its own). */
    val previewTts: TtsManager by lazy { TtsManager(appContext) }
}

class VoxAlarmApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as VoxAlarmApp).container
