package com.ironmind.app.domain.ai

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.util.spanishName

/**
 * Builds the prompt for adjusting an existing draft from the athlete's own words — "más corta",
 * "sin sentadilla", "más hombro".
 *
 * Same answer shape as [RoutineGeneratorPromptBuilder] — positions from one numbered list — so the
 * same parser reads it. The current session's exercises are listed first, so "the session as it
 * is" is simply "1, 2, …, n" and a small change to it is a small change to that answer: dropping
 * a number, swapping one. Asking a 1B model to reason about free text *and* produce a fresh
 * routine at once is what this avoids.
 */
object RoutineRefinePromptBuilder {

    /** Long enough for a sentence, short enough not to crowd out the list in the token budget. */
    const val MAX_INSTRUCTION_CHARS = 160

    /**
     * @param candidates the current session first, in its order, then the alternatives on offer.
     * @param currentCount how many of [candidates] make up the current session.
     */
    fun build(
        split: RoutineSplit,
        goal: TrainingGoal,
        candidates: List<Exercise>,
        currentCount: Int,
        instruction: String,
    ): String {
        val list = candidates.mapIndexed { index, ex ->
            "${index + 1}. ${ex.nameEs ?: ex.name} — ${ex.muscleGroup.spanishName()}"
        }.joinToString("\n")
        val current = (1..currentCount).joinToString(", ")
        val request = instruction.replace(Regex("\\s+"), " ").trim().take(MAX_INSTRUCTION_CHARS)

        return buildString {
            appendLine("Eres un entrenador personal ajustando la sesión de un día.")
            appendLine("Tipo de día: ${split.spanishName()}. Objetivo: ${goal.spanishName()}.")
            appendLine()
            appendLine("Ejercicios (número. nombre — músculo):")
            appendLine(list)
            appendLine()
            appendLine("Sesión actual: $current")
            appendLine("Lo que pide el atleta: «$request»")
            appendLine()
            appendLine(
                "Cambia la sesión actual solo lo necesario para cumplir el pedido, usando números " +
                    "de la lista. Responde SOLO con los números de la nueva sesión, en orden, " +
                    "separados por comas.",
            )
        }.trimEnd()
    }

    /** Sampling: the same list-of-numbers answer as generation, so the same low temperature. */
    const val TEMPERATURE = RoutineGeneratorPromptBuilder.TEMPERATURE
}
