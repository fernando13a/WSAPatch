package com.ironmind.app

import com.ironmind.app.domain.model.ExercisePrescription
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.util.StartingWeight
import com.ironmind.app.ui.session.SetPrefill
import org.junit.Assert.assertEquals
import org.junit.Test

class SetPrefillTest {

    private fun last(weightKg: Double, reps: Int) =
        SetLog(sessionId = 1, exerciseId = 1, setNumber = 1, weightKg = weightKg, reps = reps)

    @Test
    fun theRoutinesTargetComesFirst() {
        val target = ExercisePrescription(sets = 4, reps = 6, restSeconds = 150, weightKg = 80.0)

        assertEquals(SetPrefill(80.0, 6), SetPrefill.from(target, last(100.0, 3)))
    }

    /**
     * A hand-built routine stores 3 × 10 and no weight. Last time's 100 kg × 5 is not a load for
     * 10 reps — it used to fill in exactly that.
     */
    @Test
    fun lastTimesWeightIsConvertedToTheRoutinesReps() {
        val target = ExercisePrescription(sets = 3, reps = 10, restSeconds = 90)

        assertEquals(SetPrefill(StartingWeight.suggestKg(100.0, 5, 10), 10), SetPrefill.from(target, last(100.0, 5)))
    }

    @Test
    fun withTheSameRepsLastTimesWeightIsKeptAsItWas() {
        val target = ExercisePrescription(sets = 3, reps = 8, restSeconds = 90)

        assertEquals(SetPrefill(61.0, 8), SetPrefill.from(target, last(61.0, 8)))
    }

    @Test
    fun withoutARoutineItIsLastTimeAsItWas() {
        assertEquals(SetPrefill(60.0, 8), SetPrefill.from(null, last(60.0, 8)))
    }

    /** A build rounded light loads down to a saved target of 0 kg; that is no target. */
    @Test
    fun aZeroTargetWeightIsNoTarget() {
        val target = ExercisePrescription(sets = 3, reps = 12, restSeconds = 60, weightKg = 0.0)

        assertEquals(SetPrefill(4.0, 12), SetPrefill.from(target, last(4.0, 12)))
    }

    @Test
    fun bodyweightWorkGetsRepsButNoWeight() {
        assertEquals(SetPrefill(null, 15), SetPrefill.from(null, last(0.0, 15)))
    }

    @Test
    fun nothingToGoOnLeavesBothBlank() {
        assertEquals(SetPrefill(null, null), SetPrefill.from(null, null))
    }
}
