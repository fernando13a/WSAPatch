package com.ironmind.app.domain.model

/**
 * A performed training session. Optionally linked to the [Routine] it was based on.
 * Session-level [notes] can capture pre-workout state (sleep, energy, supplements).
 */
data class WorkoutSession(
    val id: Long = 0,
    val routineId: Long? = null,
    val title: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val notes: String? = null,
) {
    /** Elapsed duration in milliseconds, or `null` while the session is still open. */
    val durationMillis: Long?
        get() = endedAt?.let { it - startedAt }
}
