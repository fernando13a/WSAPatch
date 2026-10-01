package com.ironmind.app

import com.ironmind.app.domain.ai.LlmInferenceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Configurable fake for [LlmInferenceService] used across ViewModel/use-case tests. */
class FakeLlmInferenceService(
    private val chunks: List<String> = emptyList(),
    private val error: Throwable? = null,
    var modelAvailable: Boolean = true,
) : LlmInferenceService {

    /** Every prompt received, in order — lets a test check what the model was actually asked. */
    val prompts = mutableListOf<String>()

    /**
     * When set, each answer waits for it before streaming — a model still "thinking", so a test
     * can act in between. Set it after any request that should answer straight away.
     */
    var gate: CompletableDeferred<Unit>? = null

    /** The temperature of the last request, or null when it used the default sampling. */
    var lastTemperature: Float? = null
        private set

    override suspend fun isModelAvailable(): Boolean = modelAvailable

    override fun generateResponseStream(prompt: String): Flow<String> {
        lastTemperature = null
        return respond(prompt)
    }

    override fun generateResponseStream(prompt: String, temperature: Float): Flow<String> {
        lastTemperature = temperature
        return respond(prompt)
    }

    private fun respond(prompt: String): Flow<String> = flow {
        prompts += prompt
        gate?.await()
        error?.let { throw it }
        chunks.forEach { emit(it) }
    }

    override fun close() = Unit
}
