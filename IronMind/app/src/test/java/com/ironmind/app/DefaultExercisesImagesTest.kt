package com.ironmind.app

import com.ironmind.app.data.local.seed.DefaultExercises
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the curated-exercise → demo-image mapping. The curated Spanish entries are the ones in
 * the starter routines, so a broken mapping here means the first exercises anybody opens are the
 * only ones with no reference image.
 */
class DefaultExercisesImagesTest {

    private val index = mapOf(
        "barbell squat" to "https://example.test/barbell-squat.jpg",
        "plank" to "https://example.test/plank.jpg",
    )

    @Test
    fun resolvesByExactCatalogName() {
        assertEquals("https://example.test/plank.jpg", DefaultExercises.demoImageFor("Plank", index))
    }

    @Test
    fun resolvesThroughTheAliasTable() {
        // "Back Squat" is the curated name; free-exercise-db calls it "Barbell Squat".
        assertEquals("https://example.test/barbell-squat.jpg", DefaultExercises.demoImageFor("Back Squat", index))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals("https://example.test/plank.jpg", DefaultExercises.demoImageFor("PLANK", index))
    }

    @Test
    fun returnsNullWhenTheCatalogHasNoImageForIt() {
        assertNull(DefaultExercises.demoImageFor("Some Made Up Lift", index))
        // Aliased, but the target isn't in this index.
        assertNull(DefaultExercises.demoImageFor("Hip Thrust", index))
    }

    @Test
    fun everyAliasPointsAtARealCuratedExercise() {
        val curated = DefaultExercises.curatedNames
        val orphans = DefaultExercises.imageAliases.keys.filterNot { it in curated }
        assertTrue("Aliases with no matching curated exercise: $orphans", orphans.isEmpty())
    }

    @Test
    fun aliasesNeverPointAtThemselves() {
        val selfReferencing = DefaultExercises.imageAliases.filter { (curated, target) -> curated == target }
        assertTrue("Pointless self-aliases: ${selfReferencing.keys}", selfReferencing.isEmpty())
    }
}
