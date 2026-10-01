package com.ironmind.app

import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.domain.ai.SessionComparisonPromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SessionDetail
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class SessionComparisonPromptBuilderTest {

    private val squat = Exercise(id = 1, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)
    private val exercisesById = mapOf(1L to squat)
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun set(weightKg: Double, reps: Int, isWarmup: Boolean = false, isCompleted: Boolean = true) = SetLog(
        sessionId = 1, exerciseId = 1, setNumber = 1, weightKg = weightKg, reps = reps,
        isWarmup = isWarmup, isCompleted = isCompleted,
    )

    @Test
    fun summarize_excludesWarmupAndIncompleteSets() {
        val detail = SessionDetail(
            session = WorkoutSession(id = 1, startedAt = 0L, title = "Push"),
            sets = listOf(
                set(40.0, 10, isWarmup = true),
                set(100.0, 5, isCompleted = false),
                set(90.0, 5),
            ),
        )

        val summary = SessionComparisonPromptBuilder.summarize(detail, exercisesById, zone)

        assertEquals(1, summary.workingSetCount)
        assertTrue(summary.exerciseLines.single().contains("90kg"))
    }

    @Test
    fun summarize_capsExercisesByVolumeDescending() {
        val exercises = (1..10).associate { it.toLong() to Exercise(id = it.toLong(), name = "Ex $it", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL) }
        val sets = (1..10).map { id -> SetLog(sessionId = 1, exerciseId = id.toLong(), setNumber = 1, weightKg = id * 10.0, reps = 5) }
        val detail = SessionDetail(session = WorkoutSession(id = 1, startedAt = 0L), sets = sets)

        val summary = SessionComparisonPromptBuilder.summarize(detail, exercises, zone)

        assertEquals(6, summary.exerciseLines.size)
        // Highest-volume exercise (id=10, 100kg) must be first.
        assertTrue(summary.exerciseLines.first().contains("Ex 10"))
    }

    @Test
    fun summarize_fallsBackToFreeSessionLabelWhenUntitled() {
        val detail = SessionDetail(session = WorkoutSession(id = 1, startedAt = 0L, title = null), sets = listOf(set(90.0, 5)))

        val summary = SessionComparisonPromptBuilder.summarize(detail, exercisesById, zone)

        assertTrue(summary.label.contains("Sesión libre"))
    }

    @Test
    fun build_labelsBothSessionsAndAsksForAComparison() {
        val detailA = SessionDetail(session = WorkoutSession(id = 1, startedAt = 0L, title = "Push A"), sets = listOf(set(90.0, 5)))
        val detailB = SessionDetail(session = WorkoutSession(id = 2, startedAt = 604_800_000L, title = "Push B"), sets = listOf(set(95.0, 5)))
        val a = SessionComparisonPromptBuilder.summarize(detailA, exercisesById, zone)
        val b = SessionComparisonPromptBuilder.summarize(detailB, exercisesById, zone)

        val prompt = SessionComparisonPromptBuilder.build(a, b)

        assertTrue(prompt.contains("Push A"))
        assertTrue(prompt.contains("Push B"))
        assertTrue(prompt.contains("Sesión A"))
        assertTrue(prompt.contains("Sesión B"))
        assertTrue(prompt.contains("Responde en español"))
    }

    @Test
    fun build_worstCaseBothSessionsStayWithinPromptBudget() {
        val longName = "Standing Barbell Overhead Military Press Variation"
        val exercises = (1..6).associate { it.toLong() to Exercise(id = it.toLong(), name = "$longName $it", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL) }
        val sets = (1..6).map { id -> SetLog(sessionId = 1, exerciseId = id.toLong(), setNumber = 1, weightKg = 999.5, reps = 12) }
        val detail = SessionDetail(session = WorkoutSession(id = 1, startedAt = 0L, title = longName), sets = sets)
        val summary = SessionComparisonPromptBuilder.summarize(detail, exercises, zone)

        val prompt = SessionComparisonPromptBuilder.build(summary, summary)

        assertTrue(
            "prompt was ${prompt.length} chars, budget is ${AiConstants.PROMPT_CHAR_BUDGET}",
            prompt.length <= AiConstants.PROMPT_CHAR_BUDGET,
        )
    }
}
