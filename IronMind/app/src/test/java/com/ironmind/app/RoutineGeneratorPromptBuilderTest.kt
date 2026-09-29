package com.ironmind.app

import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.domain.ai.RoutineGeneratorPromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.util.RoutineCandidateSelector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineGeneratorPromptBuilderTest {

    private val candidates = listOf(
        Exercise(id = 41, name = "Barbell Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
        Exercise(id = 77, name = "Overhead Press", nameEs = "Press militar", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL),
    )

    private fun prompt(goal: TrainingGoal = TrainingGoal.HYPERTROPHY, list: List<Exercise> = candidates) =
        RoutineGeneratorPromptBuilder.build(RoutineSplit.PUSH, goal, list)

    /** Positions 1..N with the muscle group, not catalog ids: short and never mistaken for reps. */
    @Test
    fun listsCandidatesByPositionWithTheirMuscleGroup() {
        val text = prompt()

        assertTrue(text, text.contains("1. Barbell Bench Press — pecho"))
        assertTrue(text, text.contains("2. Press militar — hombro"))
        assertFalse("catalog ids must not leak into the list", text.contains("41"))
    }

    @Test
    fun asksOnlyForTheChosenNumbers() {
        val text = prompt()

        assertTrue(text.contains("SOLO con los números"))
        assertTrue(text.contains("separados por comas"))
    }

    /** Sets, reps and rest are the assembler's job now; asking for them is what broke parsing. */
    @Test
    fun doesNotAskTheModelForSetsRepsOrRest() {
        val text = prompt()

        assertFalse(text.contains("repeticiones"))
        assertFalse(text.contains("descanso"))
        assertFalse(text.contains("|"))
    }

    /** A small model often copies the example; every number in it must be a real position. */
    @Test
    fun theExampleOnlyUsesPositionsThatExist() {
        val example = prompt().lineSequence().first { it.startsWith("Ejemplo de formato:") }
        val numbers = Regex("""\d+""").findAll(example).map { it.value.toInt() }.toList()

        assertTrue(example, numbers.isNotEmpty())
        assertTrue(example, numbers.all { it in 1..candidates.size })
    }

    @Test
    fun reflectsTheGoal() {
        assertTrue(prompt(TrainingGoal.STRENGTH).contains("fuerza"))
        assertTrue(prompt(TrainingGoal.ENDURANCE).contains("resistencia"))
    }

    @Test
    fun worstCaseCandidateCountAndNamesStaysWithinPromptBudget() {
        val longName = "Standing Barbell Overhead Military Press Variation"
        val worstCaseCandidates = (1..RoutineCandidateSelector.MAX_CANDIDATES).map {
            Exercise(id = it.toLong(), name = "$longName $it", muscleGroup = MuscleGroup.FULL_BODY, equipment = Equipment.BARBELL)
        }

        val text = prompt(list = worstCaseCandidates)

        assertTrue(
            "prompt was ${text.length} chars, budget is ${AiConstants.PROMPT_CHAR_BUDGET}",
            text.length <= AiConstants.PROMPT_CHAR_BUDGET,
        )
    }
}
