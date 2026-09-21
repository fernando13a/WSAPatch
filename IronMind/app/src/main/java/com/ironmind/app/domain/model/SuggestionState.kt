package com.ironmind.app.domain.model

/**
 * UI-facing state for an AI progression suggestion. As tokens stream in, the use case emits
 * [Success] repeatedly with the text accumulated so far ([isComplete] = false), then a final
 * [Success] with [isComplete] = true. This drives the typewriter effect while keeping the
 * classic Loading / Success / Error shape.
 */
sealed interface SuggestionState {

    /** The prompt has been built and inference is starting; nothing has streamed yet. */
    data object Loading : SuggestionState

    /** Response text so far. When [isComplete] is true the stream has finished. */
    data class Success(
        val suggestion: String,
        val isComplete: Boolean = false,
    ) : SuggestionState

    /** Something went wrong (model missing, no history, inference failure, …). */
    data class Error(val message: String) : SuggestionState
}
