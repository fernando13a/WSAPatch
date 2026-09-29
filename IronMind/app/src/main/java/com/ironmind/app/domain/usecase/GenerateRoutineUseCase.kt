package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.RoutineGeneratorPromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.RoutineDraft
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.util.RoutineAssembler
import com.ironmind.app.domain.util.RoutineCandidateSelector
import com.ironmind.app.domain.util.RoutineChoiceParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Generates a [RoutineDraft] for [split], fully on-device.
 *
 * Pipeline: shortlist the catalog ([RoutineCandidateSelector]) → ask the model only which of the
 * shortlist to use and in what order ([RoutineGeneratorPromptBuilder], [RoutineChoiceParser]) →
 * balance, order and prescribe in Kotlin ([RoutineAssembler]). The result is never written to
 * [WorkoutRepository] here — it's an editable draft the caller decides whether to save.
 *
 * Whatever the model does, the athlete gets a routine. If it isn't downloaded, fails to start,
 * hangs, or answers with nothing usable, the same assembler builds the day from rules and the
 * draft says why ([RoutineDraft.Source.RULES] with a [RoutineDraft.notice]). The only error left
 * is the one no model could fix: nothing in the catalog matches the split and equipment.
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

        val draft = when (val choice = askModel(split, goal, candidates)) {
            is ModelChoice.Picked -> RoutineDraft(
                split = split,
                exercises = RoutineAssembler.assemble(split, goal, candidates, choice.exercises),
                source = RoutineDraft.Source.AI,
            )
            is ModelChoice.Unusable -> RoutineDraft(
                split = split,
                exercises = RoutineAssembler.assemble(split, goal, candidates),
                source = RoutineDraft.Source.RULES,
                notice = choice.reason,
            )
        }
        emit(RoutineDraftState.Success(draft))
    }.catch { throwable ->
        // Only the repository can get here now; every model failure is already a rule-based draft.
        emit(RoutineDraftState.Error(throwable.message ?: "Ocurrió un error al generar la rutina."))
    }

    private suspend fun askModel(
        split: RoutineSplit,
        goal: TrainingGoal,
        candidates: List<Exercise>,
    ): ModelChoice {
        val response = StringBuilder()
        val finished = try {
            withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                llmInferenceService
                    .generateResponseStream(RoutineGeneratorPromptBuilder.build(split, goal, candidates))
                    .collect { response.append(it) }
                true
            }
        } catch (e: CancellationException) {
            throw e // the screen went away; nothing to fall back for
        } catch (e: LlmModelNotFoundException) {
            return ModelChoice.Unusable(
                "El modelo de IA no está descargado, así que esta rutina se armó con reglas. " +
                    "Descárgalo en Modelo de IA para que la IA elija los ejercicios.",
            )
        } catch (e: Exception) {
            return ModelChoice.Unusable(
                "La IA no pudo responder, así que esta rutina se armó con reglas. " +
                    "Motivo: ${e.message.orEmpty().take(REASON_CHARS)}",
            )
        }
        if (finished == null) {
            return ModelChoice.Unusable(
                "La IA tardó demasiado, así que esta rutina se armó con reglas. Prueba «Regenerar».",
            )
        }

        val text = response.toString().trim()
        if (text.isEmpty()) {
            return ModelChoice.Unusable("La IA terminó sin responder nada, así que esta rutina se armó con reglas.")
        }
        val picked = RoutineChoiceParser.parse(text, candidates)
        return when {
            picked.isEmpty() -> ModelChoice.Unusable(
                "No pude interpretar la respuesta de la IA, así que esta rutina se armó con reglas. " +
                    "Respondió: «${excerpt(text)}»",
            )
            // Every line naming an exercise means it recited the list back rather than choosing.
            picked.size > ECHO_THRESHOLD -> ModelChoice.Unusable(
                "La IA repitió la lista en vez de elegir, así que esta rutina se armó con reglas.",
            )
            else -> ModelChoice.Picked(picked)
        }
    }

    private fun excerpt(text: String): String =
        if (text.length > REASON_CHARS) text.take(REASON_CHARS).trimEnd() + "…" else text

    private sealed interface ModelChoice {
        data class Picked(val exercises: List<Exercise>) : ModelChoice
        data class Unusable(val reason: String) : ModelChoice
    }

    private companion object {
        /**
         * Generous on purpose: the first request of a session also loads half a gigabyte into the
         * engine, which takes tens of seconds on a mid-range phone before a token comes out.
         */
        const val GENERATION_TIMEOUT_MS = 120_000L

        /** How much of a reason or of the model's own words a notice shows. */
        const val REASON_CHARS = 300

        /** More picks than a session could hold, with slack for a model that overshoots a bit. */
        const val ECHO_THRESHOLD = RoutineAssembler.MAX_EXERCISES + 2
    }
}
