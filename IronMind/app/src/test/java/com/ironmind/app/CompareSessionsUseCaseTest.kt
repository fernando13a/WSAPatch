package com.ironmind.app

import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SessionDetail
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.usecase.CompareSessionsUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareSessionsUseCaseTest {

    private val squat = Exercise(id = 1, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)

    private fun detail(sessionId: Long, weightKg: Double) = SessionDetail(
        session = WorkoutSession(id = sessionId, startedAt = 0L, title = "Session $sessionId"),
        sets = listOf(SetLog(sessionId = sessionId, exerciseId = 1, setNumber = 1, weightKg = weightKg, reps = 5)),
    )

    private fun repository(a: SessionDetail?, b: SessionDetail?) = FakeWorkoutRepository().apply {
        exercisesFlow.value = listOf(squat)
        a?.let { sessionDetailsById[1L] = it }
        b?.let { sessionDetailsById[2L] = it }
    }

    @Test
    fun invoke_streamsAccumulatedTextThenCompletes() = runTest {
        val repo = repository(detail(1, 90.0), detail(2, 95.0))
        val llm = FakeLlmInferenceService(chunks = listOf("Mejoraste ", "el volumen"))
        val useCase = CompareSessionsUseCase(repo, llm)

        val states = useCase(1L, 2L).toList()

        assertEquals(SuggestionState.Loading, states.first())
        val last = states.last()
        assertTrue(last is SuggestionState.Success && last.isComplete)
        assertEquals("Mejoraste el volumen", (last as SuggestionState.Success).suggestion)
    }

    @Test
    fun invoke_emitsErrorWhenFirstSessionIsMissing() = runTest {
        val repo = repository(a = null, b = detail(2, 95.0))
        val useCase = CompareSessionsUseCase(repo, FakeLlmInferenceService())

        val last = useCase(1L, 2L).toList().last()

        assertTrue(last is SuggestionState.Error)
    }

    @Test
    fun invoke_emitsErrorWhenSecondSessionIsMissing() = runTest {
        val repo = repository(a = detail(1, 90.0), b = null)
        val useCase = CompareSessionsUseCase(repo, FakeLlmInferenceService())

        val last = useCase(1L, 2L).toList().last()

        assertTrue(last is SuggestionState.Error)
    }

    @Test
    fun invoke_mapsModelNotFoundToFriendlyError() = runTest {
        val repo = repository(detail(1, 90.0), detail(2, 95.0))
        val llm = FakeLlmInferenceService(error = LlmModelNotFoundException("/models/x.bin"))
        val useCase = CompareSessionsUseCase(repo, llm)

        val last = useCase(1L, 2L).toList().last()

        assertTrue(last is SuggestionState.Error)
        assertTrue((last as SuggestionState.Error).message.contains("modelo", ignoreCase = true))
    }
}
