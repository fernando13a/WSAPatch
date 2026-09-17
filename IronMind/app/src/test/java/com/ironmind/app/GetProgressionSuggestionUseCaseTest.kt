package com.ironmind.app

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutinePlan
import com.ironmind.app.domain.model.SessionDetail
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.usecase.GetProgressionSuggestionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetProgressionSuggestionUseCaseTest {

    private val sampleExercise = Exercise(
        id = 1, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL,
    )
    private val sampleSet = SetLog(
        id = 1, sessionId = 1, exerciseId = 1, setNumber = 1, weightKg = 100.0, reps = 5,
    )

    @Test
    fun invoke_streamsAccumulatedTextThenCompletes() = runTest {
        val repo = FakeWorkoutRepository(exercise = sampleExercise, recent = listOf(sampleSet))
        val llm = FakeLlmInferenceService(chunks = listOf("Sube ", "2.5 ", "kg"))
        val useCase = GetProgressionSuggestionUseCase(repo, llm)

        val states = useCase(exerciseId = 1).toList()

        assertEquals(SuggestionState.Loading, states.first())
        // Intermediate Success emissions accumulate the streamed text (typewriter effect).
        assertEquals(SuggestionState.Success("Sube ", isComplete = false), states[1])
        val last = states.last()
        assertTrue(last is SuggestionState.Success && last.isComplete)
        assertEquals("Sube 2.5 kg", (last as SuggestionState.Success).suggestion)
    }

    @Test
    fun invoke_emitsErrorWhenNoHistory() = runTest {
        val repo = FakeWorkoutRepository(exercise = sampleExercise, recent = emptyList())
        val useCase = GetProgressionSuggestionUseCase(repo, FakeLlmInferenceService())

        val states = useCase(1).toList()

        assertEquals(SuggestionState.Loading, states.first())
        assertTrue(states.last() is SuggestionState.Error)
    }

    @Test
    fun invoke_emitsErrorWhenExerciseMissing() = runTest {
        val repo = FakeWorkoutRepository(exercise = null, recent = emptyList())
        val useCase = GetProgressionSuggestionUseCase(repo, FakeLlmInferenceService())

        val states = useCase(99).toList()

        assertTrue(states.last() is SuggestionState.Error)
    }

    @Test
    fun invoke_mapsModelNotFoundToFriendlyError() = runTest {
        val repo = FakeWorkoutRepository(exercise = sampleExercise, recent = listOf(sampleSet))
        val llm = FakeLlmInferenceService(error = LlmModelNotFoundException("/models/x.bin"))
        val useCase = GetProgressionSuggestionUseCase(repo, llm)

        val last = useCase(1).toList().last()

        assertTrue(last is SuggestionState.Error)
        assertTrue((last as SuggestionState.Error).message.contains("modelo", ignoreCase = true))
    }
}

// ---- Test doubles -------------------------------------------------------------------------

private class FakeLlmInferenceService(
    private val chunks: List<String> = emptyList(),
    private val error: Throwable? = null,
    private val modelAvailable: Boolean = true,
) : LlmInferenceService {
    override suspend fun isModelAvailable(): Boolean = modelAvailable
    override fun generateResponseStream(prompt: String): Flow<String> = flow {
        error?.let { throw it }
        chunks.forEach { emit(it) }
    }
    override fun close() = Unit
}

private class FakeWorkoutRepository(
    private val exercise: Exercise?,
    private val recent: List<SetLog>,
) : WorkoutRepository {

    override suspend fun getExercise(id: Long): Exercise? = exercise
    override suspend fun getRecentSetLogs(exerciseId: Long, limit: Int): List<SetLog> = recent

    // --- Unused in these tests: sensible no-op defaults ---
    override fun observeExercises(): Flow<List<Exercise>> = emptyFlow()
    override fun observeExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<Exercise>> = emptyFlow()
    override fun searchExercises(query: String): Flow<List<Exercise>> = emptyFlow()
    override suspend fun upsertExercise(exercise: Exercise): Long = 0
    override suspend fun deleteExercise(exercise: Exercise) = Unit
    override fun observeRoutines(): Flow<List<Routine>> = emptyFlow()
    override fun observeRoutinePlans(): Flow<List<RoutinePlan>> = emptyFlow()
    override fun observeRoutinePlan(routineId: Long): Flow<RoutinePlan?> = emptyFlow()
    override suspend fun upsertRoutine(routine: Routine): Long = 0
    override suspend fun deleteRoutine(routine: Routine) = Unit
    override suspend fun addExerciseToRoutine(
        routineId: Long,
        exerciseId: Long,
        position: Int,
        targetSets: Int,
        targetReps: Int,
        targetRestSeconds: Int,
    ) = Unit
    override suspend fun removeExerciseFromRoutine(routineId: Long, exerciseId: Long) = Unit
    override fun observeSessions(): Flow<List<WorkoutSession>> = emptyFlow()
    override fun observeSessionDetails(): Flow<List<SessionDetail>> = emptyFlow()
    override fun observeSessionDetail(sessionId: Long): Flow<SessionDetail?> = emptyFlow()
    override suspend fun getSession(id: Long): WorkoutSession? = null
    override suspend fun startSession(session: WorkoutSession): Long = 0
    override suspend fun updateSession(session: WorkoutSession) = Unit
    override suspend fun deleteSession(session: WorkoutSession) = Unit
    override fun observeSetLogsForExercise(exerciseId: Long): Flow<List<SetLog>> = emptyFlow()
    override suspend fun getLastSetLogForExercise(exerciseId: Long): SetLog? = null
    override suspend fun upsertSetLog(setLog: SetLog): Long = 0
    override suspend fun deleteSetLog(setLog: SetLog) = Unit
}
