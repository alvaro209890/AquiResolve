package com.aquiresolve.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Entrada por voz (fala → texto) usando o reconhecedor NATIVO do Android (Google), em pt-BR.
 *
 * Não depende de chave de API nem de backend: o sistema operacional faz o STT. Exige a permissão
 * [android.Manifest.permission.RECORD_AUDIO] e, no Android 11+, o `<queries>` de
 * `android.speech.RecognitionService` no Manifest (para [isAvailable] enxergar o serviço).
 *
 * Reusa uma única instância de [SpeechRecognizer] e entrega:
 *  - [onReadyForSpeech]: o microfone está pronto (mostre "ouvindo…");
 *  - [onPartial]: texto parcial enquanto a pessoa fala (preencha o campo ao vivo);
 *  - [onResult]: texto final reconhecido;
 *  - [onError]: mensagem amigável em pt-BR (vazia = cancelamento silencioso, não exiba);
 *  - [onEnd]: sempre ao terminar (sucesso ou erro) — bom para resetar a UI.
 *
 * Nunca lança para o chamador; qualquer falha vira [onError]/[onEnd].
 */
class VoiceInputManager(
    private val context: Context,
    private val onReadyForSpeech: () -> Unit = {},
    private val onPartial: (String) -> Unit = {},
    private val onResult: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onEnd: () -> Unit = {}
) {

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    val isListening: Boolean get() = listening

    /** Há um reconhecedor de voz disponível no aparelho? */
    fun isAvailable(): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (_: Exception) {
        false
    }

    /** Começa a ouvir. Se já estiver ouvindo ou indisponível, trata com [onError]. */
    fun start() {
        if (listening) return
        if (!isAvailable()) {
            onError("Reconhecimento de voz indisponível neste aparelho.")
            onEnd()
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listening = true
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            listening = false
            onError("Não foi possível iniciar a escuta.")
            onEnd()
        }
    }

    /** Encerra a captura e processa o que já foi falado. */
    fun stop() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    /** Cancela sem processar (silencioso). */
    fun cancel() {
        listening = false
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    /** Libera o reconhecedor — chame em onDestroy. */
    fun destroy() {
        listening = false
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onReadyForSpeech()
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            listening = false
            val msg = messageForError(error)
            if (msg.isNotBlank()) onError(msg)
            onEnd()
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) onResult(text) else onError("Não entendi. Pode repetir?")
            onEnd()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // Mensagens amigáveis em pt-BR. ERROR_CLIENT costuma ser cancelamento → silencioso ("").
    private fun messageForError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Erro ao captar o áudio. Tente de novo."
        SpeechRecognizer.ERROR_CLIENT -> ""
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permita o microfone para falar."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Sem conexão para reconhecer a voz."
        SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi. Pode repetir?"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Um instante e tente de novo."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não ouvi nada. Toque e fale."
        else -> "Não consegui captar sua voz."
    }
}
