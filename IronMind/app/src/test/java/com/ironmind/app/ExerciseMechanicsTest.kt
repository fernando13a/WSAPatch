package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.util.isCompound
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseMechanicsTest {

    private fun ex(name: String, group: MuscleGroup, nameEs: String? = null) =
        Exercise(name = name, nameEs = nameEs, muscleGroup = group, equipment = Equipment.BARBELL)

    @Test
    fun theBigLiftsAreCompound() {
        listOf(
            ex("Barbell Bench Press", MuscleGroup.CHEST),
            ex("Back Squat", MuscleGroup.QUADS),
            ex("Romanian Deadlift", MuscleGroup.HAMSTRINGS),
            ex("Bent Over Barbell Row", MuscleGroup.BACK),
            ex("Pull-Up", MuscleGroup.BACK),
            ex("Lat Pulldown", MuscleGroup.BACK),
            ex("Dips", MuscleGroup.TRICEPS),
            ex("Hip Thrust", MuscleGroup.GLUTES),
            ex("Seated Overhead Press", MuscleGroup.SHOULDERS),
        ).forEach { assertTrue("${it.name} should be compound", it.isCompound()) }
    }

    @Test
    fun singleJointMovementsAreIsolation() {
        listOf(
            ex("Barbell Curl", MuscleGroup.BICEPS),
            ex("Cable Fly", MuscleGroup.CHEST),
            ex("Lateral Raise", MuscleGroup.SHOULDERS),
            ex("Standing Calf Raises", MuscleGroup.CALVES),
            ex("Crunches", MuscleGroup.ABS),
            ex("Pec Deck", MuscleGroup.CHEST),
        ).forEach { assertFalse("${it.name} should be isolation", it.isCompound()) }
    }

    /** These contain a compound-sounding word; the more specific isolation word has to win. */
    @Test
    fun isolationKeywordsOutrankCompoundOnes() {
        assertFalse(ex("Triceps Pushdown", MuscleGroup.TRICEPS).isCompound())
        assertFalse(ex("Triceps Pressdown", MuscleGroup.TRICEPS).isCompound())
        assertFalse(ex("Leg Extension", MuscleGroup.QUADS).isCompound())
        assertFalse(ex("Lying Leg Curl", MuscleGroup.HAMSTRINGS).isCompound())
    }

    /** Custom exercises are typed in Spanish, often with accents. */
    @Test
    fun readsSpanishNamesIgnoringAccents() {
        assertTrue(ex("Custom", MuscleGroup.OTHER, nameEs = "Sentadilla búlgara").isCompound())
        assertTrue(ex("Custom", MuscleGroup.OTHER, nameEs = "Jalón al pecho").isCompound())
        assertFalse(ex("Custom", MuscleGroup.OTHER, nameEs = "Elevaciones laterales").isCompound())
        assertFalse(ex("Custom", MuscleGroup.OTHER, nameEs = "Extensión de tríceps").isCompound())
    }

    /** "row" must match at a word start: "Rows" counts, "Throw" does not. */
    @Test
    fun keywordsMatchAtWordStartOnly() {
        assertTrue(ex("Seated Cable Rows", MuscleGroup.OTHER).isCompound())
        assertFalse(ex("Medicine Ball Throw", MuscleGroup.OTHER).isCompound())
    }

    @Test
    fun withNoKeywordTheMuscleGroupDecides() {
        assertTrue(ex("Landmine 180", MuscleGroup.BACK).isCompound())
        assertFalse(ex("Face Pull", MuscleGroup.SHOULDERS).isCompound())
    }
}
