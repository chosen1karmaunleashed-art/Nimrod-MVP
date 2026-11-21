package com.example.nimrod

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the OpenAI chat completions endpoint.
 *
 * The API key comes from BuildConfig (fed by local.properties) and is never
 * hardcoded or committed. [callback] runs on a background thread; callers that
 * touch UI must hop back to the main thread themselves.
 */
object OpenAIClient {

    private const val TAG = "OpenAIClient"
    private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
    private const val MODEL = "gpt-4o-mini"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """
You are Nimrod, a voice assistant.

Here is past memory:
%s

Respond naturally and intelligently. You are speaking out loud, so keep answers
conversational and concise - usually one to three sentences, no markdown, no
bullet lists, no code blocks unless the user explicitly asks for code.
""".trimIndent()

    fun sendMessage(userMessage: String, contextMemory: String, callback: (String) -> Unit) {
        val apiKey = BuildConfig.OPENAI_API_KEY

        if (apiKey.isBlank()) {
            callback("I don't have an OpenAI key configured yet. Add OPENAI_API_KEY to local.properties and rebuild.")
            return
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT.format(contextMemory))
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val payload = JSONObject().apply {
            put("model", MODEL)
            put("messages", messages)
            put("temperature", 0.7)
            put("max_tokens", 400)
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed", e)
                callback("I couldn't reach the network just now. Try again in a moment.")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()

                    if (!it.isSuccessful) {
                        Log.e(TAG, "HTTP ${it.code}: $body")
                        callback(errorMessageFor(it.code))
                        return
                    }

                    val text = runCatching {
                        JSONObject(body)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    }.getOrNull()

                    callback(text?.takeIf { t -> t.isNotBlank() } ?: "I didn't get a response back that time.")
                }
            }
        })
    }

    private fun errorMessageFor(code: Int): String = when (code) {
        401 -> "My API key was rejected. Check the OpenAI key in local.properties."
        429 -> "I'm being rate limited or out of credits right now. Try again shortly."
        in 500..599 -> "OpenAI is having trouble on their end. Try again in a moment."
        else -> "Something went wrong talking to OpenAI (error $code)."
    }
}
