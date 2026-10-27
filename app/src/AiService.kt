package com.harpreet.omniai.ai

import android.util.Base64
import com.harpreet.omniai.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface AiService {
    suspend fun reply(history: List<Message>): String
    suspend fun vision(prompt: String, jpeg: ByteArray): String
}

/** Used when no API key is set, so the app works out of the box. */
class DemoAiService : AiService {
    override suspend fun reply(history: List<Message>): String {
        val last = history.lastOrNull { it.isUser }?.text ?: ""
        return "Demo mode: add your API key in Settings to get real AI answers.\n\nYou said: \"$last\""
    }

    override suspend fun vision(prompt: String, jpeg: ByteArray): String =
        "Demo mode: add your API key in Settings (and use a vision-capable model) to analyse images."
}

/** Works with any OpenAI-compatible /chat/completions endpoint. */
class OpenAiCompatibleService(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) : AiService {

    override suspend fun reply(history: List<Message>): String {
        val msgs = JSONArray()
        (history.filter { it.role == "system" } + history.filter { it.role != "system" }.takeLast(20)).forEach {
            msgs.put(JSONObject().put("role", it.role).put("content", it.text))
        }
        return call(msgs)
    }

    override suspend fun vision(prompt: String, jpeg: ByteArray): String {
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(
                JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
            )
        return call(JSONArray().put(JSONObject().put("role", "user").put("content", content)))
    }

    private suspend fun call(messages: JSONArray): String = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("model", model).put("messages", messages).toString()
            val conn = URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) return@withContext "Error $code: ${text.take(400)}"
            JSONObject(text).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        } catch (e: Exception) {
            "Network error: ${e.message}"
        }
    }
}
