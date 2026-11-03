package com.docscan.ai.provider

import android.content.Context
import com.docscan.data.model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

enum class AIProviderMode(val displayName: String, val badge: String) {
    AUTO("Auto (Local AI → Cloud)", "⚡"),
    LOCAL("Local AI (On-Device)", "🟢"),
    GEMINI("Google Gemini", "✨"),
    CLAUDE("Anthropic Claude", "🟣")
}

sealed class StreamEvent {
    data class ProviderStarted(val providerName: String, val isLocal: Boolean) : StreamEvent()
    data class Token(val text: String) : StreamEvent()
    data class Completed(val fullText: String, val tokensUsed: Int = 0) : StreamEvent()
    data class Error(val message: String, val canFallback: Boolean = false) : StreamEvent()
}

interface AIProvider {
    val id: String
    val displayName: String
    val isLocal: Boolean

    fun isAvailable(context: Context): Boolean

    fun generateStream(
        prompt: String,
        history: List<ChatMessageEntity>,
        documentContext: String?,
        context: Context
    ): Flow<StreamEvent>

    fun cancel()
}
