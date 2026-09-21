package com.ironmind.app.ui.util

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import java.text.Normalizer

/** Combining marks left behind by NFD decomposition — hoisted so it compiles once, not per call. */
private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Accent- and case-insensitive fold, so "biceps", "Bíceps" and "BICEPS" all match. */
fun String.foldForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()
        .trim()

/**
 * Filters an exercise catalog by a free-text query (accent/case-insensitive) and an optional
 * muscle-group facet. Pure and deterministic, so it is unit-testable and can drive the searchable
 * catalog picker without a huge scrolling dropdown.
 *
 * Matches both [Exercise.name] and [Exercise.nameEs]: the rows show the Spanish name on a Spanish
 * device, so searching for what's on screen has to work — matching the English name only made the
 * 876-entry catalog look empty for the exact query the user could read.
 */
fun filterExercises(
    catalog: List<Exercise>,
    query: String,
    muscleGroup: MuscleGroup? = null,
): List<Exercise> {
    val q = query.foldForSearch()
    // Facet first: it's a reference comparison, and on a filtered muscle group it spares the fold
    // for most of the ~890-entry catalog on every keystroke.
    return catalog.filter { ex ->
        if (muscleGroup != null && ex.muscleGroup != muscleGroup) return@filter false
        q.isEmpty() ||
            ex.name.foldForSearch().contains(q) ||
            ex.nameEs?.foldForSearch()?.contains(q) == true
    }
}
