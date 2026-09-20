package com.ironmind.app

import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.usecase.GenerateRoutineUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateRoutineUseCaseTest {

    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val ohp = Exercise(id = 2, name = "Overhead Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL)

    private fun repository(exercises: List<Exercise>) = FakeWorkoutRepository().apply {
        exercisesFlow.value = exercises
    }

    @Test
    fun invoke_parsesAValidDraftFromTheModelResponse() = runTest {
        val repo = repository(listOf(bench, ohp))
        val llm = FakeLlmInferenceService(chunks = listOf("1|3|10|90\n", "2|4|8|120"))
        val useCase = GenerateRoutineUseCase(repo, llm)

        val states = useCase(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY).toList()

        assertEquals(RoutineDraftState.Loading, states.first())
        val last = states.last()
        assertTrue(last is RoutineDraftState.Success)
        val draft = (last as RoutineDraftState.Success).draft
        assertEquals(RoutineSplit.PUSH, draft.split)
        assertEquals(setOf(1L, 2L), draft.exercises.map { it.exerciseId }.toSet())
    }

    @Test
    fun invoke_emitsErrorWhenNoCandidatesMatchTheSplit() = runTest {
        // Only a QUADS exercise exists — nothing matches a PUSH split.
        val repo = repository(listOf(Exercise(id = 9, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)))
        val useCase = GenerateRoutineUseCase(repo, FakeLlmInferenceService())

        val last = useCase(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY).toList().last()

        assertTrue(last is RoutineDraftState.Error)
    }

    @Test
    fun invoke_emitsErrorWhenTheModelResponseParsesToNothing() = runTest {
        val repo = repository(listOf(bench))
        val llm = FakeLlmInferenceService(chunks = listOf("Lo siento, no puedo ayudar con eso."))
        val useCase = GenerateRoutineUseCase(repo, llm)

        val last = useCase(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY).toList().last()

        assertTrue(last is RoutineDraftState.Error)
    }

    @Test
    fun invoke_mapsModelNotFoundToFriendlyError() = runTest {
        val repo = repository(listOf(bench))
        val llm = FakeLlmInferenceService(error = LlmModelNotFoundException("/models/x.bin"))
        val useCase = GenerateRoutineUseCase(repo, llm)

        val last = useCase(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY).toList().last()

        assertTrue(last is RoutineDraftState.Error)
        assertTrue((last as RoutineDraftState.Error).message.contains("modelo", ignoreCase = true))
    }

    @Test
    fun invoke_respectsTheEquipmentFilter() = runTest {
        val dumbbellOnly = Exercise(id = 3, name = "Dumbbell Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.DUMBBELL)
        val repo = repository(listOf(bench, dumbbellOnly)) // bench is BARBELL
        val llm = FakeLlmInferenceService(chunks = listOf("3|3|10|90"))
        val useCase = GenerateRoutineUseCase(repo, llm)

        val last = useCase(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, availableEquipment = setOf(Equipment.DUMBBELL)).toList().last()

        assertTrue(last is RoutineDraftState.Success)
        assertEquals(listOf(3L), (last as RoutineDraftState.Success).draft.exercises.map { it.exerciseId })
    }
}
