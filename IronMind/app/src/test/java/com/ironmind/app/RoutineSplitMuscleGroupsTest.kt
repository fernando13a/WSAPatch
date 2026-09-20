package com.ironmind.app

import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.util.muscleGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSplitMuscleGroupsTest {

    @Test
    fun pushNeverIncludesLegMuscleGroups() {
        val groups = RoutineSplit.PUSH.muscleGroups()
        assertTrue(MuscleGroup.QUADS !in groups)
        assertTrue(MuscleGroup.HAMSTRINGS !in groups)
        assertTrue(MuscleGroup.CHEST in groups)
    }

    @Test
    fun legsOnlyIncludesLowerBodyGroups() {
        val groups = RoutineSplit.LEGS.muscleGroups()
        assertEquals(setOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES), groups)
    }

    @Test
    fun everySplitMapsToAtLeastOneMuscleGroup() {
        RoutineSplit.entries.forEach { split ->
            assertTrue("split $split has no muscle groups", split.muscleGroups().isNotEmpty())
        }
    }

    @Test
    fun customCoversTheWholeTaxonomy() {
        assertEquals(MuscleGroup.entries.toSet(), RoutineSplit.CUSTOM.muscleGroups())
    }
}
