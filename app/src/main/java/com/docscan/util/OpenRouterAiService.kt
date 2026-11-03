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
 * OpenRouter AI Service — OpenAI-compatible chat completions that can route to
 * many underlying models (OpenAI, Anthropic, Google, Meta, Mistral, etc.) behind
 * a single API key. Used to power the in-app "AI Chat" document assistant.
 * Docs: https://openrouter.ai/docs
 */
object OpenRouterAiService {
    private const val TAG = "OpenRouterAiService"
    private const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    // Pick any model slug from https://openrouter.ai/models — change freely.
    private const val DEFAULT_MODEL = "meta-llama/llama-3.3-70b-instruct"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isAvailable(context: Context? = null): Boolean {
        val key = AiSettingsManager.getOpenRouterKey(context)
        return key.isNotBlank()
    }

    private fun baseRequestBuilder(apiKey: String): Request.Builder {
        return Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            // Optional but recommended by OpenRouter for attribution/rate-limit tiers.
            .addHeader("HTTP-Referer", "https://docscan.app")
            .addHeader("X-Title", "DocScanner AI Assistant")
    }

    /**
     * Generic caller to OpenRouter's Chat Completions API
     */
    suspend fun callOpenRouterApi(
        prompt: String,
        temperature: Float = 0.2f,
        model: String = DEFAULT_MODEL,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = AiSettingsManager.getOpenRouterKey(context)
        if (apiKey.isBlank()) {
            Log.w(TAG, "OpenRouter API key is empty.")
            return@withContext ""
        }

        try {
            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a precise document intelligence assistant.")
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
            val request = baseRequestBuilder(apiKey).post(requestBody).build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "OpenRouter API HTTP error ${response.code}: $responseBodyString")
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
            Log.e(TAG, "Error executing OpenRouter API request: ${e.message}", e)
            ""
        }
    }

    /**
     * AI-Powered Interactive Document Assistant Chat (OpenRouter-backed)
     */
    suspend fun chatWithAi(
        history: List<Pair<String, String>>,
        userMessage: String,
        documentContext: String? = null,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = AiSettingsManager.getOpenRouterKey(context)
        if (apiKey.isBlank()) return@withContext ""

        try {
            val messagesArray = JSONArray()

            val systemContext = buildString {
                append("You are the CamScanner AI Document Assistant, an intelligent, helpful document intelligence companion routed via OpenRouter. ")
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
            val request = baseRequestBuilder(apiKey).post(requestBody).build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "OpenRouter chat error: ${response.code} $responseBodyString")
                val errorMsg = try {
                    JSONObject(responseBodyString).optJSONObject("error")?.optString("message") ?: "HTTP error ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                return@withContext "⚠️ OpenRouter API Notice ($errorMsg). Please verify your OpenRouter API key in Settings."
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
            Log.e(TAG, "Error in OpenRouter chatWithAi: ${e.message}", e)
            "⚠️ Network error connecting to OpenRouter: ${e.localizedMessage ?: "Please check internet connection."}"
        }
    }
}
