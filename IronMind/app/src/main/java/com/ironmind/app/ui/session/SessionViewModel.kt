package com.ironmind.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.notification.RestTimerNotifier
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
    private val restNotifier: RestTimerNotifier,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val argSessionId: Long = savedStateHandle[Destinations.ARG_SESSION_ID] ?: 0L
    private val argRoutineId: Long = savedStateHandle[Destinations.ARG_ROUTINE_ID] ?: 0L

    private val activeSessionId = MutableStateFlow(0L)

    init {
        // Only bind to an already-existing session. A brand-new session is created lazily on the
        // first logged set (see ensureSession) so that entering and leaving without logging never
        // persists an empty session that would inflate the streak / session count.
        if (argSessionId != 0L) activeSessionId.value = argSessionId
    }

    /** Returns the active session id, creating the session on first use. */
    private suspend fun ensureSession(): Long {
        activeSessionId.value.takeIf { it != 0L }?.let { return it }
        val id = repository.startSession(
            WorkoutSession(
                startedAt = System.currentTimeMillis(),
                routineId = argRoutineId.takeIf { it != 0L },
            ),
        )
        activeSessionId.value = id
        return id
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
            // Render as soon as the catalog/plan are available, even before a session row exists.
            isLoading = false,
            sessionId = detail?.session?.id ?: 0,
            title = plan?.routine?.name ?: detail?.session?.title,
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
        // Mirror the countdown into a notification so a backgrounded / pocketed user gets the buzz
        // when it ends, not only while the Session screen is on-screen.
        restNotifier.showCountdown(seconds)
        restJob = viewModelScope.launch {
            while (_restRemaining.value > 0) {
                delay(1_000)
                _restRemaining.value -= 1
                if (_restRemaining.value > 0) restNotifier.showCountdown(_restRemaining.value)
            }
            restNotifier.showComplete()
        }
    }

    fun stopRest() {
        restJob?.cancel()
        _restRemaining.value = 0
        restNotifier.cancel()
    }

    // ---- Set logging ----------------------------------------------------------------
    fun addSet(exerciseId: Long, weightKg: Double, reps: Int, notes: String?, autoRestSeconds: Int? = 90) {
        if (exerciseId == 0L) return
        val nextSetNumber =
            (uiState.value.exerciseBlocks.firstOrNull { it.exerciseId == exerciseId }?.sets?.size ?: 0) + 1
        viewModelScope.launch {
            val id = ensureSession()
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

    /** Edits an existing set in place (same id). */
    fun updateSet(set: SetLog) {
        viewModelScope.launch { repository.upsertSetLog(set) }
    }

    fun deleteSet(set: SetLog) {
        viewModelScope.launch { repository.deleteSetLog(set) }
    }

    fun finishSession(onDone: () -> Unit) {
        stopRest()
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

    override fun onCleared() {
        // Don't leave a stuck "resting" notification behind if the screen is torn down mid-rest.
        restNotifier.cancel()
        super.onCleared()
    }
}
