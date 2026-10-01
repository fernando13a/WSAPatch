package com.ironmind.app

import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.RoutineExplanationPromptBuilder
import com.ironmind.app.domain.ai.RoutineRefinePromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineDraftExercise
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.usecase.ExplainRoutineUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineExplainAndRefineTest {

    private val bench = Exercise(id = 1, name = "Bench Press", nameEs = "Press de banca", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val raise = Exercise(id = 2, name = "Lateral Raise", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.DUMBBELL)
    private val byId = listOf(bench, raise).associateBy { it.id }
    private val rows = listOf(
        RoutineDraftExercise(exerciseId = 1, sets = 4, reps = 8, restSeconds = 90),
        RoutineDraftExercise(exerciseId = 2, sets = 3, reps = 12, restSeconds = 60),
    )

    // ---- explanation prompt ---------------------------------------------------------------

    @Test
    fun theExplanationPromptCarriesTheFinishedSession() {
        val prompt = RoutineExplanationPromptBuilder.build(
            RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, rows, byId, adjustments = listOf("Incluye 1 ejercicio que ya haces."),
        )

        assertTrue(prompt, prompt.contains("1. Press de banca — pecho: 4 × 8, descanso 90 s"))
        assertTrue(prompt, prompt.contains("2. Lateral Raise — hombro: 3 × 12, descanso 60 s"))
        assertTrue(prompt, prompt.contains("Objetivo: hipertrofia"))
        assertTrue(prompt, prompt.contains("- Incluye 1 ejercicio que ya haces."))
        // It explains; it doesn't get to redesign.
        assertTrue(prompt, prompt.contains("No cambies la rutina"))
    }

    // ---- explanation use case -------------------------------------------------------------

    private suspend fun explain(llm: FakeLlmInferenceService, withRows: List<RoutineDraftExercise> = rows) =
        ExplainRoutineUseCase(llm)(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, withRows, byId).toList()

    @Test
    fun theExplanationTypesItselfOutAndThenCompletes() = runTest {
        val states = explain(FakeLlmInferenceService(chunks = listOf("Empiezas ", "con lo pesado.")))

        assertEquals(SuggestionState.Loading, states.first())
        assertEquals(SuggestionState.Success("Empiezas ", isComplete = false), states[1])
        assertEquals(SuggestionState.Success("Empiezas con lo pesado.", isComplete = true), states.last())
    }

    @Test
    fun anEmptyExplanationIsAnErrorNotABlankCard() = runTest {
        assertTrue(explain(FakeLlmInferenceService(chunks = listOf("  "))).last() is SuggestionState.Error)
    }

    @Test
    fun aMissingModelPointsAtTheDownload() = runTest {
        val last = explain(FakeLlmInferenceService(error = LlmModelNotFoundException("/m"))).last()

        assertTrue((last as SuggestionState.Error).message.contains("Modelo de IA"))
    }

    @Test
    fun nothingToExplainWithoutRows() = runTest {
        val llm = FakeLlmInferenceService(chunks = listOf("x"))

        assertTrue(explain(llm, withRows = emptyList()).last() is SuggestionState.Error)
        assertTrue("the model must not be asked", llm.prompts.isEmpty())
    }

    // ---- refine prompt --------------------------------------------------------------------

    private fun refinePrompt(instruction: String) = RoutineRefinePromptBuilder.build(
        RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, listOf(bench, raise), currentCount = 1, instruction = instruction,
    )

    /** The current session is positions 1..n, so "as it is" is a trivial answer to adapt. */
    @Test
    fun theRefinePromptListsTheCurrentSessionAsLeadingPositions() {
        val prompt = refinePrompt("más hombro")

        assertTrue(prompt, prompt.contains("1. Press de banca — pecho"))
        assertTrue(prompt, prompt.contains("2. Lateral Raise — hombro"))
        assertTrue(prompt, prompt.contains("Sesión actual: 1\n"))
        assertTrue(prompt, prompt.contains("«más hombro»"))
        assertTrue(prompt, prompt.contains("SOLO con los números"))
    }

    /** Free text is capped and flattened so it can't crowd the list out of the token budget. */
    @Test
    fun aLongOrMultiLineRequestIsFlattenedAndCapped() {
        val prompt = refinePrompt("sin\n\nsentadilla " + "muy ".repeat(200))

        val request = prompt.substringAfter("«").substringBefore("»")
        assertFalse(request.contains("\n"))
        assertTrue(request.startsWith("sin sentadilla"))
        assertTrue(request.length <= RoutineRefinePromptBuilder.MAX_INSTRUCTION_CHARS)
    }
}
