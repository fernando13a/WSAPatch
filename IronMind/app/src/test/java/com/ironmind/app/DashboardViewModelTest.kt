package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutinePlan
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.SessionDetail
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.usecase.GetProgressionSuggestionUseCase
import com.ironmind.app.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val bench = Exercise(id = 1, name = "Bench", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)

    private fun repositoryWithOneSession(): FakeWorkoutRepository = FakeWorkoutRepository().apply {
        val now = System.currentTimeMillis()
        exercisesFlow.value = listOf(bench)
        routinePlansFlow.value = listOf(RoutinePlan(Routine(id = 10, name = "Push", split = RoutineSplit.PUSH), listOf(bench)))
        val set = SetLog(id = 2, sessionId = 5, exerciseId = 1, setNumber = 1, weightKg = 100.0, reps = 5, performedAt = now)
        sessionDetailsFlow.value = listOf(SessionDetail(WorkoutSession(id = 5, startedAt = now), listOf(set)))
        recentSetLogs = listOf(set)
    }

    private fun viewModel(repo: FakeWorkoutRepository, llm: FakeLlmInferenceService) =
        DashboardViewModel(repo, GetProgressionSuggestionUseCase(repo, llm), llm)

    @Test
    fun uiState_computesStreakSessionsRoutineProgressAndFocus() = runTest(mainRule.dispatcher) {
        val repo = repositoryWithOneSession()
        val vm = viewModel(repo, FakeLlmInferenceService())

        val state = vm.uiState.first { !it.isLoading }

        assertEquals(1, state.streak)
        assertEquals(1, state.totalSessions)
        assertEquals(1, state.routines.size)
        assertEquals(1f, state.routines.first().progress)
        assertEquals(1L, state.focusExerciseId)
        assertEquals("Bench", state.focusExerciseName)
    }

    @Test
    fun modelAvailabilityReflectsService() = runTest(mainRule.dispatcher) {
        val repo = repositoryWithOneSession()
        val vm = viewModel(repo, FakeLlmInferenceService(modelAvailable = true))
        assertTrue(vm.modelAvailable.value)
    }

    @Test
    fun generateSuggestionStreamsIntoState() = runTest(mainRule.dispatcher) {
        val repo = repositoryWithOneSession()
        val vm = viewModel(repo, FakeLlmInferenceService(chunks = listOf("ok")))

        vm.generateSuggestion(1L)

        val result = vm.suggestion.value
        assertTrue(result is SuggestionState.Success && result.isComplete)
        assertEquals("ok", (result as SuggestionState.Success).suggestion)
    }
}
