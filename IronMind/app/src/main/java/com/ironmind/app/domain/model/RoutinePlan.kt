package com.ironmind.app.domain.model

/**
 * A [Routine] together with the exercises it prescribes, in the routine's own order. This is the
 * domain counterpart of the `RoutineWithExercises` Room relation.
 */
data class RoutinePlan(
    val routine: Routine,
    val exercises: List<Exercise>,
    /**
     * What the routine asks of each exercise, by exercise id. Stored all along but never read back
     * until a session needed it — without it a workout ran on a hardcoded 90 s rest whatever the
     * routine said, and the athlete had to remember the sets and reps themselves.
     */
    val prescriptions: Map<Long, ExercisePrescription> = emptyMap(),
)

/** One exercise's target within a routine. */
data class ExercisePrescription(
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    /** Suggested working weight; null when there was nothing to suggest from. */
    val weightKg: Double? = null,
)
