package com.ironmind.app.domain.model

/**
 * A [WorkoutSession] together with every set logged during it. Domain counterpart of
 * the `SessionWithSets` Room relation.
 */
data class SessionDetail(
    val session: WorkoutSession,
    val sets: List<SetLog>,
) {
    /** Total training volume across all completed working sets. */
    val totalVolume: Double
        get() = sets.filter { it.isCompleted && !it.isWarmup }.sumOf { it.volume }

    /** Number of completed working sets (warm-ups excluded). */
    val workingSetCount: Int
        get() = sets.count { it.isCompleted && !it.isWarmup }
}
