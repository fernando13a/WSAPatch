package com.ironmind.app.domain.model

/**
 * A single exercise in the catalog (e.g. "Barbell Bench Press").
 * Pure domain model — free of any Room/persistence annotations.
 */
data class Exercise(
    val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val description: String? = null,
    /** `true` for user-created exercises, `false` for the seeded catalog. */
    val isCustom: Boolean = true,
)
