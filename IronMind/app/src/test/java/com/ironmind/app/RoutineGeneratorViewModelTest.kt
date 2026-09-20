package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.usecase.GenerateRoutineUseCase
import com.ironmind.app.ui.routinegenerator.RoutineGeneratorViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineGeneratorViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val ohp = Exercise(id = 2, name = "Overhead Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL)

    private fun viewModel(repo: FakeWorkoutRepository, llm: FakeLlmInferenceService) =
        RoutineGeneratorViewModel(repo, GenerateRoutineUseCase(repo, llm))

    @Test
    fun generate_populatesDraftRowsOnSuccess() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply { exercisesFlow.value = listOf(bench, ohp) }
        val llm = FakeLlmInferenceService(chunks = listOf("1|3|10|90\n2|4|8|120"))
        val vm = viewModel(repo, llm)
        vm.setSplit(RoutineSplit.PUSH)

        vm.generate()

        assertTrue(vm.draftState.value is RoutineDraftState.Success)
        assertEquals(setOf(1L, 2L), vm.draftRows.value.map { it.exerciseId }.toSet())
    }

    @Test
    fun generate_doesNothingWhenNoEquipmentSelected() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply { exercisesFlow.value = listOf(bench) }
        val vm = viewModel(repo, FakeLlmInferenceService())
        Equipment.entries.forEach { vm.toggleEquipment(it) } // deselect everything

        assertFalse(vm.ui.value.canGenerate)
        vm.generate()

        assertEquals(null, vm.draftState.value)
    }

    @Test
    fun removeRow_dropsOnlyThatExercise() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply { exercisesFlow.value = listOf(bench, ohp) }
        val llm = FakeLlmInferenceService(chunks = listOf("1|3|10|90\n2|4|8|120"))
        val vm = viewModel(repo, llm)
        vm.generate()

        vm.removeRow(1L)

        assertEquals(listOf(2L), vm.draftRows.value.map { it.exerciseId })
    }

    @Test
    fun updateRow_changesOnlyTheTargetedExercisesValues() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply { exercisesFlow.value = listOf(bench, ohp) }
        val llm = FakeLlmInferenceService(chunks = listOf("1|3|10|90\n2|4|8|120"))
        val vm = viewModel(repo, llm)
        vm.generate()

        vm.updateRow(exerciseId = 1L, sets = 5, reps = 6, restSeconds = 150)

        val updated = vm.draftRows.value.first { it.exerciseId == 1L }
        val untouched = vm.draftRows.value.first { it.exerciseId == 2L }
        assertEquals(5, updated.sets)
        assertEquals(6, updated.reps)
        assertEquals(150, updated.restSeconds)
        assertEquals(4, untouched.sets) // unaffected
    }

    @Test
    fun save_createsRoutineAndAddsEachDraftRowWithItsOwnPrescription() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply {
            exercisesFlow.value = listOf(bench, ohp)
            newRoutineId = 42L
        }
        val llm = FakeLlmInferenceService(chunks = listOf("1|3|10|90\n2|4|8|120"))
        val vm = viewModel(repo, llm)
        vm.setRoutineName("Push del lunes")
        vm.generate()

        var savedId: Long? = null
        vm.save { id -> savedId = id }

        assertEquals(42L, savedId)
        assertEquals("Push del lunes", repo.upsertedRoutines.single().name)
        assertEquals(2, repo.addedToRoutine.size)
        assertEquals(listOf(1L, 2L), repo.addedToRoutine.map { it.exerciseId })
        assertEquals(listOf(0, 1), repo.addedToRoutine.map { it.position })
        // The point of this whole feature: each exercise keeps its own AI-proposed prescription,
        // not the app-wide default (3 sets / 10 reps / 90s) every other routine gets.
        val benchCall = repo.addedToRoutine.first { it.exerciseId == 1L }
        assertEquals(3, benchCall.targetSets)
        assertEquals(10, benchCall.targetReps)
        assertEquals(90, benchCall.targetRestSeconds)
        val ohpCall = repo.addedToRoutine.first { it.exerciseId == 2L }
        assertEquals(4, ohpCall.targetSets)
        assertEquals(8, ohpCall.targetReps)
        assertEquals(120, ohpCall.targetRestSeconds)
    }

    @Test
    fun save_doesNothingWhenDraftIsEmpty() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = viewModel(repo, FakeLlmInferenceService())

        var called = false
        vm.save { called = true }

        assertFalse(called)
        assertTrue(repo.upsertedRoutines.isEmpty())
    }
}
