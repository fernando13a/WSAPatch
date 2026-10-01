package com.ironmind.app.ui.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.data.preferences.AppPreferences
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.usecase.CompareSessionsUseCase
import com.ironmind.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val compareSessions: CompareSessionsUseCase,
    appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** The user's preferred display weight unit (data stays in kg). */
    val weightUnit = appPreferences.weightUnitFlow

    private val argExerciseId: Long = savedStateHandle[Destinations.ARG_EXERCISE_ID] ?: 0L
    private val zone: ZoneId = ZoneId.systemDefault()
    private val shortDate = DateTimeFormatter.ofPattern("dd/MM")

    // If no exercise was passed, follow the most recently trained one.
    private val resolvedExerciseId: Flow<Long> =
        if (argExerciseId != 0L) {
            flowOf(argExerciseId)
        } else {
            repository.observeSessionDetails()
                .map { sessions -> sessions.firstOrNull()?.sets?.maxByOrNull { it.performedAt }?.exerciseId ?: 0L }
                .distinctUntilChanged()
        }

    val uiState: StateFlow<ProgressUiState> = resolvedExerciseId.flatMapLatest { id ->
        if (id == 0L) {
            flowOf(ProgressUiState(isLoading = false))
        } else {
            combine(
                repository.observeSetLogsForExercise(id),
                repository.observeExercises(),
            ) { logs, exercises ->
                val name = exercises.firstOrNull { it.id == id }?.name ?: "Ejercicio"
                val working = logs.filter { !it.isWarmup }

                val points = working
                    .groupBy { dayStartMillis(it.performedAt) }
                    .map { (dayStart, daySets) ->
                        LoadPoint(
                            timeMillis = dayStart,
                            value = daySets.maxOf { it.weightKg }.toFloat(),
                            label = shortDate.format(Instant.ofEpochMilli(dayStart).atZone(zone)),
                        )
                    }
                    .sortedBy { it.timeMillis }

                ProgressUiState(
                    isLoading = false,
                    exerciseId = id,
                    exerciseName = name,
                    points = points,
                    history = logs.sortedByDescending { it.performedAt },
                    bestWeight = points.maxOfOrNull { it.value } ?: 0f,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProgressUiState(),
    )

    private fun dayStartMillis(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    // ---- Session comparison -----------------------------------------------------------
    val allSessions: StateFlow<List<WorkoutSession>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedSessionIdA = MutableStateFlow<Long?>(null)
    val selectedSessionIdA: StateFlow<Long?> = _selectedSessionIdA.asStateFlow()

    private val _selectedSessionIdB = MutableStateFlow<Long?>(null)
    val selectedSessionIdB: StateFlow<Long?> = _selectedSessionIdB.asStateFlow()

    private val _comparisonResult = MutableStateFlow<SuggestionState?>(null)
    val comparisonResult: StateFlow<SuggestionState?> = _comparisonResult.asStateFlow()

    private var comparisonJob: Job? = null

    fun selectSessionA(sessionId: Long) {
        _selectedSessionIdA.value = sessionId
        dismissComparison()
    }

    fun selectSessionB(sessionId: Long) {
        _selectedSessionIdB.value = sessionId
        dismissComparison()
    }

    /** Streams an AI comparison of the two selected sessions into [comparisonResult]. */
    fun compareSelectedSessions() {
        val idA = _selectedSessionIdA.value
        val idB = _selectedSessionIdB.value
        if (idA == null || idB == null || idA == idB) return
        comparisonJob?.cancel()
        comparisonJob = viewModelScope.launch {
            compareSessions(idA, idB).collect { state -> _comparisonResult.value = state }
        }
    }

    fun dismissComparison() {
        comparisonJob?.cancel()
        _comparisonResult.value = null
    }
}
