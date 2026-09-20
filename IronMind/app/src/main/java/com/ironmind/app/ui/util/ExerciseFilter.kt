package com.ironmind.app.ui.util

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import java.text.Normalizer

/** Accent- and case-insensitive fold, so "biceps", "Bíceps" and "BICEPS" all match. */
fun String.foldForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .trim()

/**
 * Filters an exercise catalog by a free-text query (accent/case-insensitive, matched against the
 * name) and an optional muscle-group facet. Pure and deterministic, so it is unit-testable and can
 * drive the searchable catalog picker without a huge scrolling dropdown.
 */
fun filterExercises(
    catalog: List<Exercise>,
    query: String,
    muscleGroup: MuscleGroup? = null,
): List<Exercise> {
    val q = query.foldForSearch()
    return catalog.filter { ex ->
        (muscleGroup == null || ex.muscleGroup == muscleGroup) &&
            (q.isEmpty() || ex.name.foldForSearch().contains(q))
    }
}
