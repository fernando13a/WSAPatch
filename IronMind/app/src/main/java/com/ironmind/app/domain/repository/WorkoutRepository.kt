package com.ironmind.app.domain.repository

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutinePlan
import com.ironmind.app.domain.model.SessionDetail
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

/**
 * The domain-facing contract for all persistence. The presentation layer (via use cases,
 * added in Stage 2) depends only on this interface — never on Room. Reads expose [Flow]
 * so the UI updates reactively; writes are `suspend` so they run off the main thread.
 */
interface WorkoutRepository {

    // ---- Exercises ------------------------------------------------------------------
    fun observeExercises(): Flow<List<Exercise>>
    fun observeExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<Exercise>>
    fun searchExercises(query: String): Flow<List<Exercise>>
    suspend fun getExercise(id: Long): Exercise?
    suspend fun upsertExercise(exercise: Exercise): Long
    suspend fun deleteExercise(exercise: Exercise)

    // ---- Routines -------------------------------------------------------------------
    fun observeRoutines(): Flow<List<Routine>>
    fun observeRoutinePlans(): Flow<List<RoutinePlan>>
    fun observeRoutinePlan(routineId: Long): Flow<RoutinePlan?>
    suspend fun getRoutine(id: Long): Routine?
    suspend fun upsertRoutine(routine: Routine): Long
    suspend fun deleteRoutine(routine: Routine)

    /** Adds [exerciseId] to [routineId] with an optional prescription. */
    suspend fun addExerciseToRoutine(
        routineId: Long,
        exerciseId: Long,
        position: Int = 0,
        targetSets: Int = 3,
        targetReps: Int = 10,
        targetRestSeconds: Int = 90,
    )

    suspend fun removeExerciseFromRoutine(routineId: Long, exerciseId: Long)

    // ---- Sessions & set logs --------------------------------------------------------
    fun observeSessions(): Flow<List<WorkoutSession>>
    fun observeSessionDetails(): Flow<List<SessionDetail>>
    fun observeSessionDetail(sessionId: Long): Flow<SessionDetail?>
    suspend fun getSession(id: Long): WorkoutSession?
    suspend fun startSession(session: WorkoutSession): Long
    suspend fun updateSession(session: WorkoutSession)
    suspend fun deleteSession(session: WorkoutSession)

    fun observeSetLogsForExercise(exerciseId: Long): Flow<List<SetLog>>
    /** The most recent logged set for an exercise — the "last time" reference for progression. */
    suspend fun getLastSetLogForExercise(exerciseId: Long): SetLog?
    /** The most recent [limit] set logs for an exercise (newest first) — the AI history window. */
    suspend fun getRecentSetLogs(exerciseId: Long, limit: Int): List<SetLog>
    suspend fun upsertSetLog(setLog: SetLog): Long
    suspend fun deleteSetLog(setLog: SetLog)
}
