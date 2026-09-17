package com.ironmind.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val argSessionId: Long = savedStateHandle[Destinations.ARG_SESSION_ID] ?: 0L
    private val argRoutineId: Long = savedStateHandle[Destinations.ARG_ROUTINE_ID] ?: 0L

    private val activeSessionId = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            activeSessionId.value = if (argSessionId != 0L) {
                argSessionId
            } else {
                repository.startSession(
                    WorkoutSession(
                        startedAt = System.currentTimeMillis(),
                        routineId = argRoutineId.takeIf { it != 0L },
                    ),
                )
            }
        }
    }

    private val detailFlow = activeSessionId.flatMapLatest { id ->
        if (id == 0L) flowOf(null) else repository.observeSessionDetail(id)
    }

    private val routinePlanFlow =
        if (argRoutineId != 0L) repository.observeRoutinePlan(argRoutineId) else flowOf(null)

    val uiState: StateFlow<SessionUiState> = combine(
        detailFlow,
        repository.observeExercises(),
        routinePlanFlow,
    ) { detail, exercises, plan ->
        val names = exercises.associate { it.id to it.name }
        val setsByExercise = (detail?.sets ?: emptyList()).groupBy { it.exerciseId }

        val orderedIds = buildList {
            plan?.exercises?.forEach { add(it.id) }
            setsByExercise.keys.forEach { if (it !in this) add(it) }
        }

        SessionUiState(
            isLoading = detail == null,
            sessionId = detail?.session?.id ?: 0,
            title = plan?.routine?.name ?: detail?.session?.title ?: "Sesión libre",
            startedAt = detail?.session?.startedAt ?: 0,
            isFinished = detail?.session?.endedAt != null,
            totalVolume = detail?.totalVolume ?: 0.0,
            exerciseBlocks = orderedIds.map { id ->
                ExerciseBlockUi(
                    exerciseId = id,
                    exerciseName = names[id] ?: "Ejercicio",
                    sets = setsByExercise[id]?.sortedBy { it.setNumber } ?: emptyList(),
                )
            },
            availableExercises = exercises,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionUiState(),
    )

    // ---- Rest timer -----------------------------------------------------------------
    private val _restRemaining = MutableStateFlow(0)
    val restRemaining: StateFlow<Int> = _restRemaining.asStateFlow()
    private var restJob: Job? = null

    fun startRest(seconds: Int) {
        restJob?.cancel()
        _restRemaining.value = seconds
        restJob = viewModelScope.launch {
            while (_restRemaining.value > 0) {
                delay(1_000)
                _restRemaining.value -= 1
            }
        }
    }

    fun stopRest() {
        restJob?.cancel()
        _restRemaining.value = 0
    }

    // ---- Set logging ----------------------------------------------------------------
    fun addSet(exerciseId: Long, weightKg: Double, reps: Int, notes: String?, autoRestSeconds: Int? = 90) {
        val id = activeSessionId.value
        if (id == 0L || exerciseId == 0L) return
        val nextSetNumber =
            (uiState.value.exerciseBlocks.firstOrNull { it.exerciseId == exerciseId }?.sets?.size ?: 0) + 1
        viewModelScope.launch {
            repository.upsertSetLog(
                SetLog(
                    sessionId = id,
                    exerciseId = exerciseId,
                    setNumber = nextSetNumber,
                    weightKg = weightKg,
                    reps = reps,
                    notes = notes?.takeIf { it.isNotBlank() },
                ),
            )
        }
        autoRestSeconds?.let { startRest(it) }
    }

    fun deleteSet(set: SetLog) {
        viewModelScope.launch { repository.deleteSetLog(set) }
    }

    fun finishSession(onDone: () -> Unit) {
        val id = activeSessionId.value
        if (id == 0L) {
            onDone()
            return
        }
        viewModelScope.launch {
            repository.getSession(id)?.let { session ->
                repository.updateSession(session.copy(endedAt = System.currentTimeMillis()))
            }
            onDone()
        }
    }
}
