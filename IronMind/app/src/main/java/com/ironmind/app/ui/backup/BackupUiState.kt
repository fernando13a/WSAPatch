package com.ironmind.app.ui.backup

import com.ironmind.app.domain.backup.BackupResult

/** UI state for the data backup / restore screen. */
sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
    data object Exported : BackupUiState
    data class Imported(val result: BackupResult) : BackupUiState
    data class Error(val message: String) : BackupUiState
}
