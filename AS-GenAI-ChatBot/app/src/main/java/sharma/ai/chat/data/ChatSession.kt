package sharma.ai.chat.data

data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val updatedAt: Long,
)
