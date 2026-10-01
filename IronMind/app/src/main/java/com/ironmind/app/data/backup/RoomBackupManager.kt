package com.ironmind.app.data.backup

import androidx.room.withTransaction
import com.ironmind.app.core.util.DispatcherProvider
import com.ironmind.app.data.local.IronMindDatabase
import com.ironmind.app.data.local.dao.WorkoutDao
import com.ironmind.app.domain.backup.BackupManager
import com.ironmind.app.domain.backup.BackupResult
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Room-backed [BackupManager]. Serialization / deserialization is dispatched to IO; the actual
 * table rewrite runs inside a single [withTransaction] block so a restore is all-or-nothing —
 * and the payload is parsed *before* the transaction opens, so a malformed file never touches
 * existing data.
 */
class RoomBackupManager @Inject constructor(
    private val database: IronMindDatabase,
    private val dao: WorkoutDao,
    private val dispatchers: DispatcherProvider,
) : BackupManager {

    private val jsonFormat = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun export(): String = withContext(dispatchers.io) {
        val snapshot = BackupSnapshot(
            exportedAt = System.currentTimeMillis(),
            exercises = dao.getAllExercisesOnce().map { it.toDto() },
            routines = dao.getAllRoutinesOnce().map { it.toDto() },
            routineExercises = dao.getAllRoutineExercisesOnce().map { it.toDto() },
            sessions = dao.getAllSessionsOnce().map { it.toDto() },
            setLogs = dao.getAllSetLogsOnce().map { it.toDto() },
        )
        jsonFormat.encodeToString(BackupSnapshot.serializer(), snapshot)
    }

    override suspend fun import(json: String): BackupResult {
        val snapshot = withContext(dispatchers.io) {
            jsonFormat.decodeFromString(BackupSnapshot.serializer(), json)
        }
        // Deletes run child-first and inserts parent-first so foreign-key constraints hold.
        database.withTransaction {
            dao.deleteAllSetLogs()
            dao.deleteAllRoutineExercises()
            dao.deleteAllSessions()
            dao.deleteAllRoutines()
            dao.deleteAllExercises()

            dao.insertExercises(snapshot.exercises.map { it.toEntity() })
            dao.insertRoutines(snapshot.routines.map { it.toEntity() })
            dao.insertRoutineExercises(snapshot.routineExercises.map { it.toEntity() })
            dao.insertSessions(snapshot.sessions.map { it.toEntity() })
            dao.insertSetLogs(snapshot.setLogs.map { it.toEntity() })
        }
        return BackupResult(
            exercises = snapshot.exercises.size,
            routines = snapshot.routines.size,
            sessions = snapshot.sessions.size,
            setLogs = snapshot.setLogs.size,
        )
    }
}
