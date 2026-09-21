package com.ironmind.app.domain.model

/**
 * UI-facing state for an AI-generated [RoutineDraft]. Unlike [SuggestionState], there is no
 * incremental/typewriter variant of [Success] — the raw model output is parsed once it fully
 * streams in, since a half-parsed routine table has no meaningful partial rendering.
 */
sealed interface RoutineDraftState {
    data object Loading : RoutineDraftState
    data class Success(val draft: RoutineDraft) : RoutineDraftState
    data class Error(val message: String) : RoutineDraftState
}
