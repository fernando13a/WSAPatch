package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.TechniqueCoachPromptBuilder
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Generates an on-device AI technique/safety coaching answer for an exercise, fully offline.
 * Mirrors [GetProgressionSuggestionUseCase]'s Loading/Success/Error streaming shape, but needs no
 * set-log history — [TechniqueCoachPromptBuilder] grounds the prompt in the exercise's curated
 * guide when one exists, or asks for general, conservative cues when it doesn't.
 */
class GetTechniqueCoachingUseCase @Inject constructor(
    private val repository: WorkoutRepository,
    private val llmInferenceService: LlmInferenceService,
) {

    operator fun invoke(exerciseId: Long): Flow<SuggestionState> = flow {
        emit(SuggestionState.Loading)

        val exercise = repository.getExercise(exerciseId)
        if (exercise == null) {
            emit(SuggestionState.Error("No se encontró el ejercicio."))
            return@flow
        }

        val prompt = TechniqueCoachPromptBuilder.build(exercise)

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
            else -> throwable.message ?: "Ocurrió un error al generar la guía de técnica."
        }
        emit(SuggestionState.Error(message))
    }
}
