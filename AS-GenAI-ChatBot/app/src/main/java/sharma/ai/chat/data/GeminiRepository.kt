package sharma.ai.chat.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiRepository(
    private val apiKey: String,
    private val model: String = "gemini-3.6-flash",
    private val imageModel: String = "gemini-3.1-flash-image",
) {
    fun generateReply(history: List<ChatMessage>): GeminiReply {
        val key = apiKey.trim()
        if (key.isEmpty()) {
            error("Add GEMINI_API_KEY in local.properties and rebuild the app.")
        }

        val lastUserPrompt = history.lastOrNull { it.isFromUser }?.text.orEmpty()
        val useImageModel = wantsImage(lastUserPrompt)
        val selectedModel = if (useImageModel) imageModel else model

        val contents = JSONArray()
        history.forEach { message ->
            val parts = JSONArray()
            if (message.text.isNotEmpty()) {
                parts.put(JSONObject().put("text", message.text))
            }
            if (!message.imageBase64.isNullOrEmpty()) {
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", message.imageMimeType ?: "image/png")
                            .put("data", message.imageBase64)
                    )
                )
            }
            if (parts.length() == 0) return@forEach
            contents.put(
                JSONObject()
                    .put("role", if (message.isFromUser) "user" else "model")
                    .put("parts", parts)
            )
        }
        val requestBody = JSONObject().put("contents", contents)
        if (useImageModel) {
            requestBody.put(
                "generationConfig",
                JSONObject().put(
                    "responseModalities",
                    JSONArray().put("TEXT").put("IMAGE")
                )
            )
        }

        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", key)
        }

        try {
            connection.outputStream.use { output ->
                output.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            if (code !in 200..299) {
                error(parseApiError(code, body))
            }
            return parseReply(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun wantsImage(prompt: String): Boolean {
        val value = prompt.lowercase()
        return listOf(
            "image",
            "picture",
            "photo",
            "draw",
            "illustration",
            "logo",
            "sketch",
            "painting",
            "artwork",
            "wallpaper",
            "render",
        ).any { value.contains(it) }
    }

    private fun parseReply(body: String): GeminiReply {
        val parts = JSONObject(body)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        val text = StringBuilder()
        var imageBase64: String? = null
        var imageMimeType: String? = null
        if (parts != null) {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                val partText = part.optString("text")
                if (partText.isNotEmpty()) {
                    if (text.isNotEmpty()) text.append('\n')
                    text.append(partText)
                }
                val inline = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                if (inline != null) {
                    val data = inline.optString("data")
                    if (data.isNotEmpty()) {
                        imageBase64 = data
                        imageMimeType = inline.optString("mime_type")
                            .ifEmpty { inline.optString("mimeType") }
                            .ifEmpty { "image/png" }
                    }
                }
            }
        }
        val replyText = text.toString().trim()
        if (replyText.isEmpty() && imageBase64.isNullOrEmpty()) {
            error("Gemini returned an empty response.")
        }
        return GeminiReply(
            text = replyText,
            imageBase64 = imageBase64,
            imageMimeType = imageMimeType,
        )
    }

    private fun parseApiError(code: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return if (message.isNullOrBlank()) {
            "Gemini HTTP $code"
        } else {
            message
        }
    }
}
