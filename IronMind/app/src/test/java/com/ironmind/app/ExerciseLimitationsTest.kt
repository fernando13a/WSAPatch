package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Limitation
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.util.stresses
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLimitationsTest {

    private fun ex(name: String, group: MuscleGroup = MuscleGroup.OTHER, nameEs: String? = null) =
        Exercise(name = name, nameEs = nameEs, muscleGroup = group, equipment = Equipment.BARBELL)

    /** A bad knee rules out loaded knee flexion — not every leg exercise. */
    @Test
    fun kneeExcludesSquatsAndLungesButNotHamstringCurls() {
        listOf("Back Squat", "Walking Lunges", "Leg Extension", "Leg Press", "Box Jump").forEach {
            assertTrue(it, ex(it).stresses(Limitation.KNEE))
        }
        assertTrue(ex("Custom", nameEs = "Sentadilla búlgara").stresses(Limitation.KNEE))
        assertFalse(ex("Lying Leg Curl", MuscleGroup.HAMSTRINGS).stresses(Limitation.KNEE))
        assertFalse(ex("Hip Thrust", MuscleGroup.GLUTES).stresses(Limitation.KNEE))
    }

    @Test
    fun shoulderExcludesOverheadWorkButNotRows() {
        listOf("Seated Overhead Press", "Military Press", "Dips", "Upright Row", "Arnold Press").forEach {
            assertTrue(it, ex(it).stresses(Limitation.SHOULDER))
        }
        assertTrue(ex("Custom", nameEs = "Press militar").stresses(Limitation.SHOULDER))
        assertFalse(ex("Seated Cable Row", MuscleGroup.BACK).stresses(Limitation.SHOULDER))
        assertFalse(ex("Barbell Curl", MuscleGroup.BICEPS).stresses(Limitation.SHOULDER))
    }

    @Test
    fun lowerBackExcludesHingesAndBentOverRowsButNotPulldowns() {
        listOf("Deadlift", "Romanian Deadlift", "Good Morning", "Bent Over Barbell Row", "Power Clean").forEach {
            assertTrue(it, ex(it).stresses(Limitation.LOWER_BACK))
        }
        assertTrue(ex("Custom", nameEs = "Peso muerto rumano").stresses(Limitation.LOWER_BACK))
        assertFalse(ex("Lat Pulldown", MuscleGroup.BACK).stresses(Limitation.LOWER_BACK))
        assertFalse(ex("Leg Extension", MuscleGroup.QUADS).stresses(Limitation.LOWER_BACK))
    }
}
