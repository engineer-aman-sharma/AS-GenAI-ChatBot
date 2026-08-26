package sharma.ai.chat.data

data class GeminiReply(
    val text: String,
    val imageBase64: String? = null,
    val imageMimeType: String? = null,
)
