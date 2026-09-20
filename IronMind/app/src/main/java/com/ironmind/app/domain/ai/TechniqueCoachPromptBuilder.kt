package com.ironmind.app.domain.ai

import com.ironmind.app.domain.model.Exercise

/**
 * Builds the on-device AI prompt for exercise technique coaching.
 *
 * Only ~23 of the 870+ catalog exercises ship a curated Spanish how-to guide
 * (`Exercise.instructions`) — the rest come from a bundled public-domain catalog with no guide at
 * all. When a guide exists, the prompt anchors to it explicitly ("basándote PRINCIPALMENTE en
 * esta guía") the same way [ProgressionPromptBuilder] anchors to logged history. When it doesn't,
 * there is no app data to ground the answer in — asking the model to reason about exercise form
 * from its own general knowledge is the expected use of an AI coach here (unlike a progression
 * suggestion, which must never invent numbers), so the prompt says so plainly and asks for
 * conservative, general cues rather than presenting a guess as curated content.
 */
object TechniqueCoachPromptBuilder {

    /** Caps the embedded guide text so a long `instructions` field can't blow the prompt budget. */
    private const val MAX_GUIDE_CHARS = 1200

    fun build(exercise: Exercise): String {
        val guide = exercise.instructions?.takeIf { it.isNotBlank() }?.let(::truncate)

        return buildString {
            appendLine(
                "Eres un entrenador personal de élite, experto en biomecánica y técnica de " +
                    "levantamiento.",
            )
            appendLine()
            appendLine("Ejercicio: ${exercise.name} (${exercise.muscleGroup}, ${exercise.equipment}).")
            if (guide != null) {
                appendLine("Guía técnica disponible para este ejercicio:")
                appendLine(guide)
                appendLine()
                appendLine(
                    "Basándote PRINCIPALMENTE en esta guía, profundiza en errores comunes, cues " +
                        "de seguridad y variantes útiles de agarre o postura.",
                )
            } else {
                appendLine("No hay una guía técnica curada para este ejercicio en la app.")
                appendLine()
                appendLine(
                    "Usa tu conocimiento general de biomecánica para dar cues de técnica y " +
                        "seguridad conservadores para este ejercicio. Deja claro que es " +
                        "orientación general (no una guía verificada por la app) y recomienda " +
                        "revisar la forma con un profesional si el movimiento es nuevo para el " +
                        "atleta.",
                )
            }
            appendLine(
                "Responde en 3-5 frases: errores comunes, un cue de seguridad, y una variante " +
                    "útil si aplica.",
            )
            append("Responde en español, en tono motivador y conciso.")
        }
    }

    private fun truncate(text: String): String =
        if (text.length <= MAX_GUIDE_CHARS) text else text.take(MAX_GUIDE_CHARS) + "…"
}
