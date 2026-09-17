package com.ironmind.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ironmind.app.data.local.entity.ExerciseEntity
import com.ironmind.app.data.local.entity.RoutineEntity
import com.ironmind.app.data.local.entity.RoutineExerciseCrossRef
import com.ironmind.app.data.local.entity.SetLogEntity
import com.ironmind.app.data.local.entity.WorkoutSessionEntity
import com.ironmind.app.data.local.relation.RoutineWithExercises
import com.ironmind.app.data.local.relation.SessionWithSets
import com.ironmind.app.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

/**
 * Single data-access object for the whole schema. Read operations return [Flow] (Room emits
 * a new value whenever the underlying tables change); writes are `suspend`. Queries that
 * hydrate `@Relation` POJOs are marked `@Transaction` so the multi-query read is consistent.
 */
@Dao
interface WorkoutDao {

    // ---------------------------------------------------------------------------------
    // Exercises
    // ---------------------------------------------------------------------------------
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE muscleGroup = :muscleGroup ORDER BY name ASC")
    fun observeExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchExercises(query: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseById(id: Long): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun countExercises(): Int

    @Upsert
    suspend fun upsertExercise(exercise: ExerciseEntity): Long

    @Upsert
    suspend fun upsertExercises(exercises: List<ExerciseEntity>)

    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity)

    // ---------------------------------------------------------------------------------
    // Routines
    // ---------------------------------------------------------------------------------
    @Query("SELECT * FROM routines ORDER BY position ASC, createdAt ASC")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutineById(id: Long): RoutineEntity?

    @Upsert
    suspend fun upsertRoutine(routine: RoutineEntity): Long

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    // ---- Routine ↔ Exercise junction ----
    @Upsert
    suspend fun upsertRoutineExerciseCrossRef(crossRef: RoutineExerciseCrossRef)

    @Delete
    suspend fun deleteRoutineExerciseCrossRef(crossRef: RoutineExerciseCrossRef)

    @Query("DELETE FROM routine_exercise_cross_ref WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun removeExerciseFromRoutine(routineId: Long, exerciseId: Long)

    // ---- Routine + exercises relation ----
    @Transaction
    @Query("SELECT * FROM routines ORDER BY position ASC, createdAt ASC")
    fun observeRoutinesWithExercises(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :routineId")
    fun observeRoutineWithExercises(routineId: Long): Flow<RoutineWithExercises?>

    // ---------------------------------------------------------------------------------
    // Workout sessions
    // ---------------------------------------------------------------------------------
    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): WorkoutSessionEntity?

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Delete
    suspend fun deleteSession(session: WorkoutSessionEntity)

    // ---- Session + sets relation ----
    @Transaction
    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun observeSessionsWithSets(): Flow<List<SessionWithSets>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    fun observeSessionWithSets(sessionId: Long): Flow<SessionWithSets?>

    // ---------------------------------------------------------------------------------
    // Set logs
    // ---------------------------------------------------------------------------------
    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY exerciseId ASC, setNumber ASC")
    fun observeSetLogsForSession(sessionId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId ORDER BY performedAt DESC")
    fun observeSetLogsForExercise(exerciseId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId ORDER BY performedAt DESC LIMIT 1")
    suspend fun getLastSetLogForExercise(exerciseId: Long): SetLogEntity?

    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId ORDER BY performedAt DESC LIMIT :limit")
    suspend fun getRecentSetLogsForExercise(exerciseId: Long, limit: Int): List<SetLogEntity>

    @Upsert
    suspend fun upsertSetLog(setLog: SetLogEntity): Long

    @Delete
    suspend fun deleteSetLog(setLog: SetLogEntity)
}
