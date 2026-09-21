package com.ironmind.app

import com.ironmind.app.data.backup.BackupSnapshot
import com.ironmind.app.data.backup.toDto
import com.ironmind.app.data.backup.toEntity
import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.RoutineEntity
import com.ironmind.app.data.local.entity.RoutineExerciseCrossRef
import com.ironmind.app.data.local.entity.SetLogEntity
import com.ironmind.app.data.local.entity.WorkoutSessionEntity
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSnapshotTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Every nullable column carries a value here on purpose: a field the DTO forgets is invisible
    // to a round-trip test whose fixture left it null on both sides — which is exactly how nameEs
    // went missing from backups unnoticed.
    private val exercise = ExerciseEntity(
        id = 7,
        name = "Bench Press",
        muscleGroup = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL,
        description = "Flat barbell press",
        isCustom = false,
        instructions = "1) Lie down. 2) Press.",
        imagePath = "/data/user/0/com.ironmind.app/files/bench.jpg",
        imageUrl = "file:///android_asset/exercise_images/barbell_bench_press.jpg",
        nameEs = "Press de Banca",
    )
    private val routine = RoutineEntity(id = 3, name = "Push", split = RoutineSplit.PUSH, position = 1, createdAt = 1000L)
    private val crossRef = RoutineExerciseCrossRef(routineId = 3, exerciseId = 7, position = 0, targetSets = 4, targetReps = 8, targetRestSeconds = 120)
    private val session = WorkoutSessionEntity(id = 5, routineId = 3, title = "Morning", startedAt = 2000L, endedAt = 3000L, notes = "felt strong")
    private val setLog = SetLogEntity(
        id = 11,
        sessionId = 5,
        exerciseId = 7,
        setNumber = 2,
        weightKg = 102.5,
        reps = 8,
        rpe = 8.5f,
        isWarmup = false,
        isCompleted = true,
        restSeconds = 120,
        notes = "ON Gold Standard Whey",
        performedAt = 2500L,
    )

    @Test
    fun snapshotRoundTripsThroughJsonPreservingAllFields() {
        val original = BackupSnapshot(
            exportedAt = 42L,
            exercises = listOf(exercise.toDto()),
            routines = listOf(routine.toDto()),
            routineExercises = listOf(crossRef.toDto()),
            sessions = listOf(session.toDto()),
            setLogs = listOf(setLog.toDto()),
        )

        val encoded = json.encodeToString(BackupSnapshot.serializer(), original)
        val decoded = json.decodeFromString(BackupSnapshot.serializer(), encoded)

        assertEquals(BackupSnapshot.CURRENT_VERSION, decoded.version)
        // Mapping back to entities must reproduce the originals exactly (ids + enums + all fields).
        assertEquals(exercise, decoded.exercises.single().toEntity())
        assertEquals(routine, decoded.routines.single().toEntity())
        assertEquals(crossRef, decoded.routineExercises.single().toEntity())
        assertEquals(session, decoded.sessions.single().toEntity())
        assertEquals(setLog, decoded.setLogs.single().toEntity())
    }

    @Test
    fun unknownFieldsAndMissingDefaultsAreTolerated() {
        // A payload from a slightly different build: extra key + omitted optional fields.
        val payload = """
            {
              "version": 1,
              "exportedAt": 1,
              "exercises": [
                {"id": 1, "name": "Squat", "muscleGroup": "QUADS", "equipment": "BARBELL", "unexpected": true}
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(BackupSnapshot.serializer(), payload)
        val entity = decoded.exercises.single().toEntity()

        assertEquals("Squat", entity.name)
        assertEquals(MuscleGroup.QUADS, entity.muscleGroup)
        assertEquals(true, entity.isCustom) // default applied
        assertEquals(0, decoded.routines.size) // default empty list
    }
}
