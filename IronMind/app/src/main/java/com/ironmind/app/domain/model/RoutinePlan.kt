package com.ironmind.app.domain.model

/**
 * A [Routine] together with the exercises it prescribes. This is the domain
 * counterpart of the `RoutineWithExercises` Room relation.
 */
data class RoutinePlan(
    val routine: Routine,
    val exercises: List<Exercise>,
)
