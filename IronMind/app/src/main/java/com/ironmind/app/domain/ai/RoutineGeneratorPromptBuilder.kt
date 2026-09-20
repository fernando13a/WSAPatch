package com.ironmind.app.domain.ai

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal

/**
 * Builds the on-device AI prompt for routine generation. The model never sees the full catalog —
 * only the short, pre-filtered [candidates] list ([RoutineCandidateSelector] built it) — and is
 * required to answer in a strict `ID|SETS|REPS|REST` line format ([RoutineDraftParser] parses it),
 * never free text. This is the key mitigation for generating structured output on a small
 * on-device model that cannot reliably produce valid JSON: a closed choice from a short list plus
 * four numbers per line is a much easier target to hit consistently than open-ended generation.
 */
object RoutineGeneratorPromptBuilder {

    fun build(split: RoutineSplit, goal: TrainingGoal, candidates: List<Exercise>): String {
        val candidateLines = candidates.joinToString(separator = "\n") { "${it.id}|${it.name}" }
        val goalGuidance = when (goal) {
            TrainingGoal.STRENGTH -> "fuerza (series de 3-6 repeticiones, descansos largos de 120-180s)"
            TrainingGoal.HYPERTROPHY -> "hipertrofia (series de 8-12 repeticiones, descansos de 60-90s)"
            TrainingGoal.ENDURANCE -> "resistencia muscular (series de 15-20 repeticiones, descansos de 30-45s)"
        }

        return buildString {
            appendLine(
                "Eres un entrenador personal de élite diseñando la rutina de un día de entrenamiento.",
            )
            appendLine("Split: $split. Objetivo: $goalGuidance.")
            appendLine()
            appendLine(
                "Elige entre 5 y 8 ejercicios EXCLUSIVAMENTE de esta lista (formato id|nombre). " +
                    "Nunca inventes ejercicios fuera de esta lista:",
            )
            appendLine(candidateLines)
            appendLine()
            appendLine(
                "Responde ÚNICAMENTE con una línea por ejercicio elegido, en este formato exacto, " +
                    "sin texto adicional, encabezados ni explicaciones:",
            )
            appendLine("id|series|repeticiones|descanso_segundos")
            appendLine("Ejemplo: 12|3|10|90")
            append(
                "Reglas: series entre 2 y 5, repeticiones entre 4 y 20, descanso entre 30 y 180 " +
                    "segundos.",
            )
        }
    }
}
