package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Limitation
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.util.RoutineSwap
import com.ironmind.app.domain.util.TrainingHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSwapTest {

    private fun ex(id: Long, name: String, group: MuscleGroup = MuscleGroup.QUADS, equipment: Equipment = Equipment.BARBELL) =
        Exercise(id = id, name = name, muscleGroup = group, equipment = equipment)

    private val squat = ex(1, "Back Squat")
    private val frontSquat = ex(2, "Front Squat")
    private val legPress = ex(3, "Leg Press", equipment = Equipment.MACHINE)
    private val legExtension = ex(4, "Leg Extension", equipment = Equipment.MACHINE)
    private val gobletSquat = ex(5, "Goblet Squat", equipment = Equipment.DUMBBELL)
    private val curl = ex(6, "Barbell Curl", group = MuscleGroup.BICEPS)
    private val catalog = listOf(squat, frontSquat, legPress, legExtension, gobletSquat, curl)
    private val allEquipment = Equipment.entries.toSet()

    private fun options(
        inDraft: Set<Long> = setOf(squat.id),
        equipment: Set<Equipment> = allEquipment,
        avoid: Set<Limitation> = emptySet(),
        history: TrainingHistory = TrainingHistory.EMPTY,
    ) = RoutineSwap.options(squat, catalog, inDraft, equipment, avoid, history).map { it.id }

    @Test
    fun onlyTheSameMuscleAndNeverItselfOrTheDraft() {
        val ids = options(inDraft = setOf(squat.id, frontSquat.id))

        assertTrue(squat.id !in ids)
        assertTrue("already in the draft", frontSquat.id !in ids)
        assertTrue("other muscle", curl.id !in ids)
    }

    /** Swapping a squat for a squat keeps the session's shape; for a leg extension it doesn't. */
    @Test
    fun theSameKindOfMovementComesFirst() {
        val ids = options()

        assertEquals(legExtension.id, ids.last())
    }

    @Test
    fun keepsToTheEquipmentTheAthleteHas() {
        assertEquals(listOf(frontSquat.id), options(equipment = setOf(Equipment.BARBELL)))
    }

    @Test
    fun respectsTheJointsToSpare() {
        // Every quad option loads the knee.
        assertTrue(options(avoid = setOf(Limitation.KNEE)).isEmpty())
    }

    @Test
    fun exercisesTheAthleteDoesRankAheadWithinTheSameKind() {
        val history = TrainingHistory(setsByExercise = mapOf(gobletSquat.id to 20))

        assertEquals(gobletSquat.id, options(history = history).first())
    }

    @Test
    fun offersAtMostAHandful() {
        val many = (100L..130L).map { ex(it, "Squat Variation $it") }

        val result = RoutineSwap.options(squat, many + squat, setOf(squat.id), allEquipment)

        assertEquals(RoutineSwap.MAX_OPTIONS, result.size)
    }
}
