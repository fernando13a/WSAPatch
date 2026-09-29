package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.util.RoutineAssembler
import com.ironmind.app.domain.util.RoutinePrescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineAssemblerTest {

    private fun ex(id: Long, name: String, group: MuscleGroup) =
        Exercise(id = id, name = name, muscleGroup = group, equipment = Equipment.BARBELL)

    private val bench = ex(1, "Barbell Bench Press", MuscleGroup.CHEST)
    private val incline = ex(2, "Incline Dumbbell Press", MuscleGroup.CHEST)
    private val fly = ex(3, "Cable Fly", MuscleGroup.CHEST)
    private val pecDeck = ex(4, "Pec Deck", MuscleGroup.CHEST)
    private val ohp = ex(5, "Overhead Press", MuscleGroup.SHOULDERS)
    private val lateral = ex(6, "Lateral Raise", MuscleGroup.SHOULDERS)
    private val pushdown = ex(7, "Triceps Pushdown", MuscleGroup.TRICEPS)
    private val dips = ex(8, "Dips", MuscleGroup.TRICEPS)

    /** Round-robin order, as RoutineCandidateSelector produces it. */
    private val push = listOf(bench, ohp, pushdown, incline, lateral, dips, fly, pecDeck)

    private fun ids(rows: List<com.ironmind.app.domain.model.RoutineDraftExercise>) = rows.map { it.exerciseId }

    /** The fallback: no model choice at all still yields a full, balanced session. */
    @Test
    fun withNoChoiceItBuildsAFullRoutineFromRules() {
        val rows = RoutineAssembler.assemble(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, push)

        assertEquals(RoutineAssembler.TARGET_EXERCISES, rows.size)
        val groups = rows.map { row -> push.first { it.id == row.exerciseId }.muscleGroup }.toSet()
        assertEquals(setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS), groups)
    }

    @Test
    fun compoundsComeFirstAndKeepTheirRelativeOrder() {
        val chosen = listOf(lateral, bench, pushdown, ohp)

        val rows = RoutineAssembler.assemble(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, push, chosen)

        // bench and ohp are compounds (in the model's order), then the isolations in its order.
        assertEquals(listOf(1L, 5L, 6L, 7L), ids(rows).take(4))
    }

    /** A model picking only chest for a push day gets shoulders and triceps added. */
    @Test
    fun aMissingMuscleGroupIsAdded() {
        val chosen = listOf(bench, incline, fly, pecDeck)

        val rows = RoutineAssembler.assemble(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, push, chosen)
        val groups = rows.map { row -> push.first { it.id == row.exerciseId }.muscleGroup }

        assertTrue(MuscleGroup.SHOULDERS in groups)
        assertTrue(MuscleGroup.TRICEPS in groups)
    }

    @Test
    fun oneMuscleGroupCannotTakeOverTheSession() {
        val chosen = listOf(bench, incline, fly, pecDeck)

        val rows = RoutineAssembler.assemble(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, push, chosen)
        val chest = rows.count { row -> push.first { it.id == row.exerciseId }.muscleGroup == MuscleGroup.CHEST }

        assertEquals(3, chest)
    }

    /** A shoulders-only day must not be cut to 3 exercises by the per-group cap. */
    @Test
    fun aSingleGroupSplitIsNotStarvedByTheCap() {
        val shoulders = (1L..8L).map { ex(it, "Shoulder Press $it", MuscleGroup.SHOULDERS) }

        val rows = RoutineAssembler.assemble(RoutineSplit.SHOULDERS, TrainingGoal.HYPERTROPHY, shoulders)

        assertEquals(RoutineAssembler.TARGET_EXERCISES, rows.size)
    }

    @Test
    fun aShortChoiceIsToppedUpButALongOneIsCapped() {
        val topped = RoutineAssembler.assemble(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, push, listOf(bench))
        assertTrue(topped.size >= RoutineAssembler.MIN_EXERCISES)

        val many = (1L..20L).map { ex(it, "Exercise $it", MuscleGroup.entries[(it % 3).toInt()]) }
        val capped = RoutineAssembler.assemble(RoutineSplit.CUSTOM, TrainingGoal.HYPERTROPHY, many, many)
        assertTrue(capped.size <= RoutineAssembler.MAX_EXERCISES)
    }

    @Test
    fun picksOutsideTheShortlistAndRepeatsAreIgnored() {
        val stranger = ex(99, "Back Squat", MuscleGroup.QUADS)

        val rows = RoutineAssembler.assemble(
            RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, push, listOf(stranger, bench, bench, ohp, pushdown, dips),
        )

        assertTrue(99L !in ids(rows))
        assertEquals(ids(rows).distinct(), ids(rows))
    }

    /** The numbers come from the goal, not from the model. */
    @Test
    fun prescriptionsFollowTheGoalAndTheMovement() {
        val rows = RoutineAssembler.assemble(
            RoutineSplit.PUSH, TrainingGoal.STRENGTH, push, listOf(bench, ohp, lateral, pushdown),
        )
        val benchRow = rows.first { it.exerciseId == bench.id }
        val lateralRow = rows.first { it.exerciseId == lateral.id }

        val compound = RoutinePrescription.forExercise(TrainingGoal.STRENGTH, compound = true)
        val isolation = RoutinePrescription.forExercise(TrainingGoal.STRENGTH, compound = false)
        assertEquals(compound, RoutinePrescription.Prescription(benchRow.sets, benchRow.reps, benchRow.restSeconds))
        assertEquals(isolation, RoutinePrescription.Prescription(lateralRow.sets, lateralRow.reps, lateralRow.restSeconds))
    }

    @Test
    fun compoundsGetHeavierWorkAndMoreRestThanIsolation() {
        TrainingGoal.entries.forEach { goal ->
            val compound = RoutinePrescription.forExercise(goal, compound = true)
            val isolation = RoutinePrescription.forExercise(goal, compound = false)
            assertTrue("$goal reps", compound.reps <= isolation.reps)
            assertTrue("$goal rest", compound.restSeconds >= isolation.restSeconds)
        }
    }

    @Test
    fun theGoalsPrescribeDifferentRepRanges() {
        val strength = RoutinePrescription.forExercise(TrainingGoal.STRENGTH, compound = true)
        val hypertrophy = RoutinePrescription.forExercise(TrainingGoal.HYPERTROPHY, compound = true)
        val endurance = RoutinePrescription.forExercise(TrainingGoal.ENDURANCE, compound = true)

        assertTrue(strength.reps in 3..6)
        assertTrue(hypertrophy.reps in 6..12)
        assertTrue(endurance.reps in 12..20)
    }

    @Test
    fun noCandidatesMeansNoRoutine() {
        assertTrue(RoutineAssembler.assemble(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, emptyList(), push).isEmpty())
    }
}
