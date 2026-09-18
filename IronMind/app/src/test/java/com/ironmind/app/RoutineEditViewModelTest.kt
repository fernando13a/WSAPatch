package com.ironmind.app

import androidx.lifecycle.SavedStateHandle
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
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
        assertEquals(listOf(ohp.id, bench.id), repo.addedToRoutine.map { it.second })
        assertEquals(listOf(0, 1), repo.addedToRoutine.map { it.third })
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
