package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.TrendAnalysisPromptBuilder
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

/**
 * Generates an on-device AI summary of recent cross-exercise training trends: weekly volume,
 * average RPE and estimated 1RM per exercise over the last [WINDOW_DAYS], aggregated
 * deterministically in Kotlin ([TrendAnalysisPromptBuilder]) so the model only interprets
 * numbers — it never computes them. Reuses [SuggestionState] (Loading / Success / Error), the
 * same shape [GetProgressionSuggestionUseCase] streams, so the UI layer treats both identically.
 */
class GetTrainingInsightsUseCase @Inject constructor(
    private val repository: WorkoutRepository,
    private val llmInferenceService: LlmInferenceService,
) {

    operator fun invoke(): Flow<SuggestionState> = flow {
        emit(SuggestionState.Loading)

        val since = System.currentTimeMillis() - WINDOW_DAYS.days.inWholeMilliseconds
        val activity = repository.getRecentActivity(since)
        val exercisesById = repository.getAllExercises().associateBy { it.id }
        val trends = TrendAnalysisPromptBuilder.aggregate(activity, exercisesById)
        if (trends.isEmpty()) {
            emit(SuggestionState.Error("Aún no hay actividad reciente para analizar."))
            return@flow
        }

        val prompt = TrendAnalysisPromptBuilder.build(trends)

        val accumulated = StringBuilder()
        emitAll(
            llmInferenceService.generateResponseStream(prompt).map { chunk ->
                accumulated.append(chunk)
                SuggestionState.Success(accumulated.toString(), isComplete = false)
            },
        )
        emit(SuggestionState.Success(accumulated.toString(), isComplete = true))
    }.catch { throwable ->
        val message = when (throwable) {
            is LlmModelNotFoundException ->
                "El modelo de IA no está disponible en el dispositivo. Descárgalo para continuar."
            else -> throwable.message ?: "Ocurrió un error al generar los insights."
        }
        emit(SuggestionState.Error(message))
    }

    companion object {
        /** How many days back to pull cross-exercise activity for aggregation. */
        const val WINDOW_DAYS = TrendAnalysisPromptBuilder.WEEK_COUNT * 7
    }
}
