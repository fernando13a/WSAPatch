package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.RoutineDraftExercise
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Turns a choice of exercises into a finished day: balanced, ordered, prescribed, and sized to the
 * time the athlete has.
 *
 * This is the half of routine generation that doesn't need a language model, and doing it here is
 * what makes the other half workable on a 1B one. The model is asked only which candidates to use
 * and in what order; sets, reps and rest follow directly from the goal, and asking a small model
 * to also invent four numbers per row is what made its answers unreadable. Kotlin then fixes what
 * the model tends to get wrong: a muscle group left out, five chest movements in a row, isolation
 * work before the heavy compounds, a session that runs past the time available.
 *
 * Called with no choice at all it builds the whole routine from the candidates, which is the
 * fallback when the model is missing, fails, or answers with nothing usable — so generation never
 * ends on an error the athlete can't do anything about.
 */
object RoutineAssembler {

    /** Fewer than this reads as a warm-up, not a session; the routine is topped up. */
    const val MIN_EXERCISES = 4

    /** A short time budget may go below [MIN_EXERCISES], but never below this. */
    const val MIN_EXERCISES_WHEN_SHORT_ON_TIME = 3

    /** How many a rule-built routine gets when there's no time budget. */
    const val TARGET_EXERCISES = 6

    /** Past this the session runs long for most people; extra picks are dropped. */
    const val MAX_EXERCISES = 8

    /**
     * Muscles trained within [TrainingHistory.RECOVERY_HOURS] get this many exercises at most.
     * One, not zero: the athlete asked for this split, and a single movement keeps the pattern
     * without adding meaningful fatigue — the draft says why the rest are missing.
     */
    const val RECENTLY_TRAINED_CAP = 1

    /**
     * @param candidates the shortlist, in [RoutineCandidateSelector]'s round-robin order — also the
     *   order used to fill gaps, so filling spreads across muscle groups too.
     * @param chosen the model's picks in its order; empty builds the routine from rules alone.
     *   Anything not in [candidates] is ignored.
     * @param timeBudgetMinutes the whole session including warm-up; null sizes by count instead.
     * @param fillGaps false keeps [chosen] as the whole routine — no muscle added for coverage,
     *   nothing topped up. For an athlete's explicit "make it shorter" or "no shoulders", where
     *   putting a shoulder exercise back in would be overruling them. Caps and ordering still apply.
     */
    fun assemble(
        split: RoutineSplit,
        goal: TrainingGoal,
        candidates: List<Exercise>,
        chosen: List<Exercise> = emptyList(),
        history: TrainingHistory = TrainingHistory.EMPTY,
        timeBudgetMinutes: Int? = null,
        fillGaps: Boolean = true,
    ): List<RoutineDraftExercise> {
        if (candidates.isEmpty()) return emptyList()
        val candidateIds = candidates.mapTo(HashSet()) { it.id }
        val groupsOnOffer = candidates.map { it.muscleGroup }.distinct()
        val splitGroups = split.muscleGroups()

        // A single-group split (shoulders, core) must not be starved by the per-group cap.
        val perGroupCap = maxOf(3, ceil(TARGET_EXERCISES.toDouble() / groupsOnOffer.size).toInt())
        fun capFor(exercise: Exercise) =
            if (exercise.muscleGroup in history.hoursSinceTrained) RECENTLY_TRAINED_CAP else perGroupCap

        val picked = mutableListOf<Exercise>()
        fun fits(exercise: Exercise) = picked.size < MAX_EXERCISES &&
            picked.none { it.id == exercise.id } &&
            picked.count { it.muscleGroup == exercise.muscleGroup } < capFor(exercise)

        chosen.filter { it.id in candidateIds }.forEach { if (fits(it)) picked += it }

        // Cover every muscle group the split trains that the shortlist can offer. Skipped when
        // there are more such groups than a session holds (a custom split spans the catalog).
        val groupsToCover = groupsOnOffer.filter { it in splitGroups }
        if (fillGaps && groupsToCover.size <= MAX_EXERCISES) {
            groupsToCover.filter { group -> picked.none { it.muscleGroup == group } }.forEach { group ->
                val forGroup = candidates.filter { it.muscleGroup == group && fits(it) }
                (forGroup.firstOrNull { it.isCompound() } ?: forGroup.firstOrNull())?.let { picked += it }
            }
        }

        when {
            // Nothing added and nothing trimmed for time: the athlete said what they want.
            !fillGaps -> Unit
            timeBudgetMinutes == null -> if (picked.size < MIN_EXERCISES) {
                candidates.forEach { if (picked.size < TARGET_EXERCISES && fits(it)) picked += it }
            }
            else -> {
                // With time to spare, use it; then trim whatever doesn't fit.
                candidates.forEach { candidate ->
                    if (fits(candidate) && minutesFor(picked + candidate, goal) <= timeBudgetMinutes) picked += candidate
                }
                while (picked.size > MIN_EXERCISES_WHEN_SHORT_ON_TIME && minutesFor(picked, goal) > timeBudgetMinutes) {
                    picked.remove(leastNeeded(picked))
                }
            }
        }

        // Compounds first, while the athlete is fresh. Stable, so the model's own order — or the
        // shortlist's — survives within each half.
        val rows = picked.sortedBy { if (it.isCompound()) 0 else 1 }.map { exercise ->
            val prescription = RoutinePrescription.forExercise(goal, compound = exercise.isCompound())
            RoutineDraftExercise(
                exerciseId = exercise.id,
                sets = prescription.sets,
                reps = prescription.reps,
                restSeconds = prescription.restSeconds,
            )
        }
        return if (timeBudgetMinutes == null || !fillGaps) rows else fitSetsToBudget(rows, timeBudgetMinutes)
    }

    /** Below this a movement stops being worth the setup; the budget gives way instead. */
    private const val MIN_SETS_WHEN_SHORT_ON_TIME = 2

    /**
     * Still over time with nothing left to drop — a 30-minute push day needs one movement for each
     * of three muscles, and three compounds at four sets run ~35 minutes — so take sets off,
     * one at a time from whichever row has most, rather than leave a muscle out.
     */
    private fun fitSetsToBudget(rows: List<RoutineDraftExercise>, budget: Int): List<RoutineDraftExercise> {
        val fitted = rows.toMutableList()
        while (RoutinePrescription.sessionMinutes(fitted) > budget) {
            val index = fitted.indices
                .filter { fitted[it].sets > MIN_SETS_WHEN_SHORT_ON_TIME }
                .maxByOrNull { fitted[it].sets }
                ?: break
            fitted[index] = fitted[index].copy(sets = fitted[index].sets - 1)
        }
        return fitted
    }

    /**
     * The exercise to drop first when over time: the latest-added one whose muscle group still has
     * another exercise, so trimming shortens the session before it leaves a muscle out.
     */
    private fun leastNeeded(picked: List<Exercise>): Exercise =
        picked.lastOrNull { ex -> picked.count { it.muscleGroup == ex.muscleGroup } > 1 } ?: picked.last()

    private fun minutesFor(exercises: List<Exercise>, goal: TrainingGoal): Double =
        RoutinePrescription.WARM_UP_MINUTES + exercises.sumOf {
            RoutinePrescription.minutesFor(RoutinePrescription.forExercise(goal, it.isCompound()))
        }
}

/**
 * The default sets, reps and rest for a goal. Deterministic on purpose: these follow from the goal
 * the athlete picked, not from anything a model needs to decide, and they land in an editable draft.
 * Compounds get more rest and, for strength and hypertrophy, heavier (lower-rep) work.
 */
object RoutinePrescription {

    data class Prescription(val sets: Int, val reps: Int, val restSeconds: Int)

    /** Time under the bar per set — a typical 8-12 rep set at a controlled tempo. */
    const val WORK_SECONDS_PER_SET = 40

    /** Loading the bar, adjusting the bench, walking to the next station. */
    const val SETUP_MINUTES_PER_EXERCISE = 1.5

    const val WARM_UP_MINUTES = 5.0

    fun forExercise(goal: TrainingGoal, compound: Boolean): Prescription = when (goal) {
        TrainingGoal.STRENGTH ->
            if (compound) Prescription(sets = 4, reps = 5, restSeconds = 150) else Prescription(3, 8, 90)
        TrainingGoal.HYPERTROPHY ->
            if (compound) Prescription(sets = 4, reps = 8, restSeconds = 90) else Prescription(3, 12, 60)
        TrainingGoal.ENDURANCE ->
            if (compound) Prescription(sets = 3, reps = 15, restSeconds = 45) else Prescription(3, 20, 30)
    }

    fun minutesFor(prescription: Prescription): Double =
        minutesFor(prescription.sets, prescription.restSeconds)

    fun minutesFor(sets: Int, restSeconds: Int): Double =
        sets * (WORK_SECONDS_PER_SET + restSeconds) / 60.0 + SETUP_MINUTES_PER_EXERCISE

    /**
     * Whole-session estimate for rows as the athlete has edited them, warm-up included — so the
     * figure on the draft moves when they change a set count or a rest.
     */
    fun estimatedSessionMinutes(rows: List<RoutineDraftExercise>): Int = sessionMinutes(rows).roundToInt()

    fun sessionMinutes(rows: List<RoutineDraftExercise>): Double =
        WARM_UP_MINUTES + rows.sumOf { minutesFor(it.sets, it.restSeconds) }

    /**
     * How many exercises a [timeBudgetMinutes] session holds for [goal] — told to the model so it
     * picks about the right number, since the assembler would otherwise have to trim or pad.
     */
    fun exerciseCountFor(goal: TrainingGoal, timeBudgetMinutes: Int): Int {
        val average = (minutesFor(forExercise(goal, true)) + minutesFor(forExercise(goal, false))) / 2
        val count = ((timeBudgetMinutes - WARM_UP_MINUTES) / average).toInt()
        return count.coerceIn(RoutineAssembler.MIN_EXERCISES_WHEN_SHORT_ON_TIME, RoutineAssembler.MAX_EXERCISES)
    }
}
