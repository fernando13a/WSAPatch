package com.ironmind.app

import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.usecase.GetTechniqueCoachingUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetTechniqueCoachingUseCaseTest {

    private val squat = Exercise(id = 1, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)

    private fun repository(exercisePresent: Boolean) = FakeWorkoutRepository().apply {
        if (exercisePresent) exercisesFlow.value = listOf(squat)
    }

    @Test
    fun invoke_streamsAccumulatedTextThenCompletes() = runTest {
        val repo = repository(exercisePresent = true)
        val llm = FakeLlmInferenceService(chunks = listOf("Cuida ", "la espalda baja"))
        val useCase = GetTechniqueCoachingUseCase(repo, llm)

        val states = useCase(exerciseId = 1).toList()

        assertEquals(SuggestionState.Loading, states.first())
        val last = states.last()
        assertTrue(last is SuggestionState.Success && last.isComplete)
        assertEquals("Cuida la espalda baja", (last as SuggestionState.Success).suggestion)
    }

    @Test
    fun invoke_emitsErrorWhenExerciseMissing() = runTest {
        val repo = repository(exercisePresent = false)
        val useCase = GetTechniqueCoachingUseCase(repo, FakeLlmInferenceService())

        val last = useCase(99).toList().last()

        assertTrue(last is SuggestionState.Error)
    }

    @Test
    fun invoke_mapsModelNotFoundToFriendlyError() = runTest {
        val repo = repository(exercisePresent = true)
        val llm = FakeLlmInferenceService(error = LlmModelNotFoundException("/models/x.bin"))
        val useCase = GetTechniqueCoachingUseCase(repo, llm)

        val last = useCase(1).toList().last()

        assertTrue(last is SuggestionState.Error)
        assertTrue((last as SuggestionState.Error).message.contains("modelo", ignoreCase = true))
    }
}
