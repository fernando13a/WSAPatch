package com.ironmind.app

import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.RoutineEntity
import com.ironmind.app.data.local.entity.RoutineExerciseCrossRef
import com.ironmind.app.data.local.relation.RoutineWithExercises
import com.ironmind.app.data.mapper.toDomain
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.ExercisePrescription
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutinePlanMappingTest {

    private val routine = RoutineEntity(id = 1, name = "Push", split = RoutineSplit.PUSH)
    private val bench = ExerciseEntity(id = 10, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val ohp = ExerciseEntity(id = 20, name = "Overhead Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL)
    private val raise = ExerciseEntity(id = 30, name = "Lateral Raise", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.DUMBBELL)

    /**
     * The junction relation comes back in insertion order, so a reorder — an UPDATE of
     * `position` — never showed. The plan must follow `position`, not the order rows arrive in.
     */
    @Test
    fun exercisesFollowTheRoutinesPositionNotTheOrderTheyArrive() {
        val relation = RoutineWithExercises(
            routine = routine,
            exercises = listOf(bench, ohp, raise), // as SQLite returns them
            crossRefs = listOf(
                RoutineExerciseCrossRef(1, 10, position = 2),
                RoutineExerciseCrossRef(1, 20, position = 0),
                RoutineExerciseCrossRef(1, 30, position = 1),
            ),
        )

        assertEquals(listOf(20L, 30L, 10L), relation.toDomain().exercises.map { it.id })
    }

    /** Rows saved before positions were used all sit at 0; they keep their order, not shuffle. */
    @Test
    fun equalPositionsKeepTheOrderTheyCameIn() {
        val relation = RoutineWithExercises(
            routine = routine,
            exercises = listOf(bench, ohp, raise),
            crossRefs = listOf(RoutineExerciseCrossRef(1, 10), RoutineExerciseCrossRef(1, 20), RoutineExerciseCrossRef(1, 30)),
        )

        assertEquals(listOf(10L, 20L, 30L), relation.toDomain().exercises.map { it.id })
    }

    @Test
    fun theStoredPrescriptionIsReadBack() {
        val relation = RoutineWithExercises(
            routine = routine,
            exercises = listOf(bench, raise),
            crossRefs = listOf(
                RoutineExerciseCrossRef(1, 10, targetSets = 4, targetReps = 5, targetRestSeconds = 150, targetWeightKg = 82.5),
                RoutineExerciseCrossRef(1, 30, targetSets = 3, targetReps = 12, targetRestSeconds = 60),
            ),
        )

        val plan = relation.toDomain()

        assertEquals(ExercisePrescription(sets = 4, reps = 5, restSeconds = 150, weightKg = 82.5), plan.prescriptions[10L])
        assertNull("no suggestion stays no suggestion", plan.prescriptions.getValue(30L).weightKg)
    }
}
