package com.ironmind.app.ui.progress

import com.ironmind.app.domain.model.SetLog

/** One point on the load-evolution curve: the top working weight for a given day. */
data class LoadPoint(
    val timeMillis: Long,
    val value: Float,
    val label: String,
)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val exerciseId: Long = 0,
    val exerciseName: String = "",
    val points: List<LoadPoint> = emptyList(),
    val history: List<SetLog> = emptyList(),
    val bestWeight: Float = 0f,
)
