package com.ironmind.app

import com.ironmind.app.domain.ai.RecoveryPromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPromptBuilderTest {

    private val squat = Exercise(id = 1, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)
    private val bench = Exercise(id = 2, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val exercisesById = mapOf(1L to squat, 2L to bench)

    private fun setAt(exerciseId: Long, daysAgo: Int, sessionId: Long = 1, rpe: Float? = null, isWarmup: Boolean = false) =
        SetLog(
            sessionId = sessionId, exerciseId = exerciseId, setNumber = 1,
            weightKg = 100.0, reps = 5, rpe = rpe, isWarmup = isWarmup,
            performedAt = REFERENCE - daysAgo * DAY_MILLIS,
        )

    @Test
    fun snapshot_returnsNullFieldsWhenMuscleGroupNeverTrained() {
        val snapshot = RecoveryPromptBuilder.snapshot(MuscleGroup.QUADS, emptyList(), exercisesById, REFERENCE)

        assertNull(snapshot.daysSinceLastTrained)
        assertNull(snapshot.lastSessionAverageRpe)
    }

    @Test
    fun snapshot_ignoresActivityFromOtherMuscleGroups() {
        val activity = listOf(setAt(2, daysAgo = 1)) // bench = CHEST, not QUADS

        val snapshot = RecoveryPromptBuilder.snapshot(MuscleGroup.QUADS, activity, exercisesById, REFERENCE)

        assertNull(snapshot.daysSinceLastTrained)
    }

    @Test
    fun snapshot_computesDaysSinceLastTrained() {
        val activity = listOf(setAt(1, daysAgo = 3))

        val snapshot = RecoveryPromptBuilder.snapshot(MuscleGroup.QUADS, activity, exercisesById, REFERENCE)

        assertEquals(3, snapshot.daysSinceLastTrained)
    }

    @Test
    fun snapshot_usesTheMostRecentSessionWhenMultipleExist() {
        val activity = listOf(
            setAt(1, daysAgo = 10, sessionId = 1, rpe = 6f),
            setAt(1, daysAgo = 2, sessionId = 2, rpe = 9f),
        )

        val snapshot = RecoveryPromptBuilder.snapshot(MuscleGroup.QUADS, activity, exercisesById, REFERENCE)

        assertEquals(2, snapshot.daysSinceLastTrained)
        assertEquals(9f, snapshot.lastSessionAverageRpe)
    }

    @Test
    fun snapshot_averagesRpeOnlyFromTheLastSessionNotOlderOnes() {
        val activity = listOf(
            setAt(1, daysAgo = 10, sessionId = 1, rpe = 10f), // older session, must not leak in
            setAt(1, daysAgo = 1, sessionId = 2, rpe = 6f),
            setAt(1, daysAgo = 1, sessionId = 2, rpe = 8f),
        )

        val snapshot = RecoveryPromptBuilder.snapshot(MuscleGroup.QUADS, activity, exercisesById, REFERENCE)

        assertEquals(7f, snapshot.lastSessionAverageRpe)
    }

    @Test
    fun snapshot_excludesWarmupSetsFromLastTrainedAndRpe() {
        val activity = listOf(
            setAt(1, daysAgo = 1, sessionId = 1, rpe = 9f, isWarmup = true),
            setAt(1, daysAgo = 5, sessionId = 2, rpe = 7f, isWarmup = false),
        )

        val snapshot = RecoveryPromptBuilder.snapshot(MuscleGroup.QUADS, activity, exercisesById, REFERENCE)

        // The warmup set is 1 day ago but excluded, so the real (working-set) last-trained is day 5.
        assertEquals(5, snapshot.daysSinceLastTrained)
        assertEquals(7f, snapshot.lastSessionAverageRpe)
    }

    @Test
    fun snapshot_returnsNullAverageRpeWhenLastSessionHasNoRpeRecorded() {
        val activity = listOf(setAt(1, daysAgo = 1, rpe = null))

        val snapshot = RecoveryPromptBuilder.snapshot(MuscleGroup.QUADS, activity, exercisesById, REFERENCE)

        assertNull(snapshot.lastSessionAverageRpe)
    }

    @Test
    fun build_includesDisclaimerAndDataWhenNeverTrained() {
        val snapshot = RecoveryPromptBuilder.snapshot(MuscleGroup.QUADS, emptyList(), exercisesById, REFERENCE)

        val prompt = RecoveryPromptBuilder.build(snapshot)

        assertTrue(prompt.contains("No hay registros"))
        assertTrue(prompt.contains("no un diagnóstico"))
        assertTrue(prompt.contains("QUADS"))
        assertTrue(prompt.contains("Responde en español"))
    }

    @Test
    fun build_includesDaysAndRpeWhenAvailable() {
        val activity = listOf(setAt(1, daysAgo = 2, rpe = 8.5f))
        val snapshot = RecoveryPromptBuilder.snapshot(MuscleGroup.QUADS, activity, exercisesById, REFERENCE)

        val prompt = RecoveryPromptBuilder.build(snapshot)

        assertTrue(prompt.contains("hace 2 días"))
        assertTrue(prompt.contains("RPE promedio de 8.5"))
    }

    @Test
    fun build_usesSingularDayWording() {
        val activity = listOf(setAt(1, daysAgo = 1))
        val snapshot = RecoveryPromptBuilder.snapshot(MuscleGroup.QUADS, activity, exercisesById, REFERENCE)

        val prompt = RecoveryPromptBuilder.build(snapshot)

        assertTrue(prompt.contains("hace 1 día."))
    }

    private companion object {
        const val REFERENCE = 1_800_000_000_000L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
