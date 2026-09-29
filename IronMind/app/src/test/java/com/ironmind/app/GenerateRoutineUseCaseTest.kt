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

    /**
     * The error has to carry what the model said: "no valid routine" alone couldn't tell a model
     * ignoring the format from one using a format the parser didn't know, and each needs a
     * different fix.
     */
    @Test
    fun invoke_unparseableErrorShowsWhatTheModelSaid() = runTest {
        val llm = FakeLlmInferenceService(chunks = listOf("Claro, aquí tienes ", "una rutina genial"))
        val useCase = GenerateRoutineUseCase(repository(listOf(bench)), llm)

        val last = useCase(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY).toList().last()

        val message = (last as RoutineDraftState.Error).message
        assertTrue(message, message.contains("Claro, aquí tienes una rutina genial"))
    }

    @Test
    fun invoke_anEmptyAnswerIsReportedAsEmpty() = runTest {
        val llm = FakeLlmInferenceService(chunks = listOf("  ", "\n"))
        val useCase = GenerateRoutineUseCase(repository(listOf(bench)), llm)

        val last = useCase(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY).toList().last()

        val message = (last as RoutineDraftState.Error).message
        assertTrue(message, message.contains("sin responder nada"))
    }

    @Test
    fun invoke_aLongAnswerIsCutShortInTheError() = runTest {
        val llm = FakeLlmInferenceService(chunks = listOf("x".repeat(5_000)))
        val useCase = GenerateRoutineUseCase(repository(listOf(bench)), llm)

        val last = useCase(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY).toList().last()

        val message = (last as RoutineDraftState.Error).message
        assertTrue("was ${message.length} chars", message.length < 500)
        assertTrue(message.endsWith("…"))
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
