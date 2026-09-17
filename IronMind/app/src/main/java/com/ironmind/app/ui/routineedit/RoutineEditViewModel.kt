package com.ironmind.app.ui.routineedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineEditUiState(
    val routineId: Long = 0,
    val name: String = "",
    val split: RoutineSplit = RoutineSplit.PUSH,
    val selected: List<Exercise> = emptyList(),
    val catalog: List<Exercise> = emptyList(),
) {
    val canSave: Boolean get() = name.isNotBlank() && selected.isNotEmpty()
    /** Catalog entries not already added to the routine. */
    val addable: List<Exercise> get() = catalog.filter { c -> selected.none { it.id == c.id } }
}

@HiltViewModel
class RoutineEditViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routineId: Long = savedStateHandle[Destinations.ARG_ROUTINE_ID] ?: 0L

    private val _ui = MutableStateFlow(RoutineEditUiState())
    val ui: StateFlow<RoutineEditUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            if (routineId != 0L) {
                repository.getRoutine(routineId)?.let { r ->
                    _ui.update { it.copy(routineId = r.id, name = r.name, split = r.split) }
                }
                val existing = repository.observeRoutinePlan(routineId).first()?.exercises.orEmpty()
                _ui.update { it.copy(selected = existing) }
            }
        }
        viewModelScope.launch {
            repository.observeExercises().collect { list -> _ui.update { it.copy(catalog = list) } }
        }
    }

    fun setName(value: String) = _ui.update { it.copy(name = value) }
    fun setSplit(value: RoutineSplit) = _ui.update { it.copy(split = value) }

    fun addExercise(exercise: Exercise) = _ui.update {
        if (it.selected.any { e -> e.id == exercise.id }) it
        else it.copy(selected = it.selected + exercise)
    }

    fun removeExercise(exercise: Exercise) = _ui.update {
        it.copy(selected = it.selected.filterNot { e -> e.id == exercise.id })
    }

    fun move(fromIndex: Int, toIndex: Int) = _ui.update {
        val list = it.selected.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            list.add(toIndex, list.removeAt(fromIndex))
        }
        it.copy(selected = list)
    }

    /** Creates a custom exercise and adds it to the routine selection. */
    fun createExercise(name: String, muscleGroup: MuscleGroup, equipment: Equipment) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.upsertExercise(
                Exercise(name = name.trim(), muscleGroup = muscleGroup, equipment = equipment, isCustom = true),
            )
            addExercise(Exercise(id = id, name = name.trim(), muscleGroup = muscleGroup, equipment = equipment))
        }
    }

    fun save(onDone: () -> Unit) {
        val state = _ui.value
        if (!state.canSave) return
        viewModelScope.launch {
            val id = repository.upsertRoutine(
                Routine(id = state.routineId, name = state.name.trim(), split = state.split),
            )
            // Reconcile the junction: remove dropped exercises, (re)add selected with order.
            val originalIds = if (routineId != 0L) {
                repository.observeRoutinePlan(routineId).first()?.exercises?.map { it.id }?.toSet().orEmpty()
            } else {
                emptySet()
            }
            val currentIds = state.selected.map { it.id }
            originalIds.filter { it !in currentIds }.forEach { repository.removeExerciseFromRoutine(id, it) }
            currentIds.forEachIndexed { index, exId ->
                repository.addExerciseToRoutine(routineId = id, exerciseId = exId, position = index)
            }
            onDone()
        }
    }
}
