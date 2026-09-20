package com.ironmind.app.domain.ai

import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.domain.model.ChatAuthor
import com.ironmind.app.domain.model.ChatMessage
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.util.StreakCalculator
import com.ironmind.app.domain.util.estimateOneRepMax
import java.time.Instant
import java.time.ZoneId
import kotlin.math.round

/**
 * Builds the prompt for the conversational coach: a deterministic snapshot of what the athlete has
 * actually been training ([snapshot], computed in Kotlin — the model never does the arithmetic),
 * plus as much of the recent conversation as fits under [AiConstants.PROMPT_CHAR_BUDGET].
 *
 * The budget matters more here than in the one-shot features: a conversation grows without bound,
 * so [build] keeps the snapshot and the current question whole and drops the *oldest* turns first,
 * which is what a reader would expect the model to forget.
 */
object ChatPromptBuilder {

    /** Exercises embedded in the snapshot, most-trained first. */
    const val MAX_EXERCISES = 5

    /** Hard cap on conversation turns considered, before the character budget trims further. */
    const val MAX_HISTORY_MESSAGES = 8

    /** Per-message cap, so one long answer can't crowd out every other turn. */
    const val MAX_MESSAGE_CHARS = 320

    /** Cap on the athlete's own question — a pasted wall of text would blow the budget alone. */
    const val MAX_QUESTION_CHARS = 500

    private const val STREAK_WINDOW_DAYS = 7
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val CONVERSATION_LABEL = "CONVERSACIÓN HASTA AHORA:"

    /** One exercise line in the snapshot: where the athlete stands and what they last did. */
    data class ExerciseSnapshot(
        val name: String,
        val estimatedOneRepMaxKg: Double,
        val lastWeightKg: Double,
        val lastReps: Int,
    )

    /** Everything the coach is allowed to reason from, all of it computed here rather than inferred. */
    data class GymSnapshot(
        val streakDays: Int,
        val sessionsLastWeek: Int,
        val exercises: List<ExerciseSnapshot>,
    ) {
        val hasHistory: Boolean get() = exercises.isNotEmpty()
    }

    /**
     * Aggregates [activity] (a recent cross-exercise window, e.g. from
     * [com.ironmind.app.domain.repository.WorkoutRepository.getRecentActivity]) and
     * [sessionStartMillis] into the coach's view of the athlete. Warm-up sets are excluded — they
     * aren't representative working effort.
     */
    fun snapshot(
        activity: List<SetLog>,
        exercisesById: Map<Long, Exercise>,
        sessionStartMillis: List<Long>,
        referenceTimeMillis: Long = System.currentTimeMillis(),
    ): GymSnapshot {
        val workingSets = activity.filterNot { it.isWarmup }
        val weekAgo = referenceTimeMillis - STREAK_WINDOW_DAYS * DAY_MILLIS

        val exercises = workingSets
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, sets) ->
                val exercise = exercisesById[exerciseId] ?: return@mapNotNull null
                val latest = sets.maxBy { it.performedAt }
                ExerciseSnapshot(
                    name = exercise.name,
                    estimatedOneRepMaxKg = sets.maxOf { estimateOneRepMax(it.weightKg, it.reps) },
                    lastWeightKg = latest.weightKg,
                    lastReps = latest.reps,
                ) to sets.sumOf { it.volume }
            }
            .sortedByDescending { (_, volume) -> volume }
            .take(MAX_EXERCISES)
            .map { (snapshot, _) -> snapshot }

        val today = Instant.ofEpochMilli(referenceTimeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return GymSnapshot(
            streakDays = StreakCalculator.currentStreak(sessionStartMillis, today = today),
            sessionsLastWeek = sessionStartMillis.count { it >= weekAgo },
            exercises = exercises,
        )
    }

    /**
     * Assembles the full prompt. [history] is oldest-first and should *exclude* [question] (and any
     * placeholder bubble the UI is about to stream into); the oldest turns are dropped first when
     * the whole thing would exceed [AiConstants.PROMPT_CHAR_BUDGET].
     */
    fun build(question: String, history: List<ChatMessage>, snapshot: GymSnapshot): String {
        val trimmedQuestion = question.trim().take(MAX_QUESTION_CHARS)
        val header = buildHeader(snapshot)
        val footer = "Atleta: $trimmedQuestion\nCoach:"

        // The snapshot and the current question are non-negotiable; whatever budget is left over
        // goes to conversation turns, newest first. The +3 covers the newlines buildString adds
        // around the label and before the footer, so the assembled prompt can't overshoot.
        val fixedLength = header.length + footer.length + CONVERSATION_LABEL.length + 3
        val remaining = AiConstants.PROMPT_CHAR_BUDGET - fixedLength

        // Newest-first while it fits, then stop — stopping (rather than skipping a turn that
        // happens to be long) keeps the conversation the model sees contiguous.
        val candidates = history.takeLast(MAX_HISTORY_MESSAGES).map { formatTurn(it) }
        val turns = ArrayDeque<String>()
        var used = 0
        for (turn in candidates.asReversed()) {
            if (used + turn.length + 1 > remaining) break
            turns.addFirst(turn)
            used += turn.length + 1
        }

        return buildString {
            append(header)
            if (turns.isNotEmpty()) {
                appendLine()
                appendLine(CONVERSATION_LABEL)
                turns.forEach { appendLine(it) }
            }
            appendLine()
            append(footer)
        }
    }

    private fun buildHeader(snapshot: GymSnapshot): String = buildString {
        appendLine(
            "Eres el entrenador personal de este atleta dentro de su app de gimnasio. Respondes " +
                "SIEMPRE en español, en 2-4 frases, directo, concreto y motivador.",
        )
        appendLine()
        appendLine("DATOS REALES DE SU ENTRENAMIENTO (ya calculados, NO los recalcules ni inventes otros):")
        appendLine("- Racha actual: ${snapshot.streakDays} días")
        appendLine("- Sesiones en los últimos $STREAK_WINDOW_DAYS días: ${snapshot.sessionsLastWeek}")
        if (snapshot.hasHistory) {
            snapshot.exercises.forEach { appendLine(formatExercise(it)) }
        } else {
            appendLine("- Todavía no hay sets registrados.")
        }
        appendLine()
        appendLine(
            "Reglas: usa solo estos datos para hablar de su progreso; si te falta un dato, dilo y " +
                "sugiere qué registrar. Si la pregunta no es sobre entrenamiento, nutrición " +
                "deportiva o descanso, redirígela amablemente al gimnasio.",
        )
    }

    private fun formatExercise(exercise: ExerciseSnapshot): String =
        "- ${exercise.name}: 1RM est. ${trim(exercise.estimatedOneRepMaxKg)} kg · último set " +
            "${trim(exercise.lastWeightKg)} kg x ${exercise.lastReps}"

    private fun formatTurn(message: ChatMessage): String {
        val speaker = if (message.author == ChatAuthor.USER) "Atleta" else "Coach"
        val text = message.text.trim().replace('\n', ' ')
        val capped = if (text.length > MAX_MESSAGE_CHARS) text.take(MAX_MESSAGE_CHARS) + "…" else text
        return "$speaker: $capped"
    }

    /** Renders 80.0 as "80" and 82.47 as "82.5". */
    private fun trim(value: Double): String {
        val rounded = round(value * 10) / 10
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
