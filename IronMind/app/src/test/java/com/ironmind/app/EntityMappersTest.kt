package com.ironmind.app

import com.ironmind.app.data.mapper.toDomain
import com.ironmind.app.data.mapper.toEntity
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM unit tests for the entity ↔ domain mappers. */
class EntityMappersTest {

    @Test
    fun exercise_roundTripsThroughEntity() {
        val original = Exercise(
            id = 7,
            name = "Front Squat",
            muscleGroup = MuscleGroup.QUADS,
            equipment = Equipment.BARBELL,
            description = "Elbows high",
            isCustom = false,
        )
        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun setLog_roundTripsAndComputesVolume() {
        val original = SetLog(
            id = 1,
            sessionId = 2,
            exerciseId = 3,
            setNumber = 1,
            weightKg = 80.0,
            reps = 5,
            rpe = 8.5f,
            notes = "Energy 9/10",
        )
        val roundTripped = original.toEntity().toDomain()
        assertEquals(original, roundTripped)
        assertEquals(400.0, roundTripped.volume, 0.0001)
    }
}
