package org.stypox.dicio.skills.fallback.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs an open-source language model (Qwen3, Apache 2.0) fully on the phone using LiteRT-LM.
 * The model file is downloaded once with Android's DownloadManager, then nothing leaves the phone.
 */
object LocalLlm {
    private const val TAG = "LocalLlm"

    const val MODEL_NAME = "Qwen3 4B"
    const val MODEL_SIZE_TEXT = "2.7 GB"
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm"
    private const val MODEL_FILE = "qwen3_4b_mixed_int4.litertlm"
    private const val PARTIAL_SUFFIX = ".part"
    private const val MIN_VALID_SIZE = 100L * 1024 * 1024

    private const val PREFS = "local_llm"
    private const val PREF_DOWNLOAD_ID = "download_id"

    private const val SYSTEM_PROMPT =
        "You are a helpful voice assistant running on the user's phone. Your answer will be " +
            "read aloud, so reply in one to three short sentences of plain spoken language. " +
            "Never use lists, markdown, symbols or emoji. If you are not sure, say so briefly."

    enum class ModelState { MISSING, DOWNLOADING, READY }

    private val mutex = Mutex()
    private var engine: Engine? = null

    private fun dir(ctx: Context): File = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    private fun modelFile(ctx: Context) = File(dir(ctx), MODEL_FILE)
    private fun partialFile(ctx: Context) = File(dir(ctx), MODEL_FILE + PARTIAL_SUFFIX)

    fun modelState(ctx: Context): ModelState {
        val model = modelFile(ctx)
        if (model.exists() && model.length() > MIN_VALID_SIZE) {
            return ModelState.READY
        }

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(PREF_DOWNLOAD_ID, -1L)
        if (id == -1L) {
            return ModelState.MISSING
        }

        val dm = ctx.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val partial = partialFile(ctx)
                        if (partial.exists() && partial.length() > MIN_VALID_SIZE &&
                            partial.renameTo(model)
                        ) {
                            prefs.edit().remove(PREF_DOWNLOAD_ID).apply()
                            return ModelState.READY
                        }
                    }
                    DownloadManager.STATUS_FAILED -> Unit
                    else -> return ModelState.DOWNLOADING
                }
            }
        }

        // the download failed or disappeared: allow starting it again
        prefs.edit().remove(PREF_DOWNLOAD_ID).apply()
        return ModelState.MISSING
    }

    fun startDownload(ctx: Context) {
        if (modelState(ctx) != ModelState.MISSING) {
            return
        }
        partialFile(ctx).delete()
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("AI model ($MODEL_NAME)")
            .setDescription("Downloading the on-device AI model ($MODEL_SIZE_TEXT)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, null, MODEL_FILE + PARTIAL_SUFFIX)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
        val id = ctx.getSystemService(DownloadManager::class.java).enqueue(request)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(PREF_DOWNLOAD_ID, id)
            .apply()
    }

    private fun createEngine(ctx: Context): Engine {
        val path = modelFile(ctx).absolutePath
        for (backend in listOf(Backend.GPU(), Backend.CPU())) {
            try {
                val newEngine = Engine(
                    EngineConfig(
                        modelPath = path,
                        backend = backend,
                        cacheDir = ctx.cacheDir.path,
                    )
                )
                newEngine.initialize()
                Log.i(TAG, "Loaded $MODEL_NAME with backend $backend")
                return newEngine
            } catch (t: Throwable) {
                Log.w(TAG, "Could not load model with backend $backend", t)
            }
        }
        throw IllegalStateException("Could not load the AI model on this phone")
    }

    /**
     * Answers [question] with the on-device model. The first call loads the model, which can
     * take around ten seconds; later calls reuse it.
     */
    suspend fun ask(ctx: Context, question: String): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loadedEngine = engine ?: createEngine(ctx.applicationContext).also { engine = it }
            val config = ConversationConfig(systemInstruction = Contents.of(SYSTEM_PROMPT))
            loadedEngine.createConversation(config).use { conversation ->
                // "/no_think" turns off Qwen3's slow step-by-step reasoning mode
                val reply = conversation.sendMessage("$question /no_think")
                cleanForSpeech(reply.toString())
            }
        }
    }

    internal fun cleanForSpeech(raw: String): String {
        return raw
            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("</?think>"), "")
            .replace(Regex("[*#_`]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
