package com.ironmind.app.domain.ai

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.util.RoutineAssembler
import com.ironmind.app.domain.util.RoutinePrescription
import com.ironmind.app.domain.util.TrainingHistory
import com.ironmind.app.domain.util.spanishName

/**
 * Builds the on-device AI prompt for routine generation.
 *
 * The model is asked for one thing only: which of a short numbered list of candidates to use, and
 * in what order — "3, 7, 1, 12". Sets, reps and rest are not its job; they follow from the goal and
 * [com.ironmind.app.domain.util.RoutineAssembler] fills them in. An earlier version asked a 1B
 * model for `id|sets|reps|rest` per row, and the extra structure is what it kept getting wrong.
 *
 * Positions 1..N rather than catalog ids: short, contiguous, and impossible to confuse with a
 * rep count. Each line carries the muscle group so the model can spread its picks, and names are
 * shown in Spanish when the catalog has them, matching the language of the instructions.
 */
object RoutineGeneratorPromptBuilder {

    /**
     * Sampling for this prompt. The answer is a list of numbers to parse, so the default 0.8 —
     * tuned for chat — only adds ways to get the format wrong; low keeps the choice sensible
     * while still letting "Regenerar" come back with a different routine.
     */
    const val TEMPERATURE = 0.2f

    /** The numbers a model most often copies verbatim; each one it keeps is at least a valid pick. */
    private val EXAMPLE_PICKS = listOf(3, 1, 6, 2, 5)

    /** Working sets logged before an exercise is marked as one the athlete does regularly. */
    private const val HABITUAL_SETS = 3

    fun build(
        split: RoutineSplit,
        goal: TrainingGoal,
        candidates: List<Exercise>,
        history: TrainingHistory = TrainingHistory.EMPTY,
        timeBudgetMinutes: Int? = null,
    ): String {
        val list = candidates.mapIndexed { index, ex ->
            // One character, not a word: it repeats on up to 24 lines of a prompt that has to fit
            // the engine's shared prompt+response token budget.
            val habitual = if (history.familiarity(ex.id) >= HABITUAL_SETS) " *" else ""
            "${index + 1}. ${ex.nameEs ?: ex.name} — ${ex.muscleGroup.spanish()}$habitual"
        }.joinToString("\n")
        val example = EXAMPLE_PICKS.filter { it <= candidates.size }.ifEmpty { listOf(1) }.joinToString(", ")
        val recent = candidates.map { it.muscleGroup }.distinct().filter { it in history.hoursSinceTrained }
        val howMany = when (timeBudgetMinutes) {
            null -> "entre 5 y 7 ejercicios"
            else -> {
                val count = RoutinePrescription.exerciseCountFor(goal, timeBudgetMinutes)
                val low = maxOf(RoutineAssembler.MIN_EXERCISES_WHEN_SHORT_ON_TIME, count - 1)
                if (low == count) "$count ejercicios" else "entre $low y $count ejercicios"
            }
        }

        return buildString {
            appendLine("Eres un entrenador personal. Diseña la sesión de un día.")
            appendLine("Tipo de día: ${split.spanish()}. Objetivo: ${goal.spanish()}.")
            timeBudgetMinutes?.let { appendLine("Tiempo disponible: $it minutos.") }
            if (recent.isNotEmpty()) {
                // The assembler enforces this anyway; saying it just saves the model a wasted pick.
                appendLine(
                    "Ya entrenó hace menos de 48 h: ${recent.joinToString(", ") { it.spanish() }}. " +
                        "Elige solo un ejercicio para cada uno de esos músculos.",
                )
            }
            appendLine()
            appendLine("Ejercicios disponibles (número. nombre — músculo; * = lo hace seguido):")
            appendLine(list)
            appendLine()
            appendLine(
                "Elige $howMany de esta lista, repartidos entre los músculos, prefiriendo los " +
                    "marcados con *, y ordénalos como se harían: primero los que mueven varias " +
                    "articulaciones (press, sentadilla, remo, peso muerto), después los de aislamiento.",
            )
            appendLine("Responde SOLO con los números elegidos, en ese orden, separados por comas.")
            append("Ejemplo de formato: $example")
        }
    }

    private fun RoutineSplit.spanish(): String = spanishName()
    private fun TrainingGoal.spanish(): String = spanishName()
    private fun MuscleGroup.spanish(): String = spanishName()
}
