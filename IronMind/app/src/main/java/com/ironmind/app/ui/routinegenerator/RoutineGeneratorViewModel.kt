package com.ironmind.app.ui.routinegenerator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutineDraftExercise
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.usecase.GenerateRoutineUseCase
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
) {
    val canGenerate: Boolean get() = availableEquipment.isNotEmpty()
}

/**
 * Drives the AI routine generator screen: a form (split / goal / equipment) feeds
 * [GenerateRoutineUseCase], whose result becomes an **editable** draft — [draftRows] is a mutable
 * copy of the AI's proposal the user can remove rows from or adjust before anything reaches
 * [WorkoutRepository]. [draftState] separately tracks the AI call's own Loading/Success/Error
 * status (for the loading spinner / error message), while [draftRows] is what actually gets saved.
 */
@HiltViewModel
class RoutineGeneratorViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val generateRoutine: GenerateRoutineUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(RoutineGeneratorUiState())
    val ui: StateFlow<RoutineGeneratorUiState> = _ui.asStateFlow()

    private val _draftState = MutableStateFlow<RoutineDraftState?>(null)
    val draftState: StateFlow<RoutineDraftState?> = _draftState.asStateFlow()

    private val _draftRows = MutableStateFlow<List<RoutineDraftExercise>>(emptyList())
    val draftRows: StateFlow<List<RoutineDraftExercise>> = _draftRows.asStateFlow()

    private var generateJob: Job? = null

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

    fun generate() {
        val state = _ui.value
        if (!state.canGenerate) return
        generateJob?.cancel()
        _draftRows.value = emptyList()
        generateJob = viewModelScope.launch {
            generateRoutine(state.split, state.goal, state.availableEquipment).collect { result ->
                _draftState.value = result
                if (result is RoutineDraftState.Success) _draftRows.value = result.draft.exercises
            }
        }
    }

    fun removeRow(exerciseId: Long) {
        _draftRows.update { rows -> rows.filterNot { it.exerciseId == exerciseId } }
    }

    fun updateRow(exerciseId: Long, sets: Int, reps: Int, restSeconds: Int) {
        _draftRows.update { rows ->
            rows.map { row ->
                if (row.exerciseId == exerciseId) row.copy(sets = sets, reps = reps, restSeconds = restSeconds) else row
            }
        }
    }

    fun dismissDraft() {
        generateJob?.cancel()
        _draftState.value = null
        _draftRows.value = emptyList()
    }

    /** Persists the current (possibly user-edited) draft rows as a new routine. */
    fun save(onDone: (routineId: Long) -> Unit) {
        val rows = _draftRows.value
        if (rows.isEmpty()) return
        val state = _ui.value
        viewModelScope.launch {
            // Fallback name only matters if the user never typed one — the screen encourages
            // naming the routine, this just guarantees upsertRoutine never gets a blank name.
            val name = state.routineName.trim().ifBlank { "${state.split.name} (IA)" }
            val routineId = repository.upsertRoutine(Routine(name = name, split = state.split))
            rows.forEachIndexed { index, row ->
                repository.addExerciseToRoutine(
                    routineId = routineId,
                    exerciseId = row.exerciseId,
                    position = index,
                    targetSets = row.sets,
                    targetReps = row.reps,
                    targetRestSeconds = row.restSeconds,
                )
            }
            onDone(routineId)
        }
    }
}
