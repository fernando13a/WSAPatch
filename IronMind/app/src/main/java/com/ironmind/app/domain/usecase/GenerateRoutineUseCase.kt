package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.RoutineGeneratorPromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.RoutineDraft
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.util.RoutineCandidateSelector
import com.ironmind.app.domain.util.RoutineDraftParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Generates an AI-proposed [RoutineDraft] for [split], fully on-device.
 *
 * Pipeline: filter the catalog to a short, muscle-group/equipment-matched shortlist
 * ([RoutineCandidateSelector]) → build a closed-choice prompt ([RoutineGeneratorPromptBuilder]) →
 * collect the model's full response (no typewriter effect — a routine table has no meaningful
 * partial rendering) → parse it against the shortlist ([RoutineDraftParser]), discarding
 * anything the model got wrong rather than failing outright. The result is never written to
 * [WorkoutRepository] here — it's an editable draft the caller decides whether to save.
 */
class GenerateRoutineUseCase @Inject constructor(
    private val repository: WorkoutRepository,
    private val llmInferenceService: LlmInferenceService,
) {

    operator fun invoke(
        split: RoutineSplit,
        goal: TrainingGoal,
        availableEquipment: Set<Equipment>? = null,
    ): Flow<RoutineDraftState> = flow {
        emit(RoutineDraftState.Loading)

        val catalog = repository.getAllExercises()
        val candidates = RoutineCandidateSelector.select(split, catalog, availableEquipment)
        if (candidates.isEmpty()) {
            emit(
                RoutineDraftState.Error(
                    "No hay ejercicios disponibles para este split con el equipo seleccionado.",
                ),
            )
            return@flow
        }

        val prompt = RoutineGeneratorPromptBuilder.build(split, goal, candidates)

        val response = StringBuilder()
        llmInferenceService.generateResponseStream(prompt).collect { chunk -> response.append(chunk) }

        val exercises = RoutineDraftParser.parse(response.toString(), candidates)
        if (exercises.isEmpty()) {
            emit(RoutineDraftState.Error(unparseableResponseMessage(response.toString())))
            return@flow
        }

        emit(RoutineDraftState.Success(RoutineDraft(split = split, exercises = exercises)))
    }.catch { throwable ->
        val message = when (throwable) {
            is LlmModelNotFoundException ->
                "El modelo de IA no está disponible en el dispositivo. Descárgalo para continuar."
            else -> throwable.message ?: "Ocurrió un error al generar la rutina."
        }
        emit(RoutineDraftState.Error(message))
    }
}

/** How much of the model's raw answer an unparseable-response error shows. */
private const val RESPONSE_EXCERPT_CHARS = 300

/**
 * The error for a response no line of which could be read as a routine row.
 *
 * Carries what the model actually said. The bare "the AI did not return a valid routine" gave no
 * way to tell a model ignoring the format from one answering in a format the parser didn't know,
 * or from one returning nothing at all — and each of those has a different fix. An empty answer
 * gets its own wording because it points at the engine or the prompt, not at the parser.
 */
private fun unparseableResponseMessage(rawResponse: String): String {
    val excerpt = rawResponse.trim()
    if (excerpt.isEmpty()) return "La IA terminó sin responder nada. Intenta de nuevo."
    val shown = if (excerpt.length > RESPONSE_EXCERPT_CHARS) {
        excerpt.take(RESPONSE_EXCERPT_CHARS).trimEnd() + "…"
    } else {
        excerpt
    }
    return "La IA no devolvió una rutina válida. Intenta de nuevo.\n\nRespondió:\n$shown"
}
