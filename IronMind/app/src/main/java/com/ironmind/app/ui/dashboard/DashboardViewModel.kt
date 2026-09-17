package com.ironmind.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.usecase.GetProgressionSuggestionUseCase
import com.ironmind.app.domain.util.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val getProgressionSuggestion: GetProgressionSuggestionUseCase,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeExercises(),
        repository.observeRoutinePlans(),
        repository.observeSessionDetails(),
    ) { exercises, plans, sessions ->
        val exerciseNames = exercises.associate { it.id to it.name }
        val weekAgo = System.currentTimeMillis() - 7.days.inWholeMilliseconds
        val trainedThisWeek = sessions
            .filter { it.session.startedAt >= weekAgo }
            .flatMap { detail -> detail.sets.map { it.exerciseId } }
            .toSet()

        val latestSet = sessions.firstOrNull()?.sets?.maxByOrNull { it.performedAt }

        DashboardUiState(
            isLoading = false,
            streak = StreakCalculator.currentStreak(sessions.map { it.session.startedAt }),
            totalSessions = sessions.size,
            routines = plans.map { plan ->
                val count = plan.exercises.size
                RoutineProgressUi(
                    routineId = plan.routine.id,
                    name = plan.routine.name,
                    split = plan.routine.split,
                    exerciseCount = count,
                    progress = if (count == 0) 0f else {
                        plan.exercises.count { it.id in trainedThisWeek } / count.toFloat()
                    },
                )
            },
            focusExerciseId = latestSet?.exerciseId,
            focusExerciseName = latestSet?.let { exerciseNames[it.exerciseId] },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    private val _suggestion = MutableStateFlow<SuggestionState?>(null)
    val suggestion: StateFlow<SuggestionState?> = _suggestion.asStateFlow()

    private var suggestionJob: Job? = null

    /** Streams an AI progressive-overload suggestion for [exerciseId] into [suggestion]. */
    fun generateSuggestion(exerciseId: Long) {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            getProgressionSuggestion(exerciseId).collect { state -> _suggestion.value = state }
        }
    }

    fun dismissSuggestion() {
        suggestionJob?.cancel()
        _suggestion.value = null
    }
}
