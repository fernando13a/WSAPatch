package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.RoutineGeneratorPromptBuilder
import com.ironmind.app.domain.ai.RoutineRefinePromptBuilder
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Limitation
import com.ironmind.app.domain.model.RoutineDraft
import com.ironmind.app.domain.model.RoutineDraftExercise
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineRefineState
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.util.RoutineAssembler
import com.ironmind.app.domain.util.RoutineCandidateSelector
import com.ironmind.app.domain.util.RoutineChoiceParser
import com.ironmind.app.domain.util.TrainingHistory
import com.ironmind.app.domain.util.joinAsSpanishList
import com.ironmind.app.domain.util.spanishName
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
 * is the one no model could fix: nothing in the catalog matches the split, equipment and joints
 * to spare.
 *
 * Personal to the athlete through their own log ([TrainingHistory]): muscles trained in the last
 * 48 h get a single exercise, the exercises they actually do are shortlisted first, each row shows
 * the last session's top set, and the day is sized to [invoke]'s time budget. What was tailored
 * is listed on the draft ([RoutineDraft.adjustments]).
 */
class GenerateRoutineUseCase @Inject constructor(
    private val repository: WorkoutRepository,
    private val llmInferenceService: LlmInferenceService,
) {

    /**
     * @param timeBudgetMinutes the whole session, warm-up included; null sizes by count instead.
     * @param avoid joints to spare — exercises loading them never reach the shortlist.
     * @param now injectable so tests can place logged sets relative to "today".
     */
    operator fun invoke(
        split: RoutineSplit,
        goal: TrainingGoal,
        availableEquipment: Set<Equipment>? = null,
        timeBudgetMinutes: Int? = null,
        avoid: Set<Limitation> = emptySet(),
        now: Long = System.currentTimeMillis(),
    ): Flow<RoutineDraftState> = flow {
        emit(RoutineDraftState.Loading(RoutineDraftState.Loading.Phase.READING_HISTORY))

        val catalog = repository.getAllExercises()
        val history = TrainingHistory.from(
            activity = repository.getRecentActivity(now - HISTORY_WINDOW_MS),
            exercisesById = catalog.associateBy { it.id },
            now = now,
        )
        val candidates = RoutineCandidateSelector.select(split, catalog, availableEquipment, history, avoid)
        if (candidates.isEmpty()) {
            val limits = if (avoid.isEmpty()) "" else " y las molestias marcadas"
            emit(
                RoutineDraftState.Error(
                    "No hay ejercicios disponibles para este split con el equipo seleccionado$limits.",
                ),
            )
            return@flow
        }

        emit(RoutineDraftState.Loading(RoutineDraftState.Loading.Phase.ASKING_MODEL))
        val prompt = RoutineGeneratorPromptBuilder.build(split, goal, candidates, history, timeBudgetMinutes)
        val choice = interpret(ask(prompt, RoutineGeneratorPromptBuilder.TEMPERATURE), candidates, BUILT_FROM_RULES)
        val rows = RoutineAssembler.assemble(
            split = split,
            goal = goal,
            candidates = candidates,
            chosen = (choice as? ModelChoice.Picked)?.exercises.orEmpty(),
            history = history,
            timeBudgetMinutes = timeBudgetMinutes,
        ).map { it.withLastTopSet(history) }

        emit(
            RoutineDraftState.Success(
                RoutineDraft(
                    split = split,
                    exercises = rows,
                    source = if (choice is ModelChoice.Picked) RoutineDraft.Source.AI else RoutineDraft.Source.RULES,
                    notice = (choice as? ModelChoice.Unusable)?.reason,
                    adjustments = adjustments(candidates, rows, history, avoid),
                    goal = goal,
                    availableEquipment = availableEquipment,
                    avoid = avoid,
                    history = history,
                ),
            ),
        )
    }.catch { throwable ->
        // Only the repository can get here now; every model failure is already a rule-based draft.
        emit(RoutineDraftState.Error(throwable.message ?: "Ocurrió un error al generar la rutina."))
    }

    /**
     * Adjusts [current] from the athlete's own words — "más corta", "sin sentadilla".
     *
     * Unlike generation, a failure here never replaces anything: the draft the athlete has been
     * editing stays exactly as it was, and [RoutineRefineState.Failed] says why. Exercises kept
     * from [current] keep their rows as they are, edits included; only new ones are prescribed
     * from the goal. Nothing is added back for coverage or time ([RoutineAssembler]'s `fillGaps`),
     * since that would overrule the request.
     */
    fun refine(
        split: RoutineSplit,
        goal: TrainingGoal,
        current: List<RoutineDraftExercise>,
        instruction: String,
        availableEquipment: Set<Equipment>? = null,
        avoid: Set<Limitation> = emptySet(),
        now: Long = System.currentTimeMillis(),
    ): Flow<RoutineRefineState> = flow {
        emit(RoutineRefineState.Loading)
        if (instruction.isBlank()) {
            emit(RoutineRefineState.Failed("Escribe qué quieres cambiar de la rutina."))
            return@flow
        }

        val catalog = repository.getAllExercises()
        val byId = catalog.associateBy { it.id }
        val inDraft = current.mapNotNull { byId[it.exerciseId] }
        if (inDraft.isEmpty()) {
            emit(RoutineRefineState.Failed("No hay ninguna rutina que ajustar."))
            return@flow
        }
        val history = TrainingHistory.from(repository.getRecentActivity(now - HISTORY_WINDOW_MS), byId, now)
        val inDraftIds = inDraft.mapTo(HashSet()) { it.id }
        // The current session first, so "as it is" reads 1..n; then what could replace or join it.
        val candidates = inDraft + RoutineCandidateSelector
            .select(split, catalog, availableEquipment, history, avoid)
            .filter { it.id !in inDraftIds }
            .take((RoutineCandidateSelector.MAX_CANDIDATES - inDraft.size).coerceAtLeast(0))

        val prompt = RoutineRefinePromptBuilder.build(split, goal, candidates, inDraft.size, instruction)
        when (val choice = interpret(ask(prompt, RoutineRefinePromptBuilder.TEMPERATURE), candidates, LEFT_AS_IT_WAS)) {
            is ModelChoice.Unusable -> emit(RoutineRefineState.Failed(choice.reason))
            is ModelChoice.Picked -> {
                val currentById = current.associateBy { it.exerciseId }
                val rows = RoutineAssembler
                    .assemble(split, goal, candidates, choice.exercises, fillGaps = false)
                    .map { currentById[it.exerciseId] ?: it.withLastTopSet(history) }
                // Compared as sets: the assembler puts compounds first, so after a swap the same
                // exercises can come back in another order — that is not a change anyone asked for.
                emit(
                    if (rows.mapTo(HashSet()) { it.exerciseId } == current.mapTo(HashSet()) { it.exerciseId }) {
                        RoutineRefineState.Failed(
                            "La IA devolvió la misma rutina, así que no cambió nada. Prueba a pedirlo de otra forma.",
                        )
                    } else {
                        RoutineRefineState.Applied(rows)
                    },
                )
            }
        }
    }.catch { throwable ->
        emit(RoutineRefineState.Failed(throwable.message ?: "No se pudo ajustar la rutina."))
    }

    /**
     * One line per thing that was tailored, in the order an athlete would ask about them: why a
     * muscle has fewer exercises, why an obvious movement is missing, what was favoured.
     */
    private fun adjustments(
        candidates: List<Exercise>,
        rows: List<RoutineDraftExercise>,
        history: TrainingHistory,
        avoid: Set<Limitation>,
    ): List<String> = buildList {
        val groupById = candidates.associate { it.id to it.muscleGroup }
        rows.mapNotNull { groupById[it.exerciseId] }.distinct()
            .mapNotNull { group -> history.hoursSinceTrained[group]?.let { group to it } }
            .forEach { (group, hours) ->
                add(
                    "${group.spanishName().replaceFirstChar { it.uppercase() }}: lo entrenaste hace " +
                        "${hours.coerceAtLeast(1)} h, así que lleva un solo ejercicio.",
                )
            }
        if (avoid.isNotEmpty()) {
            add("Dejé fuera los ejercicios que cargan ${avoid.map { it.spanishName() }.joinAsSpanishList()}.")
        }
        val familiar = rows.count { history.familiarity(it.exerciseId) > 0 }
        if (familiar > 0) {
            add(if (familiar == 1) "Incluye 1 ejercicio que ya haces." else "Incluye $familiar ejercicios que ya haces.")
        }
    }

    private fun RoutineDraftExercise.withLastTopSet(history: TrainingHistory): RoutineDraftExercise =
        history.lastTopSet[exerciseId]?.let { copy(lastWeightKg = it.weightKg, lastReps = it.reps) } ?: this

    /** Runs [prompt] to completion. Says what happened; [interpret] decides what it means. */
    private suspend fun ask(prompt: String, temperature: Float): ModelAnswer {
        val response = StringBuilder()
        val finished = try {
            withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                llmInferenceService.generateResponseStream(prompt, temperature).collect { response.append(it) }
                true
            }
        } catch (e: CancellationException) {
            throw e // the screen went away; nothing to fall back for
        } catch (e: LlmModelNotFoundException) {
            return ModelAnswer.NotDownloaded
        } catch (e: Exception) {
            return ModelAnswer.Failed(e.message.orEmpty().take(REASON_CHARS))
        }
        return if (finished == null) ModelAnswer.TimedOut else ModelAnswer.Text(response.toString())
    }

    /**
     * The model's picks, or why there aren't any — worded as "<what happened>, así que
     * <[consequence]>" so generation can say it fell back to rules and refining can say the
     * draft was left alone.
     */
    private fun interpret(answer: ModelAnswer, candidates: List<Exercise>, consequence: String): ModelChoice {
        val text = when (answer) {
            ModelAnswer.NotDownloaded -> return ModelChoice.Unusable(
                "El modelo de IA no está descargado, así que $consequence. " +
                    "Descárgalo en Modelo de IA para que la IA elija los ejercicios.",
            )
            is ModelAnswer.Failed -> return ModelChoice.Unusable(
                "La IA no pudo responder, así que $consequence. Motivo: ${answer.reason}",
            )
            ModelAnswer.TimedOut -> return ModelChoice.Unusable(
                "La IA tardó demasiado, así que $consequence. Prueba de nuevo.",
            )
            is ModelAnswer.Text -> answer.text.trim()
        }
        if (text.isEmpty()) return ModelChoice.Unusable("La IA terminó sin responder nada, así que $consequence.")

        val picked = RoutineChoiceParser.parse(text, candidates)
        return when {
            picked.isEmpty() -> ModelChoice.Unusable(
                "No pude interpretar la respuesta de la IA, así que $consequence. Respondió: «${excerpt(text)}»",
            )
            // Every line naming an exercise means it recited the list back rather than choosing.
            picked.size > ECHO_THRESHOLD -> ModelChoice.Unusable(
                "La IA repitió la lista en vez de elegir, así que $consequence.",
            )
            else -> ModelChoice.Picked(picked)
        }
    }

    private fun excerpt(text: String): String =
        if (text.length > REASON_CHARS) text.take(REASON_CHARS).trimEnd() + "…" else text

    private sealed interface ModelAnswer {
        data class Text(val text: String) : ModelAnswer
        data object NotDownloaded : ModelAnswer
        data class Failed(val reason: String) : ModelAnswer
        data object TimedOut : ModelAnswer
    }

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

        /**
         * One query covers recovery (48 h), familiarity and the last top set; 90 days reaches an
         * exercise done once a month without pulling in a lifetime of sets.
         */
        const val HISTORY_WINDOW_MS = 90L * 24 * 60 * 60 * 1000

        /** How much of a reason or of the model's own words a notice shows. */
        const val REASON_CHARS = 300

        /** More picks than a session could hold, with slack for a model that overshoots a bit. */
        const val ECHO_THRESHOLD = RoutineAssembler.MAX_EXERCISES + 2

        const val BUILT_FROM_RULES = "esta rutina se armó con reglas"
        const val LEFT_AS_IT_WAS = "tu rutina quedó igual"
    }
}
