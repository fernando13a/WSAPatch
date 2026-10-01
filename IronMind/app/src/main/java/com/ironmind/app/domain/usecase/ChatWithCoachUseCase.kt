package com.ironmind.app.domain.usecase

import com.ironmind.app.domain.ai.ChatPromptBuilder
import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.model.ChatMessage
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

/**
 * Answers a free-form question from the athlete as their on-device coach. Unlike the one-shot AI
 * features, the model gets the recent conversation as well as a deterministic snapshot of real
 * training data ([ChatPromptBuilder]) — so follow-ups like "¿y para la próxima semana?" make sense
 * while the numbers it quotes still come from Kotlin, never from the model's arithmetic.
 *
 * Streams [SuggestionState] like every other AI feature here, so the UI treats it identically.
 */
class ChatWithCoachUseCase @Inject constructor(
    private val repository: WorkoutRepository,
    private val llmInferenceService: LlmInferenceService,
) {

    operator fun invoke(question: String, history: List<ChatMessage>): Flow<SuggestionState> = flow {
        emit(SuggestionState.Loading)

        val since = System.currentTimeMillis() - WINDOW_DAYS.days.inWholeMilliseconds
        val activity = repository.getRecentActivity(since)
        val exercisesById = repository.getAllExercises().associateBy { it.id }
        val sessionStarts = repository.observeSessions().first().map { it.startedAt }
        val snapshot = ChatPromptBuilder.snapshot(activity, exercisesById, sessionStarts)
        val prompt = ChatPromptBuilder.build(question, history, snapshot)

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
            else -> throwable.message ?: "Ocurrió un error al responder."
        }
        emit(SuggestionState.Error(message))
    }

    companion object {
        /** How far back the snapshot looks for cross-exercise activity. */
        const val WINDOW_DAYS = 28
    }
}
