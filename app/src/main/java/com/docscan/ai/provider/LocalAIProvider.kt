package com.docscan.ai.provider

import android.content.Context
import android.util.Log
import com.docscan.ai.manager.LocalModelManager
import com.docscan.data.model.ChatMessageEntity
import com.docscan.util.AiOrchestrator
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LocalAIProvider : AIProvider {

    override val id: String = "LOCAL"
    override val displayName: String = "Local AI"
    override val isLocal: Boolean = true

    companion object {
        private const val TAG = "LocalAIProvider"
        @Volatile
        private var cachedInference: LlmInference? = null
        @Volatile
        private var loadedModelPath: String? = null
    }

    private val isCancelled = AtomicBoolean(false)

    override fun isAvailable(context: Context): Boolean {
        return true
    }

    override fun generateStream(
        prompt: String,
        history: List<ChatMessageEntity>,
        documentContext: String?,
        context: Context
    ): Flow<StreamEvent> = callbackFlow {
        isCancelled.set(false)
        trySend(StreamEvent.ProviderStarted(displayName, isLocal = true))

        val activeModel = LocalModelManager.getActiveInstalledModel(context)
        val isBuiltIn = (activeModel == null || activeModel.id == LocalModelManager.BUILTIN_MODEL.id)
        val modelFile = if (activeModel != null) LocalModelManager.getModelFile(context, activeModel) else null
        val hasExternalModel = (!isBuiltIn && modelFile != null && modelFile.exists() && LocalModelManager.isModelInstalled(context, activeModel!!))

        if (!hasExternalModel) {
            val historyPairs = history.map {
                (if (it.role.equals("user", ignoreCase = true)) "User" else "Assistant") to it.content
            }

            val job = launch(Dispatchers.IO) {
                try {
                    val fullResponse = AiOrchestrator.chatWithAi(
                        history = historyPairs,
                        userMessage = prompt,
                        documentContext = documentContext,
                        context = context
                    )

                    if (isCancelled.get()) return@launch

                    val textToSend = if (fullResponse.isNotBlank()) {
                        fullResponse
                    } else {
                        "Hello! I am your offline AI assistant. How can I help you with your scanned document?"
                    }

                    val words = textToSend.split(" ")
                    val chunkBuilder = StringBuilder()
                    for (i in words.indices) {
                        if (isCancelled.get()) break
                        chunkBuilder.append(words[i])
                        if (i < words.size - 1) chunkBuilder.append(" ")

                        if ((i + 1) % 2 == 0 || i == words.size - 1) {
                            trySend(StreamEvent.Token(chunkBuilder.toString()))
                            chunkBuilder.clear()
                            delay(14)
                        }
                    }

                    trySend(StreamEvent.Completed(textToSend))
                    close()
                } catch (e: Exception) {
                    Log.e(TAG, "Local built-in AI error: ${e.message}", e)
                    trySend(StreamEvent.Error("Local AI error: ${e.localizedMessage}", canFallback = false))
                    close()
                }
            }

            awaitClose {
                isCancelled.set(true)
                job.cancel()
            }
            return@callbackFlow
        }

        val formattedPrompt = formatConversationPrompt(prompt, history, documentContext, activeModel!!.name)
        val fullResponse = StringBuilder()

        try {
            val inference = getOrInitInference(context, modelFile!!.absolutePath) { partialText, isDone ->
                if (isCancelled.get()) return@getOrInitInference

                if (partialText.isNotEmpty()) {
                    fullResponse.append(partialText)
                    trySend(StreamEvent.Token(partialText))
                }

                if (isDone) {
                    trySend(StreamEvent.Completed(fullResponse.toString()))
                    close()
                }
            }

            if (inference == null) {
                // Fallback to built-in local engine seamlessly
                val historyPairs = history.map {
                    (if (it.role.equals("user", ignoreCase = true)) "User" else "Assistant") to it.content
                }
                val fallbackText = AiOrchestrator.chatWithAi(
                    history = historyPairs,
                    userMessage = prompt,
                    documentContext = documentContext,
                    context = context
                )
                trySend(StreamEvent.Completed(fallbackText))
                close()
                return@callbackFlow
            }

            inference.generateResponseAsync(formattedPrompt)

        } catch (e: Exception) {
            Log.e(TAG, "Error during local inference: ${e.message}", e)
            val historyPairs = history.map {
                (if (it.role.equals("user", ignoreCase = true)) "User" else "Assistant") to it.content
            }
            val fallbackText = AiOrchestrator.chatWithAi(
                history = historyPairs,
                userMessage = prompt,
                documentContext = documentContext,
                context = context
            )
            trySend(StreamEvent.Completed(fallbackText))
            close()
        }

        awaitClose {
            isCancelled.set(true)
        }
    }

    override fun cancel() {
        isCancelled.set(true)
    }

    @Synchronized
    private fun getOrInitInference(
        context: Context,
        modelPath: String,
        resultCallback: (String, Boolean) -> Unit
    ): LlmInference? {
        return try {
            if (cachedInference != null && loadedModelPath == modelPath) {
                // Return cached if model hasn't changed
                return cachedInference
            }

            // Close existing instance if any
            unload()

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(2048)
                .setResultListener { partial, done ->
                    resultCallback(partial, done)
                }
                .setErrorListener { error ->
                    Log.e(TAG, "LlmInference error listener: ${error.message}")
                    resultCallback("\n[Error: ${error.message}]", true)
                }
                .build()

            val instance = LlmInference.createFromOptions(context, options)
            cachedInference = instance
            loadedModelPath = modelPath
            Log.i(TAG, "Successfully initialized on-device LLM from $modelPath")
            instance
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create LlmInference from $modelPath: ${e.message}", e)
            null
        }
    }

    fun unload() {
        try {
            // Some native implementations release resources on close
            cachedInference = null
            loadedModelPath = null
            System.gc()
            Log.d(TAG, "Unloaded on-device LLM")
        } catch (e: Exception) {
            Log.w(TAG, "Error unloading LLM: ${e.message}")
        }
    }

    private fun formatConversationPrompt(
        userMessage: String,
        history: List<ChatMessageEntity>,
        documentContext: String?,
        modelName: String
    ): String = buildString {
        append("<|system|>\n")
        append("You are Doc Scanner's Private On-Device AI Assistant ($modelName). ")
        append("You operate 100% locally and offline. Be helpful, precise, clear, and professional. ")
        append("When document excerpts are provided, ground your answers directly in the document facts.\n")

        if (!documentContext.isNullOrBlank()) {
            append("\n[CURRENT DOCUMENT CONTEXT]\n")
            append(documentContext.trim())
            append("\n[END DOCUMENT CONTEXT]\n")
        }
        append("<|end|>\n")

        // Include recent conversation history (up to last 6 messages to preserve context budget)
        val recentHistory = history.takeLast(6)
        for (msg in recentHistory) {
            when (msg.role.lowercase()) {
                "user" -> append("<|user|>\n${msg.content.trim()}<|end|>\n")
                "assistant" -> append("<|assistant|>\n${msg.content.trim()}<|end|>\n")
            }
        }

        // Current user message
        append("<|user|>\n${userMessage.trim()}<|end|>\n")
        append("<|assistant|>\n")
    }
}
