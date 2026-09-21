package com.ironmind.app

import com.ironmind.app.data.local.seed.DefaultExercises
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the bundled reference images. These are the exercises in the starter routines, so a
 * broken mapping here means the first exercises anybody opens are the only ones with no image.
 */
class DefaultExercisesImagesTest {

    @Test
    fun assetPathsMatchTheShippedFileNames() {
        assertEquals(
            "file:///android_asset/exercise_images/bent_over_barbell_row.jpg",
            DefaultExercises.bundledImageFor("Bent-Over Barbell Row"),
        )
        assertEquals(
            "file:///android_asset/exercise_images/pull_up.jpg",
            DefaultExercises.bundledImageFor("Pull-Up"),
        )
        assertEquals(
            "file:///android_asset/exercise_images/back_squat.jpg",
            DefaultExercises.bundledImageFor("Back Squat"),
        )
    }

    @Test
    fun exercisesOutsideTheBundleGetNoAssetPath() {
        // Part of the 876-exercise catalog, but its image is fetched on demand, not bundled.
        assertNull(DefaultExercises.bundledImageFor("3/4 Sit-Up"))
        assertNull(DefaultExercises.bundledImageFor("Some Made Up Lift"))
    }

    @Test
    fun everyCuratedExerciseShipsWithAnImage() {
        val missing = DefaultExercises.curatedNames - DefaultExercises.bundledImageNames
        assertTrue(
            "Curated exercises with no bundled image (add the JPEG to assets/exercise_images " +
                "and list it in bundledImageNames): $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun everyBundledNameIsACuratedExercise() {
        val orphans = DefaultExercises.bundledImageNames - DefaultExercises.curatedNames
        assertTrue("Bundled images with no matching curated exercise: $orphans", orphans.isEmpty())
    }
}
