package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.RoutineExplanationPromptBuilder
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.RoutineDraftExercise
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.TrainingGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Streams a short explanation of a routine draft, on request only — see
 * [RoutineExplanationPromptBuilder] for why it isn't part of generation.
 *
 * Same Loading → Success(partial) … Success(complete) shape as the other streaming AI features, so
 * the text types itself out. An answer that ends empty is reported as an error rather than shown
 * as a blank card.
 */
class ExplainRoutineUseCase @Inject constructor(
    private val llmInferenceService: LlmInferenceService,
) {

    operator fun invoke(
        split: RoutineSplit,
        goal: TrainingGoal,
        rows: List<RoutineDraftExercise>,
        exercisesById: Map<Long, Exercise>,
        adjustments: List<String> = emptyList(),
    ): Flow<SuggestionState> = flow {
        emit(SuggestionState.Loading)
        if (rows.isEmpty()) {
            emit(SuggestionState.Error("La rutina no tiene ejercicios que explicar."))
            return@flow
        }

        val prompt = RoutineExplanationPromptBuilder.build(split, goal, rows, exercisesById, adjustments)
        val accumulated = StringBuilder()
        emitAll(
            llmInferenceService.generateResponseStream(prompt).map { chunk ->
                accumulated.append(chunk)
                SuggestionState.Success(accumulated.toString(), isComplete = false)
            },
        )
        val text = accumulated.toString().trim()
        emit(
            if (text.isEmpty()) {
                SuggestionState.Error("La IA terminó sin responder nada. Intenta de nuevo.")
            } else {
                SuggestionState.Success(text, isComplete = true)
            },
        )
    }.catch { throwable ->
        val message = when (throwable) {
            is LlmModelNotFoundException ->
                "El modelo de IA no está descargado. Descárgalo en Modelo de IA para pedir la explicación."
            else -> throwable.message ?: "No se pudo generar la explicación."
        }
        emit(SuggestionState.Error(message))
    }
}
