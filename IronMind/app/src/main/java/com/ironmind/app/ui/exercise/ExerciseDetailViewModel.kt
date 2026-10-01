package com.ironmind.app.ui.exercise

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.core.util.DispatcherProvider
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.usecase.GetTechniqueCoachingUseCase
import com.ironmind.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val getTechniqueCoaching: GetTechniqueCoachingUseCase,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val exerciseId: Long = savedStateHandle[Destinations.ARG_EXERCISE_ID] ?: 0L

    val exercise: StateFlow<Exercise?> = repository.observeExercise(exerciseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Deterministic (non-AI) same-muscle-group / different-equipment alternatives. */
    private val _alternatives = MutableStateFlow<List<Exercise>>(emptyList())
    val alternatives: StateFlow<List<Exercise>> = _alternatives.asStateFlow()

    init {
        if (exerciseId != 0L) {
            viewModelScope.launch {
                _alternatives.value = repository.getAlternatives(exerciseId)
            }
        }
    }

    // ---- Technique coach --------------------------------------------------------------
    private val _techniqueAdvice = MutableStateFlow<SuggestionState?>(null)
    val techniqueAdvice: StateFlow<SuggestionState?> = _techniqueAdvice.asStateFlow()

    private var techniqueJob: Job? = null

    /** Streams AI technique/safety coaching for this exercise into [techniqueAdvice]. */
    fun generateTechniqueCoaching() {
        if (exerciseId == 0L) return
        techniqueJob?.cancel()
        techniqueJob = viewModelScope.launch {
            getTechniqueCoaching(exerciseId).collect { state -> _techniqueAdvice.value = state }
        }
    }

    fun dismissTechniqueCoaching() {
        techniqueJob?.cancel()
        _techniqueAdvice.value = null
    }

    /** Copies the picked image into app storage and links it to this exercise. */
    fun attachImage(uri: Uri) {
        if (exerciseId == 0L) return
        viewModelScope.launch {
            runCatching {
                val path = copyImageToStorage(uri, exerciseId)
                repository.getExercise(exerciseId)?.let { repository.upsertExercise(it.copy(imagePath = path)) }
            }
        }
    }

    /** Removes the linked reference image (and deletes the copied file). */
    fun removeImage() {
        if (exerciseId == 0L) return
        viewModelScope.launch {
            repository.getExercise(exerciseId)?.let { current ->
                current.imagePath?.let { path -> runCatching { File(path).delete() } }
                repository.upsertExercise(current.copy(imagePath = null))
            }
        }
    }

    private suspend fun copyImageToStorage(uri: Uri, id: Long): String = withContext(dispatchers.io) {
        val dir = File(context.filesDir, IMAGE_SUBDIR).apply { mkdirs() }
        val dest = File(dir, "ex_$id.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo abrir la imagen seleccionada.")
        dest.absolutePath
    }

    private companion object {
        const val IMAGE_SUBDIR = "exercise_images"
    }
}
