package com.ironmind.app.domain.ai

/**
 * Service for translating exercise names and guides to Spanish.
 * Uses vocabulary for fast composition, falls back to Gemma for complex names.
 * Caching is handled by the repository/data layer.
 */
interface ExerciseTranslationService {
    /**
     * Translate an English exercise name to Spanish.
     * Returns null if translation fails (e.g., Gemma unavailable and vocabulary has no match).
     */
    suspend fun translateExerciseName(englishName: String): String?

    /**
     * Translate exercise instructions/guide to Spanish via Gemma.
     * Returns null if model is unavailable or translation fails.
     */
    suspend fun translateGuide(englishGuide: String): String?
}
