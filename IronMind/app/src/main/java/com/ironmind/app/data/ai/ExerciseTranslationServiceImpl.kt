package com.ironmind.app.data.ai

import com.ironmind.app.domain.ai.ExerciseTranslationService
import com.ironmind.app.domain.ai.ExerciseVocabulary
import com.ironmind.app.domain.ai.LlmInferenceService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Implementation of ExerciseTranslationService.
 * Uses vocabulary for composed translations; falls back to Gemma for complex names.
 */
class ExerciseTranslationServiceImpl(
    private val llmService: LlmInferenceService,
) : ExerciseTranslationService {

    override suspend fun translateExerciseName(englishName: String): String? {
        // Try fast vocabulary translation first
        val vocabularyTranslation = ExerciseVocabulary.translateName(englishName)
        if (vocabularyTranslation != null) {
            return vocabularyTranslation
        }

        // Fall back to Gemma for complex or specialized names
        return try {
            val prompt = """
                Translate the following exercise name from English to Spanish in 2-3 words maximum.
                Use standard fitness terminology and capitalize properly.
                Reply with only the Spanish translation, nothing else.

                English: $englishName
                Spanish:
            """.trimIndent()

            llmService.callStreaming(prompt)
                .map { it.trim() }
                .first { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun translateGuide(englishGuide: String): String? {
        return try {
            val prompt = """
                Translate the following exercise how-to guide to Spanish, keeping it concise and clear.
                Maintain the same structure and format as the original.

                English guide:
                $englishGuide

                Spanish translation:
            """.trimIndent()

            llmService.callStreaming(prompt)
                .map { it.trim() }
                .first { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
