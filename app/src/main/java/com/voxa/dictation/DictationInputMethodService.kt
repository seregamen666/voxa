package com.voxa.dictation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.voxa.dictation.databinding.KeyboardViewBinding
import java.util.Locale

/**
 * Клавиатура вместо обычной раскладки: одна большая кнопка "держи — говори".
 * Распознавание идёт через системный SpeechRecognizer с EXTRA_PREFER_OFFLINE —
 * если в системе включена офлайн-модель языка, звук не уходит в сеть.
 */
class DictationInputMethodService : InputMethodService(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var binding: KeyboardViewBinding? = null

    override fun onCreateInputView(): View {
        val b = KeyboardViewBinding.inflate(layoutInflater)
        binding = b

        b.btnMic.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    startListening()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopListening()
                    v.performClick()
                    true
                }
                else -> false
            }
        }

        b.btnBackspace.setOnClickListener { sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL) }
        b.btnSpace.setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        b.btnEnter.setOnClickListener { sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER) }
        b.btnSwitchKeyboard.setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        return b.root
    }

    private fun sendDownUpKeyEvent(keyCode: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (isListening) return
        if (!hasMicPermission()) {
            binding?.tvHint?.text = getString(R.string.keyboard_hint_no_permission)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding?.tvHint?.text = getString(R.string.keyboard_hint_error)
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(this@DictationInputMethodService)
            }
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        isListening = true
        binding?.btnMic?.setBackgroundResource(R.drawable.bg_mic_recording)
        binding?.tvHint?.text = getString(R.string.keyboard_hint_listening)
        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun stopListening() {
        if (!isListening) return
        speechRecognizer?.stopListening()
    }

    private fun resetMicUi() {
        isListening = false
        binding?.btnMic?.setBackgroundResource(R.drawable.bg_mic_idle)
        binding?.tvHint?.text = getString(R.string.keyboard_hint_idle)
    }

    // --- RecognitionListener ---

    override fun onResults(results: Bundle) {
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        if (!text.isNullOrBlank()) {
            currentInputConnection?.commitText("$text ", 1)
        }
        resetMicUi()
    }

    override fun onError(error: Int) {
        binding?.tvHint?.text = getString(R.string.keyboard_hint_error)
        resetMicUi()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
