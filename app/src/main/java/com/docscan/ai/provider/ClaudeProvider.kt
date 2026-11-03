package com.docscan.ai.provider

import android.content.Context
import android.util.Log
import com.docscan.data.model.ChatMessageEntity
import com.docscan.util.ClaudeAiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ClaudeProvider : AIProvider {

    override val id: String = "CLAUDE"
    override val displayName: String = "Anthropic Claude"
    override val isLocal: Boolean = false

    private val isCancelled = AtomicBoolean(false)

    override fun isAvailable(context: Context): Boolean {
        return ClaudeAiService.isAvailable(context)
    }

    override fun generateStream(
        prompt: String,
        history: List<ChatMessageEntity>,
        documentContext: String?,
        context: Context
    ): Flow<StreamEvent> = callbackFlow {
        isCancelled.set(false)
        trySend(StreamEvent.ProviderStarted(displayName, isLocal = false))

        val historyPairs = history.map {
            (if (it.role.equals("user", ignoreCase = true)) "User" else "Assistant") to it.content
        }

        val job = launch(Dispatchers.IO) {
            try {
                val fullResponse = ClaudeAiService.chatWithAi(
                    history = historyPairs,
                    userMessage = prompt,
                    documentContext = documentContext,
                    context = context
                )

                if (isCancelled.get()) return@launch

                if (fullResponse.isBlank()) {
                    trySend(StreamEvent.Error("Claude returned an empty response. Please check API key or internet.", canFallback = true))
                    close()
                    return@launch
                }

                // Stream tokens/words smoothly for UI responsiveness
                val words = fullResponse.split(" ")
                for (i in words.indices) {
                    if (isCancelled.get()) return@launch

                    val word = words[i] + if (i < words.size - 1) " " else ""
                    trySend(StreamEvent.Token(word))
                    delay(18)
                }

                trySend(StreamEvent.Completed(fullResponse))
                close()

            } catch (e: Exception) {
                Log.e("ClaudeProvider", "Error: ${e.message}", e)
                trySend(StreamEvent.Error("Claude error: ${e.localizedMessage}", canFallback = true))
                close()
            }
        }

        awaitClose {
            isCancelled.set(true)
            job.cancel()
        }
    }

    override fun cancel() {
        isCancelled.set(true)
    }
}
