package com.ironmind.app

import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.domain.ai.RoutineGeneratorPromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.util.RoutineCandidateSelector
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineGeneratorPromptBuilderTest {

    private val candidates = listOf(
        Exercise(id = 1, name = "Barbell Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
        Exercise(id = 2, name = "Overhead Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL),
    )

    @Test
    fun build_listsCandidatesByIdAndName() {
        val prompt = RoutineGeneratorPromptBuilder.build(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, candidates)

        assertTrue(prompt.contains("1|Barbell Bench Press"))
        assertTrue(prompt.contains("2|Overhead Press"))
    }

    @Test
    fun build_includesTheRequiredOutputFormat() {
        val prompt = RoutineGeneratorPromptBuilder.build(RoutineSplit.PUSH, TrainingGoal.STRENGTH, candidates)

        assertTrue(prompt.contains("id|series|repeticiones|descanso_segundos"))
        assertTrue(prompt.contains("EXCLUSIVAMENTE"))
    }

    @Test
    fun build_reflectsGoalInTheGuidance() {
        val strength = RoutineGeneratorPromptBuilder.build(RoutineSplit.PUSH, TrainingGoal.STRENGTH, candidates)
        val endurance = RoutineGeneratorPromptBuilder.build(RoutineSplit.PUSH, TrainingGoal.ENDURANCE, candidates)

        assertTrue(strength.contains("fuerza"))
        assertTrue(endurance.contains("resistencia"))
    }

    @Test
    fun build_worstCaseCandidateCountAndNamesStaysWithinPromptBudget() {
        val longName = "Standing Barbell Overhead Military Press Variation"
        val worstCaseCandidates = (1..RoutineCandidateSelector.MAX_CANDIDATES).map {
            Exercise(id = it.toLong(), name = "$longName $it", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL)
        }

        val prompt = RoutineGeneratorPromptBuilder.build(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, worstCaseCandidates)

        assertTrue(
            "prompt was ${prompt.length} chars, budget is ${AiConstants.PROMPT_CHAR_BUDGET}",
            prompt.length <= AiConstants.PROMPT_CHAR_BUDGET,
        )
    }
}
