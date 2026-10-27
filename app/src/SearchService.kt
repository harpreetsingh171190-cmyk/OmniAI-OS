package com.harpreet.omniai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SearchHit(val title: String, val url: String, val snippet: String)

/** Web search through a search API key the user provides (Tavily or Brave Search). */
class SearchService(private val provider: String, private val apiKey: String) {

    companion object {
        val providers = listOf("Tavily", "Brave")
    }

    suspend fun search(query: String): Result<List<SearchHit>> = withContext(Dispatchers.IO) {
        try {
            Result.success(if (provider == "Brave") brave(query) else tavily(query))
        } catch (e: Exception) {
            Result.failure<List<SearchHit>>(Exception("Search failed: ${e.message}"))
        }
    }

    private fun tavily(query: String): List<SearchHit> {
        val body = JSONObject().put("api_key", apiKey).put("query", query).put("max_results", 5).toString()
        val conn = URL("https://api.tavily.com/search").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.outputStream.use { it.write(body.toByteArray()) }
        val text = read(conn)
        val arr = JSONObject(text).getJSONArray("results")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SearchHit(o.optString("title"), o.optString("url"), o.optString("content").take(600))
        }
    }

    private fun brave(query: String): List<SearchHit> {
        val q = URLEncoder.encode(query, "UTF-8")
        val conn = URL("https://api.search.brave.com/res/v1/web/search?q=$q&count=5").openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("X-Subscription-Token", apiKey)
        val text = read(conn)
        val arr = JSONObject(text).optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            SearchHit(o.optString("title"), o.optString("url"), o.optString("description").take(600))
        }
    }

    private fun read(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) error("HTTP $code ${text.take(200)}")
        return text
    }
}
