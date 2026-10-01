package com.ironmind.app.ui.session

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.data.vision.WeightPhotoReader
import com.ironmind.app.domain.util.DetectedWeight
import com.ironmind.app.domain.util.parseDetectedWeights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of reading a weight off a photo. */
sealed interface PhotoWeightUiState {
    data object Idle : PhotoWeightUiState
    data object Analyzing : PhotoWeightUiState

    /** OCR finished; [detected] may be empty when nothing readable was found. */
    data class Ready(val detected: List<DetectedWeight>) : PhotoWeightUiState
    data class Error(val message: String) : PhotoWeightUiState
}

@HiltViewModel
class PhotoWeightViewModel @Inject constructor(
    private val photoReader: WeightPhotoReader,
) : ViewModel() {

    private val _state = MutableStateFlow<PhotoWeightUiState>(PhotoWeightUiState.Idle)
    val state: StateFlow<PhotoWeightUiState> = _state.asStateFlow()

    /** A private cache URI for the camera app to write the capture into. */
    fun newCaptureUri(): Uri = photoReader.newCaptureUri()

    /** Runs on-device OCR over the picture and extracts the candidate weights. */
    fun analyze(uri: Uri) {
        _state.value = PhotoWeightUiState.Analyzing
        viewModelScope.launch {
            _state.value = runCatching { photoReader.readText(uri) }.fold(
                onSuccess = { PhotoWeightUiState.Ready(parseDetectedWeights(it)) },
                onFailure = { PhotoWeightUiState.Error(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun reset() {
        _state.value = PhotoWeightUiState.Idle
    }
}
