package sharma.ai.chat.data

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val imageBase64: String? = null,
    val imageMimeType: String? = null,
)