package com.ironmind.app.domain.model

/**
 * UI-facing state for a generated [RoutineDraft]. Unlike [SuggestionState], there is no
 * incremental/typewriter variant of [Success] — the model's answer is a short list of numbers,
 * parsed once it has fully streamed in.
 */
sealed interface RoutineDraftState {

    /**
     * Still working, and on what — so the wait reads as progress rather than a frozen spinner.
     * The first request of a session also loads the model into memory, which can take tens of
     * seconds on its own before a token comes out.
     */
    data class Loading(val phase: Phase = Phase.READING_HISTORY) : RoutineDraftState {
        enum class Phase {
            /** Loading the catalog and the athlete's log; fast. */
            READING_HISTORY,

            /** Waiting on the model, including loading it on the session's first request. */
            ASKING_MODEL,
        }
    }

    data class Success(val draft: RoutineDraft) : RoutineDraftState
    data class Error(val message: String) : RoutineDraftState
}
