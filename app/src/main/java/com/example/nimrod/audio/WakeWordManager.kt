package com.example.nimrod.audio

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import com.example.nimrod.BuildConfig

/**
 * Always-on wake word detection via Picovoice Porcupine.
 *
 * Resolution order for the keyword:
 *   1. a custom model at res/raw/nimrod.ppn (the real "Nimrod" wake word), then
 *   2. Porcupine's built-in JARVIS as a placeholder so the app still runs.
 *
 * Custom keywords are trained in the Picovoice console and dropped into res/raw.
 * The .ppn files are gitignored because they are account-bound and large.
 */
class WakeWordManager(
    private val context: Context,
    private val onWakeWord: () -> Unit
) {

    private var manager: PorcupineManager? = null
    private var running = false

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return

        val accessKey = BuildConfig.PICOVOICE_ACCESS_KEY
        if (accessKey.isBlank()) {
            Log.w(TAG, "No PICOVOICE_ACCESS_KEY configured - wake word disabled, use the on-screen button.")
            return
        }

        try {
            val builder = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setSensitivity(0.6f)

            val customKeyword = context.resources.getIdentifier(
                CUSTOM_KEYWORD_NAME, "raw", context.packageName
            )

            if (customKeyword != 0) {
                builder.setKeywordPath("$CUSTOM_KEYWORD_NAME.ppn")
            } else {
                Log.i(TAG, "No res/raw/$CUSTOM_KEYWORD_NAME.ppn found - falling back to built-in JARVIS.")
                builder.setKeyword(Porcupine.BuiltInKeyword.JARVIS)
            }

            manager = builder.build(context) {
                Log.d(TAG, "Wake word detected")
                onWakeWord()
            }
            manager?.start()
            running = true
            Log.i(TAG, "Wake word listening started")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Could not start Porcupine: ${e.message}", e)
            running = false
        }
    }

    fun stop() {
        try {
            manager?.stop()
        } catch (e: PorcupineException) {
            Log.e(TAG, "Error stopping Porcupine", e)
        } finally {
            running = false
        }
    }

    fun release() {
        try {
            manager?.delete()
        } catch (e: PorcupineException) {
            Log.e(TAG, "Error releasing Porcupine", e)
        } finally {
            manager = null
            running = false
        }
    }

    private companion object {
        const val TAG = "WakeWordManager"
        const val CUSTOM_KEYWORD_NAME = "nimrod"
    }
}
