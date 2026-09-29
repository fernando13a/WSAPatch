package com.ironmind.app.domain.model

/**
 * A proposed routine, staged for the user to review, edit, or discard before anything is
 * written to [com.ironmind.app.domain.repository.WorkoutRepository]. Never persisted directly —
 * [exercises] carries only the exercise id (not a name), since the id is what was validated
 * against the real catalog; the UI resolves display names itself.
 */
data class RoutineDraft(
    val split: RoutineSplit,
    val exercises: List<RoutineDraftExercise>,
    val source: Source = Source.AI,
    /**
     * Why the model's choice wasn't used, for a [Source.RULES] draft: the model isn't downloaded,
     * failed to start, took too long, or answered with nothing usable. Null for an AI draft.
     */
    val notice: String? = null,
) {
    enum class Source {
        /** The model picked and ordered the exercises; Kotlin balanced and prescribed them. */
        AI,

        /** Built from rules alone, because the model's choice couldn't be used. */
        RULES,
    }
}

data class RoutineDraftExercise(
    val exerciseId: Long,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
)
