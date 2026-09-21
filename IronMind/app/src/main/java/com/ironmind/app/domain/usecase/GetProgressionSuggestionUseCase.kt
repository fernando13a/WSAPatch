package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.ProgressionPromptBuilder
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Generates an AI progressive-overload suggestion for an exercise, fully on-device.
 *
 * Pipeline: pull the recent history from Room → build a structured coach prompt →
 * stream the model's answer, re-emitting the accumulated text so the UI can render a
 * typewriter effect. The whole flow is expressed as [SuggestionState] (Loading / Success / Error).
 */
class GetProgressionSuggestionUseCase @Inject constructor(
    private val repository: WorkoutRepository,
    private val llmInferenceService: LlmInferenceService,
) {

    operator fun invoke(
        exerciseId: Long,
        historyLimit: Int = DEFAULT_HISTORY_LIMIT,
    ): Flow<SuggestionState> = flow {
        emit(SuggestionState.Loading)

        val exercise = repository.getExercise(exerciseId)
        if (exercise == null) {
            emit(SuggestionState.Error("No se encontró el ejercicio."))
            return@flow
        }

        val history = repository.getRecentSetLogs(exerciseId, historyLimit)
        if (history.isEmpty()) {
            emit(SuggestionState.Error("Aún no hay registros de este ejercicio para analizar."))
            return@flow
        }

        val prompt = ProgressionPromptBuilder.build(exercise, history)

        // Stream tokens, re-emitting the accumulated text (typewriter), then a final complete state.
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
            else -> throwable.message ?: "Ocurrió un error al generar la sugerencia."
        }
        emit(SuggestionState.Error(message))
    }

    companion object {
        /** How many recent sets to feed the model as context. */
        const val DEFAULT_HISTORY_LIMIT = 15
    }
}
