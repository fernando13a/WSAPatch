package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.ui.util.filterExercises
import com.ironmind.app.ui.util.foldForSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseFilterTest {

    private fun ex(name: String, muscle: MuscleGroup) =
        Exercise(name = name, muscleGroup = muscle, equipment = Equipment.BARBELL)

    private val catalog = listOf(
        ex("Barbell Bench Press", MuscleGroup.CHEST),
        ex("Incline Dumbbell Press", MuscleGroup.CHEST),
        ex("Bíceps Curl", MuscleGroup.BICEPS),
        ex("Back Squat", MuscleGroup.QUADS),
    )

    @Test
    fun foldRemovesAccentsAndCase() {
        assertEquals("biceps", "Bíceps".foldForSearch())
        assertEquals("squat", "  SQUAT ".foldForSearch())
    }

    @Test
    fun emptyQueryReturnsEverything() {
        assertEquals(catalog.size, filterExercises(catalog, "").size)
    }

    @Test
    fun queryMatchesCaseAndAccentInsensitively() {
        // "biceps" (no accent, lowercase) still matches "Bíceps Curl".
        val results = filterExercises(catalog, "biceps")
        assertEquals(1, results.size)
        assertEquals("Bíceps Curl", results.first().name)
    }

    @Test
    fun querySubstringMatchesInsideName() {
        val results = filterExercises(catalog, "press").map { it.name }
        assertTrue(results.contains("Barbell Bench Press"))
        assertTrue(results.contains("Incline Dumbbell Press"))
        assertEquals(2, results.size)
    }

    @Test
    fun muscleGroupFacetNarrowsResults() {
        val chest = filterExercises(catalog, "", MuscleGroup.CHEST)
        assertEquals(2, chest.size)
        assertTrue(chest.all { it.muscleGroup == MuscleGroup.CHEST })
    }

    @Test
    fun queryAndFacetCombine() {
        val results = filterExercises(catalog, "press", MuscleGroup.CHEST)
        assertEquals(2, results.size)
        assertEquals(0, filterExercises(catalog, "press", MuscleGroup.BICEPS).size)
    }
}
