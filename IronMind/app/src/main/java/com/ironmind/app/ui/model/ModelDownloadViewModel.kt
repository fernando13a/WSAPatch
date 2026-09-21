package com.ironmind.app.ui.model

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.data.ai.ModelDownloader
import com.ironmind.app.data.ai.RobustModelDownloadService
import com.ironmind.app.domain.model.ModelDownloadState
import com.ironmind.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val downloader: ModelDownloader,
    private val robustDownloader: RobustModelDownloadService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<ModelDownloadState>(
        if (downloader.isReady()) ModelDownloadState.Ready else ModelDownloadState.Idle,
    )
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    val defaultUrl: String = AiConstants.DEFAULT_MODEL_URL

    init {
        // Arriving from the first-launch prompt: begin the download immediately if the model isn't
        // present and a default URL is configured.
        val autostart = savedStateHandle[Destinations.ARG_AUTOSTART] ?: false
        if (autostart && defaultUrl.isNotBlank() && !downloader.isReady()) {
            downloadWithMobileWarning(defaultUrl)
        }
    }

    /** Download with mobile data warning (shows prompt if on cellular). */
    fun downloadWithMobileWarning(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            robustDownloader.downloadWithWarning(url.trim()).collect { _state.value = it }
        }
    }

    /** User accepted the data cost; download over the metered connection, streaming progress. */
    fun confirmDownloadOnMobileData(url: String) {
        if (url.isBlank() || _state.value is ModelDownloadState.Downloading) return
        // Set synchronously: the confirmation dialog is mounted on the Awaiting state and isn't
        // dismissed by its own button, so leaving the flip until the first emission crosses the IO
        // dispatcher leaves it tappable — and a second tap starts a second download appending into
        // the same .part file, interleaving both bodies into a corrupt model.
        _state.value = ModelDownloadState.Downloading(null)
        viewModelScope.launch {
            robustDownloader.confirmAndDownload(url.trim()).collect { _state.value = it }
        }
    }

    /** Direct download (for WiFi or custom URLs). */
    fun download(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            downloader.download(url.trim()).collect { _state.value = it }
        }
    }

    /** Imports a model file the user picked from device storage. */
    fun importFromFile(uri: Uri) {
        viewModelScope.launch {
            downloader.importFromFile(uri).collect { _state.value = it }
        }
    }

    /** Model size on disk in bytes (0 if not present) — for the "free up space" affordance. */
    fun modelSizeBytes(): Long = downloader.modelSizeBytes()

    /** Deletes the on-device model to reclaim storage, returning the screen to the idle state. */
    fun deleteModel() {
        downloader.deleteModel()
        _state.value = ModelDownloadState.Idle
    }
}
