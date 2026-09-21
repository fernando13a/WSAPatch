package com.ironmind.app.domain.ai

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog

/**
 * Builds the on-device AI prompt for recovery advice: given how long ago [MuscleGroup] was last
 * trained and how hard that session was (avg RPE), the model suggests whether training it again
 * today is reasonable or whether more rest is warranted. Health-adjacent territory, so the
 * disclaimer that this is not medical advice is baked into the prompt itself, not left to the UI.
 */
object RecoveryPromptBuilder {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** `daysSinceLastTrained` / `lastSessionAverageRpe` are null when there is no recent record. */
    data class RecoverySnapshot(
        val muscleGroup: MuscleGroup,
        val daysSinceLastTrained: Int?,
        val lastSessionAverageRpe: Float?,
    )

    /**
     * Aggregates [activity] (e.g. from
     * [com.ironmind.app.domain.repository.WorkoutRepository.getRecentActivity]) into a snapshot
     * for [muscleGroup]: how many days ago it was last trained, and the average RPE of that
     * specific session (grouped by `sessionId`, not just "sets from around that day", so a
     * different session that happens to land nearby never gets mixed in). Warmup sets are
     * excluded from both the "last trained" timestamp and the RPE average.
     */
    fun snapshot(
        muscleGroup: MuscleGroup,
        activity: List<SetLog>,
        exercisesById: Map<Long, Exercise>,
        referenceTimeMillis: Long = System.currentTimeMillis(),
    ): RecoverySnapshot {
        val relevantSets = activity.filter { set ->
            !set.isWarmup && exercisesById[set.exerciseId]?.muscleGroup == muscleGroup
        }
        val lastSet = relevantSets.maxByOrNull { it.performedAt }
            ?: return RecoverySnapshot(muscleGroup, null, null)

        val daysSince = ((referenceTimeMillis - lastSet.performedAt) / DAY_MILLIS).toInt()
        val lastSessionRpes = relevantSets
            .filter { it.sessionId == lastSet.sessionId }
            .mapNotNull { it.rpe }

        return RecoverySnapshot(
            muscleGroup = muscleGroup,
            daysSinceLastTrained = daysSince,
            lastSessionAverageRpe = if (lastSessionRpes.isEmpty()) null else lastSessionRpes.average().toFloat(),
        )
    }

    /** Builds the full Spanish-framed coach prompt from an already-[snapshot]ted state. */
    fun build(snapshot: RecoverySnapshot): String {
        val history = if (snapshot.daysSinceLastTrained == null) {
            "No hay registros de este grupo muscular en los últimos meses."
        } else {
            val days = snapshot.daysSinceLastTrained
            val dayWord = if (days == 1) "día" else "días"
            val rpeInfo = snapshot.lastSessionAverageRpe
                ?.let { " con RPE promedio de ${trimFloat(it)}" }
                .orEmpty()
            "Última vez entrenado hace $days $dayWord$rpeInfo."
        }

        return buildString {
            appendLine(
                "Eres un entrenador personal de élite con criterio conservador sobre recuperación " +
                    "muscular.",
            )
            appendLine("Grupo muscular a entrenar hoy: ${snapshot.muscleGroup}.")
            appendLine(history)
            appendLine()
            appendLine(
                "Con base EXCLUSIVAMENTE en estos datos, responde en 2-3 frases si es razonable " +
                    "entrenar este grupo hoy o si conviene más descanso, y por qué.",
            )
            appendLine(
                "IMPORTANTE: aclara que esto es una sugerencia orientativa, no un diagnóstico " +
                    "médico ni reemplaza el criterio de un profesional de salud.",
            )
            append("Responde en español, en tono motivador y conciso.")
        }
    }

    /** Renders 8.0 as "8" and 8.5 as "8.5". */
    private fun trimFloat(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}
