package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.util.StartingWeight
import com.ironmind.app.domain.util.TrainingHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingHistoryTest {

    private val hour = 3_600_000L
    private val now = 1_000_000L * hour

    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val squat = Exercise(id = 2, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)
    private val byId = listOf(bench, squat).associateBy { it.id }

    private fun set(
        exercise: Exercise,
        hoursAgo: Long,
        weight: Double = 60.0,
        reps: Int = 8,
        session: Long = hoursAgo,
        warmup: Boolean = false,
        completed: Boolean = true,
    ) = SetLog(
        sessionId = session, exerciseId = exercise.id, setNumber = 1, weightKg = weight, reps = reps,
        isWarmup = warmup, isCompleted = completed, performedAt = now - hoursAgo * hour,
    )

    private fun history(vararg sets: SetLog) = TrainingHistory.from(sets.toList(), byId, now)

    @Test
    fun aMuscleTrainedInsideTheRecoveryWindowIsRecent() {
        val h = history(set(bench, hoursAgo = 20), set(squat, hoursAgo = 72))

        assertEquals(20L, h.hoursSinceTrained[MuscleGroup.CHEST])
        assertFalse("72 h ago is recovered", MuscleGroup.QUADS in h.hoursSinceTrained)
    }

    @Test
    fun theMostRecentSetDecidesHowLongAgo() {
        val h = history(set(bench, hoursAgo = 40), set(bench, hoursAgo = 6))

        assertEquals(6L, h.hoursSinceTrained[MuscleGroup.CHEST])
    }

    /** A warm-up or an abandoned set didn't tire the muscle and isn't "an exercise they do". */
    @Test
    fun warmUpsAndIncompleteSetsDoNotCount() {
        val h = history(set(bench, hoursAgo = 2, warmup = true), set(squat, hoursAgo = 2, completed = false))

        assertTrue(h.hoursSinceTrained.isEmpty())
        assertEquals(0, h.familiarity(bench.id))
        assertNull(h.lastTopSet[bench.id])
    }

    @Test
    fun familiarityCountsWorkingSets() {
        val h = history(set(bench, 100), set(bench, 200), set(bench, 300), set(squat, 100))

        assertEquals(3, h.familiarity(bench.id))
        assertEquals(1, h.familiarity(squat.id))
        assertEquals(0, h.familiarity(999))
    }

    /** The last *session's* best set — not an all-time best from months ago. */
    @Test
    fun theTopSetComesFromTheMostRecentSession() {
        val h = history(
            set(bench, hoursAgo = 500, weight = 100.0, reps = 5, session = 1), // older, heavier
            set(bench, hoursAgo = 100, weight = 60.0, reps = 8, session = 2),
            set(bench, hoursAgo = 100, weight = 70.0, reps = 6, session = 2), // best of the last session
        )

        val top = h.lastTopSet.getValue(bench.id)
        assertEquals(70.0, top.weightKg, 0.0)
        assertEquals(6, top.reps)
    }

    @Test
    fun startingWeightRoundsDownToAPlate() {
        // 60 kg × 8 → e1RM 76 → for 12 reps 76 / 1.4 = 54.3 → 52.5
        assertEquals(52.5, StartingWeight.suggestKg(60.0, 8, 12)!!, 0.0)
    }

    @Test
    fun moreRepsMeansLessWeightAndNeverMoreThanLastTimeForTheSameReps() {
        val sameReps = StartingWeight.suggestKg(61.0, 8, 8)!!
        val fewer = StartingWeight.suggestKg(61.0, 8, 5)!!
        val more = StartingWeight.suggestKg(61.0, 8, 15)!!

        assertTrue(sameReps <= 61.0)
        assertTrue(fewer > sameReps)
        assertTrue(more < sameReps)
    }

    /** A 2 kg raise rounded to the 2.5 kg plate step came out as "Sugerido: 0 kg". */
    @Test
    fun aLightLoadIsRoundedFinelyAndNeverToZero() {
        // 2 kg × 12 → e1RM 2.8 → for 12 reps 2.0 (was floor(0.8) × 2.5 = 0)
        assertEquals(2.0, StartingWeight.suggestKg(2.0, 12, 12)!!, 0.0)
        // 7.5 kg × 10 → e1RM 10 → for 12 reps 7.14 → 7.0, not 5.0
        assertEquals(7.0, StartingWeight.suggestKg(7.5, 10, 12)!!, 0.0)
        // Too light to suggest anything at that many reps: nothing, not 0.
        assertNull(StartingWeight.suggestKg(0.5, 5, 30))
    }

    @Test
    fun bodyweightWorkGetsNoWeightSuggestion() {
        assertNull(StartingWeight.suggestKg(0.0, 12, 10))
    }

    @Test
    fun aSingleIsItsOwnOneRepMax() {
        assertEquals(100.0, StartingWeight.estimatedOneRepMax(100.0, 1), 0.0)
    }
}
