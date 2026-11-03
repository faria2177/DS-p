package com.docscan.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docscan.ai.manager.AIModelDownloadManager
import com.docscan.ai.manager.DownloadState
import com.docscan.ai.manager.LocalModelManager
import com.docscan.ai.model.LocalModelInfo
import com.docscan.ai.provider.AIProviderMode
import com.docscan.ai.provider.AIProviderRouter
import com.docscan.ai.provider.StreamEvent
import com.docscan.ai.rag.DocumentTextExtractor
import com.docscan.ai.rag.LocalRAGEngine
import com.docscan.ai.rag.TextChunker
import com.docscan.data.db.AppDatabase
import com.docscan.data.model.ChatMessageEntity
import com.docscan.data.model.ChatSessionEntity
import com.docscan.data.model.DocumentEntity
import com.docscan.data.repository.ChatRepository
import com.docscan.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AIChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val chatRepository = ChatRepository(db.chatDao())
    private val documentRepository = DocumentRepository(db)
    val downloadManager = AIModelDownloadManager(application)

    val sessions: StateFlow<List<ChatSessionEntity>> = chatRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableDocuments: StateFlow<List<DocumentEntity>> = documentRepository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSessionEntity?>(null)
    val currentSession: StateFlow<ChatSessionEntity?> = _currentSession.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = _currentSessionId
        .flatMapLatest { id ->
            if (id != null) chatRepository.getMessagesForSession(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _attachedDocument = MutableStateFlow<DocumentEntity?>(null)
    val attachedDocument: StateFlow<DocumentEntity?> = _attachedDocument.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingMessage = MutableStateFlow<String>("")
    val streamingMessage: StateFlow<String> = _streamingMessage.asStateFlow()

    private val _streamingProviderName = MutableStateFlow("Google Gemini")
    val streamingProviderName: StateFlow<String> = _streamingProviderName.asStateFlow()

    private val _isLocalInstalled = MutableStateFlow(false)
    val isLocalInstalled: StateFlow<Boolean> = _isLocalInstalled.asStateFlow()

    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState

    private val _activeProviderInfo = MutableStateFlow("Google Gemini" to false)
    val activeProviderInfo: StateFlow<Pair<String, Boolean>> = _activeProviderInfo.asStateFlow()

    private var generationJob: Job? = null

    init {
        refreshModelStatus()
    }

    fun refreshModelStatus() {
        val app = getApplication<Application>()
        val installed = LocalModelManager.getActiveInstalledModel(app) != null
        _isLocalInstalled.value = installed
        _activeProviderInfo.value = AIProviderRouter.getActiveProviderInfo(app)
    }

    fun enableOfflineAi() {
        val app = getApplication<Application>()
        downloadManager.enableBuiltInModel()
        AIProviderRouter.setSelectedMode(app, AIProviderMode.LOCAL)
        refreshModelStatus()
    }

    fun clearDownloadError() {
        downloadManager.clearError()
    }

    fun initChat(initialSessionId: Long? = null, initialDocId: Long? = null) {
        viewModelScope.launch {
            if (downloadManager.downloadState.value is DownloadState.Failed) {
                downloadManager.clearError()
            }
            refreshModelStatus()

            // 1. If document ID provided, attach document and check for linked session
            if (initialDocId != null) {
                val doc = documentRepository.getDocumentById(initialDocId)
                _attachedDocument.value = doc

                if (initialSessionId == null) {
                    val existing = chatRepository.getSessionForDocument(initialDocId)
                    if (existing != null) {
                        _currentSessionId.value = existing.id
                        _currentSession.value = existing
                        return@launch
                    } else if (doc != null) {
                        // Create dedicated session for this document
                        val newId = chatRepository.createSession(
                            title = "Chat: ${doc.title.take(28)}",
                            documentId = doc.id,
                            documentTitle = doc.title
                        )
                        _currentSessionId.value = newId
                        _currentSession.value = chatRepository.getSessionById(newId)
                        return@launch
                    }
                }
            }

            // 2. If explicit session requested
            if (initialSessionId != null) {
                _currentSessionId.value = initialSessionId
                val session = chatRepository.getSessionById(initialSessionId)
                _currentSession.value = session
                if (session?.documentId != null) {
                    _attachedDocument.value = documentRepository.getDocumentById(session.documentId)
                }
                return@launch
            }

            // 3. Fallback to most recent session or create new empty chat state
            val all = chatRepository.getAllSessions().first()
            if (all.isNotEmpty()) {
                val mostRecent = all.first()
                _currentSessionId.value = mostRecent.id
                _currentSession.value = mostRecent
                if (mostRecent.documentId != null) {
                    _attachedDocument.value = documentRepository.getDocumentById(mostRecent.documentId)
                }
            } else {
                _currentSessionId.value = null
                _currentSession.value = null
            }
        }
    }

    fun createNewChat() {
        _currentSessionId.value = null
        _currentSession.value = null
        _streamingMessage.value = ""
        _isGenerating.value = false
        // Keep attached document if user explicitly selected one
    }

    fun switchSession(sessionId: Long) {
        viewModelScope.launch {
            _currentSessionId.value = sessionId
            val session = chatRepository.getSessionById(sessionId)
            _currentSession.value = session
            if (session?.documentId != null) {
                _attachedDocument.value = documentRepository.getDocumentById(session.documentId)
            } else {
                _attachedDocument.value = null
            }
            _streamingMessage.value = ""
            _isGenerating.value = false
        }
    }

    fun attachDocument(doc: DocumentEntity) {
        _attachedDocument.value = doc
        viewModelScope.launch {
            val sId = _currentSessionId.value
            if (sId != null) {
                val session = chatRepository.getSessionById(sId)
                if (session != null) {
                    chatRepository.renameSession(sId, "Chat: ${doc.title.take(28)}")
                }
            }
        }
    }

    fun detachDocument() {
        _attachedDocument.value = null
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        viewModelScope.launch {
            val app = getApplication<Application>()
            var sId = _currentSessionId.value

            // Create session on first message if not exists
            if (sId == null) {
                val doc = _attachedDocument.value
                val sessionTitle = if (doc != null) {
                    "Chat: ${doc.title.take(24)}"
                } else {
                    if (trimmed.length <= 32) trimmed else trimmed.take(28) + "..."
                }

                sId = chatRepository.createSession(
                    title = sessionTitle,
                    documentId = doc?.id,
                    documentTitle = doc?.title
                )
                _currentSessionId.value = sId
                _currentSession.value = chatRepository.getSessionById(sId)
            }

            // Save user message to database
            chatRepository.insertMessage(
                sessionId = sId,
                role = "user",
                content = trimmed,
                provider = "USER"
            )

            // Prepare history & RAG context
            val history = chatRepository.getMessagesForSessionDirect(sId)
            val docContext = prepareDocumentContext(trimmed)

            _isGenerating.value = true
            _streamingMessage.value = ""

            generationJob?.cancel()
            generationJob = launch(Dispatchers.IO) {
                var finalResponseText = ""
                var usedProvider = "LOCAL"

                try {
                    AIProviderRouter.streamChat(
                        prompt = trimmed,
                        history = history,
                        documentContext = docContext,
                        context = app
                    ).collect { event ->
                        when (event) {
                            is StreamEvent.ProviderStarted -> {
                                _streamingProviderName.value = event.providerName
                                usedProvider = if (event.isLocal) "LOCAL" else "CLOUD"
                            }
                            is StreamEvent.Token -> {
                                finalResponseText += event.text
                                _streamingMessage.value = finalResponseText
                            }
                            is StreamEvent.Completed -> {
                                finalResponseText = event.fullText
                                _streamingMessage.value = finalResponseText
                            }
                            is StreamEvent.Error -> {
                                if (finalResponseText.isBlank()) {
                                    finalResponseText = "⚠️ ${event.message}"
                                } else {
                                    finalResponseText += "\n\n⚠️ ${event.message}"
                                }
                                _streamingMessage.value = finalResponseText
                            }
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        _isGenerating.value = false
                    }

                    if (finalResponseText.isNotBlank()) {
                        chatRepository.insertMessage(
                            sessionId = sId,
                            role = "assistant",
                            content = finalResponseText,
                            provider = usedProvider
                        )
                    }
                    _streamingMessage.value = ""
                    refreshModelStatus()
                }
            }
        }
    }

    fun stopGeneration() {
        AIProviderRouter.cancelActiveInference()
        generationJob?.cancel()
        _isGenerating.value = false

        viewModelScope.launch {
            val sId = _currentSessionId.value ?: return@launch
            val partial = _streamingMessage.value
            if (partial.isNotBlank()) {
                chatRepository.insertMessage(
                    sessionId = sId,
                    role = "assistant",
                    content = "$partial\n\n*(Generation stopped)*",
                    provider = _streamingProviderName.value
                )
            }
            _streamingMessage.value = ""
        }
    }

    fun regenerateLastMessage() {
        val sId = _currentSessionId.value ?: return
        viewModelScope.launch {
            val list = chatRepository.getMessagesForSessionDirect(sId)
            val lastAssistant = list.lastOrNull { it.role.equals("assistant", ignoreCase = true) }
            val lastUser = list.lastOrNull { it.role.equals("user", ignoreCase = true) }

            if (lastAssistant != null) {
                chatRepository.deleteMessage(lastAssistant.id)
            }

            if (lastUser != null) {
                sendMessage(lastUser.content)
            }
        }
    }

    fun editAndResend(messageId: Long, newText: String) {
        val sId = _currentSessionId.value ?: return
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
            sendMessage(newText)
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            chatRepository.renameSession(sessionId, newTitle)
            if (_currentSessionId.value == sessionId) {
                _currentSession.value = chatRepository.getSessionById(sessionId)
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                createNewChat()
            }
        }
    }

    fun clearCurrentChat() {
        val sId = _currentSessionId.value ?: return
        viewModelScope.launch {
            chatRepository.clearMessagesForSession(sId)
        }
    }

    private suspend fun prepareDocumentContext(userQuery: String): String? = withContext(Dispatchers.IO) {
        val doc = _attachedDocument.value ?: return@withContext null
        val app = getApplication<Application>()

        try {
            val pageTexts = DocumentTextExtractor.extractAllText(app, doc, documentRepository)
            if (pageTexts.isEmpty()) return@withContext null

            val chunks = TextChunker.chunkPages(pageTexts)
            val relevantChunks = LocalRAGEngine.findRelevantChunks(chunks, userQuery, topK = 4)
            LocalRAGEngine.buildRAGContext(doc.title, relevantChunks)
        } catch (e: Exception) {
            Log.e("AIChatViewModel", "Error building RAG context: ${e.message}")
            null
        }
    }
}
