package com.ironmind.app

import androidx.lifecycle.SavedStateHandle
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.ExercisePrescription
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutinePlan
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.usecase.GetRecoveryAdviceUseCase
import com.ironmind.app.ui.navigation.Destinations
import com.ironmind.app.ui.session.SessionViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun freeSessionHandle() = SavedStateHandle(
        mapOf(Destinations.ARG_SESSION_ID to 0L, Destinations.ARG_ROUTINE_ID to 0L),
    )

    private fun viewModel(
        repo: FakeWorkoutRepository,
        notifier: FakeRestTimerNotifier = FakeRestTimerNotifier(),
        llm: FakeLlmInferenceService = FakeLlmInferenceService(),
    ) = SessionViewModel(repo, GetRecoveryAdviceUseCase(repo, llm), notifier, FakeAppPreferences(), freeSessionHandle())

    @Test
    fun noSessionIsCreatedUntilFirstSetIsLogged() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo)

        assertEquals(0, repo.startSessionCount)

        vm.addSet(exerciseId = 1L, weightKg = 100.0, reps = 5, notes = null, autoRestSeconds = null)

        assertEquals(1, repo.startSessionCount)
        assertEquals(1, repo.upsertedSetLogs.size)
        assertEquals(repo.newSessionId, repo.upsertedSetLogs.first().sessionId)
    }

    @Test
    fun finishSessionWithoutLoggingDoesNotPersistAnything() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo)

        var done = false
        vm.finishSession { done = true }

        assertTrue(done)
        assertEquals(0, repo.startSessionCount)
        assertTrue(repo.updatedSessions.isEmpty())
    }

    @Test
    fun restTimerStartsAndStops() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository()
        val notifier = FakeRestTimerNotifier()
        val vm = viewModel(repo, notifier = notifier)

        vm.startRest(90)
        assertEquals(90, vm.restRemaining.value)
        // The total is recorded so the UI dial can render a proportional countdown.
        assertEquals(90, vm.restTotal.value)
        // Starting a rest posts the initial countdown to the notification.
        assertEquals(90, notifier.countdownValues.first())

        vm.stopRest()
        assertEquals(0, vm.restRemaining.value)
        assertEquals(0, vm.restTotal.value)
        // Stopping clears the notification and never fires the completion alert.
        assertEquals(1, notifier.cancelCount)
        assertEquals(0, notifier.completeCount)
    }

    // ---- Routine sessions ---------------------------------------------------------------

    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val benchTarget = ExercisePrescription(sets = 4, reps = 6, restSeconds = 150, weightKg = 80.0)

    private fun routineRepo() = FakeWorkoutRepository().apply {
        exercisesFlow.value = listOf(bench)
        routinePlanFlow.value = RoutinePlan(
            routine = Routine(id = 3, name = "Push", split = RoutineSplit.PUSH),
            exercises = listOf(bench),
            prescriptions = mapOf(bench.id to benchTarget),
        )
    }

    private fun routineViewModel(repo: FakeWorkoutRepository, sessionId: Long = 0L) = SessionViewModel(
        repo,
        GetRecoveryAdviceUseCase(repo, FakeLlmInferenceService()),
        FakeRestTimerNotifier(),
        FakeAppPreferences(),
        SavedStateHandle(mapOf(Destinations.ARG_SESSION_ID to sessionId, Destinations.ARG_ROUTINE_ID to 3L)),
    )

    @Test
    fun aRoutineSessionShowsWhatTheRoutineAsksAndWhatWasDoneLastTime() = runTest(mainRule.dispatcher) {
        val repo = routineRepo().apply {
            recentSetLogs = listOf(
                SetLog(sessionId = 2, exerciseId = 1, setNumber = 1, weightKg = 77.5, reps = 6, performedAt = 1_000L),
                SetLog(sessionId = 2, exerciseId = 1, setNumber = 2, weightKg = 75.0, reps = 6, performedAt = 1_100L),
            )
        }
        val vm = routineViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }

        val block = vm.uiState.value.exerciseBlocks.single()
        assertEquals(benchTarget, block.target)
        assertEquals(77.5, block.lastTopSet!!.weightKg, 0.0)
    }

    /** Resuming a session: "last time" is the session before, never the sets already on screen. */
    @Test
    fun lastTimeNeverCountsTheSessionBeingTrained() = runTest(mainRule.dispatcher) {
        val repo = routineRepo().apply {
            recentSetLogs = listOf(
                SetLog(sessionId = 9, exerciseId = 1, setNumber = 1, weightKg = 85.0, reps = 6, performedAt = 5_000L),
                SetLog(sessionId = 2, exerciseId = 1, setNumber = 1, weightKg = 77.5, reps = 6, performedAt = 1_000L),
            )
        }
        val vm = routineViewModel(repo, sessionId = 9L)
        backgroundScope.launch { vm.uiState.collect {} }

        assertEquals(2L, vm.uiState.value.exerciseBlocks.single().lastTopSet!!.sessionId)
    }

    /** The rest after a set is the routine's for that exercise — it was a flat 90 s whatever it said. */
    @Test
    fun aLoggedSetRestsForTheRoutinesRest() = runTest(mainRule.dispatcher) {
        val vm = routineViewModel(routineRepo())
        backgroundScope.launch { vm.uiState.collect {} }

        vm.addSet(exerciseId = 1L, weightKg = 80.0, reps = 6, notes = null)

        assertEquals(150, vm.restTotal.value)
        vm.stopRest()
    }

    @Test
    fun aFreeSessionSetRestsForTheDefault() = runTest(mainRule.dispatcher) {
        val vm = viewModel(FakeWorkoutRepository())
        backgroundScope.launch { vm.uiState.collect {} }

        vm.addSet(exerciseId = 1L, weightKg = 80.0, reps = 6, notes = null)

        assertEquals(90, vm.restTotal.value)
        vm.stopRest()
    }

    @Test
    fun generateRecoveryAdviceStreamsIntoState() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo, llm = FakeLlmInferenceService(chunks = listOf("Descansa un poco")))

        vm.generateRecoveryAdvice(MuscleGroup.QUADS)

        val result = vm.recoveryAdvice.value
        assertTrue(result is SuggestionState.Success && result.isComplete)
        assertEquals("Descansa un poco", (result as SuggestionState.Success).suggestion)
    }

    @Test
    fun dismissRecoveryAdviceClearsState() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo, llm = FakeLlmInferenceService(chunks = listOf("ok")))

        vm.generateRecoveryAdvice(MuscleGroup.QUADS)
        assertTrue(vm.recoveryAdvice.value != null)

        vm.dismissRecoveryAdvice()

        assertEquals(null, vm.recoveryAdvice.value)
    }
}
