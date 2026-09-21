package com.ironmind.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironmind.app.core.util.StandardDispatcherProvider
import com.ironmind.app.data.backup.RoomBackupManager
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented round-trip test for [RoomBackupManager] against a real Room database. Exercises the
 * part unit tests can't: the withTransaction restore has to satisfy SQLite's foreign-key
 * constraints (deletes child-first, inserts parent-first) and preserve primary keys so relations
 * still resolve after a restore.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {

    private lateinit var db: IronMindDatabase
    private lateinit var dao: WorkoutDao
    private lateinit var manager: RoomBackupManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java).build()
        dao = db.workoutDao()
        manager = RoomBackupManager(db, dao, StandardDispatcherProvider())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun exportThenImport_restoresAllDataWithRelationsAndWipesTheRest() = runTest {
        // Seed a linked graph: routine -> exercises (junction), session -> sets.
        val routineId = dao.upsertRoutine(RoutineEntity(name = "Push Day", split = RoutineSplit.PUSH))
        val benchId = dao.upsertExercise(
            ExerciseEntity(name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
        )
        val ohpId = dao.upsertExercise(
            ExerciseEntity(name = "Overhead Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL),
        )
        dao.upsertRoutineExerciseCrossRef(RoutineExerciseCrossRef(routineId, benchId, position = 0))
        dao.upsertRoutineExerciseCrossRef(RoutineExerciseCrossRef(routineId, ohpId, position = 1))
        val sessionId = dao.insertSession(WorkoutSessionEntity(routineId = routineId, startedAt = 1_000L, title = "Morning"))
        dao.upsertSetLog(SetLogEntity(sessionId = sessionId, exerciseId = benchId, setNumber = 1, weightKg = 100.0, reps = 5))
        dao.upsertSetLog(SetLogEntity(sessionId = sessionId, exerciseId = benchId, setNumber = 2, weightKg = 102.5, reps = 5))

        val json = manager.export()

        // Mutate the DB so a successful restore is observable: add junk that must be wiped.
        dao.upsertExercise(ExerciseEntity(name = "Junk Exercise", muscleGroup = MuscleGroup.OTHER, equipment = Equipment.OTHER))
        dao.insertSession(WorkoutSessionEntity(startedAt = 9_999L, title = "Junk Session"))

        val result = manager.import(json)

        // Counts reflect the backup, not the mutated state.
        assertEquals(2, result.exercises)
        assertEquals(1, result.routines)
        assertEquals(1, result.sessions)
        assertEquals(2, result.setLogs)

        // Junk is gone; original rows are back with their original ids.
        assertEquals(setOf("Bench Press", "Overhead Press"), dao.getAllExercisesOnce().map { it.name }.toSet())
        assertEquals(1, dao.getAllSessionsOnce().size)

        // Relations still resolve after the restore (FK-preserving).
        val plan = dao.observeRoutineWithExercises(routineId).first()
        assertEquals(setOf(benchId, ohpId), plan?.exercises?.map { it.id }?.toSet())

        val detail = dao.observeSessionWithSets(sessionId).first()
        assertEquals(routineId, detail?.session?.routineId)
        assertEquals(2, detail?.sets?.size)
    }

    @Test
    fun importEmptyBackup_WipesAllData() = runTest {
        // Populate with data
        dao.upsertExercise(ExerciseEntity(name = "Test Exercise", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL))
        dao.insertSession(WorkoutSessionEntity(startedAt = 1_000L, title = "Test Session"))

        // An empty backup, sourced from a separate, genuinely empty database — not `db`, which was
        // just seeded above. Exporting from `manager`/`db` at this point would capture that seeded
        // data instead of an empty snapshot.
        val emptyDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IronMindDatabase::class.java,
        ).build()
        val emptyManager = RoomBackupManager(emptyDb, emptyDb.workoutDao(), StandardDispatcherProvider())
        val emptyJson = emptyManager.export()
        emptyDb.close()

        val result = manager.import(emptyJson)

        // All counts should be zero
        assertEquals(0, result.exercises)
        assertEquals(0, result.routines)
        assertEquals(0, result.sessions)
        assertEquals(0, result.setLogs)

        // Database should be empty
        assertEquals(0, dao.getAllExercisesOnce().size)
        assertEquals(0, dao.getAllSessionsOnce().size)
    }
}
