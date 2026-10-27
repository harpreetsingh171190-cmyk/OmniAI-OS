package com.harpreet.omniai.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harpreet.omniai.ai.AiService
import com.harpreet.omniai.ai.DemoAiService
import com.harpreet.omniai.ai.ImageService
import com.harpreet.omniai.ai.OfflineAiService
import com.harpreet.omniai.ai.OfflineLlm
import com.harpreet.omniai.ai.OfflineTranslator
import com.harpreet.omniai.ai.OpenAiCompatibleService
import com.harpreet.omniai.ai.SearchHit
import com.harpreet.omniai.ai.SearchService
import com.harpreet.omniai.ai.PdfReader
import com.harpreet.omniai.data.Account
import com.harpreet.omniai.data.Accounts
import com.harpreet.omniai.data.AppLock
import com.harpreet.omniai.data.Conversation
import com.harpreet.omniai.data.Flashcard
import com.harpreet.omniai.data.Message
import com.harpreet.omniai.data.Persona
import com.harpreet.omniai.data.QuizQuestion
import com.harpreet.omniai.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val accounts = Accounts(app)
    private val appLock = AppLock(app)

    // ---- App lock
    var locked by mutableStateOf(appLock.hasPin())
        private set
    var hasPin by mutableStateOf(appLock.hasPin())
        private set
    var biometricEnabled by mutableStateOf(appLock.biometricEnabled)
        private set
    var lockError by mutableStateOf<String?>(null)
        private set

    fun unlock(pin: String): Boolean {
        val ok = appLock.verify(pin)
        if (ok) { locked = false; lockError = null } else lockError = "Wrong PIN"
        return ok
    }

    /** Called after a successful fingerprint / face check. */
    fun unlockWithBiometric() { locked = false; lockError = null }

    fun setBiometric(on: Boolean) {
        if (on && !hasPin) return
        appLock.biometricEnabled = on
        biometricEnabled = on
    }

    fun lockNow() { if (appLock.hasPin()) locked = true }

    fun setPin(pin: String): Boolean {
        if (!Regex("\\d{4,6}").matches(pin)) { lockError = "PIN must be 4–6 digits"; return false }
        appLock.setPin(pin); hasPin = true; lockError = null
        return true
    }

    fun removePin() { appLock.clear(); hasPin = false; biometricEnabled = false; locked = false; lockError = null }

    // ---- Usage dashboard
    var usage by mutableStateOf(store.usage())
        private set

    private fun bump(key: String, n: Int = 1) { store.bump(key, n); usage = store.usage() }

    // ---- Account
    var user by mutableStateOf(accounts.current())
        private set
    var authError by mutableStateOf<String?>(null)
        private set
    private val uid: String get() = user?.username ?: ""

    // ---- Chat state
    var conversations by mutableStateOf(store.loadConversations(uid).sortedByDescending { it.updated })
        private set
    var currentId by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var dark by mutableStateOf(store.darkMode)
        private set
    var draft by mutableStateOf("")
        private set

    // ---- Settings
    var apiKey by mutableStateOf(store.apiKey)
    var baseUrl by mutableStateOf(store.baseUrl)
    var model by mutableStateOf(store.model)
    var voiceLang by mutableStateOf(store.voiceLang)
    var imageModel by mutableStateOf(store.imageModel)
    var imageSize by mutableStateOf(store.imageSize)

    // ---- Language & model selector
    var uiLang by mutableStateOf(store.uiLang)
        private set
    var savedModels by mutableStateOf(store.savedModels)
        private set

    init { I18n.lang = uiLang }

    fun setUiLang(code: String) {
        uiLang = code; store.uiLang = code; I18n.lang = code
    }

    /** Saved models plus whatever model is currently typed in Settings. */
    val modelOptions: List<String> get() = (savedModels + model.trim()).filter { it.isNotEmpty() }.distinct()

    fun selectModel(m: String) { model = m; store.model = m }

    fun addModel(name: String) {
        val m = name.trim()
        if (m.isEmpty()) return
        if (m !in savedModels) { savedModels = savedModels + m; store.savedModels = savedModels }
        selectModel(m)
    }

    fun removeSelectedModel() {
        val m = model.trim()
        if (m !in savedModels || savedModels.size <= 1) return
        savedModels = savedModels - m
        store.savedModels = savedModels
        selectModel(savedModels.first())
    }

    // ---- Data export (API key is never included)
    fun exportAllJson(): String {
        val personas = JSONArray()
        customPersonas.forEach {
            personas.put(JSONObject().put("name", it.name).put("prompt", it.prompt))
        }
        return JSONObject()
            .put("app", "OmniAI-OS")
            .put("exportedAt", System.currentTimeMillis())
            .put("user", user?.username ?: "guest")
            .put("conversations", store.conversationsToJson(conversations))
            .put("personas", personas)
            .put("memory", JSONArray(memories))
            .toString(2)
    }

    fun exportAllMarkdown(): String = buildString {
        append("# OmniAI-OS export\n\n")
        conversations.forEach { c ->
            append("## ").append(c.title).append("\n\n")
            c.messages.forEach {
                append(if (it.isUser) "**You:** " else "**AI:** ").append(it.text).append("\n\n")
            }
        }
    }

    val current: Conversation? get() = conversations.find { it.id == currentId }

    private fun service(): AiService = when {
        offlineMode -> OfflineAiService(app, offlineModelPath)
        apiKey.isBlank() -> DemoAiService()
        else -> OpenAiCompatibleService(baseUrl, apiKey, model)
    }

    /** Every text request goes through here so usage can be counted. */
    private suspend fun ask(history: List<Message>): String {
        val answer = service().reply(history)
        bump("tokens", (history.sumOf { it.text.length } + answer.length) / 4)
        return answer
    }

    private fun persist() = store.saveConversations(conversations, uid)

    private fun update(c: Conversation) {
        conversations = (conversations.filter { it.id != c.id } + c).sortedByDescending { it.updated }
        persist()
    }

    // ---- Personas
    var customPersonas by mutableStateOf(store.customPersonas())
        private set
    var personaId by mutableStateOf(store.personaId)
        private set

    val allPersonas: List<Persona> get() = Personas.builtIn + customPersonas
    val persona: Persona get() = allPersonas.firstOrNull { it.id == personaId } ?: Personas.builtIn[0]

    fun selectPersona(id: String) { personaId = id; store.personaId = id }

    fun addPersona(name: String, prompt: String) {
        val n = name.trim()
        if (n.isEmpty() || prompt.isBlank() || allPersonas.any { it.name.equals(n, ignoreCase = true) }) return
        customPersonas = customPersonas + Persona(UUID.randomUUID().toString(), n, prompt.trim(), false)
        store.saveCustomPersonas(customPersonas)
    }

    fun deletePersona(id: String) {
        customPersonas = customPersonas.filter { it.id != id }
        store.saveCustomPersonas(customPersonas)
        if (personaId == id) selectPersona("default")
    }

    // ---- Prompt library hand-off
    fun useDraft(text: String) { newChat(); draft = text }
    fun consumeDraft() { draft = "" }

    // ---- Accounts
    fun clearAuthError() { authError = null }

    fun signUp(username: String, displayName: String, password: String, confirm: String) {
        if (password != confirm) { authError = "Passwords do not match"; return }
        val r = accounts.signUp(username, displayName, password)
        if (r.isSuccess) switchUser(r.getOrNull()) else authError = r.exceptionOrNull()?.message
    }

    fun signIn(username: String, password: String) {
        val r = accounts.signIn(username, password)
        if (r.isSuccess) switchUser(r.getOrNull()) else authError = r.exceptionOrNull()?.message
    }

    fun signOut() { accounts.signOut(); switchUser(null) }

    fun deleteAccount() {
        val u = user ?: return
        store.deleteUserData(u.username)
        accounts.delete(u.username)
        switchUser(null)
    }

    private fun switchUser(a: Account?) {
        user = a
        authError = null
        conversations = store.loadConversations(uid).sortedByDescending { it.updated }
        memories = store.loadMemory(uid)
        currentId = null
        // clear temporary data of the previous user
        writerText = ""; translation = ""; summary = ""
        pdfMessages.clear(); pdfText = ""; pdfName = null; pdfStatus = ""
        images.clear(); visionImage = null; visionResult = ""
        cards.clear(); quiz.clear(); studyKind = ""; studyError = null
    }

    // ---- Chat
    fun newChat() { currentId = null }
    fun open(id: String) { currentId = id }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || loading) return
        val now = System.currentTimeMillis()
        val isNew = current == null
        val base = current ?: Conversation(
            id = UUID.randomUUID().toString(),
            title = clean.take(40),
            messages = emptyList()
        )
        val withUser = base.copy(messages = base.messages + Message("user", clean), updated = now)
        currentId = withUser.id
        update(withUser)
        if (isNew) bump("chats")
        bump("messages")
        loading = true
        val system = (persona.prompt.takeIf { it.isNotBlank() }?.let { listOf(Message("system", it)) } ?: emptyList()) + memoryMessage()
        viewModelScope.launch {
            val answer = ask(system + withUser.messages)
            val latest = conversations.find { it.id == withUser.id } ?: withUser
            update(latest.copy(messages = latest.messages + Message("assistant", answer),
                updated = System.currentTimeMillis()))
            loading = false
        }
    }

    fun exportCurrent(): String? {
        val c = current ?: return null
        return buildString {
            append("# ").append(c.title).append("\n\n")
            c.messages.forEach {
                append(if (it.isUser) "**You:** " else "**AI:** ").append(it.text).append("\n\n")
            }
        }
    }

    fun toggleFavorite(id: String) {
        conversations.find { it.id == id }?.let { update(it.copy(favorite = !it.favorite)) }
    }

    fun delete(id: String) {
        conversations = conversations.filter { it.id != id }
        if (currentId == id) currentId = null
        persist()
    }

    fun clearAll() {
        conversations = emptyList(); currentId = null
        persist()
    }

    fun setDark(v: Boolean) { dark = v; store.darkMode = v }

    fun saveSettings() {
        store.apiKey = apiKey.trim(); store.baseUrl = baseUrl.trim(); store.model = model.trim()
        store.voiceLang = voiceLang; store.imageModel = imageModel.trim(); store.imageSize = imageSize
        store.searchProvider = searchProvider; store.searchKey = searchKey.trim()
    }

    // ---- Memory
    var memoryEnabled by mutableStateOf(store.memoryEnabled)
        private set
    var memories by mutableStateOf(store.loadMemory(uid))
        private set

    fun setMemoryEnabled(on: Boolean) { memoryEnabled = on; store.memoryEnabled = on }

    fun addMemory(text: String) {
        val t = text.trim().take(300)
        if (t.isEmpty() || t in memories) return
        memories = (memories + t).takeLast(50)
        store.saveMemory(memories, uid)
    }

    fun deleteMemory(index: Int) {
        memories = memories.filterIndexed { i, _ -> i != index }
        store.saveMemory(memories, uid)
    }

    fun clearMemory() { memories = emptyList(); store.saveMemory(memories, uid) }

    private fun memoryMessage(): List<Message> =
        if (!memoryEnabled || memories.isEmpty()) emptyList()
        else listOf(Message("system",
            "Facts the user asked you to remember. Use them when relevant and never mention this list:\n" +
                memories.joinToString("\n") { "- $it" }))

    // ---- Web search
    var searchProvider by mutableStateOf(store.searchProvider)
    var searchKey by mutableStateOf(store.searchKey)
    var searchAnswer by mutableStateOf("")
        private set
    var searchHits by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    fun webSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        if (offlineMode) { searchError = "Web search needs internet. Turn off Offline mode in Settings."; return }
        if (searchKey.isBlank()) { searchError = "Add your search API key in Settings (Tavily or Brave)."; return }
        searching = true
        searchError = null
        searchAnswer = ""
        searchHits = emptyList()
        viewModelScope.launch {
            val r = SearchService(searchProvider, searchKey.trim()).search(q)
            val hits = r.getOrNull()
            if (hits == null) {
                searchError = r.exceptionOrNull()?.message ?: "Search failed"
            } else if (hits.isEmpty()) {
                searchError = "No results found."
            } else {
                searchHits = hits
                val context = hits.mapIndexed { i, h -> "[${i + 1}] ${h.title}\n${h.url}\n${h.snippet}" }
                    .joinToString("\n\n")
                val system = Message("system",
                    "Answer the question using only the numbered search results. Cite sources like [1]. " +
                        "If the results are not enough, say so. Reply in the same language as the question.")
                searchAnswer = ask(listOf(system, Message("user", "Question: $q\n\nSEARCH RESULTS:\n$context")))
                bump("search")
            }
            searching = false
        }
    }

    // ---- Offline mode
    var offlineMode by mutableStateOf(store.offlineMode)
        private set
    var offlineModelPath by mutableStateOf(store.offlineModelPath)
        private set
    var offlineStatus by mutableStateOf("")
        private set
    var offlineBusy by mutableStateOf(false)
        private set

    fun setOfflineMode(on: Boolean) { offlineMode = on; store.offlineMode = on }

    fun importOfflineModel(uri: Uri) {
        if (offlineBusy) return
        offlineBusy = true
        offlineStatus = "Copying model… this can take a few minutes."
        viewModelScope.launch {
            val r: Result<String> = withContext(Dispatchers.IO) {
                try {
                    val dest = File(app.filesDir, "offline_model.bin")
                    val input = app.contentResolver.openInputStream(uri) ?: error("Could not open the file")
                    input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
                    Result.success(dest.absolutePath)
                } catch (e: Exception) {
                    Result.failure<String>(e)
                }
            }
            val path = r.getOrNull()
            if (path != null) {
                OfflineLlm.reset()
                offlineModelPath = path
                store.offlineModelPath = path
                offlineStatus = "Model ready ✓"
            } else {
                offlineStatus = "Could not copy model: " + (r.exceptionOrNull()?.message ?: "unknown error")
            }
            offlineBusy = false
        }
    }

    // ---- Document scanner
    val scanPages = mutableStateListOf<Uri>()
    var scanPdf by mutableStateOf<Uri?>(null)
        private set
    var scanText by mutableStateOf("")
        private set
    var scanLoading by mutableStateOf(false)
        private set
    var scanError by mutableStateOf<String?>(null)
        private set

    fun onScanResult(pages: List<Uri>, pdf: Uri?) {
        scanPages.clear(); scanPages.addAll(pages)
        scanPdf = pdf; scanText = ""; scanError = null
    }

    fun setScanError(msg: String?) { scanError = msg }

    fun ocrScan() {
        if (scanPages.isEmpty() || scanLoading) return
        if (offlineMode) { scanError = "Text extraction needs an online vision model. Turn off Offline mode."; return }
        if (apiKey.isBlank()) { scanError = "Add your API key in Settings (with a vision-capable model) to extract text."; return }
        scanLoading = true
        scanError = null
        viewModelScope.launch {
            val parts = mutableListOf<String>()
            try {
                val pages = scanPages.toList()
                pages.forEachIndexed { i, uri ->
                    val jpeg = withContext(Dispatchers.IO) { decodeForVision(uri) }
                    val text = service().vision(
                        "Extract all text from this page exactly as written. Keep the original language and line breaks. Output only the text.",
                        jpeg
                    )
                    parts.add(if (pages.size > 1) "--- Page ${i + 1} ---\n$text" else text)
                }
                scanText = parts.joinToString("\n\n")
                bump("scans")
            } catch (e: Exception) {
                scanError = "Could not read the scan: ${e.message}"
            }
            scanLoading = false
        }
    }

    /** Hands the scanned text to PDF Chat so the user can ask questions about it. */
    fun useScanAsDocument() {
        pdfMessages.clear()
        pdfText = scanText
        pdfName = "Scanned document"
        pdfStatus = ""
    }

    // ---- Code assistant
    var codeResult by mutableStateOf("")
        private set
    var codeLoading by mutableStateOf(false)
        private set

    fun runCode(mode: String, lang: String, input: String) {
        if (input.isBlank() || codeLoading) return
        codeLoading = true
        viewModelScope.launch {
            val task = when (mode) {
                "Explain" -> "Explain what this $lang code does, step by step, in simple words."
                "Debug" -> "Find the bugs in this $lang code or error message, explain the cause, and give corrected code."
                "Write" -> "Write $lang code for the request below. Add brief comments and a short usage example."
                "Review" -> "Review this $lang code for correctness, readability, performance and security. Give concrete improvements."
                "Tests" -> "Write unit tests for this $lang code, covering normal and edge cases."
                else -> "Convert the code below to $lang and briefly note any behaviour differences."
            }
            val system = Message("system",
                "You are an expert software engineer. Put code in fenced markdown blocks. " +
                    "Be accurate and concise. Explain in the same language as the user's text.")
            codeResult = ask(listOf(system, Message("user", task + "\n\n" + input.trim().take(12000))))
            bump("code")
            codeLoading = false
        }
    }

    // ---- AI Writer
    var writerText by mutableStateOf("")
        private set
    var writerLoading by mutableStateOf(false)
        private set

    fun write(topic: String, type: String, tone: String, length: String) {
        if (topic.isBlank() || writerLoading) return
        writerLoading = true
        viewModelScope.launch {
            val system = Message("system",
                "You are a professional writer. Output only the finished text, no commentary. " +
                    "Write in the same language as the user's topic.")
            val userMsg = Message("user", "Write a $length $type. Tone: $tone.\n\nTopic / instructions: ${topic.trim()}")
            writerText = ask(listOf(system, userMsg))
            bump("writer")
            writerLoading = false
        }
    }

    // ---- Summarizer
    var summary by mutableStateOf("")
        private set
    var summarizing by mutableStateOf(false)
        private set

    fun summarize(text: String, style: String) {
        if (text.isBlank() || summarizing) return
        summarizing = true
        viewModelScope.launch {
            val instruction = when (style) {
                "Bullet points" -> "Summarize the text below as 5-8 concise bullet points."
                "Short paragraph" -> "Summarize the text below in one short paragraph of 3-4 sentences."
                "Detailed" -> "Write a detailed, structured summary of the text below with short headings and key points."
                else -> "Explain the text below in very simple words, as if to a beginner."
            }
            val system = Message("system",
                "You are an expert summarizer. Reply in the same language as the text unless asked otherwise. Output only the result.")
            val userMsg = Message("user", instruction + "\n\nTEXT:\n" + text.trim().take(15000))
            summary = ask(listOf(system, userMsg))
            bump("summaries")
            summarizing = false
        }
    }

    // ---- Study tools
    val cards = mutableStateListOf<Flashcard>()
    val quiz = mutableStateListOf<QuizQuestion>()
    var studyKind by mutableStateOf("")
        private set
    var studyLoading by mutableStateOf(false)
        private set
    var studyError by mutableStateOf<String?>(null)
        private set

    fun generateStudy(kind: String, text: String, count: Int) {
        if (text.isBlank() || studyLoading) return
        if (apiKey.isBlank()) {
            studyError = "Add your API key in Settings to generate study material."; return
        }
        studyLoading = true
        studyError = null
        viewModelScope.launch {
            val prompt = if (kind == "cards") {
                "Create $count flashcards from the text below. Reply with ONLY a JSON array like " +
                    "[{\"q\":\"question\",\"a\":\"answer\"}]."
            } else {
                "Create $count multiple-choice questions from the text below, each with exactly 4 options. " +
                    "Reply with ONLY a JSON array like " +
                    "[{\"q\":\"question\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":0,\"why\":\"short explanation\"}] " +
                    "where answer is the 0-based index of the correct option."
            }
            val raw = ask(listOf(
                Message("system", "You create study material. Output valid JSON only, no markdown."),
                Message("user", prompt + "\n\nTEXT:\n" + text.trim().take(12000))
            ))
            if (raw.startsWith("Error ") || raw.startsWith("Network error")) {
                studyError = raw
            } else {
                try {
                    val arr = JSONArray(raw.substring(raw.indexOf('['), raw.lastIndexOf(']') + 1))
                    if (kind == "cards") {
                        val list = (0 until arr.length()).map {
                            val o = arr.getJSONObject(it); Flashcard(o.getString("q"), o.getString("a"))
                        }
                        require(list.isNotEmpty())
                        cards.clear(); cards.addAll(list)
                    } else {
                        val list = (0 until arr.length()).map {
                            val o = arr.getJSONObject(it)
                            val opts = o.getJSONArray("options")
                            val options = (0 until opts.length()).map { j -> opts.getString(j) }
                            val ans = o.getInt("answer")
                            require(ans in options.indices)
                            QuizQuestion(o.getString("q"), options, ans, o.optString("why", ""))
                        }
                        require(list.isNotEmpty())
                        quiz.clear(); quiz.addAll(list)
                    }
                    studyKind = kind
                    bump("study")
                } catch (e: Exception) {
                    studyError = "Could not read the AI response. Please try again."
                }
            }
            studyLoading = false
        }
    }

    // ---- Translator
    var translation by mutableStateOf("")
        private set
    var translating by mutableStateOf(false)
        private set

    fun translate(text: String, fromLabel: String, toLabel: String) {
        if (text.isBlank() || translating) return
        translating = true
        viewModelScope.launch {
            val to = Languages.byLabel(toLabel)
            val fromPart = if (fromLabel == Languages.AUTO) "" else "from ${Languages.byLabel(fromLabel).promptName} "
            val system = Message("system",
                "You are a professional translator. Output only the translation, with no notes or quotation marks.")
            val userMsg = Message("user", "Translate the following text ${fromPart}to ${to.promptName}:\n\n${text.trim()}")
            if (offlineMode) {
                if (fromLabel == Languages.AUTO) {
                    translation = "Offline translation needs a source language (not Auto-detect)."
                } else {
                    val r = OfflineTranslator.translate(text.trim(), Languages.byLabel(fromLabel).tag, to.tag)
                    translation = r.getOrNull() ?: ("Offline translation failed: " + (r.exceptionOrNull()?.message ?: "unknown error"))
                }
            } else {
                translation = ask(listOf(system, userMsg))
            }
            bump("translations")
            translating = false
        }
    }

    fun setTranslationText(t: String) { translation = t }

    // ---- PDF Chat
    var pdfName by mutableStateOf<String?>(null)
        private set
    var pdfText by mutableStateOf("")
        private set
    var pdfStatus by mutableStateOf("")
        private set
    var pdfLoading by mutableStateOf(false)
        private set
    val pdfMessages = mutableStateListOf<Message>()

    fun loadPdf(uri: Uri) {
        viewModelScope.launch {
            pdfLoading = true
            pdfMessages.clear()
            try {
                val text = PdfReader.extractText(app, uri).trim()
                pdfText = text
                pdfName = displayName(uri)
                pdfStatus = if (text.isBlank()) "No readable text found (scanned PDFs are not supported yet)." else ""
            } catch (e: Exception) {
                pdfText = ""; pdfName = null
                pdfStatus = "Could not read PDF: ${e.message}"
            }
            pdfLoading = false
        }
    }

    private fun displayName(uri: Uri): String? =
        app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }

    fun askPdf(question: String) {
        val q = question.trim()
        if (q.isEmpty() || pdfText.isBlank() || pdfLoading) return
        pdfMessages.add(Message("user", q))
        pdfLoading = true
        viewModelScope.launch {
            val system = Message("system",
                "Answer the user's questions using only the document below. " +
                    "If the answer is not in the document, say so.\n\nDOCUMENT:\n" + pdfText.take(15000))
            val answer = ask(listOf(system) + pdfMessages.toList())
            pdfMessages.add(Message("assistant", answer))
            bump("pdf")
            pdfLoading = false
        }
    }

    // ---- Image generation
    val images = mutableStateListOf<ByteArray>()
    var imageLoading by mutableStateOf(false)
        private set
    var imageError by mutableStateOf<String?>(null)
        private set

    fun generateImage(prompt: String) {
        if (prompt.isBlank() || imageLoading) return
        if (apiKey.isBlank()) {
            imageError = "Add your API key in Settings to generate images."; return
        }
        imageLoading = true
        imageError = null
        viewModelScope.launch {
            ImageService(baseUrl, apiKey, imageModel, imageSize).generate(prompt.trim())
                .onSuccess { images.add(0, it); bump("images") }
                .onFailure { imageError = it.message ?: "Image generation failed" }
            imageLoading = false
        }
    }

    // ---- Image understanding / OCR
    var visionImage by mutableStateOf<ByteArray?>(null)
        private set
    var visionResult by mutableStateOf("")
        private set
    var visionLoading by mutableStateOf(false)
        private set
    var visionError by mutableStateOf<String?>(null)
        private set

    fun loadVisionImage(uri: Uri) {
        viewModelScope.launch {
            visionError = null; visionResult = ""
            try {
                visionImage = withContext(Dispatchers.IO) { decodeForVision(uri) }
            } catch (e: Exception) {
                visionError = "Could not open image: ${e.message}"
            }
        }
    }

    private fun decodeForVision(uri: Uri): ByteArray {
        val resolver = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Unsupported image")
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, 1600f / maxOf(bmp.width, bmp.height))
        if (rotation != 0f || scale < 1f) {
            val m = Matrix().apply { postRotate(rotation); postScale(scale, scale) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    fun analyzeImage(prompt: String) {
        val img = visionImage ?: return
        if (prompt.isBlank() || visionLoading) return
        visionLoading = true
        visionError = null
        viewModelScope.launch {
            visionResult = service().vision(prompt.trim(), img)
            bump("vision")
            visionLoading = false
        }
    }
}
