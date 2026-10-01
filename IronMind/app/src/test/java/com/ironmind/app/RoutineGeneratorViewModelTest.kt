package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Limitation
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineDraft
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineRefineState
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.usecase.ExplainRoutineUseCase
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.usecase.GenerateRoutineUseCase
import com.ironmind.app.domain.util.RoutinePrescription
import com.ironmind.app.domain.util.StartingWeight
import com.ironmind.app.ui.routinegenerator.RoutineGeneratorViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
        RoutineGeneratorViewModel(repo, GenerateRoutineUseCase(repo, llm), ExplainRoutineUseCase(llm), FakeAppPreferences())

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

    /**
     * The weight the draft suggested is saved with the routine, for the reps actually saved — so
     * the session can show what to load. Before, it was lost the moment the routine was saved.
     */
    @Test
    fun save_storesTheSuggestedWeightForTheSavedReps() = runTest(mainRule.dispatcher) {
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
        vm.updateRow(exerciseId = 1L, sets = 4, reps = 8, restSeconds = 120)

        vm.save {}

        val benchCall = repo.addedToRoutine.first { it.exerciseId == 1L }
        assertEquals(StartingWeight.suggestKg(80.0, 5, 8)!!, benchCall.targetWeightKg!!, 0.0)
        // Never done before: no history, no made-up number.
        assertEquals(null, repo.addedToRoutine.first { it.exerciseId == 3L }.targetWeightKg)
    }

    // ---- Cambiar --------------------------------------------------------------------------

    private val dbPress = Exercise(id = 4, name = "Dumbbell Shoulder Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.DUMBBELL)
    private val frontRaise = Exercise(id = 5, name = "Front Raise", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.DUMBBELL)

    @Test
    fun openSwapOffersSameMuscleAlternativesNotAlreadyInTheDraft() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply { exercisesFlow.value = catalog + dbPress + frontRaise }
        val vm = viewModel(repo, FakeLlmInferenceService(chunks = listOf("1, 2, 3")))
        vm.setTimeBudget(30) // no room to top up, so the draft is exactly bench, ohp, lateral
        vm.generate()
        val draftIds = vm.draftRows.value.map { it.exerciseId }.toSet()

        vm.openSwap(ohp.id)

        val options = vm.swapMenu.value!!.options
        assertTrue(options.isNotEmpty())
        assertTrue(options.all { it.muscleGroup == MuscleGroup.SHOULDERS })
        assertTrue("never offers what's already there", options.none { it.id in draftIds })
        assertEquals("a press for a press first", dbPress.id, options.first().id)
    }

    /** Unticking every chip after generating used to leave every "Cambiar" menu empty. */
    @Test
    fun swapsFollowTheEquipmentTheDraftWasBuiltWithNotTheFormNow() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply { exercisesFlow.value = catalog + dbPress + frontRaise }
        val vm = viewModel(repo, pickAll)
        vm.setTimeBudget(30)
        vm.generate()
        Equipment.entries.forEach { vm.toggleEquipment(it) }
        assertTrue(vm.ui.value.availableEquipment.isEmpty())

        vm.openSwap(ohp.id)

        assertEquals(dbPress.id, vm.swapMenu.value!!.options.first().id)
    }

    /** A push day saved after moving the split chip used to be filed as the new split. */
    @Test
    fun save_filesTheRoutineUnderTheSplitItWasBuiltFor() = runTest(mainRule.dispatcher) {
        val repo = repo()
        val vm = viewModel(repo, pickAll)
        vm.generate() // push, the form's default
        vm.setSplit(RoutineSplit.LEGS)

        vm.save {}

        assertEquals(RoutineSplit.PUSH, repo.upsertedRoutines.single().split)
        assertEquals("PUSH (IA)", repo.upsertedRoutines.single().name)
    }

    @Test
    fun swappingLikeForLikeKeepsTheRowsNumbers() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply { exercisesFlow.value = catalog + dbPress }
        val vm = viewModel(repo, pickAll)
        vm.setTimeBudget(30) // keeps dbPress out of the draft, so it's a real swap target
        vm.generate()
        vm.updateRow(ohp.id, sets = 5, reps = 6, restSeconds = 120)

        vm.swapRow(ohp.id, dbPress)

        val row = vm.draftRows.value.single { it.exerciseId == dbPress.id }
        assertEquals(5, row.sets)
        assertEquals(6, row.reps)
        assertTrue(vm.draftRows.value.none { it.exerciseId == ohp.id })
        assertEquals(null, vm.swapMenu.value)
    }

    /** Four heavy sets of eight make no sense for a raise: across kinds, the goal re-prescribes. */
    @Test
    fun swappingACompoundForAnIsolationRePrescribes() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply { exercisesFlow.value = catalog + frontRaise }
        val vm = viewModel(repo, pickAll)
        vm.setTimeBudget(30) // keeps frontRaise out of the draft
        vm.generate()
        assertTrue(vm.draftRows.value.none { it.exerciseId == frontRaise.id })

        vm.swapRow(ohp.id, frontRaise)

        val row = vm.draftRows.value.single { it.exerciseId == frontRaise.id }
        val isolation = RoutinePrescription.forExercise(TrainingGoal.HYPERTROPHY, compound = false)
        assertEquals(isolation.reps, row.reps)
        assertEquals(isolation.restSeconds, row.restSeconds)
    }

    @Test
    fun swappingToAnExerciseAlreadyInTheDraftDoesNothing() = runTest(mainRule.dispatcher) {
        val vm = viewModel(repo(), pickAll)
        vm.generate()
        val before = vm.draftRows.value

        vm.swapRow(ohp.id, bench) // bench is already a row

        assertEquals(before, vm.draftRows.value)
    }

    // ---- Ajustar con texto ----------------------------------------------------------------

    @Test
    fun anAppliedRefineReplacesTheRows() = runTest(mainRule.dispatcher) {
        // One fake answers both requests with "1, 3". Generating: bench and lateral, with ohp
        // topped up for time → bench, ohp, lateral. Refining lists that session first, so "1, 3"
        // now means bench and lateral: the press is dropped.
        val vm = viewModel(repo(), FakeLlmInferenceService(chunks = listOf("1, 3")))
        vm.generate()
        assertEquals(listOf(bench.id, ohp.id, lateral.id), vm.draftRows.value.map { it.exerciseId })

        vm.refine("sin press militar")

        assertTrue(vm.refineState.value is RoutineRefineState.Applied)
        assertEquals(listOf(bench.id, lateral.id), vm.draftRows.value.map { it.exerciseId })
    }

    /**
     * The answer is built from the rows as they were when asked. Applying it after the athlete
     * edited the draft would undo the edit — or bring back a row they had just removed.
     */
    @Test
    fun aRefineThatAnswersAfterAnEditKeepsTheEdit() = runTest(mainRule.dispatcher) {
        val llm = FakeLlmInferenceService(chunks = listOf("1, 3"))
        val vm = viewModel(repo(), llm)
        vm.generate()
        val thinking = CompletableDeferred<Unit>()
        llm.gate = thinking

        vm.refine("sin press militar")
        vm.updateRow(lateral.id, sets = 5, reps = 15, restSeconds = 45) // while the model thinks
        thinking.complete(Unit)
        advanceUntilIdle()

        assertTrue("${vm.refineState.value}", vm.refineState.value is RoutineRefineState.Failed)
        assertEquals(listOf(bench.id, ohp.id, lateral.id), vm.draftRows.value.map { it.exerciseId })
        assertEquals(5, vm.draftRows.value.single { it.exerciseId == lateral.id }.sets)
    }

    @Test
    fun aFailedRefineLeavesTheDraftExactlyAsItWas() = runTest(mainRule.dispatcher) {
        val vm = viewModel(repo(), pickAll)
        vm.generate()
        vm.updateRow(bench.id, sets = 5, reps = 5, restSeconds = 180)
        val before = vm.draftRows.value

        vm.refine("   ") // nothing to apply

        assertTrue(vm.refineState.value is RoutineRefineState.Failed)
        assertEquals(before, vm.draftRows.value)
    }

    // ---- ¿Por qué esta rutina? ------------------------------------------------------------

    @Test
    fun explainStreamsTheModelsAnswer() = runTest(mainRule.dispatcher) {
        val llm = FakeLlmInferenceService(chunks = listOf("1, 2, 3"))
        val vm = viewModel(repo(), llm)
        vm.generate()

        vm.explain()

        val state = vm.explanation.value
        assertTrue("was $state", state is SuggestionState.Success && state.isComplete)
        assertTrue("explained with prose sampling", llm.lastTemperature == null)
    }

    /** An explanation of rows that changed since would describe a routine that isn't there. */
    @Test
    fun changingTheRowsClearsTheExplanation() = runTest(mainRule.dispatcher) {
        val vm = viewModel(repo(), pickAll)
        vm.generate()
        vm.explain()

        vm.removeRow(bench.id)

        assertEquals(null, vm.explanation.value)
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
