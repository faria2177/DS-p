package com.docscan.data.repository

import com.docscan.data.db.ChatDao
import com.docscan.data.model.ChatMessageEntity
import com.docscan.data.model.ChatSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao) {

    fun getAllSessions(): Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    suspend fun getSessionById(id: Long): ChatSessionEntity? = withContext(Dispatchers.IO) {
        chatDao.getSessionById(id)
    }

    suspend fun getSessionForDocument(docId: Long): ChatSessionEntity? = withContext(Dispatchers.IO) {
        chatDao.getSessionForDocument(docId)
    }

    suspend fun createSession(
        title: String,
        documentId: Long? = null,
        documentTitle: String? = null
    ): Long = withContext(Dispatchers.IO) {
        chatDao.insertSession(
            ChatSessionEntity(
                title = title,
                documentId = documentId,
                documentTitle = documentTitle
            )
        )
    }

    suspend fun renameSession(sessionId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        chatDao.updateSessionTitle(sessionId, newTitle)
    }

    suspend fun touchSession(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.updateSessionTimestamp(sessionId, System.currentTimeMillis())
    }

    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteSession(sessionId)
    }

    suspend fun clearAllSessions() = withContext(Dispatchers.IO) {
        chatDao.clearAllSessions()
    }

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForSession(sessionId)

    suspend fun getMessagesForSessionDirect(sessionId: Long): List<ChatMessageEntity> =
        withContext(Dispatchers.IO) {
            chatDao.getMessagesForSessionDirect(sessionId)
        }

    suspend fun insertMessage(
        sessionId: Long,
        role: String,
        content: String,
        provider: String = "LOCAL",
        isPartial: Boolean = false,
        tokensUsed: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        val id = chatDao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = role,
                content = content,
                provider = provider,
                isPartial = isPartial,
                tokensUsed = tokensUsed
            )
        )
        chatDao.updateSessionTimestamp(sessionId, System.currentTimeMillis())
        id
    }

    suspend fun updateMessageContent(messageId: Long, newContent: String, isPartial: Boolean = false) =
        withContext(Dispatchers.IO) {
            val msg = chatDao.getMessageById(messageId) ?: return@withContext
            chatDao.updateMessage(msg.copy(content = newContent, isPartial = isPartial))
        }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteMessage(messageId)
    }

    suspend fun clearMessagesForSession(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.clearMessagesForSession(sessionId)
    }
}
