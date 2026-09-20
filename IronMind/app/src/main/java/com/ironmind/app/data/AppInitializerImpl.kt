package com.ironmind.app.data

import com.ironmind.app.core.util.DispatcherProvider
import com.ironmind.app.domain.ai.ExerciseTranslationService
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.usecase.AppInitializer
import com.ironmind.app.domain.usecase.PopulateSpanishExerciseNamesUseCase
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializes the app on first run and periodically thereafter.
 * Currently handles populating Spanish exercise names from vocabulary.
 */
@Singleton
class AppInitializerImpl @Inject constructor(
    private val repository: WorkoutRepository,
    private val translationService: ExerciseTranslationService,
    private val dispatchers: DispatcherProvider,
) : AppInitializer {

    override suspend fun initialize() {
        withContext(dispatchers.io) {
            // Populate Spanish exercise names (once, then cached in DB)
            PopulateSpanishExerciseNamesUseCase(repository, translationService).invoke()
        }
    }
}
