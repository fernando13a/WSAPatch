package com.ironmind.app

import androidx.lifecycle.SavedStateHandle
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutinePlan
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.ui.navigation.Destinations
import com.ironmind.app.ui.routineedit.RoutineEditViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineEditViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val bench = Exercise(id = 1, name = "Bench", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val ohp = Exercise(id = 2, name = "OHP", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL)

    private fun newRoutineVm(repo: FakeWorkoutRepository) =
        RoutineEditViewModel(repo, SavedStateHandle(mapOf(Destinations.ARG_ROUTINE_ID to 0L)))

    @Test
    fun addingExercisesEnablesSaveAndPersistsWithOrder() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository().apply { exercisesFlow.value = listOf(bench, ohp) }
        val vm = newRoutineVm(repo)

        assertFalse(vm.ui.value.canSave)
        vm.setName("Push")
        vm.setSplit(RoutineSplit.PUSH)
        vm.addExercise(bench)
        vm.addExercise(ohp)

        assertEquals(2, vm.ui.value.selected.size)
        assertTrue(vm.ui.value.canSave)

        vm.move(0, 1) // now [OHP, Bench]

        var done = false
        vm.save { done = true }

        assertTrue(done)
        assertEquals(1, repo.upsertedRoutines.size)
        assertEquals("Push", repo.upsertedRoutines.first().name)
        assertEquals(listOf(ohp.id, bench.id), repo.addedToRoutine.map { it.exerciseId })
        assertEquals(listOf(0, 1), repo.addedToRoutine.map { it.position })
    }

    /**
     * Editing an existing routine is the path that was broken and untested: Room's @Upsert returns
     * -1 on its UPDATE path, so every junction write went to routine -1 — reorders matched
     * nothing, removals deleted nothing, and a newly added exercise hit a foreign-key violation.
     */
    @Test
    fun editingAnExistingRoutineReordersAgainstItsRealId() = runTest(mainRule.dispatcher) {
        val existing = Routine(
            id = 3,
            name = "Push",
            split = RoutineSplit.PUSH,
            description = "Generada por IA",
            position = 4,
            createdAt = 1_000L,
        )
        val repo = FakeWorkoutRepository().apply {
            exercisesFlow.value = listOf(bench, ohp)
            routineToReturn = existing
            routinePlanFlow.value = RoutinePlan(routine = existing, exercises = listOf(bench, ohp))
        }
        val vm = RoutineEditViewModel(repo, SavedStateHandle(mapOf(Destinations.ARG_ROUTINE_ID to 3L)))

        vm.move(0, 1) // [OHP, Bench]
        vm.save {}

        // Reordered, not re-added: addExerciseToRoutine would reset sets/reps/rest to its defaults.
        assertTrue(repo.addedToRoutine.isEmpty())
        assertEquals(listOf(3L, 3L), repo.repositionedInRoutine.map { it.first })
        assertEquals(listOf(ohp.id to 0, bench.id to 1), repo.repositionedInRoutine.map { it.second to it.third })
    }

    @Test
    fun renamingARoutineKeepsTheFieldsTheEditorDoesNotShow() = runTest(mainRule.dispatcher) {
        val existing = Routine(
            id = 3,
            name = "Push",
            split = RoutineSplit.PUSH,
            description = "Generada por IA",
            position = 4,
            createdAt = 1_000L,
        )
        val repo = FakeWorkoutRepository().apply {
            exercisesFlow.value = listOf(bench)
            routineToReturn = existing
            routinePlanFlow.value = RoutinePlan(routine = existing, exercises = listOf(bench))
        }
        val vm = RoutineEditViewModel(repo, SavedStateHandle(mapOf(Destinations.ARG_ROUTINE_ID to 3L)))

        vm.setName("Empuje")
        vm.save {}

        val saved = repo.upsertedRoutines.single()
        assertEquals("Empuje", saved.name)
        // Rebuilding the row from name+split alone reset these to the data-class defaults,
        // destroying the description and moving the routine in the list.
        assertEquals("Generada por IA", saved.description)
        assertEquals(4, saved.position)
        assertEquals(1_000L, saved.createdAt)
    }

    @Test
    fun createExerciseAddsItToSelectionAndCatalog() = runTest(mainRule.dispatcher) {
        val repo = FakeWorkoutRepository()
        val vm = newRoutineVm(repo)

        vm.createExercise("Cable Fly", MuscleGroup.CHEST, Equipment.CABLE)

        assertEquals(1, repo.upsertedExercises.size)
        assertEquals("Cable Fly", repo.upsertedExercises.first().name)
        assertTrue(vm.ui.value.selected.any { it.name == "Cable Fly" })
    }
}
