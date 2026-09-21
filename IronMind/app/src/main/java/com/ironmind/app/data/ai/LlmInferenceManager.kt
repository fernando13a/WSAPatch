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

        val options = LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(AiConstants.MAX_TOKENS)
            .build()
        return try {
            LlmInference.createFromOptions(context, options)
        } catch (error: Throwable) {
            // The native engine reports a bad bundle as a multi-line C++ RET_CHECK trace, which is
            // noise to an athlete standing in a gym. By far the most common cause is a truncated
            // download, so say what to do about it — the size lets them confirm.
            val megabytes = modelFile.length() / (1024 * 1024)
            throw IllegalStateException(
                "No se pudo cargar el modelo de IA (archivo de $megabytes MB). Suele estar " +
                    "incompleto o dañado: ve a Modelo de IA, pulsa «Borrar modelo» y descárgalo " +
                    "otra vez, a ser posible con WiFi.",
                error,
            )
        }
    }

    private fun resolveModelFile(): File = AiConstants.modelFile(context)
}
