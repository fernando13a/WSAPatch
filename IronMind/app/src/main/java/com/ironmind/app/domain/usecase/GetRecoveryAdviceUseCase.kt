package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.RecoveryPromptBuilder
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

/**
 * Generates on-device AI advice on whether it's reasonable to train [MuscleGroup] today, based
 * on how long ago it was last trained and how hard that session was. Unlike
 * [GetTrainingInsightsUseCase], an empty history is a valid, answerable case here (never having
 * trained a muscle group is itself useful context, not an error) — [RecoveryPromptBuilder] bakes
 * that into the prompt, so this use case only surfaces [SuggestionState.Error] for real failures
 * (missing model, inference errors).
 */
class GetRecoveryAdviceUseCase @Inject constructor(
    private val repository: WorkoutRepository,
    private val llmInferenceService: LlmInferenceService,
) {

    operator fun invoke(muscleGroup: MuscleGroup): Flow<SuggestionState> = flow {
        emit(SuggestionState.Loading)

        val since = System.currentTimeMillis() - WINDOW_DAYS.days.inWholeMilliseconds
        val activity = repository.getRecentActivity(since)
        val exercisesById = repository.getAllExercises().associateBy { it.id }
        val snapshot = RecoveryPromptBuilder.snapshot(muscleGroup, activity, exercisesById)
        val prompt = RecoveryPromptBuilder.build(snapshot)

        val accumulated = StringBuilder()
        emitAll(
            llmInferenceService.generateResponseStream(prompt).map { chunk ->
                accumulated.append(chunk)
                SuggestionState.Success(accumulated.toString(), isComplete = false)
            },
        )
        emit(SuggestionState.Success(accumulated.toString(), isComplete = true))
    }.catch { throwable ->
        val message = when (throwable) {
            is LlmModelNotFoundException ->
                "El modelo de IA no está disponible en el dispositivo. Descárgalo para continuar."
            else -> throwable.message ?: "Ocurrió un error al generar el consejo de recuperación."
        }
        emit(SuggestionState.Error(message))
    }

    companion object {
        /** Wide enough to reliably find "last trained", even for a muscle group trained rarely. */
        const val WINDOW_DAYS = 90
    }
}
