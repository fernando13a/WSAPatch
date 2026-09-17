package com.ironmind.app.data.repository

import com.ironmind.app.core.util.DispatcherProvider
import com.ironmind.app.data.local.dao.WorkoutDao
import com.ironmind.app.data.local.entity.RoutineExerciseCrossRef
import com.ironmind.app.data.mapper.toDomain
import com.ironmind.app.data.mapper.toEntity
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutinePlan
import com.ironmind.app.domain.model.SessionDetail
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Room-backed implementation of [WorkoutRepository]. Flow reads are mapped from entities to
 * domain models lazily; suspending writes are dispatched onto the IO dispatcher.
 */
class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao,
    private val dispatchers: DispatcherProvider,
) : WorkoutRepository {

    // ---- Exercises ------------------------------------------------------------------
    override fun observeExercises(): Flow<List<Exercise>> =
        dao.observeExercises().map { list -> list.map { it.toDomain() } }

    override fun observeExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<Exercise>> =
        dao.observeExercisesByMuscleGroup(muscleGroup).map { list -> list.map { it.toDomain() } }

    override fun searchExercises(query: String): Flow<List<Exercise>> =
        dao.searchExercises(query).map { list -> list.map { it.toDomain() } }

    override suspend fun getExercise(id: Long): Exercise? = withContext(dispatchers.io) {
        dao.getExerciseById(id)?.toDomain()
    }

    override suspend fun upsertExercise(exercise: Exercise): Long = withContext(dispatchers.io) {
        dao.upsertExercise(exercise.toEntity())
    }

    override suspend fun deleteExercise(exercise: Exercise) = withContext(dispatchers.io) {
        dao.deleteExercise(exercise.toEntity())
    }

    // ---- Routines -------------------------------------------------------------------
    override fun observeRoutines(): Flow<List<Routine>> =
        dao.observeRoutines().map { list -> list.map { it.toDomain() } }

    override fun observeRoutinePlans(): Flow<List<RoutinePlan>> =
        dao.observeRoutinesWithExercises().map { list -> list.map { it.toDomain() } }

    override fun observeRoutinePlan(routineId: Long): Flow<RoutinePlan?> =
        dao.observeRoutineWithExercises(routineId).map { it?.toDomain() }

    override suspend fun upsertRoutine(routine: Routine): Long = withContext(dispatchers.io) {
        dao.upsertRoutine(routine.toEntity())
    }

    override suspend fun deleteRoutine(routine: Routine) = withContext(dispatchers.io) {
        dao.deleteRoutine(routine.toEntity())
    }

    override suspend fun addExerciseToRoutine(
        routineId: Long,
        exerciseId: Long,
        position: Int,
        targetSets: Int,
        targetReps: Int,
        targetRestSeconds: Int,
    ) = withContext(dispatchers.io) {
        dao.upsertRoutineExerciseCrossRef(
            RoutineExerciseCrossRef(
                routineId = routineId,
                exerciseId = exerciseId,
                position = position,
                targetSets = targetSets,
                targetReps = targetReps,
                targetRestSeconds = targetRestSeconds,
            ),
        )
    }

    override suspend fun removeExerciseFromRoutine(routineId: Long, exerciseId: Long) =
        withContext(dispatchers.io) {
            dao.removeExerciseFromRoutine(routineId, exerciseId)
        }

    // ---- Sessions & set logs --------------------------------------------------------
    override fun observeSessions(): Flow<List<WorkoutSession>> =
        dao.observeSessions().map { list -> list.map { it.toDomain() } }

    override fun observeSessionDetails(): Flow<List<SessionDetail>> =
        dao.observeSessionsWithSets().map { list -> list.map { it.toDomain() } }

    override fun observeSessionDetail(sessionId: Long): Flow<SessionDetail?> =
        dao.observeSessionWithSets(sessionId).map { it?.toDomain() }

    override suspend fun startSession(session: WorkoutSession): Long = withContext(dispatchers.io) {
        dao.insertSession(session.toEntity())
    }

    override suspend fun updateSession(session: WorkoutSession) = withContext(dispatchers.io) {
        dao.updateSession(session.toEntity())
    }

    override suspend fun deleteSession(session: WorkoutSession) = withContext(dispatchers.io) {
        dao.deleteSession(session.toEntity())
    }

    override fun observeSetLogsForExercise(exerciseId: Long): Flow<List<SetLog>> =
        dao.observeSetLogsForExercise(exerciseId).map { list -> list.map { it.toDomain() } }

    override suspend fun getLastSetLogForExercise(exerciseId: Long): SetLog? =
        withContext(dispatchers.io) {
            dao.getLastSetLogForExercise(exerciseId)?.toDomain()
        }

    override suspend fun upsertSetLog(setLog: SetLog): Long = withContext(dispatchers.io) {
        dao.upsertSetLog(setLog.toEntity())
    }

    override suspend fun deleteSetLog(setLog: SetLog) = withContext(dispatchers.io) {
        dao.deleteSetLog(setLog.toEntity())
    }
}
