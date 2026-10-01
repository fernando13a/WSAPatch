package com.ironmind.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironmind.app.data.local.IronMindDatabase
import com.ironmind.app.data.local.Migrations
import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.SetLogEntity
import com.ironmind.app.data.local.entity.WorkoutSessionEntity
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * End-to-end integration test for a complete workout flow:
 * 1. Create/select an exercise
 * 2. Start a session
 * 3. Log sets for the exercise
 * 4. Finish session
 * 5. Verify data persistence
 */
@RunWith(AndroidJUnit4::class)
class WorkoutFlowIntegrationTest {

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
    fun completeWorkoutSession() = runTest {
        val dao = db.workoutDao()

        // 1. Create an exercise
        val exercise = ExerciseEntity(
            name = "Barbell Bench Press",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            isCustom = false,
            nameEs = "Press de Banca con Barra",
        )
        val exerciseId = dao.upsertExercise(exercise)

        // Verify exercise was created
        val retrievedExercise = dao.getExerciseById(exerciseId)
        assert(retrievedExercise != null)
        assert(retrievedExercise!!.name == "Barbell Bench Press")

        // 2. Start a session
        val session = WorkoutSessionEntity(
            title = "Chest Day",
            startedAt = Instant.now().toEpochMilli(),
        )
        val sessionId = dao.insertSession(session)

        // 3. Log sets. Explicit, distinct performedAt values so DESC ordering below is
        // deterministic — two calls to the default `System.currentTimeMillis()` in a tight loop
        // can tie on a fast emulator, which made the ordering assertions flaky.
        val now = System.currentTimeMillis()
        val set1 = SetLogEntity(
            sessionId = sessionId,
            exerciseId = exerciseId,
            setNumber = 1,
            weightKg = 100.0,
            reps = 8,
            performedAt = now,
        )
        val set2 = SetLogEntity(
            sessionId = sessionId,
            exerciseId = exerciseId,
            setNumber = 2,
            weightKg = 100.0,
            reps = 6,
            performedAt = now + 1_000,
        )

        dao.upsertSetLog(set1)
        dao.upsertSetLog(set2)

        // 4. Finish session
        val endedSession = session.copy(id = sessionId, endedAt = Instant.now().toEpochMilli())
        dao.updateSession(endedSession)

        // 5. Verify data persistence
        val retrievedSession = dao.getSessionById(sessionId)
        assert(retrievedSession != null)
        assert(retrievedSession!!.title == "Chest Day")
        assert(retrievedSession.endedAt != null)

        // Verify sets
        val sets = dao.getRecentSetLogsForExercise(exerciseId, 10)
        assert(sets.size == 2)
        assert(sets[0].weightKg == 100.0)
        assert(sets[0].reps == 6) // Most recent first
        assert(sets[1].reps == 8)
    }

    @Test
    fun multipleExercisesInSession() = runTest {
        val dao = db.workoutDao()

        // Create 2 exercises
        val benchPress = ExerciseEntity(
            name = "Barbell Bench Press",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
        )
        val squat = ExerciseEntity(
            name = "Barbell Squat",
            muscleGroup = MuscleGroup.QUADS,
            equipment = Equipment.BARBELL,
        )

        val benchId = dao.upsertExercise(benchPress)
        val squatId = dao.upsertExercise(squat)

        // Start session
        val session = WorkoutSessionEntity(
            title = "Full Body",
            startedAt = Instant.now().toEpochMilli(),
        )
        val sessionId = dao.insertSession(session)

        // Log sets for both
        dao.upsertSetLog(
            SetLogEntity(
                sessionId = sessionId,
                exerciseId = benchId,
                setNumber = 1,
                weightKg = 100.0,
                reps = 8,
            )
        )
        dao.upsertSetLog(
            SetLogEntity(
                sessionId = sessionId,
                exerciseId = squatId,
                setNumber = 1,
                weightKg = 150.0,
                reps = 5,
            )
        )

        // Verify session contains both exercises
        val benchSets = dao.getRecentSetLogsForExercise(benchId, 10)
        val squatSets = dao.getRecentSetLogsForExercise(squatId, 10)

        assert(benchSets.size == 1)
        assert(squatSets.size == 1)
        assert(benchSets[0].weightKg == 100.0)
        assert(squatSets[0].weightKg == 150.0)
    }
}
