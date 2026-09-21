package com.ironmind.app.domain.ai

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.util.estimateOneRepMax

/**
 * Builds the on-device AI prompt for cross-exercise training insights: weekly volume trend,
 * average RPE and current estimated one-rep max per exercise. All numbers are aggregated
 * deterministically in Kotlin ([aggregate]) — the model only *interprets* the table in [build],
 * it never computes it, so what the athlete sees is never hallucinated arithmetic.
 */
object TrendAnalysisPromptBuilder {

    /** Weeks of history bucketed, oldest to newest as rendered. */
    const val WEEK_COUNT = 4
    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    /** Cap on exercises embedded in the prompt — keeps it within [AiConstants.PROMPT_CHAR_BUDGET]. */
    const val MAX_EXERCISES = 8

    /** Per-exercise aggregation: chronological (oldest -> newest) weekly volumes, avg RPE, current 1RM. */
    data class ExerciseTrend(
        val exerciseName: String,
        val weeklyVolumesKg: List<Double>,
        val averageRpe: Float?,
        val estimatedOneRepMaxKg: Double,
    )

    /**
     * Aggregates [activity] (already the desired time window, e.g. from
     * [com.ironmind.app.domain.repository.WorkoutRepository.getRecentActivity]) into per-exercise
     * trends, sorted by total volume descending and capped to [MAX_EXERCISES]. Warmup sets are
     * excluded from every metric — they aren't representative working effort.
     */
    fun aggregate(
        activity: List<SetLog>,
        exercisesById: Map<Long, Exercise>,
        referenceTimeMillis: Long = System.currentTimeMillis(),
    ): List<ExerciseTrend> {
        val workingSets = activity.filterNot { it.isWarmup }
        return workingSets
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, sets) ->
                val exercise = exercisesById[exerciseId] ?: return@mapNotNull null
                val weeklyVolumes = (0 until WEEK_COUNT).map { weekIndex ->
                    val weekEnd = referenceTimeMillis - weekIndex * WEEK_MILLIS
                    val weekStart = weekEnd - WEEK_MILLIS
                    sets.filter { it.performedAt in weekStart until weekEnd }.sumOf { it.volume }
                }.reversed()
                val rpeValues = sets.mapNotNull { it.rpe }
                ExerciseTrend(
                    exerciseName = exercise.name,
                    weeklyVolumesKg = weeklyVolumes,
                    averageRpe = if (rpeValues.isEmpty()) null else rpeValues.average().toFloat(),
                    estimatedOneRepMaxKg = sets.maxOf { estimateOneRepMax(it.weightKg, it.reps) },
                )
            }
            .sortedByDescending { it.weeklyVolumesKg.sum() }
            .take(MAX_EXERCISES)
    }

    /** Builds the full Spanish-framed coach prompt from already-[aggregate]d [trends]. */
    fun build(trends: List<ExerciseTrend>): String {
        val rows = trends.joinToString(separator = "\n") { formatTrend(it) }
        return buildString {
            appendLine(
                "Eres un entrenador personal de élite. A continuación tienes datos YA CALCULADOS " +
                    "(no los recalcules) de las últimas $WEEK_COUNT semanas de entrenamiento, por " +
                    "ejercicio: volumen semanal (peso x reps sumado, en orden cronológico), RPE " +
                    "promedio y 1RM estimado actual.",
            )
            appendLine()
            appendLine(rows)
            appendLine()
            appendLine(
                "Con base EXCLUSIVAMENTE en estos datos, identifica en 3-5 frases: tendencias de " +
                    "volumen (progreso, estancamiento o caída), señales de fatiga (RPE alto con " +
                    "volumen bajando) y a qué ejercicio prestarle atención esta semana.",
            )
            append("Responde en español, en tono motivador y conciso.")
        }
    }

    private fun formatTrend(trend: ExerciseTrend): String {
        val volumes = trend.weeklyVolumesKg.joinToString(separator = " → ") { trimFloat(it.toFloat()) }
        val rpe = trend.averageRpe?.let { "RPE prom ${trimFloat(it)}" } ?: "sin RPE registrado"
        val oneRepMax = trimFloat(trend.estimatedOneRepMaxKg.toFloat())
        return "- ${trend.exerciseName}: vol $volumes kg · $rpe · 1RM est. $oneRepMax kg"
    }

    /** Renders 80.0 as "80" and 82.5 as "82.5". */
    private fun trimFloat(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}
