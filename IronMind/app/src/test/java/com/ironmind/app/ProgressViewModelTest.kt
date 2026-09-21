package com.ironmind.app

import androidx.lifecycle.SavedStateHandle
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SessionDetail
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.usecase.CompareSessionsUseCase
import com.ironmind.app.ui.navigation.Destinations
import com.ironmind.app.ui.progress.ProgressViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun viewModel(repo: FakeWorkoutRepository, llm: FakeLlmInferenceService = FakeLlmInferenceService(), exerciseId: Long = 1L) =
        ProgressViewModel(repo, CompareSessionsUseCase(repo, llm), FakeAppPreferences(), SavedStateHandle(mapOf(Destinations.ARG_EXERCISE_ID to exerciseId)))

    @Test
    fun uiState_buildsTopWeightPerDayPointsAndHistory() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository()
        repo.exercisesFlow.value = listOf(
            Exercise(id = 1, name = "Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL),
        )
        val dayOne = 1_700_000_000_000L
        val dayTwo = dayOne + 2 * 86_400_000L
        repo.setLogsForExerciseFlow.value = listOf(
            SetLog(id = 1, sessionId = 1, exerciseId = 1, setNumber = 1, weightKg = 100.0, reps = 5, performedAt = dayOne),
            SetLog(id = 2, sessionId = 2, exerciseId = 1, setNumber = 1, weightKg = 110.0, reps = 5, performedAt = dayTwo),
        )

        val vm = viewModel(repo)
        val state = vm.uiState.first { !it.isLoading }

        assertEquals("Squat", state.exerciseName)
        assertEquals(2, state.points.size)
        assertEquals(110f, state.bestWeight)
        assertEquals(2, state.history.size)
    }

    @Test
    fun compareSelectedSessions_streamsIntoComparisonResult() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply {
            exercisesFlow.value = listOf(Exercise(id = 1, name = "Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL))
            sessionDetailsById[1L] = SessionDetail(
                session = WorkoutSession(id = 1, startedAt = 0L),
                sets = listOf(SetLog(sessionId = 1, exerciseId = 1, setNumber = 1, weightKg = 90.0, reps = 5)),
            )
            sessionDetailsById[2L] = SessionDetail(
                session = WorkoutSession(id = 2, startedAt = 1_000L),
                sets = listOf(SetLog(sessionId = 2, exerciseId = 1, setNumber = 1, weightKg = 95.0, reps = 5)),
            )
        }
        val vm = viewModel(repo, FakeLlmInferenceService(chunks = listOf("Mejoraste")))

        vm.selectSessionA(1L)
        vm.selectSessionB(2L)
        vm.compareSelectedSessions()

        val result = vm.comparisonResult.value
        assertTrue(result is SuggestionState.Success && result.isComplete)
        assertEquals("Mejoraste", (result as SuggestionState.Success).suggestion)
    }

    @Test
    fun compareSelectedSessions_doesNothingWithOnlyOneSessionSelected() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo)

        vm.selectSessionA(1L)
        vm.compareSelectedSessions()

        assertEquals(null, vm.comparisonResult.value)
    }

    @Test
    fun compareSelectedSessions_doesNothingWhenBothSelectionsAreTheSameSession() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo)

        vm.selectSessionA(1L)
        vm.selectSessionB(1L)
        vm.compareSelectedSessions()

        assertEquals(null, vm.comparisonResult.value)
    }

    @Test
    fun selectingANewSessionDismissesAPreviousComparisonResult() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply {
            exercisesFlow.value = listOf(Exercise(id = 1, name = "Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL))
            sessionDetailsById[1L] = SessionDetail(WorkoutSession(id = 1, startedAt = 0L), listOf(SetLog(sessionId = 1, exerciseId = 1, setNumber = 1, weightKg = 90.0, reps = 5)))
            sessionDetailsById[2L] = SessionDetail(WorkoutSession(id = 2, startedAt = 0L), listOf(SetLog(sessionId = 2, exerciseId = 1, setNumber = 1, weightKg = 95.0, reps = 5)))
        }
        val vm = viewModel(repo, FakeLlmInferenceService(chunks = listOf("ok")))
        vm.selectSessionA(1L)
        vm.selectSessionB(2L)
        vm.compareSelectedSessions()
        assertTrue(vm.comparisonResult.value != null)

        vm.selectSessionB(2L) // re-selecting must still clear stale results

        assertEquals(null, vm.comparisonResult.value)
    }
}
