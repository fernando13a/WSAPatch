package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.SessionComparisonPromptBuilder
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Generates an on-device AI comparison of two past workout sessions, fully offline. Per-exercise
 * volume/best-set aggregation happens in [SessionComparisonPromptBuilder] (pure Kotlin); the
 * model only interprets the two summaries, streamed with the same typewriter shape as
 * [GetProgressionSuggestionUseCase] — unlike the routine generator, free-text comparison prose
 * has meaningful partial rendering.
 */
class CompareSessionsUseCase @Inject constructor(
    private val repository: WorkoutRepository,
    private val llmInferenceService: LlmInferenceService,
) {

    operator fun invoke(sessionIdA: Long, sessionIdB: Long): Flow<SuggestionState> = flow {
        emit(SuggestionState.Loading)

        val detailA = repository.observeSessionDetail(sessionIdA).first()
        val detailB = repository.observeSessionDetail(sessionIdB).first()
        if (detailA == null || detailB == null) {
            emit(SuggestionState.Error("No se encontraron las sesiones seleccionadas."))
            return@flow
        }

        val exercisesById = repository.getAllExercises().associateBy { it.id }
        val summaryA = SessionComparisonPromptBuilder.summarize(detailA, exercisesById)
        val summaryB = SessionComparisonPromptBuilder.summarize(detailB, exercisesById)
        val prompt = SessionComparisonPromptBuilder.build(summaryA, summaryB)

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
            else -> throwable.message ?: "Ocurrió un error al comparar las sesiones."
        }
        emit(SuggestionState.Error(message))
    }
}
