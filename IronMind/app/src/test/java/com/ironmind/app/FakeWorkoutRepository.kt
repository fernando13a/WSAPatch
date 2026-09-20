package com.ironmind.app

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutinePlan
import com.ironmind.app.domain.model.SessionDetail
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory, controllable fake of [WorkoutRepository]. Observable reads are backed by
 * [MutableStateFlow]s the test can set; writes are recorded for assertions.
 */
class FakeWorkoutRepository : WorkoutRepository {

    // Observable state (set these from tests).
    val exercisesFlow = MutableStateFlow<List<Exercise>>(emptyList())
    val routinesFlow = MutableStateFlow<List<Routine>>(emptyList())
    val routinePlansFlow = MutableStateFlow<List<RoutinePlan>>(emptyList())
    val sessionsFlow = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val sessionDetailsFlow = MutableStateFlow<List<SessionDetail>>(emptyList())
    val sessionDetailFlow = MutableStateFlow<SessionDetail?>(null)
    val setLogsForExerciseFlow = MutableStateFlow<List<SetLog>>(emptyList())
    val routinePlanFlow = MutableStateFlow<RoutinePlan?>(null)

    // Direct-return state.
    var recentSetLogs: List<SetLog> = emptyList()
    var recentActivity: List<SetLog> = emptyList()
    var routineToReturn: Routine? = null
    var sessionToReturn: WorkoutSession? = null
    var newSessionId: Long = 7L
    var newRoutineId: Long = 55L
    private var nextExerciseId: Long = 100L

    // Recorded writes.
    var startSessionCount = 0
    val updatedSessions = mutableListOf<WorkoutSession>()
    val upsertedSetLogs = mutableListOf<SetLog>()
    val deletedSetLogs = mutableListOf<SetLog>()
    val upsertedRoutines = mutableListOf<Routine>()
    val upsertedExercises = mutableListOf<Exercise>()
    val addedToRoutine = mutableListOf<Triple<Long, Long, Int>>() // routineId, exerciseId, position
    val removedFromRoutine = mutableListOf<Pair<Long, Long>>()

    /** Controllable return value for [getAlternatives], keyed by the exerciseId asked for. */
    var alternativesByExerciseId: Map<Long, List<Exercise>> = emptyMap()

    // ---- Exercises ----
    override fun observeExercises(): Flow<List<Exercise>> = exercisesFlow
    override fun observeExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<Exercise>> = exercisesFlow
    override fun searchExercises(query: String): Flow<List<Exercise>> = exercisesFlow
    override fun observeExercise(id: Long): Flow<Exercise?> =
        exercisesFlow.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun getExercise(id: Long): Exercise? = exercisesFlow.value.firstOrNull { it.id == id }
    override suspend fun getAllExercises(): List<Exercise> = exercisesFlow.value
    override suspend fun getAlternatives(exerciseId: Long): List<Exercise> =
        alternativesByExerciseId[exerciseId].orEmpty()
    override suspend fun upsertExercise(exercise: Exercise): Long {
        val id = if (exercise.id == 0L) nextExerciseId++ else exercise.id
        val stored = exercise.copy(id = id)
        upsertedExercises += stored
        exercisesFlow.value = exercisesFlow.value.filterNot { it.id == id } + stored
        return id
    }
    override suspend fun deleteExercise(exercise: Exercise) {
        exercisesFlow.value = exercisesFlow.value.filterNot { it.id == exercise.id }
    }
    override suspend fun updateExerciseNameEs(exerciseId: Long, spanishName: String) {
        exercisesFlow.value = exercisesFlow.value.map { exercise ->
            if (exercise.id == exerciseId) exercise.copy(nameEs = spanishName) else exercise
        }
    }

    // ---- Routines ----
    override fun observeRoutines(): Flow<List<Routine>> = routinesFlow
    override fun observeRoutinePlans(): Flow<List<RoutinePlan>> = routinePlansFlow
    override fun observeRoutinePlan(routineId: Long): Flow<RoutinePlan?> = routinePlanFlow
    override suspend fun getRoutine(id: Long): Routine? = routineToReturn
    override suspend fun upsertRoutine(routine: Routine): Long {
        upsertedRoutines += routine
        return if (routine.id == 0L) newRoutineId else routine.id
    }
    override suspend fun deleteRoutine(routine: Routine) = Unit
    override suspend fun addExerciseToRoutine(
        routineId: Long,
        exerciseId: Long,
        position: Int,
        targetSets: Int,
        targetReps: Int,
        targetRestSeconds: Int,
    ) {
        addedToRoutine += Triple(routineId, exerciseId, position)
    }
    override suspend fun removeExerciseFromRoutine(routineId: Long, exerciseId: Long) {
        removedFromRoutine += (routineId to exerciseId)
    }

    // ---- Sessions & set logs ----
    override fun observeSessions(): Flow<List<WorkoutSession>> = sessionsFlow
    override fun observeSessionDetails(): Flow<List<SessionDetail>> = sessionDetailsFlow
    override fun observeSessionDetail(sessionId: Long): Flow<SessionDetail?> = sessionDetailFlow
    override suspend fun getSession(id: Long): WorkoutSession? = sessionToReturn
    override suspend fun startSession(session: WorkoutSession): Long {
        startSessionCount++
        return newSessionId
    }
    override suspend fun updateSession(session: WorkoutSession) {
        updatedSessions += session
    }
    override suspend fun deleteSession(session: WorkoutSession) = Unit

    override fun observeSetLogsForExercise(exerciseId: Long): Flow<List<SetLog>> = setLogsForExerciseFlow
    override suspend fun getLastSetLogForExercise(exerciseId: Long): SetLog? = recentSetLogs.firstOrNull()
    override suspend fun getRecentSetLogs(exerciseId: Long, limit: Int): List<SetLog> = recentSetLogs
    override suspend fun getRecentActivity(sinceTimestamp: Long): List<SetLog> = recentActivity
    override suspend fun upsertSetLog(setLog: SetLog): Long {
        upsertedSetLogs += setLog
        return if (setLog.id == 0L) 1L else setLog.id
    }
    override suspend fun deleteSetLog(setLog: SetLog) {
        deletedSetLogs += setLog
    }
}
