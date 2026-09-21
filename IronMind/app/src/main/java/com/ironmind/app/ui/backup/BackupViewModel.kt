package com.ironmind.app.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.core.util.DispatcherProvider
import com.ironmind.app.data.preferences.AppPreferences
import com.ironmind.app.domain.backup.BackupManager
import com.ironmind.app.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val dispatchers: DispatcherProvider,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Preferred weight unit, reactive; changing it re-renders every weight in the app. */
    val weightUnit: StateFlow<WeightUnit> = appPreferences.weightUnitFlow

    fun setWeightUnit(unit: WeightUnit) {
        appPreferences.weightUnit = unit
    }

    /** Writes the full-database JSON to the document the user picked. */
    fun export(uri: Uri) {
        _state.value = BackupUiState.Working
        viewModelScope.launch {
            val outcome = runCatching {
                val payload = backupManager.export()
                withContext(dispatchers.io) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray())
                    } ?: error("No writable stream for the selected file.")
                }
            }
            _state.value = outcome.fold(
                onSuccess = { BackupUiState.Exported },
                onFailure = { BackupUiState.Error(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    /** Reads the picked JSON document and replaces all current data with it. */
    fun import(uri: Uri) {
        _state.value = BackupUiState.Working
        viewModelScope.launch {
            val outcome = runCatching {
                val payload = withContext(dispatchers.io) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("No readable stream for the selected file.")
                }
                backupManager.import(payload)
            }
            _state.value = outcome.fold(
                onSuccess = { BackupUiState.Imported(it) },
                onFailure = { BackupUiState.Error(it.message ?: it.javaClass.simpleName) },
            )
        }
    }
}
