package com.ironmind.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ironmind.app.data.local.converter.Converters
import com.ironmind.app.data.local.dao.WorkoutDao
import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.RoutineEntity
import com.ironmind.app.data.local.entity.RoutineExerciseCrossRef
import com.ironmind.app.data.local.entity.SetLogEntity
import com.ironmind.app.data.local.entity.WorkoutSessionEntity

/**
 * The app's single Room database. Fully offline — no network is involved at any point.
 *
 * Schema history is exported to `app/schemas` (configured in build.gradle.kts) so future
 * versions can ship proper migrations and be validated by instrumented tests.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseCrossRef::class,
        WorkoutSessionEntity::class,
        SetLogEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IronMindDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}
