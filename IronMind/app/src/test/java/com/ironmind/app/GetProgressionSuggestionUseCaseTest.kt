package com.ironmind.app

import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.usecase.GetProgressionSuggestionUseCase
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

    private fun repository(exercisePresent: Boolean, recent: List<SetLog>) = FakeWorkoutRepository().apply {
        if (exercisePresent) exercisesFlow.value = listOf(sampleExercise)
        recentSetLogs = recent
    }

    @Test
    fun invoke_streamsAccumulatedTextThenCompletes() = runTest {
        val repo = repository(exercisePresent = true, recent = listOf(sampleSet))
        val llm = FakeLlmInferenceService(chunks = listOf("Sube ", "2.5 ", "kg"))
        val useCase = GetProgressionSuggestionUseCase(repo, llm)

        val states = useCase(exerciseId = 1).toList()

        assertEquals(SuggestionState.Loading, states.first())
        assertEquals(SuggestionState.Success("Sube ", isComplete = false), states[1])
        val last = states.last()
        assertTrue(last is SuggestionState.Success && last.isComplete)
        assertEquals("Sube 2.5 kg", (last as SuggestionState.Success).suggestion)
    }

    @Test
    fun invoke_emitsErrorWhenNoHistory() = runTest {
        val repo = repository(exercisePresent = true, recent = emptyList())
        val useCase = GetProgressionSuggestionUseCase(repo, FakeLlmInferenceService())

        val states = useCase(1).toList()

        assertEquals(SuggestionState.Loading, states.first())
        assertTrue(states.last() is SuggestionState.Error)
    }

    @Test
    fun invoke_emitsErrorWhenExerciseMissing() = runTest {
        val repo = repository(exercisePresent = false, recent = emptyList())
        val useCase = GetProgressionSuggestionUseCase(repo, FakeLlmInferenceService())

        val states = useCase(99).toList()

        assertTrue(states.last() is SuggestionState.Error)
    }

    @Test
    fun invoke_mapsModelNotFoundToFriendlyError() = runTest {
        val repo = repository(exercisePresent = true, recent = listOf(sampleSet))
        val llm = FakeLlmInferenceService(error = LlmModelNotFoundException("/models/x.bin"))
        val useCase = GetProgressionSuggestionUseCase(repo, llm)

        val last = useCase(1).toList().last()

        assertTrue(last is SuggestionState.Error)
        assertTrue((last as SuggestionState.Error).message.contains("modelo", ignoreCase = true))
    }
}
