package com.ironmind.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.RoutineExerciseCrossRef
import com.ironmind.app.data.local.entity.RoutineEntity

/**
 * A routine together with all of its exercises, resolved through the
 * [RoutineExerciseCrossRef] junction table.
 */
data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RoutineExerciseCrossRef::class,
            parentColumn = "routineId",
            entityColumn = "exerciseId",
        ),
    )
    val exercises: List<ExerciseEntity>,
)
