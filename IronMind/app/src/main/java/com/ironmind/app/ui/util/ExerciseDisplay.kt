package com.ironmind.app.ui.util

import com.ironmind.app.domain.model.Exercise
import java.util.Locale

/**
 * UI helper for displaying exercise names in the appropriate language.
 * Uses device Locale to determine Spanish vs English.
 */

/**
 * Get the display name for an exercise in the user's preferred language.
 * Spanish: Uses nameEs if available, falls back to name.
 * English: Always uses name.
 */
fun Exercise.displayName(): String {
    val isSpanish = Locale.getDefault().language == "es"
    return if (isSpanish && !nameEs.isNullOrBlank()) nameEs else name
}

/**
 * Get both English and Spanish names for display (e.g., "Barbell Bench Press / Press de Banca").
 */
fun Exercise.displayNameBilingual(): String {
    return if (!nameEs.isNullOrBlank()) {
        "$name / $nameEs"
    } else {
        name
    }
}
