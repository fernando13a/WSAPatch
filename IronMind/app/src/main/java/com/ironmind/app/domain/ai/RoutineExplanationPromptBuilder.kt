package com.ironmind.app.domain.ai

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.RoutineDraftExercise
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.util.spanishName

/**
 * Builds the prompt behind "¿Por qué esta rutina?": a short explanation of a draft that already
 * exists. Kept separate from generation on purpose — prose is slow on a 1B model, and making every
 * routine wait for a paragraph nobody asked for would undo what choosing-by-number bought.
 *
 * The model gets the finished session, numbers included, and is told not to change it: its job is
 * to explain decisions Kotlin and the athlete already made, not to propose a different routine.
 */
object RoutineExplanationPromptBuilder {

    fun build(
        split: RoutineSplit,
        goal: TrainingGoal,
        rows: List<RoutineDraftExercise>,
        exercisesById: Map<Long, Exercise>,
        adjustments: List<String> = emptyList(),
    ): String {
        val session = rows.mapIndexed { index, row ->
            val exercise = exercisesById[row.exerciseId]
            val name = exercise?.let { it.nameEs ?: it.name } ?: "Ejercicio ${row.exerciseId}"
            val muscle = exercise?.muscleGroup?.spanishName()?.let { " — $it" }.orEmpty()
            "${index + 1}. $name$muscle: ${row.sets} × ${row.reps}, descanso ${row.restSeconds} s"
        }.joinToString("\n")

        return buildString {
            appendLine("Eres un entrenador personal. Esta es la sesión de hoy de tu atleta.")
            appendLine("Tipo de día: ${split.spanishName()}. Objetivo: ${goal.spanishName()}.")
            appendLine()
            appendLine(session)
            if (adjustments.isNotEmpty()) {
                appendLine()
                appendLine("Ajustes ya aplicados:")
                adjustments.forEach { appendLine("- $it") }
            }
            appendLine()
            append(
                "Explica en 3 o 4 frases cortas, en español y sin listas, por qué esta sesión tiene " +
                    "sentido para su objetivo: el orden de los ejercicios, las series y repeticiones " +
                    "y los descansos. No cambies la rutina ni inventes ejercicios.",
            )
        }
    }
}
