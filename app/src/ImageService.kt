package com.harpreet.omniai.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** OpenAI-compatible /images/generations. Handles both b64_json and url responses. */
class ImageService(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val size: String
) {
    suspend fun generate(prompt: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("model", model).put("prompt", prompt)
                .put("n", 1).put("size", size).toString()
            val conn = URL(baseUrl.trimEnd('/') + "/images/generations").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 120000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("Error $code: ${text.take(300)}")
            val item = JSONObject(text).getJSONArray("data").getJSONObject(0)
            if (item.has("b64_json") && !item.isNull("b64_json")) {
                Base64.decode(item.getString("b64_json"), Base64.DEFAULT)
            } else {
                URL(item.getString("url")).openStream().use { it.readBytes() }
            }
        }
    }
}
