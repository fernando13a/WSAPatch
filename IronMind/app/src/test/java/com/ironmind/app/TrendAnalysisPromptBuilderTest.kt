package com.ironmind.app

import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.domain.ai.TrendAnalysisPromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendAnalysisPromptBuilderTest {

    private val squat = Exercise(id = 1, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)
    private val bench = Exercise(id = 2, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)

    /** [daysAgo] = 0 lands in the most recent (index 0) week bucket relative to [reference]. */
    private fun setAt(exerciseId: Long, daysAgo: Int, weightKg: Double, reps: Int, rpe: Float? = null, isWarmup: Boolean = false, reference: Long = REFERENCE) =
        SetLog(
            sessionId = 1, exerciseId = exerciseId, setNumber = 1,
            weightKg = weightKg, reps = reps, rpe = rpe, isWarmup = isWarmup,
            performedAt = reference - daysAgo * DAY_MILLIS,
        )

    @Test
    fun aggregate_returnsEmptyForNoActivity() {
        val trends = TrendAnalysisPromptBuilder.aggregate(emptyList(), mapOf(1L to squat), REFERENCE)
        assertTrue(trends.isEmpty())
    }

    @Test
    fun aggregate_singleSessionLandsInOneWeekBucket() {
        val activity = listOf(setAt(1, daysAgo = 1, weightKg = 100.0, reps = 5))

        val trends = TrendAnalysisPromptBuilder.aggregate(activity, mapOf(1L to squat), REFERENCE)

        assertEquals(1, trends.size)
        val weeks = trends.first().weeklyVolumesKg
        assertEquals(TrendAnalysisPromptBuilder.WEEK_COUNT, weeks.size)
        // Most recent week (last in the chronological list) has the volume; earlier weeks are 0.
        assertEquals(500.0, weeks.last(), 0.01)
        assertEquals(0.0, weeks.first(), 0.01)
    }

    @Test
    fun aggregate_excludesWarmupSets() {
        val activity = listOf(
            setAt(1, daysAgo = 1, weightKg = 40.0, reps = 10, isWarmup = true),
            setAt(1, daysAgo = 1, weightKg = 100.0, reps = 5, isWarmup = false),
        )

        val trends = TrendAnalysisPromptBuilder.aggregate(activity, mapOf(1L to squat), REFERENCE)

        // 500.0 = 100kg x 5 reps only; if the 40kg x 10 warmup were included, volume would be 900.0.
        assertEquals(500.0, trends.first().weeklyVolumesKg.last(), 0.01)
        assertEquals(116.67, trends.first().estimatedOneRepMaxKg, 0.01)
    }

    @Test
    fun aggregate_detectsStableVolumeAcrossAllWeeks() {
        val activity = (0 until 4).map { week -> setAt(1, daysAgo = week * 7 + 1, weightKg = 100.0, reps = 5) }

        val trends = TrendAnalysisPromptBuilder.aggregate(activity, mapOf(1L to squat), REFERENCE)

        val weeks = trends.first().weeklyVolumesKg
        assertEquals(listOf(500.0, 500.0, 500.0, 500.0), weeks)
    }

    @Test
    fun aggregate_capturesRisingRpeWithFallingVolume() {
        val activity = listOf(
            setAt(1, daysAgo = 22, weightKg = 120.0, reps = 5, rpe = 6f),
            setAt(1, daysAgo = 15, weightKg = 110.0, reps = 5, rpe = 7f),
            setAt(1, daysAgo = 8, weightKg = 100.0, reps = 5, rpe = 8f),
            setAt(1, daysAgo = 1, weightKg = 90.0, reps = 5, rpe = 9.5f),
        )

        val trends = TrendAnalysisPromptBuilder.aggregate(activity, mapOf(1L to squat), REFERENCE)

        val weeks = trends.first().weeklyVolumesKg
        assertTrue("expected a falling trend: $weeks", weeks[0] > weeks.last())
        assertEquals(7.625f, trends.first().averageRpe!!, 0.01f)
    }

    @Test
    fun aggregate_returnsNullAverageRpeWhenNoneRecorded() {
        val activity = listOf(setAt(1, daysAgo = 1, weightKg = 100.0, reps = 5, rpe = null))
        val trends = TrendAnalysisPromptBuilder.aggregate(activity, mapOf(1L to squat), REFERENCE)
        assertNull(trends.first().averageRpe)
    }

    @Test
    fun aggregate_sortsByVolumeDescendingAndCapsToMaxExercises() {
        val activity = listOf(
            setAt(1, daysAgo = 1, weightKg = 200.0, reps = 5), // squat: 1000kg volume
            setAt(2, daysAgo = 1, weightKg = 60.0, reps = 5),  // bench: 300kg volume
        )

        val trends = TrendAnalysisPromptBuilder.aggregate(activity, mapOf(1L to squat, 2L to bench), REFERENCE)

        assertEquals(listOf("Back Squat", "Bench Press"), trends.map { it.exerciseName })
    }

    @Test
    fun aggregate_skipsSetsForUnknownExercises() {
        val activity = listOf(setAt(99, daysAgo = 1, weightKg = 100.0, reps = 5))
        val trends = TrendAnalysisPromptBuilder.aggregate(activity, mapOf(1L to squat), REFERENCE)
        assertTrue(trends.isEmpty())
    }

    @Test
    fun build_includesFramingAndDataRows() {
        val activity = listOf(setAt(1, daysAgo = 1, weightKg = 100.0, reps = 5, rpe = 8f))
        val trends = TrendAnalysisPromptBuilder.aggregate(activity, mapOf(1L to squat), REFERENCE)

        val prompt = TrendAnalysisPromptBuilder.build(trends)

        assertTrue(prompt.contains("Back Squat"))
        assertTrue(prompt.contains("RPE prom 8"))
        assertTrue(prompt.contains("1RM est."))
        assertTrue(prompt.contains("Responde en español"))
    }

    @Test
    fun build_worstCaseExerciseCountAndNamesStaysWithinPromptBudget() {
        val longName = "Standing Barbell Overhead Military Press Variation"
        val exercises = (1..TrendAnalysisPromptBuilder.MAX_EXERCISES).associate {
            it.toLong() to Exercise(id = it.toLong(), name = "$longName $it", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL)
        }
        val activity = exercises.keys.flatMap { id ->
            (0 until 4).map { week -> setAt(id, daysAgo = week * 7 + 1, weightKg = 999.5, reps = 9, rpe = 9.9f) }
        }

        val trends = TrendAnalysisPromptBuilder.aggregate(activity, exercises, REFERENCE)
        val prompt = TrendAnalysisPromptBuilder.build(trends)

        assertEquals(TrendAnalysisPromptBuilder.MAX_EXERCISES, trends.size)
        assertTrue(
            "prompt was ${prompt.length} chars, budget is ${AiConstants.PROMPT_CHAR_BUDGET}",
            prompt.length <= AiConstants.PROMPT_CHAR_BUDGET,
        )
    }

    private companion object {
        const val REFERENCE = 1_800_000_000_000L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
