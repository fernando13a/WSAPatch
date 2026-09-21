package com.ironmind.app

import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.usecase.GetRecoveryAdviceUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetRecoveryAdviceUseCaseTest {

    private val squat = Exercise(id = 1, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)
    private val sampleSet = SetLog(id = 1, sessionId = 1, exerciseId = 1, setNumber = 1, weightKg = 100.0, reps = 5)

    private fun repository(activity: List<SetLog> = emptyList()) = FakeWorkoutRepository().apply {
        exercisesFlow.value = listOf(squat)
        recentActivity = activity
    }

    @Test
    fun invoke_streamsAccumulatedTextThenCompletes() = runTest {
        val repo = repository(activity = listOf(sampleSet))
        val llm = FakeLlmInferenceService(chunks = listOf("Adelante, ", "entrena hoy"))
        val useCase = GetRecoveryAdviceUseCase(repo, llm)

        val states = useCase(MuscleGroup.QUADS).toList()

        assertEquals(SuggestionState.Loading, states.first())
        val last = states.last()
        assertTrue(last is SuggestionState.Success && last.isComplete)
        assertEquals("Adelante, entrena hoy", (last as SuggestionState.Success).suggestion)
    }

    @Test
    fun invoke_stillAnswersWhenMuscleGroupWasNeverTrained() = runTest {
        // Unlike GetTrainingInsightsUseCase, empty history is a valid, answerable case here.
        val repo = repository(activity = emptyList())
        val llm = FakeLlmInferenceService(chunks = listOf("ok"))
        val useCase = GetRecoveryAdviceUseCase(repo, llm)

        val last = useCase(MuscleGroup.QUADS).toList().last()

        assertTrue(last is SuggestionState.Success && last.isComplete)
    }

    @Test
    fun invoke_mapsModelNotFoundToFriendlyError() = runTest {
        val repo = repository(activity = listOf(sampleSet))
        val llm = FakeLlmInferenceService(error = LlmModelNotFoundException("/models/x.bin"))
        val useCase = GetRecoveryAdviceUseCase(repo, llm)

        val last = useCase(MuscleGroup.QUADS).toList().last()

        assertTrue(last is SuggestionState.Error)
        assertTrue((last as SuggestionState.Error).message.contains("modelo", ignoreCase = true))
    }
}
