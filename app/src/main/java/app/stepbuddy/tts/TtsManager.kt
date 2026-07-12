package app.stepbuddy.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Thin wrapper around Android [TextToSpeech]. One instance is shared by the
 * playback service (reads each step aloud) and the UI ("Read this screen").
 *
 * Per-guide language: [speak] takes the guide's language code and switches the
 * voice to match. If the voice for that language isn't installed we report it so
 * the UI can offer to install it, then fall back to the default voice cleanly —
 * guidance text is still shown large on screen regardless.
 */
class TtsManager(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Set true whenever the last requested language had no installed voice. */
    private val _voiceMissing = MutableStateFlow(false)
    val voiceMissing: StateFlow<Boolean> = _voiceMissing.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
            _ready.value = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS) Log.w(TAG, "TTS init failed: $status")
        }
    }

    fun isLanguageAvailable(languageCode: String): Boolean {
        val engine = tts ?: return false
        return when (engine.isLanguageAvailable(localeFor(languageCode))) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> true
            else -> false
        }
    }

    /**
     * Speak [text] in [languageCode] at [rate] (0.5 slow … 2.0 fast), replacing
     * anything currently being spoken. No-op (but flags [voiceMissing]) if the
     * language voice is unavailable — the on-screen text remains the fallback.
     */
    fun speak(text: String, languageCode: String, rate: Float) {
        val engine = tts ?: return
        if (!_ready.value || text.isBlank()) return

        val result = engine.setLanguage(localeFor(languageCode))
        val missing = result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
        _voiceMissing.value = missing
        if (missing) {
            Log.w(TAG, "Voice for '$languageCode' not installed; falling back to default.")
            engine.setLanguage(Locale.getDefault())
        }
        engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun localeFor(code: String): Locale = when (code) {
        "ms" -> Locale("ms", "MY")
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "ta" -> Locale("ta", "IN")
        else -> Locale.ENGLISH
    }

    companion object {
        private const val TAG = "TtsManager"
        private const val UTTERANCE_ID = "stepbuddy-utterance"

        /** Intent to send the user to install missing TTS voice data. */
        fun installVoiceIntent(): Intent =
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    }
}
