package sharma.ai.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sharma.ai.chat.BuildConfig
import sharma.ai.chat.data.ChatHistoryStore
import sharma.ai.chat.data.ChatMessage
import sharma.ai.chat.data.ChatSession
import sharma.ai.chat.data.GeminiReply
import sharma.ai.chat.data.GeminiRepository
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private val WelcomeMessages = listOf(
    "Hello, I am AS GenAI ChatBot, Developed by Aman Sharma. How can I assist you today?",
    "Hello, I am AS GenAI ChatBot, Developed by Aman Sharma. What can I help you with today?",
    "Hi, I am AS GenAI ChatBot, Developed by Aman Sharma. How may I assist you today?",
    "Welcome, I am AS GenAI ChatBot, Developed by Aman Sharma. What would you like to know?",
    "Hey, I am AS GenAI ChatBot, Developed by Aman Sharma. How can I help you today?",
    "Hello, I am AS GenAI ChatBot, Developed by Aman Sharma. What can I do for you today?",
)

private const val MaxSavedChats = 20

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val geminiRepository = GeminiRepository(BuildConfig.GEMINI_API_KEY)
    private val historyStore = ChatHistoryStore(application)
    private var generationJob: Job? = null
    private var runId = 0

    private val _uiState = MutableStateFlow(
        ChatUiState(
            messages = listOf(ChatMessage("", isFromUser = false)),
            isLoading = true,
            sessions = historyStore.load(),
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        playWelcome()
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty()) return

        generationJob?.cancel()
        val thisRun = ++runId

        val current = _uiState.value.messages.toMutableList()
        if (current.isNotEmpty() && !current.last().isFromUser &&
            current.last().text.isEmpty() && current.last().imageBase64.isNullOrEmpty()
        ) {
            current.removeAt(current.lastIndex)
        }
        val history = current + ChatMessage(prompt, isFromUser = true)
        _uiState.value = _uiState.value.copy(
            messages = history + ChatMessage("", isFromUser = false),
            isLoading = true,
            error = null,
        )
        persistCurrentChat()

        generationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val reply = geminiRepository.generateReply(
                    history.dropWhile { !it.isFromUser && it.text in WelcomeMessages }
                )
                typeIntoLastAiMessage(reply, thisRun)
                if (thisRun == runId) {
                    persistCurrentChat()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (thisRun != runId) return@launch
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.dropLastWhile {
                            !it.isFromUser && it.text.isEmpty() && it.imageBase64.isNullOrEmpty()
                        },
                        isLoading = false,
                        error = error.localizedMessage ?: "Request failed",
                    )
                }
                persistCurrentChat()
            }
        }
    }

    fun stopGeneration() {
        if (!_uiState.value.isLoading) return
        generationJob?.cancel()
        runId++
        _uiState.update { state ->
            state.copy(
                messages = state.messages.dropLastWhile {
                    !it.isFromUser && it.text.isEmpty() && it.imageBase64.isNullOrEmpty()
                },
                isLoading = false,
                error = null,
            )
        }
        persistCurrentChat()
    }

    fun startNewChat() {
        val state = _uiState.value
        if (state.currentSessionId == null && state.messages.none { it.isFromUser }) return
        persistCurrentChat()
        generationJob?.cancel()
        runId++
        _uiState.update {
            it.copy(
                messages = listOf(ChatMessage("", isFromUser = false)),
                isLoading = true,
                error = null,
                currentSessionId = null,
            )
        }
        playWelcome()
    }

    fun openSession(sessionId: String) {
        val session = _uiState.value.sessions.find { it.id == sessionId } ?: return
        if (sessionId == _uiState.value.currentSessionId) return
        persistCurrentChat()
        generationJob?.cancel()
        runId++
        _uiState.update {
            it.copy(
                messages = session.messages,
                isLoading = false,
                error = null,
                currentSessionId = session.id,
            )
        }
    }

    fun clearHistory() {
        generationJob?.cancel()
        runId++
        historyStore.save(emptyList())
        _uiState.value = ChatUiState(
            messages = listOf(ChatMessage("", isFromUser = false)),
            isLoading = true,
            sessions = emptyList(),
            currentSessionId = null,
        )
        playWelcome()
    }

    private fun playWelcome() {
        generationJob?.cancel()
        val thisRun = ++runId
        generationJob = viewModelScope.launch {
            delay(650)
            if (thisRun != runId) return@launch
            typeIntoLastAiMessage(GeminiReply(WelcomeMessages.random()), thisRun)
        }
    }

    private suspend fun typeIntoLastAiMessage(reply: GeminiReply, thisRun: Int) {
        _uiState.update { state ->
            if (thisRun != runId) return@update state
            val messages = state.messages.toMutableList()
            if (messages.isNotEmpty() && !messages.last().isFromUser) {
                val last = messages.last()
                messages[messages.lastIndex] = last.copy(
                    imageBase64 = reply.imageBase64,
                    imageMimeType = reply.imageMimeType,
                )
            }
            state.copy(messages = messages, error = null)
        }
        val typed = StringBuilder()
        reply.text.forEach { char ->
            if (thisRun != runId) return
            typed.append(char)
            val snapshot = typed.toString()
            _uiState.update { state ->
                if (thisRun != runId) return@update state
                val messages = state.messages.toMutableList()
                if (messages.isNotEmpty() && !messages.last().isFromUser) {
                    val last = messages.last()
                    messages[messages.lastIndex] = last.copy(text = snapshot)
                }
                state.copy(messages = messages, error = null)
            }
            delay(
                when (char) {
                    '.', '!', '?' -> 70L
                    ',', ';' -> 35L
                    else -> 16L
                }
            )
        }
        if (thisRun == runId) {
            _uiState.update { it.copy(isLoading = false, error = null) }
        }
    }

    private fun persistCurrentChat() {
        val state = _uiState.value
        val persistedMessages = state.messages.filter {
            it.text.isNotEmpty() || !it.imageBase64.isNullOrEmpty()
        }
        if (persistedMessages.none { it.isFromUser }) return

        val title = persistedMessages
            .first { it.isFromUser }
            .text
            .replace('\n', ' ')
            .trim()
            .replace(Regex("\\s+"), " ")
            .let { text ->
                if (text.length <= 42) text else text.take(41).trimEnd() + "…"
            }
            .ifBlank { "New chat" }

        val id = state.currentSessionId ?: UUID.randomUUID().toString()
        val session = ChatSession(
            id = id,
            title = title,
            messages = persistedMessages,
            updatedAt = System.currentTimeMillis(),
        )
        val sessions = (listOf(session) + state.sessions.filter { it.id != id })
            .sortedByDescending { it.updatedAt }
            .take(MaxSavedChats)
        historyStore.save(sessions)
        _uiState.update {
            it.copy(sessions = sessions, currentSessionId = id)
        }
    }
}
