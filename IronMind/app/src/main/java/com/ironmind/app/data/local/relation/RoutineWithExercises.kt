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
 *
 * [crossRefs] carries the junction rows themselves, because the exercise relation alone loses
 * everything stored on them: a junction relation comes back in whatever order SQLite reads it —
 * insertion order, in practice — so a reorder (an UPDATE of `position`) never showed, and the
 * per-routine sets, reps, rest and weight were written and never read.
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
    @Relation(parentColumn = "id", entityColumn = "routineId")
    val crossRefs: List<RoutineExerciseCrossRef> = emptyList(),
)
