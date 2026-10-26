package com.voxalarm.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A voice as reported by the installed TTS engine. Nothing here is invented. */
data class VoiceInfo(
    val name: String,
    val languageTag: String,
    val localeDisplay: String,
    val quality: Int,
    val requiresNetwork: Boolean,
    val notInstalled: Boolean,
    /** Only set when the engine explicitly reports gender in the voice name/features. */
    val gender: String?,
) {
    val qualityLabel: String
        get() = when {
            quality >= Voice.QUALITY_VERY_HIGH -> "Very high quality"
            quality >= Voice.QUALITY_HIGH -> "High quality"
            quality >= Voice.QUALITY_NORMAL -> "Normal quality"
            else -> "Low quality"
        }
}

data class TtsState(
    val ready: Boolean = false,
    val failed: Boolean = false,
    val engineName: String = "",
    val voices: List<VoiceInfo> = emptyList(),
    val speaking: Boolean = false,
)

data class SpeechConfig(
    val voiceName: String = "",
    val languageTag: String = "",
    val rate: Float = 1f,
    val pitch: Float = 1f,
    /** 0..1 relative to the alarm stream volume. */
    val volume: Float = 1f,
)

/**
 * Coroutine-friendly wrapper around Android's [TextToSpeech].
 * Speech is routed through the ALARM audio stream so it is audible in silent/vibrate
 * modes (like an alarm tone) and follows the alarm volume.
 */
class TtsManager(context: Context) {

    private val appContext = context.applicationContext
    private var readySignal = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private lateinit var tts: TextToSpeech
    private var usingGoogle = false

    init {
        connect()
    }

    /**
     * Binds to Google's "Speech Services" engine when it is installed (best voices),
     * otherwise to the device's default engine.
     */
    private fun connect(allowGoogle: Boolean = true) {
        val google = allowGoogle && runCatching {
            appContext.packageManager.getPackageInfo(GOOGLE_TTS, 0); true
        }.getOrDefault(false)
        usingGoogle = google
        tts = if (google) TextToSpeech(appContext, { status -> onInit(status) }, GOOGLE_TTS)
        else TextToSpeech(appContext) { status -> onInit(status) }
    }

    /**
     * Call when a screen resumes: retries if no engine was available before (e.g. the
     * user just installed one) and refreshes the voice list (new voices downloaded).
     */
    fun refresh() {
        if (_state.value.failed) {
            runCatching { tts.shutdown() }
            readySignal = CompletableDeferred()
            _state.value = TtsState()
            connect()
        } else if (_state.value.ready) {
            refreshVoices()
        }
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS && usingGoogle) {
            // Google engine present but unusable (e.g. disabled) – try the device default.
            Log.w(TAG, "Google TTS init failed: $status – trying default engine")
            // Posted, because the engine may report failure from inside its constructor.
            Handler(Looper.getMainLooper()).post {
                runCatching { tts.shutdown() }
                connect(allowGoogle = false)
            }
            return
        }
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS init failed: $status")
            _state.value = _state.value.copy(failed = true)
            readySignal.complete(false)
            return
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = _state.value.copy(speaking = true)
            }

            override fun onDone(utteranceId: String?) = finish(utteranceId, true)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId, false)

            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, false)
        })
        refreshVoices()
        readySignal.complete(true)
    }

    private fun finish(id: String?, ok: Boolean) {
        _state.value = _state.value.copy(speaking = false)
        id?.let { pending.remove(it)?.complete(ok) }
    }

    fun refreshVoices() {
        val voices = try {
            tts.voices.orEmpty().map { it.toInfo() }
                .sortedWith(compareBy({ it.localeDisplay }, { it.notInstalled }, { it.requiresNetwork }, { it.name }))
        } catch (t: Throwable) {
            Log.w(TAG, "Could not list voices", t); emptyList()
        }
        _state.value = _state.value.copy(
            ready = true,
            voices = voices,
            engineName = runCatching { tts.defaultEngine }.getOrNull().orEmpty(),
        )
    }

    /** Waits for the engine; returns false if it failed or didn't start in time. */
    suspend fun awaitReady(timeoutMs: Long = 6000): Boolean =
        withTimeoutOrNull(timeoutMs) { readySignal.await() } ?: false

    /**
     * Speaks [text] and suspends until it finishes. Returns true if the engine
     * reported success.
     */
    suspend fun speak(text: String, config: SpeechConfig): Boolean {
        if (!awaitReady()) return false
        apply(config)
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        pending[id] = done
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, config.volume.coerceIn(0f, 1f))
        }
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result != TextToSpeech.SUCCESS) {
            pending.remove(id)
            return false
        }
        // Generous timeout: ~ 1 s per 8 characters at normal speed, min 15 s.
        val timeout = 15_000L + (text.length * 150f / config.rate.coerceAtLeast(0.25f)).toLong()
        return withTimeoutOrNull(timeout) { done.await() } ?: false
    }

    private fun apply(config: SpeechConfig) {
        val locale = config.languageTag.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it) }
            ?: Locale.getDefault()
        runCatching { tts.setLanguage(locale) }
        if (config.voiceName.isNotBlank()) {
            val voice = runCatching { tts.voices?.firstOrNull { it.name == config.voiceName } }.getOrNull()
            if (voice != null) runCatching { tts.setVoice(voice) }
        }
        tts.setSpeechRate(config.rate.coerceIn(0.25f, 3f))
        tts.setPitch(config.pitch.coerceIn(0.25f, 3f))
    }

    fun stop() {
        runCatching { tts.stop() }
        pending.values.forEach { it.complete(false) }
        pending.clear()
        _state.value = _state.value.copy(speaking = false)
    }

    fun shutdown() {
        stop()
        runCatching { tts.shutdown() }
    }

    private fun Voice.toInfo(): VoiceInfo {
        val feats = runCatching { features.orEmpty() }.getOrDefault(emptySet())
        val lower = (name + " " + feats.joinToString(" ")).lowercase(Locale.ROOT)
        val gender = when {
            "female" in lower -> "Female"
            Regex("\\bmale\\b|[^e]male").containsMatchIn(lower) -> "Male"
            else -> null
        }
        return VoiceInfo(
            name = name,
            languageTag = locale.toLanguageTag(),
            localeDisplay = locale.getDisplayName(Locale.getDefault()),
            quality = quality,
            requiresNetwork = isNetworkConnectionRequired,
            notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in feats,
            gender = gender,
        )
    }

    companion object {
        private const val TAG = "TtsManager"
        const val GOOGLE_TTS = "com.google.android.tts"
    }
}
