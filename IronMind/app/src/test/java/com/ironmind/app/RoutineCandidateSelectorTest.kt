package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.util.RoutineCandidateSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineCandidateSelectorTest {

    private fun exercise(
        id: Long,
        muscleGroup: MuscleGroup,
        equipment: Equipment = Equipment.BARBELL,
        instructions: String? = null,
    ) = Exercise(id = id, name = "Exercise $id", muscleGroup = muscleGroup, equipment = equipment, instructions = instructions)

    @Test
    fun returnsEmptyForEmptyCatalog() {
        val result = RoutineCandidateSelector.select(RoutineSplit.PUSH, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun excludesExercisesOutsideTheSplitsMuscleGroups() {
        val catalog = listOf(
            exercise(1, MuscleGroup.CHEST),
            exercise(2, MuscleGroup.QUADS), // not part of PUSH
        )

        val result = RoutineCandidateSelector.select(RoutineSplit.PUSH, catalog)

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun filtersByAvailableEquipmentWhenProvided() {
        val catalog = listOf(
            exercise(1, MuscleGroup.CHEST, equipment = Equipment.BARBELL),
            exercise(2, MuscleGroup.CHEST, equipment = Equipment.DUMBBELL),
        )

        val result = RoutineCandidateSelector.select(RoutineSplit.PUSH, catalog, availableEquipment = setOf(Equipment.DUMBBELL))

        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun nullEquipmentFilterAllowsAnyEquipment() {
        val catalog = listOf(
            exercise(1, MuscleGroup.CHEST, equipment = Equipment.BARBELL),
            exercise(2, MuscleGroup.CHEST, equipment = Equipment.DUMBBELL),
        )

        val result = RoutineCandidateSelector.select(RoutineSplit.PUSH, catalog, availableEquipment = null)

        assertEquals(2, result.size)
    }

    @Test
    fun prefersCuratedExercisesWithinEachMuscleGroup() {
        val catalog = listOf(
            exercise(1, MuscleGroup.CHEST, instructions = null),
            exercise(2, MuscleGroup.CHEST, instructions = "1) Haz esto."),
        )

        val result = RoutineCandidateSelector.select(RoutineSplit.PUSH, catalog)

        // Curated (has instructions) exercise must come first within its muscle group.
        assertEquals(2L, result.first().id)
    }

    @Test
    fun roundRobinsAcrossMuscleGroupsInsteadOfExhaustingOneFirst() {
        // 5 chest exercises, 1 shoulder exercise, cap way above what's needed.
        val catalog = buildList {
            repeat(5) { i -> add(exercise(id = i.toLong(), muscleGroup = MuscleGroup.CHEST)) }
            add(exercise(id = 100, muscleGroup = MuscleGroup.SHOULDERS))
        }

        val result = RoutineCandidateSelector.select(RoutineSplit.PUSH, catalog)

        // The single shoulder exercise must show up early (round-robin), not be pushed to the end
        // behind all 5 chest exercises.
        val shoulderPosition = result.indexOfFirst { it.id == 100L }
        assertTrue("expected the shoulder exercise near the front, was at index $shoulderPosition", shoulderPosition <= 1)
    }

    @Test
    fun capsAtMaxCandidatesAcrossMultipleMuscleGroups() {
        val catalog = MuscleGroup.entries.flatMap { group ->
            (0 until 10).map { i -> exercise(id = group.ordinal * 100L + i, muscleGroup = group) }
        }

        val result = RoutineCandidateSelector.select(RoutineSplit.CUSTOM, catalog)

        assertEquals(RoutineCandidateSelector.MAX_CANDIDATES, result.size)
    }

    @Test
    fun returnsNoDuplicatesAndOnlyMatchingExercises() {
        val catalog = listOf(exercise(1, MuscleGroup.CHEST))
        val result = RoutineCandidateSelector.select(RoutineSplit.PUSH, catalog)
        assertEquals(1, result.size)
        assertEquals(1L, result.single().id)
    }
}
