package com.ironmind.app

import androidx.lifecycle.SavedStateHandle
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.ui.navigation.Destinations
import com.ironmind.app.ui.progress.ProgressViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

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

        val vm = ProgressViewModel(repo, FakeAppPreferences(), SavedStateHandle(mapOf(Destinations.ARG_EXERCISE_ID to 1L)))
        val state = vm.uiState.first { !it.isLoading }

        assertEquals("Squat", state.exerciseName)
        assertEquals(2, state.points.size)
        assertEquals(110f, state.bestWeight)
        assertEquals(2, state.history.size)
    }
}
