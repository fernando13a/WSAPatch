package com.ironmind.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.model.ChatAuthor
import com.ironmind.app.domain.model.ChatMessage
import com.ironmind.app.domain.model.SuggestionState
import com.ironmind.app.domain.usecase.ChatWithCoachUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the conversational coach screen. The transcript lives here for the lifetime of the
 * screen's ViewModel rather than in Room: a chat is a working conversation, not training data, and
 * keeping it out of the database avoids a schema migration for something the athlete never
 * revisits. Each send appends the athlete's turn plus an empty coach bubble that
 * [ChatWithCoachUseCase]'s stream fills in, giving the same typewriter effect as the other AI
 * features.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatWithCoach: ChatWithCoachUseCase,
    private val llmInferenceService: LlmInferenceService,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _modelAvailable = MutableStateFlow(false)
    val modelAvailable: StateFlow<Boolean> = _modelAvailable.asStateFlow()

    private var chatJob: Job? = null
    private var nextId = 0L

    init {
        refreshModelAvailability()
    }

    /** Re-checks whether the on-device model is present (call on resume / after downloading). */
    fun refreshModelAvailability() {
        viewModelScope.launch {
            _modelAvailable.value = llmInferenceService.isModelAvailable()
        }
    }

    /** Sends [question] to the coach, streaming the answer into a fresh coach bubble. */
    fun send(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _isGenerating.value) return

        // Failed turns carry an error message, not coaching — they'd only confuse the model.
        val history = _messages.value.filterNot { it.isError || it.text.isBlank() }
        val userMessage = ChatMessage(id = nextId++, author = ChatAuthor.USER, text = trimmed)
        val coachBubbleId = nextId++
        _messages.value = _messages.value + userMessage +
            ChatMessage(id = coachBubbleId, author = ChatAuthor.COACH, text = "", isStreaming = true)
        _isGenerating.value = true

        chatJob = viewModelScope.launch {
            try {
                chatWithCoach(trimmed, history).collect { state -> apply(coachBubbleId, state) }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** Stops generation, keeping whatever the coach had already said. */
    fun stop() {
        chatJob?.cancel()
        chatJob = null
        _isGenerating.value = false
        _messages.update { messages ->
            messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it }
        }
    }

    /** Re-asks the last question — the retry affordance on a failed turn. */
    fun retryLast() {
        if (_isGenerating.value) return
        val messages = _messages.value
        val lastQuestionIndex = messages.indexOfLast { it.author == ChatAuthor.USER }
        if (lastQuestionIndex == -1) return
        val question = messages[lastQuestionIndex].text
        _messages.value = messages.take(lastQuestionIndex)
        send(question)
    }

    /** Clears the transcript and starts a fresh conversation. */
    fun clear() {
        chatJob?.cancel()
        chatJob = null
        _isGenerating.value = false
        _messages.value = emptyList()
    }

    private fun apply(coachBubbleId: Long, state: SuggestionState) {
        _messages.update { messages ->
            messages.map { message ->
                if (message.id != coachBubbleId) {
                    message
                } else when (state) {
                    SuggestionState.Loading -> message.copy(isStreaming = true)
                    is SuggestionState.Success ->
                        message.copy(text = state.suggestion, isStreaming = !state.isComplete)
                    is SuggestionState.Error ->
                        message.copy(text = state.message, isStreaming = false, isError = true)
                }
            }
        }
    }
}
