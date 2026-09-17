package com.ironmind.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table wiring the many-to-many relationship between routines and exercises.
 * It also stores the per-routine prescription (target sets / reps / rest) and the
 * ordering of exercises within a routine.
 */
@Entity(
    tableName = "routine_exercise_cross_ref",
    primaryKeys = ["routineId", "exerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExerciseCrossRef(
    val routineId: Long,
    val exerciseId: Long,
    val position: Int = 0,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val targetRestSeconds: Int = 90,
)
