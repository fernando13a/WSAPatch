package com.ironmind.app

import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.domain.ai.ChatPromptBuilder
import com.ironmind.app.domain.model.ChatAuthor
import com.ironmind.app.domain.model.ChatMessage
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptBuilderTest {

    private val squat = Exercise(id = 1, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)
    private val bench = Exercise(id = 2, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val exercisesById = mapOf(1L to squat, 2L to bench)

    private fun setAt(
        exerciseId: Long,
        daysAgo: Int,
        weightKg: Double,
        reps: Int,
        isWarmup: Boolean = false,
    ) = SetLog(
        sessionId = 1,
        exerciseId = exerciseId,
        setNumber = 1,
        weightKg = weightKg,
        reps = reps,
        isWarmup = isWarmup,
        performedAt = REFERENCE - daysAgo * DAY_MILLIS,
    )

    private fun emptySnapshot() = ChatPromptBuilder.snapshot(emptyList(), emptyMap(), emptyList(), REFERENCE)

    @Test
    fun snapshot_isEmptyWithoutActivity() {
        val snapshot = emptySnapshot()

        assertFalse(snapshot.hasHistory)
        assertEquals(0, snapshot.streakDays)
        assertEquals(0, snapshot.sessionsLastWeek)
    }

    @Test
    fun snapshot_countsOnlySessionsInsideTheLastWeek() {
        val sessionStarts = listOf(
            REFERENCE,
            REFERENCE - 3 * DAY_MILLIS,
            REFERENCE - 20 * DAY_MILLIS, // outside the 7-day window
        )

        val snapshot = ChatPromptBuilder.snapshot(emptyList(), exercisesById, sessionStarts, REFERENCE)

        assertEquals(2, snapshot.sessionsLastWeek)
    }

    @Test
    fun snapshot_streakFollowsConsecutiveTrainingDaysEndingAtReference() {
        val sessionStarts = listOf(REFERENCE, REFERENCE - DAY_MILLIS, REFERENCE - 2 * DAY_MILLIS)

        val snapshot = ChatPromptBuilder.snapshot(emptyList(), exercisesById, sessionStarts, REFERENCE)

        assertEquals(3, snapshot.streakDays)
    }

    @Test
    fun snapshot_excludesWarmupsAndKeepsTheLatestSet() {
        val activity = listOf(
            setAt(1, daysAgo = 5, weightKg = 40.0, reps = 10, isWarmup = true),
            setAt(1, daysAgo = 5, weightKg = 100.0, reps = 5),
            setAt(1, daysAgo = 1, weightKg = 110.0, reps = 3),
        )

        val snapshot = ChatPromptBuilder.snapshot(activity, exercisesById, emptyList(), REFERENCE)

        val exercise = snapshot.exercises.single()
        assertEquals("Back Squat", exercise.name)
        // Latest working set, not the heaviest and not the warmup.
        assertEquals(110.0, exercise.lastWeightKg, 0.01)
        assertEquals(3, exercise.lastReps)
        // 110 x 3 estimates higher than 100 x 5, so the 1RM comes from the newer set here.
        assertEquals(121.0, exercise.estimatedOneRepMaxKg, 0.5)
    }

    @Test
    fun snapshot_ordersExercisesByVolumeDescending() {
        val activity = listOf(
            setAt(1, daysAgo = 1, weightKg = 50.0, reps = 5), // 250 kg
            setAt(2, daysAgo = 1, weightKg = 100.0, reps = 5), // 500 kg
        )

        val snapshot = ChatPromptBuilder.snapshot(activity, exercisesById, emptyList(), REFERENCE)

        assertEquals(listOf("Bench Press", "Back Squat"), snapshot.exercises.map { it.name })
    }

    @Test
    fun snapshot_ignoresSetsForUnknownExercises() {
        val activity = listOf(setAt(exerciseId = 99, daysAgo = 1, weightKg = 100.0, reps = 5))

        val snapshot = ChatPromptBuilder.snapshot(activity, exercisesById, emptyList(), REFERENCE)

        assertFalse(snapshot.hasHistory)
    }

    @Test
    fun build_embedsQuestionAndSnapshotNumbers() {
        val activity = listOf(setAt(1, daysAgo = 1, weightKg = 100.0, reps = 5))
        val snapshot = ChatPromptBuilder.snapshot(activity, exercisesById, listOf(REFERENCE), REFERENCE)

        val prompt = ChatPromptBuilder.build("¿Cómo voy?", emptyList(), snapshot)

        assertTrue(prompt.contains("¿Cómo voy?"))
        assertTrue(prompt.contains("Back Squat"))
        assertTrue(prompt.endsWith("Coach:"))
    }

    @Test
    fun build_omitsConversationSectionWhenThereIsNoHistory() {
        val prompt = ChatPromptBuilder.build("¿Qué entreno hoy?", emptyList(), emptySnapshot())

        assertFalse(prompt.contains("CONVERSACIÓN HASTA AHORA"))
    }

    @Test
    fun build_includesPriorTurnsLabelledBySpeaker() {
        val history = listOf(
            ChatMessage(id = 1, author = ChatAuthor.USER, text = "¿Subo peso en sentadilla?"),
            ChatMessage(id = 2, author = ChatAuthor.COACH, text = "Sí, prueba 105 kg."),
        )

        val prompt = ChatPromptBuilder.build("¿Y en press?", history, emptySnapshot())

        assertTrue(prompt.contains("Atleta: ¿Subo peso en sentadilla?"))
        assertTrue(prompt.contains("Coach: Sí, prueba 105 kg."))
    }

    @Test
    fun build_dropsOldestTurnsFirstAndStaysWithinBudget() {
        // Each turn is long enough that the whole history can't possibly fit in the budget.
        val longText = "x".repeat(ChatPromptBuilder.MAX_MESSAGE_CHARS)
        val history = (1..ChatPromptBuilder.MAX_HISTORY_MESSAGES * 3).map { index ->
            ChatMessage(
                id = index.toLong(),
                author = if (index % 2 == 1) ChatAuthor.USER else ChatAuthor.COACH,
                text = "turno$index $longText",
            )
        }

        val prompt = ChatPromptBuilder.build("¿Resumen?", history, emptySnapshot())

        assertTrue(prompt.length <= AiConstants.PROMPT_CHAR_BUDGET)
        // The newest turn survives; the oldest is dropped.
        assertTrue(prompt.contains("turno${history.size}"))
        assertFalse(prompt.contains("turno1 "))
    }

    @Test
    fun build_capsAnOverlongQuestion() {
        val question = "y".repeat(ChatPromptBuilder.MAX_QUESTION_CHARS * 2)

        val prompt = ChatPromptBuilder.build(question, emptyList(), emptySnapshot())

        assertTrue(prompt.length <= AiConstants.PROMPT_CHAR_BUDGET)
        assertFalse(prompt.contains("y".repeat(ChatPromptBuilder.MAX_QUESTION_CHARS + 1)))
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        /** Fixed "now" so every bucket/streak assertion is deterministic. */
        const val REFERENCE = 1_700_000_000_000L
    }
}
