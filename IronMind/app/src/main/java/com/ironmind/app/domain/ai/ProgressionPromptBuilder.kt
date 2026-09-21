package com.ironmind.app.domain.ai

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.SetLog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the structured prompt fed to the on-device LLM. It frames the model as a strength
 * coach and lays out the athlete's recent history for the target exercise so the model can
 * reason about progressive overload. Pure and deterministic — easy to unit test.
 */
object ProgressionPromptBuilder {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

    /**
     * @param exercise the exercise the suggestion is for.
     * @param history recent set logs, expected most-recent-first.
     * @param zoneId time zone used to render dates (injectable for testing).
     */
    fun build(
        exercise: Exercise,
        history: List<SetLog>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val historyBlock = if (history.isEmpty()) {
            "Sin registros previos para este ejercicio."
        } else {
            history.joinToString(separator = "\n") { set -> formatSet(set, zoneId) }
        }

        return buildString {
            appendLine(
                "Eres un entrenador personal de élite especializado en fuerza e hipertrofia. " +
                    "Aplicas el principio de sobrecarga progresiva de forma segura y basada en datos.",
            )
            appendLine()
            appendLine("Ejercicio: ${exercise.name} (${exercise.muscleGroup}, ${exercise.equipment}).")
            appendLine()
            appendLine("Historial reciente (más reciente primero, formato: fecha — peso x reps [RPE] · notas):")
            appendLine(historyBlock)
            appendLine()
            appendLine(
                "Con base EXCLUSIVAMENTE en estos datos, recomienda la carga para la próxima sesión:",
            )
            appendLine("1. Peso objetivo (kg) y rango de repeticiones.")
            appendLine("2. Una justificación breve (2-3 frases) citando la tendencia de volumen, el RPE")
            appendLine("   y las notas (energía, suplementos, sensaciones) cuando sean relevantes.")
            appendLine("3. Un consejo de seguridad o técnica si procede.")
            appendLine()
            append("Responde en español, en tono motivador y conciso.")
        }
    }

    private fun formatSet(set: SetLog, zoneId: ZoneId): String {
        val date = dateFormatter.format(Instant.ofEpochMilli(set.performedAt).atZone(zoneId))
        val rpe = set.rpe?.let { " [RPE ${trimFloat(it)}]" }.orEmpty()
        val warmup = if (set.isWarmup) " (calentamiento)" else ""
        val notes = set.notes?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        return "- $date — ${trimFloat(set.weightKg.toFloat())} kg x ${set.reps} reps$rpe$warmup$notes"
    }

    /** Renders 80.0 as "80" and 82.5 as "82.5". */
    private fun trimFloat(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}
