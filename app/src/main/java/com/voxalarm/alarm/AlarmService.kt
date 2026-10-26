package com.voxalarm.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.voxalarm.R
import com.voxalarm.container
import com.voxalarm.data.Alarm
import com.voxalarm.data.AppSettings
import com.voxalarm.data.HistoryAction
import com.voxalarm.data.ToneMode
import com.voxalarm.tts.SpeechConfig
import com.voxalarm.tts.SpeechText
import com.voxalarm.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.coroutines.resume

/** What the ringing screen shows. */
data class RingingState(
    val alarm: Alarm,
    val speaking: Boolean = false,
    val queued: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
)

/**
 * Foreground service that rings an alarm: tone, voice, vibration and the
 * full-screen notification. Alarms that fire while another is ringing are queued
 * and ring one after another, so nothing is lost when alarms overlap.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tts: TtsManager
    private lateinit var audioManager: AudioManager

    private var current: Alarm? = null
    private val queue = ArrayDeque<Long>()
    private var ringJob: Job? = null
    private var timeoutJob: Job? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var savedAlarmVolume: Int? = null
    private var settings = AppSettings()
    /** True between finishing one alarm and starting the next queued one. */
    private var transitioning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TtsManager(this)
        audioManager = getSystemService(AudioManager::class.java)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always enter the foreground immediately – required after startForegroundService().
        goForeground()

        when (intent?.action) {
            ACTION_RING -> {
                val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
                if (id >= 0 && current?.id != id && id !in queue) {
                    if (!transitioning && current == null && ringJob == null) startRinging(id) else {
                        queue.addLast(id)
                        publishActive()
                        current?.let { _state.value = _state.value?.copy(queued = queue.size) }
                    }
                }
            }
            ACTION_DISMISS -> finishCurrent(HistoryAction.DISMISSED, 0)
            ACTION_SNOOZE -> {
                val minutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, current?.snoozeMinutes ?: 10) ?: 10
                finishCurrent(HistoryAction.SNOOZED, minutes)
            }
            else -> if (!transitioning && current == null && ringJob == null) stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_RINGING,
            Notifications.buildRinging(this, current),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
    }

    private fun startRinging(id: Long) {
        ringJob = scope.launch {
            val repo = applicationContext.container.repository
            val alarm = withContext(Dispatchers.IO) { repo.getAlarm(id) }
            if (alarm == null) {
                ringJob = null
                next(); return@launch
            }
            settings = withContext(Dispatchers.IO) { applicationContext.container.settings.current() }
            current = alarm
            publishActive()
            _state.value = RingingState(alarm, queued = queue.size)

            getSystemService(NotificationManager::class.java)
                .notify(Notifications.ID_RINGING, Notifications.buildRinging(this@AlarmService, alarm))
            Notifications.cancelUpcoming(this@AlarmService, alarm.id)

            acquireWakeLock(settings.autoStopMinutes)
            setAlarmStreamVolume(alarm.volume)
            if (alarm.vibrate) startVibration()

            timeoutJob = launch {
                delay(settings.autoStopMinutes * 60_000L)
                finishCurrent(HistoryAction.TIMED_OUT, 0)
            }
            soundLoop(alarm)
        }
    }

    /** Tone → voice → wait, repeated until dismissed, snoozed or timed out. */
    private suspend fun soundLoop(alarm: Alarm) {
        val started = System.currentTimeMillis()
        fun gain(): Float = if (!alarm.gradualVolume) 1f
        else (0.3f + 0.7f * (System.currentTimeMillis() - started) / RAMP_MS.toFloat()).coerceIn(0.3f, 1f)

        if (!tts.awaitReady()) {
            Log.w(TAG, "TTS unavailable – falling back to alarm tone")
            playFallbackLoop(); return
        }
        var failures = 0
        while (currentCoroutineContext().isActive) {
            if (alarm.toneMode == ToneMode.TONE_THEN_VOICE) {
                playChimeOnce(gain())
                delay(250)
            }
            _state.value = _state.value?.copy(speaking = true)
            val text = SpeechText.build(alarm, LocalDateTime.now(), alarm.speakTime)
            val ok = tts.speak(
                text,
                SpeechConfig(alarm.voiceName, alarm.languageTag, alarm.speechRate, alarm.pitch, gain())
            )
            _state.value = _state.value?.copy(speaking = false)
            if (!ok) {
                failures++
                if (failures >= 2) {
                    Log.w(TAG, "Speech failed twice – falling back to alarm tone")
                    playFallbackLoop(); return
                }
            } else failures = 0

            if (alarm.repeatIntervalSec <= 0) {
                // Spoken once. Keep a soft chime going every 30 s so the user still wakes.
                if (alarm.toneMode == ToneMode.VOICE_ONLY) return
                delay(30_000)
                continue
            }
            delay(alarm.repeatIntervalSec * 1000L)
        }
    }

    private suspend fun playChimeOnce(volume: Float): Unit = suspendCancellableCoroutine<Unit> { cont ->
        stopPlayer()
        val mp = try {
            MediaPlayer.create(this, R.raw.gentle_chime, alarmAttributes(), audioManager.generateAudioSessionId())
        } catch (t: Throwable) {
            null
        }
        if (mp == null) {
            cont.resume(Unit); return@suspendCancellableCoroutine
        }
        player = mp
        mp.setVolume(volume, volume)
        mp.setOnCompletionListener {
            stopPlayer()
            if (cont.isActive) cont.resume(Unit)
        }
        mp.start()
        // On cancellation stopSound() stops and releases the player synchronously.
    }

    /** Loops the device's alarm sound (or our chime) when speech is not possible. */
    private fun playFallbackLoop() {
        stopPlayer()
        val mp = MediaPlayer()
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mp.setAudioAttributes(alarmAttributes())
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        } catch (t: Throwable) {
            mp.release()
            val chime = MediaPlayer.create(this, R.raw.gentle_chime, alarmAttributes(), audioManager.generateAudioSessionId())
            chime?.isLooping = true
            chime?.start()
            player = chime
        }
    }

    private fun alarmAttributes() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun stopPlayer() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 700, 500, 700, 1500)
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAttributes())
        }
    }

    private fun setAlarmStreamVolume(percent: Int) {
        runCatching {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (savedAlarmVolume == null) savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val target = (max * percent / 100f).toInt().coerceIn(1, max)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }
    }

    private fun restoreAlarmStreamVolume() {
        savedAlarmVolume?.let { v -> runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedAlarmVolume = null
    }

    private fun acquireWakeLock(minutes: Int) {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoxAlarm:ringing")
            .apply { acquire((minutes + 1) * 60_000L) }
    }

    private fun stopSound() {
        ringJob?.cancel(); ringJob = null
        timeoutJob?.cancel(); timeoutJob = null
        tts.stop()
        stopPlayer()
        runCatching { vibrator?.cancel() }
    }

    /** Ends the current alarm (dismiss / snooze / timeout) and moves on to any queued alarm. */
    private fun finishCurrent(action: String, snoozeMinutes: Int) {
        val alarm = current ?: run { if (!transitioning && ringJob == null) stopEverything(); return }
        current = null
        transitioning = true
        stopSound()
        restoreAlarmStreamVolume()
        _state.value = null
        publishActive()
        val app = applicationContext.container
        app.appScope.launch {
            if (action == HistoryAction.SNOOZED) app.repository.onSnoozed(alarm.id, snoozeMinutes)
            else app.repository.onDismissed(alarm.id, action)
            withContext(Dispatchers.Main) { next() }
        }
    }

    private fun next() {
        transitioning = false
        val nextId = queue.removeFirstOrNull()
        if (nextId != null) startRinging(nextId) else stopEverything()
    }

    private fun stopEverything() {
        stopSound()
        restoreAlarmStreamVolume()
        _state.value = null
        current = null
        queue.clear()
        publishActive()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishActive() {
        activeIds = buildSet { current?.let { add(it.id) }; addAll(queue) }
    }

    override fun onDestroy() {
        stopSound()
        restoreAlarmStreamVolume()
        wakeLock?.let { if (it.isHeld) it.release() }
        tts.shutdown()
        scope.cancel()
        _state.value = null
        activeIds = emptySet()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val RAMP_MS = 45_000L

        const val ACTION_RING = "com.voxalarm.action.RING"
        const val ACTION_DISMISS = "com.voxalarm.action.DISMISS"
        const val ACTION_SNOOZE = "com.voxalarm.action.SNOOZE"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

        private val _state = MutableStateFlow<RingingState?>(null)
        val state: StateFlow<RingingState?> = _state.asStateFlow()

        @Volatile private var activeIds: Set<Long> = emptySet()
        fun isActive(alarmId: Long): Boolean = alarmId in activeIds

        fun ring(context: Context, alarmId: Long) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(ACTION_RING)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            )
        }

        fun dismiss(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS))
        }

        fun snooze(context: Context, minutes: Int) {
            context.startService(
                Intent(context, AlarmService::class.java)
                    .setAction(ACTION_SNOOZE)
                    .putExtra(EXTRA_SNOOZE_MINUTES, minutes)
            )
        }
    }
}
