package com.example.jarvisphone.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceController(private val context: Context) {

    var onResult: ((String) -> Unit)? = null
    var onSpeaking: ((Boolean) -> Unit)? = null
    var onListening: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isListening = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(context).not()) {
            onError?.invoke("Speech recognizer unavailable")
            return
        }

        stopListening()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onListening?.invoke(true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    onListening?.invoke(false)
                }

                override fun onError(error: Int) {
                    isListening = false
                    onListening?.invoke(false)
                    onError?.invoke("Speech error $error")
                    restartIfNeeded()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    onListening?.invoke(false)
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = texts?.firstOrNull().orEmpty()
                    if (best.isNotBlank()) onResult?.invoke(best)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
            isListening = false
            onListening?.invoke(false)
        }
    }

    fun speak(text: String) {
        onSpeaking?.invoke(true)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-${System.currentTimeMillis()}")
        onSpeaking?.invoke(false)
    }

    private fun restartIfNeeded() {
        if (isListening) return
    }

    fun release() {
        stopListening()
        tts?.shutdown()
        tts = null
    }
}
