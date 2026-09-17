package com.ironmind.app.ui.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

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
}
