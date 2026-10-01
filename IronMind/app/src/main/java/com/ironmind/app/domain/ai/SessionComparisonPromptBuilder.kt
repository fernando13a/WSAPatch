package com.ironmind.app.domain.ai

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.SessionDetail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the on-device AI prompt comparing two [SessionDetail]s. Per-exercise volume/best-set
 * aggregation happens in pure Kotlin ([summarize]) — the model only interprets the two already-
 * computed summaries in [build], the same "Kotlin computes, the model interprets" split used by
 * [TrendAnalysisPromptBuilder] and [RecoveryPromptBuilder].
 */
object SessionComparisonPromptBuilder {

    /** Caps exercises embedded per session so two heavy sessions can't blow the prompt budget. */
    private const val MAX_EXERCISES_PER_SESSION = 6

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

    data class SessionSummary(
        val label: String,
        val totalVolumeKg: Double,
        val workingSetCount: Int,
        val exerciseLines: List<String>,
    )

    /**
     * Aggregates [detail] into a [SessionSummary]: session label, total volume, working-set
     * count, and a per-exercise line (best set + that exercise's volume in this session) for its
     * top [MAX_EXERCISES_PER_SESSION] exercises by volume. Warmup and incomplete sets are
     * excluded, same convention as [SessionDetail.totalVolume]/[SessionDetail.workingSetCount].
     */
    fun summarize(
        detail: SessionDetail,
        exercisesById: Map<Long, Exercise>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): SessionSummary {
        val workingSets = detail.sets.filter { it.isCompleted && !it.isWarmup }
        val exerciseLines = workingSets
            .groupBy { it.exerciseId }
            .map { (exerciseId, sets) ->
                val name = exercisesById[exerciseId]?.name ?: "Ejercicio $exerciseId"
                val best = sets.maxBy { it.weightKg }
                val volume = sets.sumOf { it.volume }
                Triple(name, best, volume)
            }
            .sortedByDescending { it.third }
            .take(MAX_EXERCISES_PER_SESSION)
            .map { (name, best, volume) ->
                "$name: mejor ${trimFloat(best.weightKg)}kg x${best.reps} · vol ${trimFloat(volume)}kg"
            }

        val date = dateFormatter.format(Instant.ofEpochMilli(detail.session.startedAt).atZone(zoneId))
        val title = detail.session.title?.takeIf { it.isNotBlank() } ?: "Sesión libre"

        return SessionSummary(
            label = "$title ($date)",
            totalVolumeKg = detail.totalVolume,
            workingSetCount = detail.workingSetCount,
            exerciseLines = exerciseLines,
        )
    }

    /** Builds the full Spanish-framed coach prompt from two already-[summarize]d sessions. */
    fun build(a: SessionSummary, b: SessionSummary): String {
        return buildString {
            appendLine(
                "Eres un entrenador personal de élite. A continuación tienes datos YA CALCULADOS " +
                    "(no los recalcules) de dos sesiones de entrenamiento.",
            )
            appendLine()
            appendLine(formatSession("Sesión A", a))
            appendLine()
            appendLine(formatSession("Sesión B", b))
            appendLine()
            appendLine(
                "Con base EXCLUSIVAMENTE en estos datos, compara ambas sesiones en 3-5 frases: " +
                    "qué mejoró, qué empeoró, y una recomendación breve para la próxima sesión.",
            )
            append("Responde en español, en tono motivador y conciso.")
        }
    }

    private fun formatSession(heading: String, summary: SessionSummary): String = buildString {
        appendLine("$heading — ${summary.label}:")
        appendLine("Volumen total: ${trimFloat(summary.totalVolumeKg)}kg · ${summary.workingSetCount} sets.")
        if (summary.exerciseLines.isEmpty()) {
            append("Sin sets de trabajo registrados.")
        } else {
            append(summary.exerciseLines.joinToString(separator = "\n") { "- $it" })
        }
    }

    /** Renders 80.0 as "80" and 82.5 as "82.5". */
    private fun trimFloat(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
