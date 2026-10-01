package com.ironmind.app.domain.model

/**
 * Progress of adjusting a draft from the athlete's own words. Separate from [RoutineDraftState]
 * because the draft stays on screen while this runs — and stays exactly as it was when it fails.
 */
sealed interface RoutineRefineState {
    data object Loading : RoutineRefineState

    /** The adjusted rows, ready to replace the draft's. */
    data class Applied(val rows: List<RoutineDraftExercise>) : RoutineRefineState

    /** Nothing changed; [message] says why, quoting the model when it said something. */
    data class Failed(val message: String) : RoutineRefineState
}
