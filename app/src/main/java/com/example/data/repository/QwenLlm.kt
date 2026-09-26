package com.example.data.repository

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class QwenPhase { NOT_INSTALLED, DOWNLOADING, INSTALLED, LOADING, READY, ERROR }

data class QwenState(val phase: QwenPhase = QwenPhase.NOT_INSTALLED, val percent: Int = 0, val detail: String? = null)

/**
 * A real on-device Qwen 2.5 language model, run with Google's LiteRT-LM runtime — no network
 * and no API key at inference time. The 1.6 GB int8 model file is too large to ship inside the
 * APK, so it is downloaded once (see [download]) or pushed with adb into [modelDir]; until it is
 * present [isInstalled] is false and the agent falls back to Gemini (if a key is set) or templates.
 */
object QwenLlm {
    private const val TAG = "QwenLlm"
    const val MODEL_LABEL = "Qwen 2.5 1.5B (int8)"
    private const val DEFAULT_FILE = "Qwen2.5-1.5B-Instruct_q8_ekv4096.litertlm"
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
    private const val APPROX_SIZE_BYTES = 1_600_000_000L
    private const val MIN_VALID_BYTES = 50_000_000L // rejects truncated/placeholder files

    private lateinit var appContext: Context
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val engineLock = Mutex()
    @Volatile private var engine: Engine? = null

    private val _state = MutableStateFlow(QwenState())
    val state: StateFlow<QwenState> = _state.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        refresh()
    }

    /** App-specific external storage, so `adb push` works without root: .../files/models/ */
    fun modelDir(): File =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "models").apply { mkdirs() }

    /** Any valid *.litertlm in [modelDir] counts, so a different Qwen build can simply be pushed there. */
    fun modelFile(): File? {
        if (!::appContext.isInitialized) return null
        val files = modelDir().listFiles { f -> f.isFile && f.name.endsWith(".litertlm") && f.length() >= MIN_VALID_BYTES }
            ?: return null
        return files.firstOrNull { it.name == DEFAULT_FILE } ?: files.maxByOrNull { it.length() }
    }

    fun isInstalled(): Boolean = modelFile() != null

    private fun refresh() {
        val phase = _state.value.phase
        if (phase == QwenPhase.DOWNLOADING || phase == QwenPhase.LOADING) return
        _state.value = when {
            engine != null -> QwenState(QwenPhase.READY)
            isInstalled() -> QwenState(QwenPhase.INSTALLED)
            else -> QwenState(QwenPhase.NOT_INSTALLED)
        }
    }

    /** Runs one prompt through Qwen. Returns null if the model is missing or anything fails. */
    suspend fun generate(system: String, prompt: String, maxTokens: Int = 256): String? =
        withContext(Dispatchers.Default) {
            val file = modelFile() ?: return@withContext null
            engineLock.withLock {
                try {
                    val loaded = engine ?: loadEngine(file)
                    val config = ConversationConfig(
                        systemInstruction = Contents.of(system),
                        samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.3, seed = 0),
                        maxOutputToken = maxTokens
                    )
                    // A fresh conversation per call keeps every agent step independent and the KV cache small.
                    loaded.createConversation(config).use { conversation ->
                        conversation.sendMessage(prompt).contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                            .trim()
                            .ifEmpty { null }
                    }
                } catch (t: Throwable) { // includes UnsatisfiedLinkError / OOM from the native runtime
                    Log.w(TAG, "Qwen generation failed", t)
                    engine?.let { runCatching { it.close() } }
                    engine = null
                    _state.value = QwenState(QwenPhase.ERROR, detail = t.message ?: t.javaClass.simpleName)
                    null
                }
            }
        }

    suspend fun generateJson(prompt: String, systemInstruction: String): JSONObject? {
        val text = generate("$systemInstruction Respond with a single JSON object only, no extra text.", prompt, 400)
            ?: return null
        return extractJsonObject(text)
    }

    private fun loadEngine(file: File): Engine {
        _state.value = QwenState(QwenPhase.LOADING)
        val started = System.currentTimeMillis()
        val created = Engine(
            EngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.CPU(),
                cacheDir = appContext.cacheDir.path
            )
        )
        created.initialize()
        engine = created
        _state.value = QwenState(QwenPhase.READY)
        Log.i(TAG, "Qwen loaded from ${file.name} in ${System.currentTimeMillis() - started}ms")
        return created
    }

    /** Frees the model's memory (~1.7 GB); it reloads on the next [generate]. */
    fun release() {
        runCatching { engine?.close() }
        engine = null
        if (::appContext.isInitialized) refresh()
    }

    fun delete() {
        release()
        if (!::appContext.isInitialized) return
        modelDir().listFiles()?.forEach { if (it.name.endsWith(".litertlm") || it.name.endsWith(".part")) it.delete() }
        _state.value = QwenState(QwenPhase.NOT_INSTALLED)
    }

    /**
     * Downloads the model into [modelDir], resuming a partial download if one exists. Progress is
     * published through [state]. Cancelling the calling coroutine keeps the partial file.
     */
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        if (_state.value.phase == QwenPhase.DOWNLOADING) return@withContext false
        val target = File(modelDir(), DEFAULT_FILE)
        val part = File(modelDir(), "$DEFAULT_FILE.part")
        try {
            _state.value = QwenState(QwenPhase.DOWNLOADING, 0)
            var have = if (part.exists()) part.length() else 0L
            val request = Request.Builder().url(MODEL_URL)
                .apply { if (have > 0) header("Range", "bytes=$have-") }
                .build()
            http.newCall(request).execute().use { response ->
                if (response.code == 416) return@use // the partial file already holds the whole model
                if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
                val append = response.code == 206
                if (!append) have = 0L
                val body = response.body ?: throw IOException("Empty download response")
                val total = (body.contentLength().takeIf { it > 0 } ?: (APPROX_SIZE_BYTES - have)) + have
                val needed = total - have
                if (modelDir().usableSpace < needed + 100_000_000L) {
                    throw IOException("Not enough storage: need about ${needed / 1_000_000} MB free")
                }
                FileOutputStream(part, append).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = have
                    var lastPercent = -1
                    val input = body.byteStream()
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        ensureActive()
                        out.write(buffer, 0, read)
                        done += read
                        val percent = (done * 100 / total).toInt().coerceIn(0, 99)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = QwenState(QwenPhase.DOWNLOADING, percent)
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IOException("Could not finalize the model file")
            _state.value = QwenState(QwenPhase.INSTALLED)
            true
        } catch (e: CancellationException) {
            _state.value = QwenState(QwenPhase.NOT_INSTALLED, detail = "Download paused")
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Model download failed", t)
            _state.value = QwenState(QwenPhase.ERROR, detail = t.message ?: "Download failed")
            false
        }
    }
}

/** Pulls the first JSON object out of a model reply that may include code fences or prose. */
internal fun extractJsonObject(text: String): JSONObject? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try { JSONObject(text.substring(start, end + 1)) } catch (_: Exception) { null }
}
