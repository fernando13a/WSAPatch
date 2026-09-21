package com.ironmind.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironmind.app.data.local.IronMindDatabase
import com.ironmind.app.data.local.dao.WorkoutDao
import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.RoutineEntity
import com.ironmind.app.data.local.entity.RoutineExerciseCrossRef
import com.ironmind.app.data.local.entity.SetLogEntity
import com.ironmind.app.data.local.entity.WorkoutSessionEntity
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [WorkoutDao] — run on a device/emulator against a real (in-memory)
 * Room database. Verifies the relations, the type converters, and FK cascade behavior.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var db: IronMindDatabase
    private lateinit var dao: WorkoutDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .build()
        dao = db.workoutDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun routineWithExercises_resolvesLinkedExercisesThroughJunction() = runTest {
        val routineId = dao.upsertRoutine(RoutineEntity(name = "Push Day", split = RoutineSplit.PUSH))
        val benchId = dao.upsertExercise(
            ExerciseEntity(name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
        )
        val ohpId = dao.upsertExercise(
            ExerciseEntity(name = "Overhead Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL),
        )
        dao.upsertRoutineExerciseCrossRef(RoutineExerciseCrossRef(routineId, benchId, position = 0))
        dao.upsertRoutineExerciseCrossRef(RoutineExerciseCrossRef(routineId, ohpId, position = 1))

        val plans = dao.observeRoutinesWithExercises().first()

        assertEquals(1, plans.size)
        assertEquals(RoutineSplit.PUSH, plans.first().routine.split)
        assertEquals(setOf("Bench Press", "Overhead Press"), plans.first().exercises.map { it.name }.toSet())
    }

    @Test
    fun sessionWithSets_returnsAllLoggedSets() = runTest {
        val sessionId = dao.insertSession(WorkoutSessionEntity(startedAt = 1_000L, title = "Leg Day"))
        val squatId = dao.upsertExercise(
            ExerciseEntity(name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL),
        )
        dao.upsertSetLog(SetLogEntity(sessionId = sessionId, exerciseId = squatId, setNumber = 1, weightKg = 100.0, reps = 5))
        dao.upsertSetLog(
            SetLogEntity(
                sessionId = sessionId,
                exerciseId = squatId,
                setNumber = 2,
                weightKg = 100.0,
                reps = 5,
                notes = "Felt strong — 30g ON Gold Standard Whey pre-workout",
            ),
        )

        val detail = dao.observeSessionWithSets(sessionId).first()

        assertEquals("Leg Day", detail?.session?.title)
        assertEquals(2, detail?.sets?.size)
    }

    @Test
    fun getExercisesByMuscleGroupOnce_returnsOnlyThatMuscleGroup() = runTest {
        dao.upsertExercise(
            ExerciseEntity(name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
        )
        dao.upsertExercise(
            ExerciseEntity(name = "Push-up", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BODYWEIGHT),
        )
        dao.upsertExercise(
            ExerciseEntity(name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL),
        )

        val chestExercises = dao.getExercisesByMuscleGroupOnce(MuscleGroup.CHEST)

        assertEquals(setOf("Bench Press", "Push-up"), chestExercises.map { it.name }.toSet())
    }

    @Test
    fun getSetLogsSince_excludesEntriesBeforeTheCutoff() = runTest {
        val sessionId = dao.insertSession(WorkoutSessionEntity(startedAt = 1_000L))
        val squatId = dao.upsertExercise(
            ExerciseEntity(name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL),
        )
        dao.upsertSetLog(
            SetLogEntity(sessionId = sessionId, exerciseId = squatId, setNumber = 1, weightKg = 100.0, reps = 5, performedAt = 1_000L),
        )
        dao.upsertSetLog(
            SetLogEntity(sessionId = sessionId, exerciseId = squatId, setNumber = 2, weightKg = 100.0, reps = 5, performedAt = 5_000L),
        )
        dao.upsertSetLog(
            SetLogEntity(sessionId = sessionId, exerciseId = squatId, setNumber = 3, weightKg = 105.0, reps = 5, performedAt = 9_000L),
        )

        val recent = dao.getSetLogsSince(5_000L)

        assertEquals(setOf(5_000L, 9_000L), recent.map { it.performedAt }.toSet())
    }

    @Test
    fun getSetLogsSince_spansMultipleExercises() = runTest {
        val sessionId = dao.insertSession(WorkoutSessionEntity(startedAt = 1_000L))
        val squatId = dao.upsertExercise(
            ExerciseEntity(name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL),
        )
        val benchId = dao.upsertExercise(
            ExerciseEntity(name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
        )
        dao.upsertSetLog(
            SetLogEntity(sessionId = sessionId, exerciseId = squatId, setNumber = 1, weightKg = 100.0, reps = 5, performedAt = 2_000L),
        )
        dao.upsertSetLog(
            SetLogEntity(sessionId = sessionId, exerciseId = benchId, setNumber = 1, weightKg = 60.0, reps = 8, performedAt = 3_000L),
        )

        val recent = dao.getSetLogsSince(1_000L)

        assertEquals(setOf(squatId, benchId), recent.map { it.exerciseId }.toSet())
    }

    @Test
    fun deletingSession_cascadesToSetLogs() = runTest {
        val sessionId = dao.insertSession(WorkoutSessionEntity(startedAt = 2_000L))
        val curlId = dao.upsertExercise(
            ExerciseEntity(name = "Barbell Curl", muscleGroup = MuscleGroup.BICEPS, equipment = Equipment.BARBELL),
        )
        dao.upsertSetLog(SetLogEntity(sessionId = sessionId, exerciseId = curlId, setNumber = 1, weightKg = 30.0, reps = 10))

        dao.deleteSession(WorkoutSessionEntity(id = sessionId, startedAt = 2_000L))

        assertNull(dao.getLastSetLogForExercise(curlId))
    }
}
