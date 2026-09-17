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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
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

    @Volatile
    private var engine: LlmInference? = null

    override suspend fun isModelAvailable(): Boolean = withContext(dispatchers.io) {
        resolveModelFile().exists()
    }

    override fun generateResponseStream(prompt: String): Flow<String> = callbackFlow {
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
    }.flowOn(dispatchers.io)

    override fun close() {
        engine?.close()
        engine = null
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
        return LlmInference.createFromOptions(context, options)
    }

    private fun resolveModelFile(): File = AiConstants.modelFile(context)
}
