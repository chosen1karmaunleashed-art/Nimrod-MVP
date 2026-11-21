package com.example.nimrod

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.nimrod.audio.WakeWordManager
import com.example.nimrod.data.AppDatabase
import com.example.nimrod.databinding.ActivityMainBinding
import com.example.nimrod.memory.MemoryManager
import com.example.nimrod.topic.TopicManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Nimrod's entry point.
 *
 * Flow: wake word (Porcupine) -> speech recognition -> topic classification ->
 * memory retrieval -> OpenAI -> spoken reply -> message + topic persisted in Room.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var memory: MemoryManager
    private lateinit var wakeWordManager: WakeWordManager

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var busy = false

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startWakeWord() else {
            Toast.makeText(this, R.string.mic_permission_rationale, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.get(this)
        memory = MemoryManager(db)

        setupTextToSpeech()
        setupSpeechRecognizer()

        // Tapping the transcript area is the manual fallback when the wake word
        // is unavailable (no Picovoice key, or a quiet room).
        binding.transcriptText.setOnClickListener { if (!busy) startListening() }

        if (hasMicPermission()) {
            startWakeWord()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        lifecycleScope.launch {
            val stored = withContext(Dispatchers.IO) { memory.messageCount() }
            if (stored > 0) appendLine("(resumed with $stored remembered messages)")
        }
    }

    // ---------------------------------------------------------------- audio setup

    private fun setupTextToSpeech() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread { setStatus(R.string.status_speaking) }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            busy = false
                            setStatus(R.string.status_idle)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            busy = false
                            setStatus(R.string.status_idle)
                        }
                    }
                })
            } else {
                Log.e(TAG, "TextToSpeech init failed with status $status")
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "No speech recognition service available on this device")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()
            if (text.isBlank()) {
                busy = false
                setStatus(R.string.status_idle)
            } else {
                processUserInput(text)
            }
        }

        override fun onError(error: Int) {
            Log.w(TAG, "Speech recognition error: $error")
            busy = false
            setStatus(R.string.status_idle)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun startWakeWord() {
        if (::wakeWordManager.isInitialized) return
        wakeWordManager = WakeWordManager(this) {
            runOnUiThread { if (!busy) startListening() }
        }
        wakeWordManager.start()
    }

    private fun startListening() {
        val recognizer = speechRecognizer ?: run {
            Log.w(TAG, "Speech recognizer unavailable")
            return
        }
        busy = true
        setStatus(R.string.status_listening)

        // Pause the wake word while we are actively capturing speech.
        if (::wakeWordManager.isInitialized) wakeWordManager.stop()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.startListening(intent)
    }

    // ------------------------------------------------------------------ main flow

    private fun processUserInput(userText: String) {
        setStatus(R.string.status_thinking)
        appendLine("You: $userText")

        val topic = TopicManager.classify(userText)

        lifecycleScope.launch {
            // Persist the user turn and pull context on a background thread.
            val contextMemory = withContext(Dispatchers.IO) {
                memory.remember(userText, role = "user", topicName = topic)
                memory.buildContext(currentTopic = topic)
            }

            OpenAIClient.sendMessage(userText, contextMemory) { response ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        memory.remember(response, role = "nimrod", topicName = topic)
                    }
                    runOnUiThread {
                        appendLine("Nimrod: $response")
                        speak(response)
                    }
                }
            }
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            busy = false
            setStatus(R.string.status_idle)
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    // ------------------------------------------------------------------- lifecycle

    override fun onResume() {
        super.onResume()
        if (::wakeWordManager.isInitialized && !busy) wakeWordManager.start()
    }

    override fun onPause() {
        if (::wakeWordManager.isInitialized) wakeWordManager.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (::wakeWordManager.isInitialized) wakeWordManager.release()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    // -------------------------------------------------------------------- helpers

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun setStatus(resId: Int) {
        binding.statusText.setText(resId)
    }

    private fun appendLine(line: String) {
        val current = binding.transcriptText.text?.toString().orEmpty()
        binding.transcriptText.text = if (current.isBlank()) line else "$current\n\n$line"
        binding.scrollView.post { binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val UTTERANCE_ID = "nimrod-reply"
    }
}
