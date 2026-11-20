package com.punto.launcher.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Microfone (fala -> texto) e voz do assistente (texto -> fala), em pt-BR. */
class Voice(
    private val ctx: Context,
    private val onText: (String) -> Unit,
) : TextToSpeech.OnInitListener {

    var overlayVisible by mutableStateOf(false)
        private set
    var listening by mutableStateOf(false)
        private set
    var heard by mutableStateOf("")
        private set
    var reply by mutableStateOf("")
        private set
    var level by mutableFloatStateOf(0f)
        private set

    private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
    private val tts = TextToSpeech(ctx, this)
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    val inAppRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = ptBR
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            ttsReady = true
        }
    }

    fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale um comando")

    fun listen() {
        tts.stop()
        overlayVisible = true
        heard = ""
        reply = ""
        level = 0f
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(ctx).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        listening = true
        r.startListening(recognizerIntent())
    }

    /** Usado pelos atalhos de comando (chips) e pelo reconhecimento em tela cheia. */
    fun showHeard(text: String) {
        overlayVisible = true
        listening = false
        heard = text
        reply = ""
    }

    fun say(text: String) {
        overlayVisible = true
        reply = text
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "punto-reply")
    }

    fun dismiss() {
        recognizer?.cancel()
        listening = false
        overlayVisible = false
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        tts.stop()
        tts.shutdown()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            level = 0f
        }

        override fun onError(error: Int) {
            listening = false
            level = 0f
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> "Não ouvi nada. Toque no microfone e fale de novo."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                -> "Estou sem internet para entender a voz."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Preciso de permissão para usar o microfone."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    recognizer?.destroy()
                    recognizer = null
                    "O microfone estava ocupado. Tente de novo."
                }
                else -> "Não consegui ouvir (erro $error). Tente de novo."
            }
            say(msg)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            level = 0f
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isBlank()) {
                say("Não entendi. Tente de novo.")
            } else {
                heard = text
                onText(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { heard = it }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
