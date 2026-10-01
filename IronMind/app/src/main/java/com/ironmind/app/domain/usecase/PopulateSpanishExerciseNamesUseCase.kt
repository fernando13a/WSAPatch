package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.ExerciseVocabulary
import com.ironmind.app.domain.repository.WorkoutRepository

/**
 * Fills in Spanish exercise names from [ExerciseVocabulary], which is pure, deterministic and
 * instant.
 *
 * Deliberately does **not** fall back to the on-device model. This runs on every launch over the
 * whole ~890-exercise catalog, so one LLM call per untranslated name meant hundreds of sequential
 * inferences at startup, each holding the engine's mutex — the first tap on the coach or the chat
 * would queue behind all of them and look frozen. Worse, a failed translation was never recorded,
 * so the same backlog ran again on the next launch, forever. An exercise the vocabulary can't
 * compose simply keeps its English name.
 */
class PopulateSpanishExerciseNamesUseCase(
    private val repository: WorkoutRepository,
) {
    suspend operator fun invoke() {
        for (exercise in repository.getAllExercises()) {
            if (exercise.nameEs != null) continue
            val translated = ExerciseVocabulary.translateName(exercise.name) ?: continue
            repository.updateExerciseNameEs(exercise.id, translated)
        }
    }
}
