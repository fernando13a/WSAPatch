package com.ironmind.app.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.data.ai.ModelDownloader
import com.ironmind.app.domain.model.ModelDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val downloader: ModelDownloader,
) : ViewModel() {

    private val _state = MutableStateFlow<ModelDownloadState>(
        if (downloader.isReady()) ModelDownloadState.Ready else ModelDownloadState.Idle,
    )
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    val defaultUrl: String = AiConstants.DEFAULT_MODEL_URL

    fun download(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            downloader.download(url.trim()).collect { _state.value = it }
        }
    }
}
