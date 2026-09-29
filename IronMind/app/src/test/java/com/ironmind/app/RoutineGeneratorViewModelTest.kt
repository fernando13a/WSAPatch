package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Limitation
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineDraft
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.usecase.GenerateRoutineUseCase
import com.ironmind.app.domain.util.RoutinePrescription
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
    private val lateral = Exercise(id = 3, name = "Lateral Raise", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.DUMBBELL)

    /** Shortlist order for these three on a push day: bench(1), ohp(2), lateral(3). */
    private val catalog = listOf(bench, ohp, lateral)
    private val pickAll = FakeLlmInferenceService(chunks = listOf("1, 2, 3"))

    private fun viewModel(repo: FakeWorkoutRepository, llm: FakeLlmInferenceService) =
        RoutineGeneratorViewModel(repo, GenerateRoutineUseCase(repo, llm), FakeAppPreferences())

    private fun repo() = FakeWorkoutRepository().apply { exercisesFlow.value = catalog }

    @Test
    fun generate_populatesDraftRowsOnSuccess() = runTest(mainRule.dispatcher) {
        val vm = viewModel(repo(), pickAll)
        vm.setSplit(RoutineSplit.PUSH)

        vm.generate()

        assertTrue(vm.draftState.value is RoutineDraftState.Success)
        assertEquals(setOf(1L, 2L, 3L), vm.draftRows.value.map { it.exerciseId }.toSet())
    }

    /** Even with nothing usable from the model, the athlete gets rows to edit and save. */
    @Test
    fun generate_stillPopulatesRowsWhenTheModelIsUnusable() = runTest(mainRule.dispatcher) {
        val vm = viewModel(repo(), FakeLlmInferenceService(chunks = listOf("no sé")))

        vm.generate()

        val draft = (vm.draftState.value as RoutineDraftState.Success).draft
        assertEquals(RoutineDraft.Source.RULES, draft.source)
        assertTrue(vm.draftRows.value.isNotEmpty())
    }

    @Test
    fun generate_doesNothingWhenNoEquipmentSelected() = runTest(mainRule.dispatcher) {
        val vm = viewModel(repo(), FakeLlmInferenceService())
        Equipment.entries.forEach { vm.toggleEquipment(it) } // deselect everything

        assertFalse(vm.ui.value.canGenerate)
        vm.generate()

        assertEquals(null, vm.draftState.value)
    }

    @Test
    fun removeRow_dropsOnlyThatExercise() = runTest(mainRule.dispatcher) {
        val vm = viewModel(repo(), pickAll)
        vm.generate()

        vm.removeRow(1L)

        assertEquals(listOf(2L, 3L), vm.draftRows.value.map { it.exerciseId })
    }

    @Test
    fun updateRow_changesOnlyTheTargetedExercisesValues() = runTest(mainRule.dispatcher) {
        val vm = viewModel(repo(), pickAll)
        vm.generate()
        val before = vm.draftRows.value.first { it.exerciseId == 2L }

        vm.updateRow(exerciseId = 1L, sets = 5, reps = 6, restSeconds = 150)

        val updated = vm.draftRows.value.first { it.exerciseId == 1L }
        assertEquals(5, updated.sets)
        assertEquals(6, updated.reps)
        assertEquals(150, updated.restSeconds)
        assertEquals(before, vm.draftRows.value.first { it.exerciseId == 2L }) // unaffected
    }

    @Test
    fun save_createsRoutineAndAddsEachDraftRowWithItsOwnPrescription() = runTest(mainRule.dispatcher) {
        val repo = repo().apply { newRoutineId = 42L }
        val vm = viewModel(repo, pickAll)
        vm.setRoutineName("Push del lunes")
        vm.generate()

        var savedId: Long? = null
        vm.save { id -> savedId = id }

        assertEquals(42L, savedId)
        assertEquals("Push del lunes", repo.upsertedRoutines.single().name)
        // Compounds first (bench, ohp), then the isolation raise.
        assertEquals(listOf(1L, 2L, 3L), repo.addedToRoutine.map { it.exerciseId })
        assertEquals(listOf(0, 1, 2), repo.addedToRoutine.map { it.position })

        // Each row keeps its own prescription rather than the app-wide 3 × 10 / 90 s default:
        // heavier work for the compound, lighter and shorter rest for the isolation.
        val compound = RoutinePrescription.forExercise(TrainingGoal.HYPERTROPHY, compound = true)
        val isolation = RoutinePrescription.forExercise(TrainingGoal.HYPERTROPHY, compound = false)
        val benchCall = repo.addedToRoutine.first { it.exerciseId == 1L }
        assertEquals(compound.sets, benchCall.targetSets)
        assertEquals(compound.reps, benchCall.targetReps)
        assertEquals(compound.restSeconds, benchCall.targetRestSeconds)
        val raiseCall = repo.addedToRoutine.first { it.exerciseId == 3L }
        assertEquals(isolation.sets, raiseCall.targetSets)
        assertEquals(isolation.reps, raiseCall.targetReps)
        assertEquals(isolation.restSeconds, raiseCall.targetRestSeconds)
    }

    @Test
    fun save_labelsOnlyAModelChoiceAsAi() = runTest(mainRule.dispatcher) {
        val aiRepo = repo()
        viewModel(aiRepo, pickAll).apply { generate(); save {} }
        assertEquals("PUSH (IA)", aiRepo.upsertedRoutines.single().name)

        val rulesRepo = repo()
        viewModel(rulesRepo, FakeLlmInferenceService(chunks = listOf("no sé"))).apply { generate(); save {} }
        assertEquals("PUSH", rulesRepo.upsertedRoutines.single().name)
    }

    @Test
    fun defaultsToAnHourAndNoLimitations() = runTest(mainRule.dispatcher) {
        val vm = viewModel(repo(), pickAll)

        assertEquals(60, vm.ui.value.timeBudgetMinutes)
        assertTrue(vm.ui.value.avoid.isEmpty())
    }

    /** The form's limitation chips must actually reach the shortlist, not just the UI state. */
    @Test
    fun aToggledLimitationIsAppliedToTheGeneratedRoutine() = runTest(mainRule.dispatcher) {
        val squat = Exercise(id = 10, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)
        val curl = Exercise(id = 11, name = "Lying Leg Curl", muscleGroup = MuscleGroup.HAMSTRINGS, equipment = Equipment.MACHINE)
        val repo = FakeWorkoutRepository().apply { exercisesFlow.value = listOf(squat, curl) }
        val vm = viewModel(repo, FakeLlmInferenceService(chunks = listOf("1, 2")))
        vm.setSplit(RoutineSplit.LEGS)

        vm.toggleLimitation(Limitation.KNEE)
        vm.generate()

        assertEquals(listOf(11L), vm.draftRows.value.map { it.exerciseId })

        vm.toggleLimitation(Limitation.KNEE) // off again
        vm.generate()

        assertTrue(10L in vm.draftRows.value.map { it.exerciseId })
    }

    @Test
    fun theChosenTimeBudgetIsKept() = runTest(mainRule.dispatcher) {
        val vm = viewModel(repo(), pickAll)

        vm.setTimeBudget(30)

        assertEquals(30, vm.ui.value.timeBudgetMinutes)
    }

    @Test
    fun editingARowKeepsItsLastPerformance() = runTest(mainRule.dispatcher) {
        val repo = repo().apply {
            recentActivity = listOf(
                com.ironmind.app.domain.model.SetLog(
                    sessionId = 1, exerciseId = 1, setNumber = 1, weightKg = 80.0, reps = 5,
                    performedAt = System.currentTimeMillis() - 5L * 24 * 3_600_000,
                ),
            )
        }
        val vm = viewModel(repo, pickAll)
        vm.generate()

        vm.updateRow(exerciseId = 1L, sets = 5, reps = 3, restSeconds = 180)

        val row = vm.draftRows.value.first { it.exerciseId == 1L }
        assertEquals(80.0, row.lastWeightKg!!, 0.0)
        assertEquals(5, row.lastReps)
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
