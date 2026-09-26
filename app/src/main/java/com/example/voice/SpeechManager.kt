package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Thin wrapper over Android's on-device speech engines:
 *  - STT via [SpeechRecognizer] (Google on-device recognizer, offline-capable on modern devices)
 *  - TTS via [TextToSpeech]
 *
 * Fully self-contained and additive — nothing else in the app depends on it. Every entry point
 * fails soft: if speech services are unavailable the callbacks simply report an error and the
 * caller can fall back to the typed chat flow, matching the app's AI-optional design.
 *
 * Must be created with an Application context and [shutdown] on ViewModel clear.
 */
class SpeechManager(context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        // TTS initializes async; guard all speak() calls on ttsReady.
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            } else {
                Log.w(TAG, "TextToSpeech init failed: $status")
            }
        }
    }

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    /**
     * Starts a single listening session. [onResult] fires once with the best transcript;
     * [onError] fires with a human-readable reason on any failure. [onPartial] streams
     * interim text for live display. Must be called on the main thread.
     */
    fun startListening(
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isRecognitionAvailable) {
            onError("Speech recognition isn't available on this device.")
            return
        }
        // Try on-device first for latency/privacy; if the offline language pack isn't installed
        // (ERROR_LANGUAGE_UNAVAILABLE/NOT_SUPPORTED), retry once online so it still works.
        beginSession(preferOffline = true, allowOnlineRetry = true, onPartial, onResult, onError)
    }

    private fun beginSession(
        preferOffline: Boolean,
        allowOnlineRetry: Boolean,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Recreate per session — SpeechRecognizer is single-shot and must live on main thread.
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let { if (it.isNotBlank()) onPartial(it) }
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isBlank()) onError("Didn't catch that — try again.")
                    else onResult(text)
                }

                override fun onError(error: Int) {
                    // Offline language pack missing → fall back to online recognition once.
                    val languageUnavailable = error == 12 || error == 13 // NOT_SUPPORTED / UNAVAILABLE
                    if (preferOffline && allowOnlineRetry && languageUnavailable) {
                        beginSession(false, false, onPartial, onResult, onError)
                    } else {
                        onError(errorMessage(error))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            onError(e.message ?: "Couldn't start listening.")
        }
    }

    fun stopListening() {
        recognizer?.let {
            try { it.stopListening() } catch (_: Exception) {}
        }
    }

    /** Speaks [text] aloud. No-op until TTS finishes initializing. */
    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "buddy-${text.hashCode()}")
    }

    fun stopSpeaking() {
        tts?.let { try { it.stop() } catch (_: Exception) {} }
    }

    fun shutdown() {
        recognizer?.destroy(); recognizer = null
        tts?.let { it.stop(); it.shutdown() }; tts = null
        ttsReady = false
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Recognizer busy — try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Recognition timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy — try again."
        SpeechRecognizer.ERROR_SERVER -> "Speech server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard — tap and try again."
        11 -> "Speech service disconnected — try again."
        12, 13 -> "Language pack unavailable. Connect to the internet, or install the offline speech language in Settings."
        14 -> "Couldn't check speech support — try again."
        else -> "Speech recognition error ($code)."
    }

    companion object { private const val TAG = "SpeechManager" }
}
