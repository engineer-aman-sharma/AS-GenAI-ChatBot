package sharma.ai.chat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ChatHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "as_genai_chat_history",
        Context.MODE_PRIVATE,
    )

    fun load(): List<ChatSession> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.getJSONObject(index)
                    val messagesJson = obj.getJSONArray("messages")
                    val messages = buildList {
                        for (messageIndex in 0 until messagesJson.length()) {
                            val message = messagesJson.getJSONObject(messageIndex)
                            add(
                                ChatMessage(
                                    text = message.getString("text"),
                                    isFromUser = message.getBoolean("isFromUser"),
                                    imageBase64 = message.optString("imageBase64").takeIf { it.isNotEmpty() },
                                    imageMimeType = message.optString("imageMimeType").takeIf { it.isNotEmpty() },
                                )
                            )
                        }
                    }
                    add(
                        ChatSession(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            messages = messages,
                            updatedAt = obj.getLong("updatedAt"),
                        )
                    )
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun save(sessions: List<ChatSession>) {
        val array = JSONArray()
        sessions.forEach { session ->
            val messagesJson = JSONArray()
            session.messages.forEach { message ->
                messagesJson.put(
                    JSONObject()
                        .put("text", message.text)
                        .put("isFromUser", message.isFromUser)
                        .put("imageBase64", message.imageBase64 ?: "")
                        .put("imageMimeType", message.imageMimeType ?: "")
                )
            }
            array.put(
                JSONObject()
                    .put("id", session.id)
                    .put("title", session.title)
                    .put("updatedAt", session.updatedAt)
                    .put("messages", messagesJson)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "sessions"
    }
}
