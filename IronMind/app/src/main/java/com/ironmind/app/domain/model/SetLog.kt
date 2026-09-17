package com.ironmind.app.domain.model

/**
 * A single logged set inside a [WorkoutSession].
 *
 * [notes] is a free-form field intended for context the on-device AI can reason over —
 * e.g. supplement intake ("Optimum Nutrition Gold Standard 100% Whey"), energy levels,
 * form cues, or how a lift felt.
 */
data class SetLog(
    val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    /** Rate of Perceived Exertion (1–10), optional. */
    val rpe: Float? = null,
    val isWarmup: Boolean = false,
    val isCompleted: Boolean = true,
    val restSeconds: Int? = null,
    val notes: String? = null,
    val performedAt: Long = System.currentTimeMillis(),
) {
    /** Training volume for this set (weight × reps). */
    val volume: Double
        get() = weightKg * reps
}
