package com.ironmind.app

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.model.ChatAuthor
import com.ironmind.app.domain.usecase.ChatWithCoachUseCase
import com.ironmind.app.ui.chat.ChatViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Captures the prompts it is asked to answer, so context-building can be asserted. */
    private class RecordingLlm(private val chunks: List<String> = listOf("ok")) : LlmInferenceService {
        val prompts = mutableListOf<String>()
        override suspend fun isModelAvailable(): Boolean = true
        override fun generateResponseStream(prompt: String): Flow<String> {
            prompts += prompt
            return chunks.asFlow()
        }
        override fun close() = Unit
    }

    private fun viewModel(
        llm: LlmInferenceService = FakeLlmInferenceService(),
        repo: FakeWorkoutRepository = FakeWorkoutRepository(),
    ) = ChatViewModel(ChatWithCoachUseCase(repo, llm), llm)

    @Test
    fun send_appendsAthleteTurnAndStreamsTheCoachAnswer() = runTest {
        val viewModel = viewModel(FakeLlmInferenceService(chunks = listOf("Vas ", "muy bien")))

        viewModel.send("¿Cómo voy?")

        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertEquals(ChatAuthor.USER, messages[0].author)
        assertEquals("¿Cómo voy?", messages[0].text)
        assertEquals(ChatAuthor.COACH, messages[1].author)
        assertEquals("Vas muy bien", messages[1].text)
        assertFalse(messages[1].isStreaming)
        assertFalse(viewModel.isGenerating.value)
    }

    @Test
    fun send_trimsInputAndIgnoresBlankQuestions() = runTest {
        val viewModel = viewModel()

        viewModel.send("   ")
        assertTrue(viewModel.messages.value.isEmpty())

        viewModel.send("  ¿Y hoy?  ")
        assertEquals("¿Y hoy?", viewModel.messages.value.first().text)
    }

    @Test
    fun send_feedsEarlierTurnsBackAsContext() = runTest {
        val llm = RecordingLlm()
        val viewModel = viewModel(llm)

        viewModel.send("¿Subo peso en sentadilla?")
        viewModel.send("¿Y en press?")

        assertEquals(2, llm.prompts.size)
        // The first question has no history behind it; the second carries the first exchange.
        assertFalse(llm.prompts[0].contains("CONVERSACIÓN HASTA AHORA"))
        assertTrue(llm.prompts[1].contains("¿Subo peso en sentadilla?"))
        assertTrue(llm.prompts[1].contains("¿Y en press?"))
    }

    @Test
    fun send_marksTheTurnAsErrorWhenTheModelIsMissing() = runTest {
        val llm = FakeLlmInferenceService(error = LlmModelNotFoundException("/models/x.task"))
        val viewModel = viewModel(llm)

        viewModel.send("¿Cómo voy?")

        val coachTurn = viewModel.messages.value.last()
        assertTrue(coachTurn.isError)
        assertFalse(coachTurn.isStreaming)
        assertTrue(coachTurn.text.contains("modelo", ignoreCase = true))
        assertFalse(viewModel.isGenerating.value)
    }

    @Test
    fun retryLast_replacesTheFailedTurnWithAFreshAnswer() = runTest {
        val llm = SwitchableLlm()
        val viewModel = viewModel(llm)

        llm.failure = LlmModelNotFoundException("/models/x.task")
        viewModel.send("¿Cómo voy?")
        assertTrue(viewModel.messages.value.last().isError)

        llm.failure = null
        viewModel.retryLast()

        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertEquals("¿Cómo voy?", messages[0].text)
        assertFalse(messages[1].isError)
        assertEquals("listo", messages[1].text)
    }

    @Test
    fun errorTurnsAreNotSentBackAsContext() = runTest {
        val llm = SwitchableLlm()
        val viewModel = viewModel(llm)

        llm.failure = LlmModelNotFoundException("/models/x.task")
        viewModel.send("primera")
        llm.failure = null
        viewModel.send("segunda")

        // The error text ("El modelo de IA no está disponible…") must not reach the model as if it
        // were something the coach actually said.
        assertFalse(llm.prompts.last().contains("Coach: El modelo"))
    }

    @Test
    fun clear_emptiesTheTranscript() = runTest {
        val viewModel = viewModel()

        viewModel.send("¿Cómo voy?")
        viewModel.clear()

        assertTrue(viewModel.messages.value.isEmpty())
        assertFalse(viewModel.isGenerating.value)
    }

    @Test
    fun modelAvailable_reflectsTheInferenceService() = runTest {
        val unavailable = viewModel(FakeLlmInferenceService(modelAvailable = false))
        assertFalse(unavailable.modelAvailable.value)

        val available = viewModel(FakeLlmInferenceService(modelAvailable = true))
        assertTrue(available.modelAvailable.value)
    }

    /** Like [RecordingLlm], but its outcome can be flipped between calls to drive retry tests. */
    private class SwitchableLlm : LlmInferenceService {
        val prompts = mutableListOf<String>()
        var failure: Throwable? = null

        override suspend fun isModelAvailable(): Boolean = true
        override fun generateResponseStream(prompt: String): Flow<String> {
            prompts += prompt
            val error = failure
            return if (error != null) flow { throw error } else listOf("listo").asFlow()
        }
        override fun close() = Unit
    }
}
