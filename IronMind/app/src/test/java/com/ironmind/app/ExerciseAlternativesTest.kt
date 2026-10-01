package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.util.filterAlternatives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseAlternativesTest {

    private fun exercise(id: Long, equipment: Equipment, muscleGroup: MuscleGroup = MuscleGroup.CHEST) =
        Exercise(id = id, name = "Exercise $id", muscleGroup = muscleGroup, equipment = equipment)

    @Test
    fun excludesTargetItself() {
        val target = exercise(1, Equipment.BARBELL)
        val candidates = listOf(target, exercise(2, Equipment.DUMBBELL))

        val result = filterAlternatives(target, candidates)

        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun excludesSameEquipment() {
        val target = exercise(1, Equipment.BARBELL)
        val candidates = listOf(exercise(2, Equipment.BARBELL), exercise(3, Equipment.DUMBBELL))

        val result = filterAlternatives(target, candidates)

        assertEquals(listOf(3L), result.map { it.id })
    }

    @Test
    fun returnsEmptyWhenNoOtherEquipmentAvailable() {
        val target = exercise(1, Equipment.BARBELL)
        val candidates = listOf(target, exercise(2, Equipment.BARBELL))

        val result = filterAlternatives(target, candidates)

        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmptyForEmptyCandidateList() {
        val target = exercise(1, Equipment.BARBELL)

        val result = filterAlternatives(target, emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun keepsMultipleDistinctEquipmentAlternatives() {
        val target = exercise(1, Equipment.BARBELL)
        val candidates = listOf(
            exercise(2, Equipment.DUMBBELL),
            exercise(3, Equipment.MACHINE),
            exercise(4, Equipment.CABLE),
        )

        val result = filterAlternatives(target, candidates)

        assertEquals(setOf(2L, 3L, 4L), result.map { it.id }.toSet())
    }
}
