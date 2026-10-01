package com.ironmind.app.ui.routinegenerator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.data.preferences.AppPreferences
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Limitation
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutineDraft
import com.ironmind.app.domain.model.RoutineDraftExercise
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineRefineState
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.usecase.ExplainRoutineUseCase
import com.ironmind.app.domain.usecase.GenerateRoutineUseCase
import com.ironmind.app.domain.util.RoutinePrescription
import com.ironmind.app.domain.util.RoutineSwap
import com.ironmind.app.domain.util.StartingWeight
import com.ironmind.app.domain.util.TrainingHistory
import com.ironmind.app.domain.util.isCompound
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineGeneratorUiState(
    val split: RoutineSplit = RoutineSplit.PUSH,
    val goal: TrainingGoal = TrainingGoal.HYPERTROPHY,
    /** Equipment the AI may choose from; empty disables generation (nothing to pick from). */
    val availableEquipment: Set<Equipment> = Equipment.entries.toSet(),
    val routineName: String = "",
    val exercisesById: Map<Long, Exercise> = emptyMap(),
    /** Whole session, warm-up included — the assembler sizes the routine to fit it. */
    val timeBudgetMinutes: Int = DEFAULT_TIME_BUDGET_MINUTES,
    /** Joints to spare; exercises loading them are left out of the shortlist entirely. */
    val avoid: Set<Limitation> = emptySet(),
) {
    val canGenerate: Boolean get() = availableEquipment.isNotEmpty()

    companion object {
        val TIME_BUDGET_OPTIONS = listOf(30, 45, 60, 90)
        const val DEFAULT_TIME_BUDGET_MINUTES = 60
    }
}

/** The open "Cambiar" menu: which row, and what it could become. */
data class SwapMenu(val exerciseId: Long, val options: List<Exercise>)

/**
 * Drives the AI routine generator screen: a form (split / goal / equipment / time / joints) feeds
 * [GenerateRoutineUseCase], whose result becomes an **editable** draft — [draftRows] is a mutable
 * copy the user can remove rows from, swap, adjust in words, or edit before anything reaches
 * [WorkoutRepository]. [draftState] separately tracks generation's own Loading/Success/Error
 * status, while [draftRows] is what actually gets saved.
 */
@HiltViewModel
class RoutineGeneratorViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val generateRoutine: GenerateRoutineUseCase,
    private val explainRoutine: ExplainRoutineUseCase,
    appPreferences: AppPreferences,
) : ViewModel() {

    /** For the "last time / suggested" line on each draft row; weights are stored in kg. */
    val weightUnit: StateFlow<WeightUnit> = appPreferences.weightUnitFlow

    private val _ui = MutableStateFlow(RoutineGeneratorUiState())
    val ui: StateFlow<RoutineGeneratorUiState> = _ui.asStateFlow()

    private val _draftState = MutableStateFlow<RoutineDraftState?>(null)
    val draftState: StateFlow<RoutineDraftState?> = _draftState.asStateFlow()

    private val _draftRows = MutableStateFlow<List<RoutineDraftExercise>>(emptyList())
    val draftRows: StateFlow<List<RoutineDraftExercise>> = _draftRows.asStateFlow()

    /** "¿Por qué esta rutina?" — null until asked, and again whenever the rows it explained change. */
    private val _explanation = MutableStateFlow<SuggestionState?>(null)
    val explanation: StateFlow<SuggestionState?> = _explanation.asStateFlow()

    private val _refineState = MutableStateFlow<RoutineRefineState?>(null)
    val refineState: StateFlow<RoutineRefineState?> = _refineState.asStateFlow()

    private val _swapMenu = MutableStateFlow<SwapMenu?>(null)
    val swapMenu: StateFlow<SwapMenu?> = _swapMenu.asStateFlow()

    private var generateJob: Job? = null
    private var explainJob: Job? = null
    private var refineJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeExercises().collect { list ->
                _ui.update { it.copy(exercisesById = list.associateBy { ex -> ex.id }) }
            }
        }
    }

    fun setSplit(split: RoutineSplit) = _ui.update { it.copy(split = split) }
    fun setGoal(goal: TrainingGoal) = _ui.update { it.copy(goal = goal) }
    fun setRoutineName(name: String) = _ui.update { it.copy(routineName = name) }

    fun toggleEquipment(equipment: Equipment) = _ui.update { state ->
        val current = state.availableEquipment
        state.copy(availableEquipment = if (equipment in current) current - equipment else current + equipment)
    }

    fun setTimeBudget(minutes: Int) = _ui.update { it.copy(timeBudgetMinutes = minutes) }

    fun toggleLimitation(limitation: Limitation) = _ui.update { state ->
        state.copy(avoid = if (limitation in state.avoid) state.avoid - limitation else state.avoid + limitation)
    }

    fun generate() {
        val state = _ui.value
        if (!state.canGenerate) return
        generateJob?.cancel()
        refineJob?.cancel()
        _draftRows.value = emptyList()
        _refineState.value = null
        _swapMenu.value = null
        invalidateExplanation()
        generateJob = viewModelScope.launch {
            generateRoutine(
                split = state.split,
                goal = state.goal,
                availableEquipment = state.availableEquipment,
                timeBudgetMinutes = state.timeBudgetMinutes,
                avoid = state.avoid,
            ).collect { result ->
                _draftState.value = result
                if (result is RoutineDraftState.Success) _draftRows.value = result.draft.exercises
            }
        }
    }

    fun removeRow(exerciseId: Long) {
        _draftRows.update { rows -> rows.filterNot { it.exerciseId == exerciseId } }
        invalidateExplanation()
    }

    fun updateRow(exerciseId: Long, sets: Int, reps: Int, restSeconds: Int) {
        _draftRows.update { rows ->
            rows.map { row ->
                if (row.exerciseId == exerciseId) row.copy(sets = sets, reps = reps, restSeconds = restSeconds) else row
            }
        }
        invalidateExplanation()
    }

    // ---- "Cambiar" ------------------------------------------------------------------------

    fun openSwap(exerciseId: Long) {
        val draft = currentDraft() ?: return
        val state = _ui.value
        val current = state.exercisesById[exerciseId] ?: return
        _swapMenu.value = SwapMenu(
            exerciseId = exerciseId,
            options = RoutineSwap.options(
                current = current,
                catalog = state.exercisesById.values,
                inDraft = _draftRows.value.mapTo(HashSet()) { it.exerciseId },
                // What the draft was built with, not what the form says now.
                availableEquipment = draft.availableEquipment ?: Equipment.entries.toSet(),
                avoid = draft.avoid,
                history = draft.history,
            ),
        )
    }

    fun closeSwap() {
        _swapMenu.value = null
    }

    /**
     * Puts [replacement] where [exerciseId] was. A like-for-like swap (compound for compound)
     * keeps the row's numbers, edits included; across kinds the goal's prescription for the new
     * movement applies, since four heavy sets of eight make no sense for a lateral raise.
     */
    fun swapRow(exerciseId: Long, replacement: Exercise) {
        // The menu never offers one, but a duplicate row would save the same exercise twice.
        if (_draftRows.value.any { it.exerciseId == replacement.id }) {
            _swapMenu.value = null
            return
        }
        val goal = currentDraft()?.goal ?: _ui.value.goal
        val sameKind = _ui.value.exercisesById[exerciseId]?.isCompound() == replacement.isCompound()
        _draftRows.update { rows ->
            rows.map { row ->
                if (row.exerciseId != exerciseId) return@map row
                val base = if (sameKind) {
                    row
                } else {
                    RoutinePrescription.forExercise(goal, replacement.isCompound()).let {
                        row.copy(sets = it.sets, reps = it.reps, restSeconds = it.restSeconds)
                    }
                }
                base.copy(exerciseId = replacement.id, lastWeightKg = null, lastReps = null)
            }
        }
        _swapMenu.value = null
        invalidateExplanation()

        // The swapped-in exercise's own last session, for the "last time / suggested" line.
        viewModelScope.launch {
            val logs = repository.getRecentSetLogs(replacement.id, LAST_PERFORMANCE_SETS)
            val top = TrainingHistory.from(logs, mapOf(replacement.id to replacement), System.currentTimeMillis())
                .lastTopSet[replacement.id] ?: return@launch
            _draftRows.update { rows ->
                rows.map {
                    if (it.exerciseId == replacement.id) it.copy(lastWeightKg = top.weightKg, lastReps = top.reps) else it
                }
            }
        }
    }

    // ---- Adjust in words --------------------------------------------------------------------

    fun refine(instruction: String) {
        val draft = currentDraft() ?: return
        val rows = _draftRows.value
        if (rows.isEmpty()) return
        refineJob?.cancel()
        // The model runs one request at a time: an explanation still streaming would hold it, and
        // the wait would count against the adjustment's time limit. An adjusted routine needs a
        // new explanation anyway.
        invalidateExplanation()
        refineJob = viewModelScope.launch {
            generateRoutine.refine(
                split = draft.split,
                goal = draft.goal,
                current = rows,
                instruction = instruction,
                availableEquipment = draft.availableEquipment,
                avoid = draft.avoid,
            ).collect { result ->
                when {
                    result !is RoutineRefineState.Applied -> _refineState.value = result
                    // Built from the rows as they were when asked: applying it now would undo a
                    // removal or an edit made while the model was thinking.
                    _draftRows.value != rows -> _refineState.value = RoutineRefineState.Failed(EDITED_WHILE_REFINING)
                    else -> {
                        _refineState.value = result
                        _draftRows.value = result.rows
                        invalidateExplanation()
                    }
                }
            }
        }
    }

    fun dismissRefineMessage() {
        _refineState.value = null
    }

    // ---- "¿Por qué esta rutina?" ----------------------------------------------------------

    fun explain() {
        val draft = currentDraft() ?: return
        val rows = _draftRows.value
        explainJob?.cancel()
        explainJob = viewModelScope.launch {
            explainRoutine(draft.split, draft.goal, rows, _ui.value.exercisesById, draft.adjustments)
                .collect { _explanation.value = it }
        }
    }

    /** An explanation of rows that have since changed would describe a routine that isn't there. */
    private fun invalidateExplanation() {
        explainJob?.cancel()
        _explanation.value = null
    }

    fun dismissDraft() {
        generateJob?.cancel()
        refineJob?.cancel()
        _draftState.value = null
        _draftRows.value = emptyList()
        _refineState.value = null
        _swapMenu.value = null
        invalidateExplanation()
    }

    private fun currentDraft(): RoutineDraft? = (_draftState.value as? RoutineDraftState.Success)?.draft

    /** Persists the current (possibly user-edited) draft rows as a new routine. */
    fun save(onDone: (routineId: Long) -> Unit) {
        val rows = _draftRows.value
        if (rows.isEmpty()) return
        val state = _ui.value
        val draft = currentDraft()
        // The split the exercises were chosen for. The form's chip may have moved on since, and a
        // push day saved as "LEGS" would be filed and suggested as a leg day.
        val split = draft?.split ?: state.split
        viewModelScope.launch {
            // Fallback name only matters if the user never typed one — the screen encourages
            // naming the routine, this just guarantees upsertRoutine never gets a blank name.
            // "(IA)" only when the model actually chose: a rule-built fallback isn't the AI's.
            val fromRules = draft?.source == RoutineDraft.Source.RULES
            val name = state.routineName.trim().ifBlank {
                if (fromRules) split.name else "${split.name} (IA)"
            }
            val routineId = repository.upsertRoutine(Routine(name = name, split = split))
            rows.forEachIndexed { index, row ->
                repository.addExerciseToRoutine(
                    routineId = routineId,
                    exerciseId = row.exerciseId,
                    position = index,
                    targetSets = row.sets,
                    targetReps = row.reps,
                    targetRestSeconds = row.restSeconds,
                    // The suggestion the draft showed, for the reps actually saved — so the
                    // session can say what to load, not only what was done last time.
                    targetWeightKg = row.lastWeightKg?.let { kg ->
                        row.lastReps?.let { reps -> StartingWeight.suggestKg(kg, reps, row.reps) }
                    },
                )
            }
            onDone(routineId)
        }
    }

    private companion object {
        /** Enough to cover the last session of an exercise even with many sets logged in it. */
        const val LAST_PERFORMANCE_SETS = 30

        const val EDITED_WHILE_REFINING =
            "Cambiaste la rutina mientras la IA la ajustaba, así que dejé tus cambios. Pídelo de nuevo si quieres."
    }
}
