package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.ExerciseTranslationService
import com.ironmind.app.domain.repository.WorkoutRepository

/**
 * Populates Spanish exercise names for the entire catalog (once, on first launch or manually triggered).
 * Uses vocabulary composition for fast translations; falls back to Gemma for complex names.
 */
class PopulateSpanishExerciseNamesUseCase(
    private val repository: WorkoutRepository,
    private val translationService: ExerciseTranslationService,
) {
    suspend operator fun invoke() {
        val exercises = repository.getAllExercises()
        for (exercise in exercises) {
            // Skip if already translated
            if (exercise.nameEs != null) continue

            // Try to translate (vocabulary or Gemma)
            val translated = translationService.translateExerciseName(exercise.name)
            if (translated != null) {
                repository.updateExerciseNameEs(exercise.id, translated)
            }
        }
    }
}
