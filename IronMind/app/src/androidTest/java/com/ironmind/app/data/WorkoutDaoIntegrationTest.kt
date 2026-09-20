package com.ironmind.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironmind.app.data.local.IronMindDatabase
import com.ironmind.app.data.local.Migrations
import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for WorkoutDao verifying database operations.
 * Tests migrations, CRUD operations, and data consistency.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoIntegrationTest {

    private lateinit var db: IronMindDatabase

    @Before
    fun setupDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun insertAndRetrieveExercise() = runTest {
        val exercise = ExerciseEntity(
            id = 1,
            name = "Barbell Bench Press",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            isCustom = false,
        )

        val dao = db.workoutDao()
        dao.upsertExercise(exercise)

        val retrieved = dao.getExerciseById(1)
        assert(retrieved?.name == "Barbell Bench Press")
        assert(retrieved?.muscleGroup == MuscleGroup.CHEST)
    }

    @Test
    fun migratesNameEsField() = runTest {
        val exercise = ExerciseEntity(
            id = 1,
            name = "Dumbbell Curl",
            muscleGroup = MuscleGroup.BICEPS,
            equipment = Equipment.DUMBBELL,
            isCustom = false,
            nameEs = "Curl de Mancuerna",
        )

        val dao = db.workoutDao()
        dao.upsertExercise(exercise)
        dao.updateExerciseNameEs(1, "Curl de Mancuerna")

        val retrieved = dao.getExerciseById(1)
        assert(retrieved?.nameEs == "Curl de Mancuerna")
    }

    @Test
    fun searchExercisesByName() = runTest {
        val dao = db.workoutDao()
        val exercises = listOf(
            ExerciseEntity(
                name = "Barbell Squat",
                muscleGroup = MuscleGroup.QUADS,
                equipment = Equipment.BARBELL,
            ),
            ExerciseEntity(
                name = "Leg Press",
                muscleGroup = MuscleGroup.QUADS,
                equipment = Equipment.MACHINE,
            ),
            ExerciseEntity(
                name = "Leg Curl",
                muscleGroup = MuscleGroup.HAMSTRINGS,
                equipment = Equipment.MACHINE,
            ),
        )

        exercises.forEach { dao.upsertExercise(it) }

        val results = dao.searchExercises("Leg").first()
        assert(results.size == 2)
        assert(results.any { it.name == "Leg Press" })
        assert(results.any { it.name == "Leg Curl" })
    }

    @Test
    fun countExercises() = runTest {
        val dao = db.workoutDao()
        val initialCount = dao.countExercises()

        val exercise1 = ExerciseEntity(
            name = "Bench Press",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
        )
        val exercise2 = ExerciseEntity(
            name = "Squat",
            muscleGroup = MuscleGroup.QUADS,
            equipment = Equipment.BARBELL,
        )

        dao.upsertExercise(exercise1)
        dao.upsertExercise(exercise2)

        val finalCount = dao.countExercises()
        assert(finalCount == initialCount + 2)
    }

    @Test
    fun deletesExercise() = runTest {
        val dao = db.workoutDao()
        val exercise = ExerciseEntity(
            id = 1,
            name = "Test Exercise",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.DUMBBELL,
        )

        dao.upsertExercise(exercise)
        var retrieved = dao.getExerciseById(1)
        assert(retrieved != null)

        dao.deleteExercise(exercise)
        retrieved = dao.getExerciseById(1)
        assert(retrieved == null)
    }
}
