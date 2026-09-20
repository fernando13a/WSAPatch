package com.ironmind.app.domain.model

/**
 * An AI-proposed routine, staged for the user to review, edit, or discard before anything is
 * written to [com.ironmind.app.domain.repository.WorkoutRepository]. Never persisted directly —
 * [exercises] carries only the exercise id (not a name), since the id is what the parser
 * validated against the real catalog; the UI resolves display names itself.
 */
data class RoutineDraft(
    val split: RoutineSplit,
    val exercises: List<RoutineDraftExercise>,
)

data class RoutineDraftExercise(
    val exerciseId: Long,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
)
