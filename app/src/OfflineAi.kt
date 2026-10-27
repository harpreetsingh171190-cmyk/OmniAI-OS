package com.harpreet.omniai.ai

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import com.harpreet.omniai.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Keeps one loaded on-device model (loading is slow and memory heavy). */
object OfflineLlm {
    private var engine: LlmInference? = null
    private var loadedPath: String? = null

    @Synchronized
    fun get(context: Context, path: String): LlmInference {
        val existing = engine
        if (existing != null && loadedPath == path) return existing
        existing?.close()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(1024)
            .build()
        val created = LlmInference.createFromOptions(context.applicationContext, options)
        engine = created
        loadedPath = path
        return created
    }

    @Synchronized
    fun reset() {
        engine?.close()
        engine = null
        loadedPath = null
    }
}

/**
 * Chat with a model file stored on the phone (MediaPipe LLM Inference, e.g. a Gemma .task/.bin
 * model that you download yourself). No internet needed once the file is on the device.
 */
class OfflineAiService(private val context: Context, private val modelPath: String) : AiService {

    override suspend fun reply(history: List<Message>): String = withContext(Dispatchers.Default) {
        if (modelPath.isBlank() || !File(modelPath).exists()) {
            return@withContext "Offline mode: choose a model file in Settings first."
        }
        try {
            OfflineLlm.get(context, modelPath).generateResponse(buildPrompt(history)).trim()
        } catch (e: Exception) {
            "Offline model error: ${e.message}"
        }
    }

    override suspend fun vision(prompt: String, jpeg: ByteArray): String =
        "Image understanding needs an online vision model. Turn off Offline mode in Settings."

    /** Gemma-style chat template; the system text is folded into the first user turn. */
    private fun buildPrompt(history: List<Message>): String {
        val system = history.filter { it.role == "system" }.joinToString("\n") { it.text }.take(1500)
        val turns = history.filter { it.role != "system" }.takeLast(6)
        val sb = StringBuilder()
        turns.forEachIndexed { i, m ->
            val role = if (m.isUser) "user" else "model"
            val text = m.text.take(1500)
            sb.append("<start_of_turn>").append(role).append("\n")
            if (i == 0 && system.isNotBlank() && m.isUser) sb.append(system).append("\n\n")
            sb.append(text).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }
}

/** On-device translation (Google ML Kit). Language packs download once, then work offline. */
object OfflineTranslator {

    suspend fun translate(text: String, fromTag: String, toTag: String): Result<String> {
        val src = TranslateLanguage.fromLanguageTag(fromTag.substringBefore('-'))
        val dst = TranslateLanguage.fromLanguageTag(toTag.substringBefore('-'))
        if (src == null || dst == null) {
            return Result.failure(Exception("This language is not available for offline translation yet."))
        }
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dst).build()
        )
        return try {
            awaitTask(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            Result.success(awaitTask(translator.translate(text)))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            translator.close()
        }
    }

    private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
