package com.ironmind.app

import androidx.lifecycle.SavedStateHandle
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.usecase.GetRecoveryAdviceUseCase
import com.ironmind.app.ui.navigation.Destinations
import com.ironmind.app.ui.session.SessionViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
