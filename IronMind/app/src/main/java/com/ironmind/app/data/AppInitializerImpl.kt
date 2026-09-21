package com.ironmind.app.data

import android.content.Context
import com.ironmind.app.core.util.DispatcherProvider
import com.ironmind.app.data.local.dao.WorkoutDao
import com.ironmind.app.data.local.seed.DefaultExercises
import com.ironmind.app.domain.ai.ExerciseTranslationService
import com.ironmind.app.domain.repository.WorkoutRepository
import com.ironmind.app.domain.usecase.AppInitializer
import com.ironmind.app.domain.usecase.PopulateSpanishExerciseNamesUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializes the app on first run and periodically thereafter: Spanish exercise names, and the
 * demo images for curated exercises seeded before the catalog alias table existed.
 */
@Singleton
class AppInitializerImpl @Inject constructor(
    private val repository: WorkoutRepository,
    private val translationService: ExerciseTranslationService,
    private val dao: WorkoutDao,
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : AppInitializer {

    override suspend fun initialize() {
        withContext(dispatchers.io) {
            // Idempotent: a no-op once every curated exercise has its image.
            DefaultExercises.backfillDemoImages(dao, context)

            // Populate Spanish exercise names (once, then cached in DB)
            PopulateSpanishExerciseNamesUseCase(repository, translationService).invoke()
        }
    }
}
