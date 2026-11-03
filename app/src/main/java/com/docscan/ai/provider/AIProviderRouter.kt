package com.docscan.ai.provider

import android.content.Context
import android.util.Log
import com.docscan.data.model.ChatMessageEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

object AIProviderRouter {

    private const val TAG = "AIProviderRouter"
    private const val PREFS_NAME = "ai_router_prefs"
    private const val KEY_ROUTER_MODE = "key_router_mode"

    val localProvider = LocalAIProvider()
    val geminiProvider = GeminiProvider()
    val claudeProvider = ClaudeProvider()

    private val currentActiveProvider = AtomicReference<AIProvider?>(null)

    fun getSelectedMode(context: Context): AIProviderMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_ROUTER_MODE, AIProviderMode.GEMINI.name)
        return try {
            AIProviderMode.valueOf(name ?: AIProviderMode.GEMINI.name)
        } catch (_: Exception) {
            AIProviderMode.GEMINI
        }
    }

    fun setSelectedMode(context: Context, mode: AIProviderMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROUTER_MODE, mode.name)
            .apply()
    }

    fun getActiveProviderInfo(context: Context): Pair<String, Boolean> {
        val mode = getSelectedMode(context)
        return when (mode) {
            AIProviderMode.GEMINI -> "Google Gemini" to false
            AIProviderMode.AUTO -> "Google Gemini" to false
            AIProviderMode.LOCAL -> "Local AI" to true
            AIProviderMode.CLAUDE -> "Claude" to false
        }
    }

    fun streamChat(
        prompt: String,
        history: List<ChatMessageEntity>,
        documentContext: String?,
        context: Context
    ): Flow<StreamEvent> = callbackFlow {
        val mode = getSelectedMode(context)
        val targetProviders = resolveProviderChain(mode, context)

        val job = launch {
            var succeeded = false
            var lastErrorMessage = "No AI provider available."

            for (provider in targetProviders) {
                currentActiveProvider.set(provider)
                Log.d(TAG, "Attempting inference with provider: ${provider.displayName}")

                try {
                    var providerFailed = false
                    provider.generateStream(prompt, history, documentContext, context).collect { event ->
                        when (event) {
                            is StreamEvent.Error -> {
                                if (event.canFallback && provider != targetProviders.last()) {
                                    Log.w(TAG, "${provider.displayName} failed, falling back: ${event.message}")
                                    providerFailed = true
                                    lastErrorMessage = event.message
                                } else {
                                    trySend(event)
                                }
                            }
                            is StreamEvent.Completed -> {
                                succeeded = true
                                trySend(event)
                            }
                            else -> {
                                trySend(event)
                            }
                        }
                    }

                    if (!providerFailed && succeeded) {
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in ${provider.displayName}: ${e.message}")
                    lastErrorMessage = e.localizedMessage ?: "Unknown error"
                }
            }

            if (!succeeded) {
                trySend(StreamEvent.Error(lastErrorMessage))
            }
            close()
        }

        awaitClose {
            currentActiveProvider.get()?.cancel()
            job.cancel()
        }
    }

    fun cancelActiveInference() {
        currentActiveProvider.get()?.cancel()
    }

    private fun resolveProviderChain(mode: AIProviderMode, context: Context): List<AIProvider> {
        return when (mode) {
            AIProviderMode.GEMINI -> listOf(geminiProvider, localProvider)
            AIProviderMode.AUTO -> listOf(geminiProvider, localProvider)
            AIProviderMode.LOCAL -> listOf(localProvider)
            AIProviderMode.CLAUDE -> {
                if (claudeProvider.isAvailable(context)) listOf(claudeProvider, geminiProvider, localProvider)
                else listOf(geminiProvider, localProvider)
            }
        }
    }
}
