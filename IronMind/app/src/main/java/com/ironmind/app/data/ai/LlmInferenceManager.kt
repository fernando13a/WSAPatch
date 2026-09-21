package com.ironmind.app.data.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.ironmind.app.core.util.DispatcherProvider
import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device LLM manager backed by the Google AI Edge / MediaPipe LLM Inference API.
 *
 * The heavyweight [LlmInference] engine is created lazily (once) on [DispatcherProvider.io] and
 * reused; a fresh [LlmInferenceSession] is created per request. Responses are exposed as a
 * `Flow<String>` of incremental chunks via [callbackFlow], producing the typewriter effect.
 *
 * The engine only tolerates one live [LlmInferenceSession] at a time — with several AI features
 * (progression, technique coach, insights, ...) all sharing this singleton, [inferenceMutex]
 * serializes generation so a second caller's request queues instead of racing the first one's
 * session (which would otherwise corrupt output or crash natively).
 *
 * Note: the MediaPipe API surface tracks the `tasks-genai` 0.10.x line; builder method names may
 * need minor adjustment if you bump the dependency. The progress listener is treated as emitting
 * *incremental* text — if a future version emits cumulative text, drop the concatenation upstream.
 */
@Singleton
class LlmInferenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : LlmInferenceService {

    private val initMutex = Mutex()

    /** Held for the full lifetime of a single streaming request; serializes access to [engine]. */
    private val inferenceMutex = Mutex()

    @Volatile
    private var engine: LlmInference? = null

    override suspend fun isModelAvailable(): Boolean = withContext(dispatchers.io) {
        // Same bar as ModelDownloader.isReady(): a stub or half-written file must not switch the
        // AI features on, or the screens hide the download prompt and every request fails in
        // createEngine instead.
        resolveModelFile().let { it.exists() && it.length() >= AiConstants.MIN_PLAUSIBLE_MODEL_BYTES }
    }

    override fun generateResponseStream(prompt: String): Flow<String> = callbackFlow {
        inferenceMutex.withLock {
            val llm = ensureEngine()

            val sessionOptions = LlmInferenceSessionOptions.builder()
                .setTopK(AiConstants.TOP_K)
                .setTemperature(AiConstants.TEMPERATURE)
                .build()

            val session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
            session.addQueryChunk(prompt)

            // Asynchronous, streaming generation. `partialResult` is a new chunk; `done` ends the stream.
            session.generateResponseAsync { partialResult, done ->
                trySend(partialResult)
                if (done) close()
            }

            awaitClose { session.close() }
        }
    }
        // The native callback is not backpressure-aware and trySend drops what doesn't fit, while
        // consumers recompose per chunk — far slower than generation. On callbackFlow's default
        // 64-element buffer that silently deletes words from the middle of an answer.
        .buffer(Channel.UNLIMITED)
        .flowOn(dispatchers.io)

    /**
     * Releases the engine — but only if no generation is currently in flight. A generation in
     * progress holds [inferenceMutex] for its entire lifetime, so [tryLock] failing here means
     * some caller is mid-stream; tearing down the engine underneath it would crash natively, so
     * this call is a no-op in that case rather than racing the active session.
     */
    override fun close() {
        if (inferenceMutex.tryLock()) {
            try {
                engine?.close()
                engine = null
            } finally {
                inferenceMutex.unlock()
            }
        }
    }

    /** Lazily creates the engine, guarded by a mutex so it initializes exactly once. */
    private suspend fun ensureEngine(): LlmInference {
        engine?.let { return it }
        return initMutex.withLock {
            engine ?: createEngine().also { engine = it }
        }
    }

    private fun createEngine(): LlmInference {
        val modelFile = resolveModelFile()
        if (!modelFile.exists()) throw LlmModelNotFoundException(modelFile.absolutePath)

        // CPU (XNNPACK) first, not the library's default of GPU. Gemma 3 support in this API line
        // landed as "GemmaV3-1B via XNNPACK", and asking a build that lacks the GPU path for one
        // makes the native loader reject the bundle in model_data.cc with a RET_CHECK trace whose
        // text ("Error building tflite model") reads exactly like a corrupt download — which is
        // what a complete, correctly downloaded model was being blamed for. GPU stays as a
        // fallback so a bundle that does want it still runs.
        var firstFailure: Exception? = null
        for (backend in BACKEND_PREFERENCE) {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(AiConstants.MAX_TOKENS)
                .setPreferredBackend(backend)
                .build()
            try {
                return LlmInference.createFromOptions(context, options)
            } catch (error: Exception) {
                // Only the backend-selection failures are worth retrying past; an Error (OOM from
                // mapping half a gigabyte, a missing native lib) propagates untouched.
                if (firstFailure == null) firstFailure = error
            }
        }

        val cause = checkNotNull(firstFailure) { "BACKEND_PREFERENCE must not be empty" }
        throw IllegalStateException(modelLoadFailureMessage(modelFile.length(), cause.message), cause)
    }

    private fun resolveModelFile(): File = AiConstants.modelFile(context)

    private companion object {
        /** Tried in order; the first backend that builds the engine wins. */
        val BACKEND_PREFERENCE = listOf(LlmInference.Backend.CPU, LlmInference.Backend.GPU)
    }
}

/**
 * Turns a native engine failure into something an athlete standing in a gym can act on.
 *
 * The native side reports any load failure as a multi-line C++ RET_CHECK trace, so the only signal
 * worth acting on is the file size. Split out as a pure function because [LlmInferenceManager]
 * needs a [Context] and the native libraries to construct, and this wording is the part that was
 * wrong: it told people to delete and re-download a file that was complete and correct.
 */
fun modelLoadFailureMessage(sizeBytes: Long, causeMessage: String?): String {
    val megabytes = sizeBytes / (1024 * 1024)
    val expectedMegabytes = AiConstants.EXPECTED_MODEL_BYTES / (1024 * 1024)
    if (sizeBytes != AiConstants.EXPECTED_MODEL_BYTES) {
        return "No se pudo cargar el modelo de IA: el archivo mide $megabytes MB y el modelo por " +
            "defecto mide $expectedMegabytes MB. Si lo descargaste desde la app está incompleto — " +
            "ve a Modelo de IA, pulsa «Borrar modelo» y descárgalo otra vez, a ser posible con WiFi."
    }
    // Right size, so re-downloading it would waste half a gigabyte and change nothing. Carry the
    // first line of the native error instead, which is the only part that identifies the cause.
    val detail = causeMessage
        ?.lineSequence()
        ?.map(String::trim)
        ?.firstOrNull { it.isNotEmpty() }
        ?.take(140)
    return buildString {
        append("El modelo está completo ($megabytes MB), pero el motor de IA no pudo iniciarlo en ")
        append("este teléfono. Borrarlo y volver a descargarlo no lo arregla.")
        if (!detail.isNullOrEmpty()) append(" Detalle: $detail")
    }
}
