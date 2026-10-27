package com.harpreet.omniai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Simple local persistence (SharedPreferences + JSON). Will be replaced by Room later. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("omniai", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean("dark", false)
        set(v) = prefs.edit().putBoolean("dark", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v).apply()

    var baseUrl: String
        get() = prefs.getString("base_url", "https://api.openai.com/v1") ?: ""
        set(v) = prefs.edit().putString("base_url", v).apply()

    var model: String
        get() = prefs.getString("model", "gpt-4o-mini") ?: ""
        set(v) = prefs.edit().putString("model", v).apply()

    var voiceLang: String
        get() = prefs.getString("voice_lang", "en-US") ?: "en-US"
        set(v) = prefs.edit().putString("voice_lang", v).apply()

    var uiLang: String
        get() = prefs.getString("ui_lang", "en") ?: "en"
        set(v) = prefs.edit().putString("ui_lang", v).apply()

    /** Quick-switch list for the model selector (comma separated). */
    var savedModels: List<String>
        get() = (prefs.getString("saved_models", "gpt-4o-mini,gpt-4o") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        set(v) = prefs.edit().putString("saved_models", v.joinToString(",")).apply()

    var searchProvider: String
        get() = prefs.getString("search_provider", "Tavily") ?: "Tavily"
        set(v) = prefs.edit().putString("search_provider", v).apply()

    var searchKey: String
        get() = prefs.getString("search_key", "") ?: ""
        set(v) = prefs.edit().putString("search_key", v).apply()

    var offlineMode: Boolean
        get() = prefs.getBoolean("offline_mode", false)
        set(v) = prefs.edit().putBoolean("offline_mode", v).apply()

    var offlineModelPath: String
        get() = prefs.getString("offline_model_path", "") ?: ""
        set(v) = prefs.edit().putString("offline_model_path", v).apply()

    var memoryEnabled: Boolean
        get() = prefs.getBoolean("memory_enabled", true)
        set(v) = prefs.edit().putBoolean("memory_enabled", v).apply()

    private fun memKey(user: String) = if (user.isEmpty()) "memory" else "memory_$user"

    fun loadMemory(user: String): List<String> {
        val raw = prefs.getString(memKey(user), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun saveMemory(list: List<String>, user: String) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(memKey(user), arr.toString()).apply()
    }

    var imageModel: String
        get() = prefs.getString("image_model", "dall-e-3") ?: ""
        set(v) = prefs.edit().putString("image_model", v).apply()

    var imageSize: String
        get() = prefs.getString("image_size", "1024x1024") ?: "1024x1024"
        set(v) = prefs.edit().putString("image_size", v).apply()

    var personaId: String
        get() = prefs.getString("persona_id", "default") ?: "default"
        set(v) = prefs.edit().putString("persona_id", v).apply()

    fun customPersonas(): List<Persona> {
        val raw = prefs.getString("personas", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Persona(o.getString("id"), o.getString("name"), o.getString("prompt"), false)
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveCustomPersonas(list: List<Persona>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("prompt", it.prompt)) }
        prefs.edit().putString("personas", arr.toString()).apply()
    }

    private val usageKeys = listOf(
        "chats", "messages", "code", "search", "scans", "writer", "translations", "pdf", "images", "vision", "summaries", "study", "tokens"
    )

    fun bump(key: String, n: Int = 1) {
        prefs.edit().putInt("usage_$key", prefs.getInt("usage_$key", 0) + n).apply()
    }

    fun usage(): Map<String, Int> = usageKeys.associateWith { prefs.getInt("usage_$it", 0) }

    private fun key(user: String) = if (user.isEmpty()) "conversations" else "conversations_$user"

    fun deleteUserData(user: String) { prefs.edit().remove(key(user)).remove(memKey(user)).apply() }

    fun loadConversations(user: String = ""): List<Conversation> {
        val raw = prefs.getString(key(user), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val msgs = o.getJSONArray("messages")
                Conversation(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    favorite = o.optBoolean("favorite", false),
                    updated = o.optLong("updated", 0L),
                    messages = (0 until msgs.length()).map { j ->
                        val m = msgs.getJSONObject(j)
                        Message(m.getString("role"), m.getString("text"), m.optLong("time", 0L))
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveConversations(list: List<Conversation>, user: String = "") {
        prefs.edit().putString(key(user), conversationsToJson(list).toString()).apply()
    }

    fun conversationsToJson(list: List<Conversation>): JSONArray {
        val arr = JSONArray()
        list.forEach { c ->
            val msgs = JSONArray()
            c.messages.forEach { m ->
                msgs.put(JSONObject().put("role", m.role).put("text", m.text).put("time", m.time))
            }
            arr.put(
                JSONObject().put("id", c.id).put("title", c.title).put("favorite", c.favorite)
                    .put("updated", c.updated).put("messages", msgs)
            )
        }
        return arr
    }
}
