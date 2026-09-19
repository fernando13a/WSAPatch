package com.ironmind.app.ui.model

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.data.ai.ModelDownloader
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
            download(defaultUrl)
        }
    }

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
}
