package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import kotlin.math.floor

/**
 * What the athlete's own log says, as far as building a routine is concerned — derived from one
 * [com.ironmind.app.domain.repository.WorkoutRepository.getRecentActivity] query:
 *
 *  - [hoursSinceTrained]: muscle groups worked inside the recovery window, so a push day the
 *    morning after another one doesn't pile the same muscles on again;
 *  - [setsByExercise]: working sets per exercise, so the movements they actually do come first
 *    instead of whichever catalog entry happens to sort highest;
 *  - [lastTopSet]: the heaviest set of the most recent session per exercise, the starting point
 *    for a suggested weight.
 *
 * Warm-ups and sets not completed don't count towards any of it.
 */
data class TrainingHistory(
    val hoursSinceTrained: Map<MuscleGroup, Long> = emptyMap(),
    val setsByExercise: Map<Long, Int> = emptyMap(),
    val lastTopSet: Map<Long, SetLog> = emptyMap(),
) {
    fun familiarity(exerciseId: Long): Int = setsByExercise[exerciseId] ?: 0

    companion object {
        val EMPTY = TrainingHistory()

        /** Shorter than the usual 48-72 h recovery guidance on purpose: this reduces, never bans. */
        const val RECOVERY_HOURS = 48L

        private const val HOUR_MS = 3_600_000L

        fun from(activity: List<SetLog>, exercisesById: Map<Long, Exercise>, now: Long): TrainingHistory {
            val working = activity.filter { it.isCompleted && !it.isWarmup }

            val hoursSince = buildMap<MuscleGroup, Long> {
                working.forEach { set ->
                    val group = exercisesById[set.exerciseId]?.muscleGroup ?: return@forEach
                    val hours = (now - set.performedAt).coerceAtLeast(0) / HOUR_MS
                    if (hours < RECOVERY_HOURS) put(group, minOf(hours, get(group) ?: hours))
                }
            }

            val topSets = working
                .groupBy { it.exerciseId }
                .mapValues { (_, sets) ->
                    val lastSession = sets.maxBy { it.performedAt }.sessionId
                    sets.filter { it.sessionId == lastSession }.maxBy { StartingWeight.estimatedOneRepMax(it.weightKg, it.reps) }
                }

            return TrainingHistory(
                hoursSinceTrained = hoursSince,
                setsByExercise = working.groupingBy { it.exerciseId }.eachCount(),
                lastTopSet = topSets,
            )
        }
    }
}

/**
 * A starting weight for a new rep target, from the last time the exercise was done.
 *
 * Epley: e1RM = w × (1 + reps / 30), then solved back for the target reps, and rounded *down* — a
 * suggestion that errs light is one the athlete adds to, not one that fails a set. To the nearest
 * 2.5 kg, a pair of the smallest plates; below [LIGHT_LOAD_KG], where that step is a large share of
 * the load and no barbell is involved, to the nearest 0.5 kg. Shown on the draft (adapting as the
 * athlete edits the rep count), saved as the routine's target, and used to fill in the session.
 */
object StartingWeight {

    private const val PLATE_STEP_KG = 2.5
    private const val LIGHT_LOAD_KG = 10.0
    private const val LIGHT_STEP_KG = 0.5

    fun estimatedOneRepMax(weightKg: Double, reps: Int): Double =
        if (reps <= 1) weightKg else weightKg * (1 + reps / 30.0)

    /**
     * Null for bodyweight work (no load logged), where a weight suggestion means nothing — and
     * never 0: a 2 kg raise rounded to the plate step came out as "Sugerido: 0 kg".
     */
    fun suggestKg(lastWeightKg: Double, lastReps: Int, targetReps: Int): Double? {
        if (lastWeightKg <= 0 || lastReps <= 0 || targetReps <= 0) return null
        val raw = estimatedOneRepMax(lastWeightKg, lastReps) / (1 + targetReps / 30.0)
        val step = if (raw < LIGHT_LOAD_KG) LIGHT_STEP_KG else PLATE_STEP_KG
        return (floor(raw / step) * step).takeIf { it > 0 }
    }
}
