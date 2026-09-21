package com.ironmind.app

import com.ironmind.app.domain.ai.LlmInferenceService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Configurable fake for [LlmInferenceService] used across ViewModel/use-case tests. */
class FakeLlmInferenceService(
    private val chunks: List<String> = emptyList(),
    private val error: Throwable? = null,
    var modelAvailable: Boolean = true,
) : LlmInferenceService {
    override suspend fun isModelAvailable(): Boolean = modelAvailable
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        error?.let { throw it }
        chunks.forEach { emit(it) }
    }
    override fun close() = Unit
}
