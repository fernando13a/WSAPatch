package com.ironmind.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.data.preferences.AppPreferences
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.usecase.GetRecoveryAdviceUseCase
import com.ironmind.app.domain.util.TrainingHistory
import com.ironmind.app.notification.RestTimerNotifier
import com.ironmind.app.ui.navigation.Destinations
import com.ironmind.app.ui.util.displayName
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val getRecoveryAdvice: GetRecoveryAdviceUseCase,
    private val restNotifier: RestTimerNotifier,
    appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val argSessionId: Long = savedStateHandle[Destinations.ARG_SESSION_ID] ?: 0L
    private val argRoutineId: Long = savedStateHandle[Destinations.ARG_ROUTINE_ID] ?: 0L

    /** The user's preferred display/input weight unit (data stays in kg). */
    val weightUnit = appPreferences.weightUnitFlow

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

    /**
     * For each routine exercise, the heaviest set of the last session it was done in — the
     * "last time" reference shown while training. Never this session's own sets: those are on
     * screen already, and counting them would turn "last time" into "a minute ago".
     */
    private val lastTopSetsFlow = routinePlanFlow.map { plan ->
        val thisSession = activeSessionId.value.takeIf { it != 0L } ?: argSessionId
        buildMap {
            plan?.exercises.orEmpty().forEach { exercise ->
                val earlier = repository.getRecentSetLogs(exercise.id, LAST_PERFORMANCE_SETS)
                    .filter { it.exerciseId == exercise.id && it.sessionId != thisSession }
                TrainingHistory.from(earlier, emptyMap(), System.currentTimeMillis())
                    .lastTopSet[exercise.id]?.let { put(exercise.id, it) }
            }
        }
    }

    val uiState: StateFlow<SessionUiState> = combine(
        detailFlow,
        repository.observeExercises(),
        routinePlanFlow,
        lastTopSetsFlow,
    ) { detail, exercises, plan, lastTopSets ->
        val names = exercises.associate { it.id to it.displayName() }
        val exercisesById = exercises.associateBy { it.id }
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
                    exercise = exercisesById[id],
                    target = plan?.prescriptions?.get(id),
                    lastTopSet = lastTopSets[id],
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

    // The full duration of the current rest, so the UI can draw a proportional countdown ring.
    private val _restTotal = MutableStateFlow(0)
    val restTotal: StateFlow<Int> = _restTotal.asStateFlow()
    private var restJob: Job? = null

    fun startRest(seconds: Int) {
        restJob?.cancel()
        _restTotal.value = seconds
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
        _restTotal.value = 0
        restNotifier.cancel()
    }

    // ---- Set logging ----------------------------------------------------------------
    /**
     * Logs a set and starts the rest timer — for the routine's own rest for this exercise when it
     * has one. It used to be a flat 90 s whatever the routine said, so a strength day's 150 s rest
     * ended a minute early on every set.
     */
    fun addSet(
        exerciseId: Long,
        weightKg: Double,
        reps: Int,
        notes: String?,
        autoRestSeconds: Int? = restFor(exerciseId),
    ) {
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

    private fun restFor(exerciseId: Long): Int =
        uiState.value.exerciseBlocks.firstOrNull { it.exerciseId == exerciseId }?.target?.restSeconds
            ?: DEFAULT_REST_SECONDS

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

    // ---- Recovery coach ---------------------------------------------------------------
    private val _recoveryAdvice = MutableStateFlow<SuggestionState?>(null)
    val recoveryAdvice: StateFlow<SuggestionState?> = _recoveryAdvice.asStateFlow()

    private var recoveryJob: Job? = null

    /** Streams AI recovery advice for [muscleGroup] into [recoveryAdvice]. */
    fun generateRecoveryAdvice(muscleGroup: MuscleGroup) {
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            getRecoveryAdvice(muscleGroup).collect { state -> _recoveryAdvice.value = state }
        }
    }

    /** Clears any shown advice — called both on explicit dismiss and when the selected exercise
     *  (and thus its muscle group) changes, so stale advice from a different group never lingers. */
    fun dismissRecoveryAdvice() {
        recoveryJob?.cancel()
        _recoveryAdvice.value = null
    }

    override fun onCleared() {
        // Don't leave a stuck "resting" notification behind if the screen is torn down mid-rest.
        restNotifier.cancel()
        super.onCleared()
    }

    private companion object {
        /** Rest after a set of an exercise the routine prescribes nothing for (or a free session). */
        const val DEFAULT_REST_SECONDS = 90

        /** Enough recent sets to reach back past today's to the previous session. */
        const val LAST_PERFORMANCE_SETS = 30
    }
}
