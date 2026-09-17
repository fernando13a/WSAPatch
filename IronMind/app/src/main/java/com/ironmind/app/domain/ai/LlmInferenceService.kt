package com.ironmind.app.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Domain-facing port for the on-device large language model. The concrete implementation
 * (MediaPipe LLM Inference / Google AI Edge) lives in the data layer, keeping the domain
 * and use cases free of any framework dependency and easy to fake in tests.
 */
interface LlmInferenceService {

    /** `true` if the model file is present on the device (offline-ready). */
    suspend fun isModelAvailable(): Boolean

    /**
     * Streams the model's response one chunk at a time (the "typewriter" effect). Each emission
     * is an incremental piece of text; concatenating them yields the full response. The flow
     * completes when generation is done and fails if the model is missing or inference errors.
     */
    fun generateResponseStream(prompt: String): Flow<String>

    /** Releases native resources held by the inference engine. */
    fun close()
}
