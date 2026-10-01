package com.ironmind.app

import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.domain.ai.TechniqueCoachPromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueCoachPromptBuilderTest {

    private val withGuide = Exercise(
        id = 1, name = "Barbell Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL,
        instructions = "1) Acuéstate en el banco. 2) Baja controlado. 3) Empuja sin rebotar.",
    )

    private val withoutGuide = Exercise(
        id = 2, name = "Cable Crossover", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.CABLE,
        instructions = null,
    )

    @Test
    fun build_anchorsToTheCuratedGuideWhenAvailable() {
        val prompt = TechniqueCoachPromptBuilder.build(withGuide)

        assertTrue(prompt.contains("Barbell Bench Press"))
        assertTrue(prompt.contains("Baja controlado"))
        assertTrue(prompt.contains("PRINCIPALMENTE en esta guía"))
        assertTrue(prompt.contains("Responde en español"))
    }

    @Test
    fun build_asksForGeneralConservativeCuesWhenNoGuideExists() {
        val prompt = TechniqueCoachPromptBuilder.build(withoutGuide)

        assertTrue(prompt.contains("Cable Crossover"))
        assertTrue(prompt.contains("No hay una guía técnica curada"))
        assertTrue(prompt.contains("revisar la forma con un profesional"))
        // Must not claim there IS a curated guide when there isn't one.
        assertTrue(!prompt.contains("PRINCIPALMENTE en esta guía"))
    }

    @Test
    fun build_treatsBlankInstructionsAsNoGuide() {
        val blank = withoutGuide.copy(instructions = "   ")

        val prompt = TechniqueCoachPromptBuilder.build(blank)

        assertTrue(prompt.contains("No hay una guía técnica curada"))
    }

    @Test
    fun build_truncatesAVeryLongGuideAndStaysWithinPromptBudget() {
        val longGuide = "Paso muy detallado. ".repeat(500) // ~10,000 chars
        val exercise = withGuide.copy(instructions = longGuide)

        val prompt = TechniqueCoachPromptBuilder.build(exercise)

        assertTrue(
            "prompt was ${prompt.length} chars, budget is ${AiConstants.PROMPT_CHAR_BUDGET}",
            prompt.length <= AiConstants.PROMPT_CHAR_BUDGET,
        )
        assertTrue(prompt.contains("…"))
    }
}
