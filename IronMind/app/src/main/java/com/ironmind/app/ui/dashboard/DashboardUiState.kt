package com.ironmind.app.ui.dashboard

import com.ironmind.app.domain.model.RoutineSplit

/** A routine summarized for the dashboard, with a 7-day training-progress fraction. */
data class RoutineProgressUi(
    val routineId: Long,
    val name: String,
    val split: RoutineSplit,
    val exerciseCount: Int,
    val progress: Float,
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val streak: Int = 0,
    val totalSessions: Int = 0,
    val routines: List<RoutineProgressUi> = emptyList(),
    val focusExerciseId: Long? = null,
    val focusExerciseName: String? = null,
)
