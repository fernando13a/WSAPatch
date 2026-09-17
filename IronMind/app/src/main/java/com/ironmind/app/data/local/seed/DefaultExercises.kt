package com.ironmind.app.data.local.seed

import com.ironmind.app.data.local.dao.WorkoutDao
import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.RoutineEntity
import com.ironmind.app.data.local.entity.RoutineExerciseCrossRef
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit

/**
 * Ships a starter catalog of common exercises and a few Push/Pull/Legs routines so the app is
 * useful on first launch while remaining 100% offline. Seeded rows are marked `isCustom = false`.
 */
object DefaultExercises {

    /** Inserts the starter catalog and routines if the exercises table is empty. */
    suspend fun seed(dao: WorkoutDao) {
        if (dao.countExercises() > 0) return
        dao.upsertExercises(catalog)
        seedRoutines(dao)
    }

    private suspend fun seedRoutines(dao: WorkoutDao) {
        val idByName = dao.getAllExercisesOnce().associate { it.name to it.id }

        suspend fun routine(name: String, split: RoutineSplit, exercises: List<String>) {
            val routineId = dao.upsertRoutine(RoutineEntity(name = name, split = split))
            exercises.forEachIndexed { index, exName ->
                idByName[exName]?.let { exId ->
                    dao.upsertRoutineExerciseCrossRef(
                        RoutineExerciseCrossRef(routineId = routineId, exerciseId = exId, position = index),
                    )
                }
            }
        }

        routine(
            "Push", RoutineSplit.PUSH,
            listOf("Barbell Bench Press", "Incline Dumbbell Press", "Overhead Press", "Lateral Raise", "Triceps Pushdown"),
        )
        routine(
            "Pull", RoutineSplit.PULL,
            listOf("Deadlift", "Pull-Up", "Bent-Over Barbell Row", "Lat Pulldown", "Barbell Curl"),
        )
        routine(
            "Legs", RoutineSplit.LEGS,
            listOf("Back Squat", "Leg Press", "Romanian Deadlift", "Leg Curl", "Standing Calf Raise"),
        )
    }

    private fun ex(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
    ) = ExerciseEntity(
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        isCustom = false,
    )

    private val catalog: List<ExerciseEntity> = listOf(
        // Push
        ex("Barbell Bench Press", MuscleGroup.CHEST, Equipment.BARBELL),
        ex("Incline Dumbbell Press", MuscleGroup.CHEST, Equipment.DUMBBELL),
        ex("Cable Fly", MuscleGroup.CHEST, Equipment.CABLE),
        ex("Overhead Press", MuscleGroup.SHOULDERS, Equipment.BARBELL),
        ex("Lateral Raise", MuscleGroup.SHOULDERS, Equipment.DUMBBELL),
        ex("Triceps Pushdown", MuscleGroup.TRICEPS, Equipment.CABLE),
        ex("Overhead Triceps Extension", MuscleGroup.TRICEPS, Equipment.DUMBBELL),
        // Pull
        ex("Deadlift", MuscleGroup.BACK, Equipment.BARBELL),
        ex("Pull-Up", MuscleGroup.BACK, Equipment.BODYWEIGHT),
        ex("Bent-Over Barbell Row", MuscleGroup.BACK, Equipment.BARBELL),
        ex("Lat Pulldown", MuscleGroup.BACK, Equipment.CABLE),
        ex("Face Pull", MuscleGroup.SHOULDERS, Equipment.CABLE),
        ex("Barbell Curl", MuscleGroup.BICEPS, Equipment.BARBELL),
        ex("Hammer Curl", MuscleGroup.BICEPS, Equipment.DUMBBELL),
        // Legs
        ex("Back Squat", MuscleGroup.QUADS, Equipment.BARBELL),
        ex("Leg Press", MuscleGroup.QUADS, Equipment.MACHINE),
        ex("Romanian Deadlift", MuscleGroup.HAMSTRINGS, Equipment.BARBELL),
        ex("Leg Curl", MuscleGroup.HAMSTRINGS, Equipment.MACHINE),
        ex("Hip Thrust", MuscleGroup.GLUTES, Equipment.BARBELL),
        ex("Standing Calf Raise", MuscleGroup.CALVES, Equipment.MACHINE),
        // Core
        ex("Hanging Leg Raise", MuscleGroup.ABS, Equipment.BODYWEIGHT),
        ex("Cable Crunch", MuscleGroup.ABS, Equipment.CABLE),
        ex("Plank", MuscleGroup.ABS, Equipment.BODYWEIGHT),
    )
}
