package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.RoutineDraftExercise
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import kotlin.math.ceil

/**
 * Turns a choice of exercises into a finished day: balanced, ordered, and prescribed.
 *
 * This is the half of routine generation that doesn't need a language model, and doing it here is
 * what makes the other half workable on a 1B one. The model is asked only which candidates to use
 * and in what order; sets, reps and rest follow directly from the goal, and asking a small model
 * to also invent four numbers per row is what made its answers unreadable. Kotlin then fixes what
 * the model tends to get wrong: a muscle group left out, five chest movements in a row, isolation
 * work before the heavy compounds.
 *
 * Called with no choice at all it builds the whole routine from the candidates, which is the
 * fallback when the model is missing, fails, or answers with nothing usable — so generation never
 * ends on an error the athlete can't do anything about.
 */
object RoutineAssembler {

    /** Fewer than this reads as a warm-up, not a session; the routine is topped up. */
    const val MIN_EXERCISES = 4

    /** How many a rule-built routine gets, and what a too-short choice is topped up to. */
    const val TARGET_EXERCISES = 6

    /** Past this the session runs long for most people; extra picks are dropped. */
    const val MAX_EXERCISES = 8

    /**
     * @param candidates the shortlist, in [RoutineCandidateSelector]'s round-robin order — also the
     *   order used to fill gaps, so filling spreads across muscle groups too.
     * @param chosen the model's picks in its order; empty builds the routine from rules alone.
     *   Anything not in [candidates] is ignored.
     */
    fun assemble(
        split: RoutineSplit,
        goal: TrainingGoal,
        candidates: List<Exercise>,
        chosen: List<Exercise> = emptyList(),
    ): List<RoutineDraftExercise> {
        if (candidates.isEmpty()) return emptyList()
        val candidateIds = candidates.mapTo(HashSet()) { it.id }
        val groupsOnOffer = candidates.map { it.muscleGroup }.distinct()
        val splitGroups = split.muscleGroups()

        // A single-group split (shoulders, core) must not be starved by the per-group cap.
        val perGroupCap = maxOf(3, ceil(TARGET_EXERCISES.toDouble() / groupsOnOffer.size).toInt())
        val picked = mutableListOf<Exercise>()
        fun fits(exercise: Exercise) = picked.size < MAX_EXERCISES &&
            picked.none { it.id == exercise.id } &&
            picked.count { it.muscleGroup == exercise.muscleGroup } < perGroupCap

        chosen.filter { it.id in candidateIds }.forEach { if (fits(it)) picked += it }

        // Cover every muscle group the split trains that the shortlist can offer. Skipped when
        // there are more such groups than a session holds (a custom split spans the catalog).
        val groupsToCover = groupsOnOffer.filter { it in splitGroups }
        if (groupsToCover.size <= MAX_EXERCISES) {
            groupsToCover.filter { group -> picked.none { it.muscleGroup == group } }.forEach { group ->
                val forGroup = candidates.filter { it.muscleGroup == group && fits(it) }
                (forGroup.firstOrNull { it.isCompound() } ?: forGroup.firstOrNull())?.let { picked += it }
            }
        }

        if (picked.size < MIN_EXERCISES) {
            candidates.forEach { if (picked.size < TARGET_EXERCISES && fits(it)) picked += it }
        }

        // Compounds first, while the athlete is fresh. Stable, so the model's own order — or the
        // shortlist's — survives within each half.
        val ordered = picked.sortedBy { if (it.isCompound()) 0 else 1 }
        return ordered.map { exercise ->
            val prescription = RoutinePrescription.forExercise(goal, compound = exercise.isCompound())
            RoutineDraftExercise(
                exerciseId = exercise.id,
                sets = prescription.sets,
                reps = prescription.reps,
                restSeconds = prescription.restSeconds,
            )
        }
    }
}

/**
 * The default sets, reps and rest for a goal. Deterministic on purpose: these follow from the goal
 * the athlete picked, not from anything a model needs to decide, and they land in an editable draft.
 * Compounds get more rest and, for strength and hypertrophy, heavier (lower-rep) work.
 */
object RoutinePrescription {

    data class Prescription(val sets: Int, val reps: Int, val restSeconds: Int)

    fun forExercise(goal: TrainingGoal, compound: Boolean): Prescription = when (goal) {
        TrainingGoal.STRENGTH ->
            if (compound) Prescription(sets = 4, reps = 5, restSeconds = 150) else Prescription(3, 8, 90)
        TrainingGoal.HYPERTROPHY ->
            if (compound) Prescription(sets = 4, reps = 8, restSeconds = 90) else Prescription(3, 12, 60)
        TrainingGoal.ENDURANCE ->
            if (compound) Prescription(sets = 3, reps = 15, restSeconds = 45) else Prescription(3, 20, 30)
    }
}
