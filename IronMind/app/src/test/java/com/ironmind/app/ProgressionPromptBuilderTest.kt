package com.ironmind.app

import com.ironmind.app.domain.ai.ProgressionPromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Unit tests for the on-device prompt construction. */
class ProgressionPromptBuilderTest {

    private val exercise = Exercise(
        id = 1,
        name = "Barbell Bench Press",
        muscleGroup = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL,
    )

    @Test
    fun build_includesExerciseNameAndHistoryDetails() {
        val history = listOf(
            SetLog(
                id = 2, sessionId = 1, exerciseId = 1, setNumber = 1,
                weightKg = 80.0, reps = 8, rpe = 8f,
                notes = "Energía 9/10, ON Gold Standard Whey",
                performedAt = 1_726_000_000_000L,
            ),
        )

        val prompt = ProgressionPromptBuilder.build(exercise, history, ZoneId.of("UTC"))

        assertTrue(prompt.contains("Barbell Bench Press"))
        assertTrue(prompt.contains("80 kg x 8 reps"))
        assertTrue(prompt.contains("RPE 8"))
        assertTrue(prompt.contains("ON Gold Standard Whey"))
        assertTrue(prompt.contains("sobrecarga progresiva"))
    }

    @Test
    fun build_handlesEmptyHistoryGracefully() {
        val prompt = ProgressionPromptBuilder.build(exercise, emptyList(), ZoneId.of("UTC"))
        assertTrue(prompt.contains("Sin registros previos"))
    }
}
