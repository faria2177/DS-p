package com.docscan.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Groq AI Service — OpenAI-compatible chat completions via Groq's LPU inference.
 * Extremely low-latency, good for the in-app "AI Chat" document assistant.
 * Docs: https://console.groq.com/docs/quickstart
 */
object GroqAiService {
    private const val TAG = "GroqAiService"
    private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
    // Fast, capable default. Change if you prefer another model hosted on Groq
    // (e.g. "llama-3.1-8b-instant" for even lower latency, or "openai/gpt-oss-120b").
    private const val DEFAULT_MODEL = "llama-3.3-70b-versatile"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isAvailable(context: Context? = null): Boolean {
        val key = AiSettingsManager.getGroqKey(context)
        return key.isNotBlank()
    }

    /**
     * Generic caller to Groq's Chat Completions API
     */
    suspend fun callGroqApi(
        prompt: String,
        temperature: Float = 0.2f,
        model: String = DEFAULT_MODEL,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = AiSettingsManager.getGroqKey(context)
        if (apiKey.isBlank()) {
            Log.w(TAG, "Groq API key is empty.")
            return@withContext ""
        }

        try {
            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a fast, precise document intelligence assistant running on Groq.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", temperature)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Groq API HTTP error ${response.code}: $responseBodyString")
                return@withContext ""
            }

            val responseJson = JSONObject(responseBodyString)
            val choices = responseJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                return@withContext message?.optString("content", "")?.trim() ?: ""
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Groq API request: ${e.message}", e)
            ""
        }
    }

    /**
     * AI-Powered Interactive Document Assistant Chat (Groq-backed)
     */
    suspend fun chatWithAi(
        history: List<Pair<String, String>>,
        userMessage: String,
        documentContext: String? = null,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = AiSettingsManager.getGroqKey(context)
        if (apiKey.isBlank()) return@withContext ""

        try {
            val messagesArray = JSONArray()

            val systemContext = buildString {
                append("You are the CamScanner AI Document Assistant, an intelligent, helpful document intelligence companion powered by Groq. ")
                append("You assist users with understanding scanned documents, extracting numbers, dates, summaries, and answering any document-related questions in English or Bengali. ")
                if (!documentContext.isNullOrBlank()) {
                    append("\n\nCurrently Scanned Document Context:\n")
                    append(documentContext)
                }
            }

            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemContext)
            })

            for ((sender, msg) in history.takeLast(8)) {
                val role = if (sender.equals("You", ignoreCase = true) || sender.equals("User", ignoreCase = true)) "user" else "assistant"
                messagesArray.put(JSONObject().apply {
                    put("role", role)
                    put("content", msg)
                })
            }
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            val requestJson = JSONObject().apply {
                put("model", DEFAULT_MODEL)
                put("temperature", 0.3)
                put("max_tokens", 2000)
                put("messages", messagesArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Groq chat error: ${response.code} $responseBodyString")
                val errorMsg = try {
                    JSONObject(responseBodyString).optJSONObject("error")?.optString("message") ?: "HTTP error ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                return@withContext "⚠️ Groq API Notice ($errorMsg). Please verify your Groq API key in Settings."
            }

            val responseJson = JSONObject(responseBodyString)
            val choices = responseJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val message = choice.optJSONObject("message")
                return@withContext message?.optString("content")?.trim() ?: ""
            }
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error in Groq chatWithAi: ${e.message}", e)
            "⚠️ Network error connecting to Groq: ${e.localizedMessage ?: "Please check internet connection."}"
        }
    }
}
