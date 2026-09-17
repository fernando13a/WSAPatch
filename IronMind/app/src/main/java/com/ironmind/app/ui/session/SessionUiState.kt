package com.ironmind.app.ui.session

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.SetLog

/** Sets grouped under one exercise within the active session. */
data class ExerciseBlockUi(
    val exerciseId: Long,
    val exerciseName: String,
    val sets: List<SetLog>,
)

data class SessionUiState(
    val isLoading: Boolean = true,
    val sessionId: Long = 0,
    /** Routine or session title; null means an untitled free session (UI supplies a localized label). */
    val title: String? = null,
    val startedAt: Long = 0,
    val isFinished: Boolean = false,
    val totalVolume: Double = 0.0,
    val exerciseBlocks: List<ExerciseBlockUi> = emptyList(),
    val availableExercises: List<Exercise> = emptyList(),
)
