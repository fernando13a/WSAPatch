package com.ironmind.app

import com.ironmind.app.domain.util.computePlatePlan
import com.ironmind.app.domain.util.estimateOneRepMax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthTest {

    @Test
    fun oneRepMaxReturnsWeightForSingle() {
        assertEquals(100.0, estimateOneRepMax(100.0, 1), 0.001)
    }

    @Test
    fun oneRepMaxEpleyForMultipleReps() {
        // 100 * (1 + 5/30) = 116.666…
        assertEquals(116.666, estimateOneRepMax(100.0, 5), 0.01)
    }

    @Test
    fun oneRepMaxZeroForNonPositiveReps() {
        assertEquals(0.0, estimateOneRepMax(100.0, 0), 0.001)
    }

    @Test
    fun platePlanBarOnlyWhenTargetAtOrBelowBar() {
        val plan = computePlatePlan(targetKg = 20.0, barKg = 20.0)
        assertTrue(plan.perSide.isEmpty())
        assertEquals(20.0, plan.achievable, 0.001)
    }

    @Test
    fun platePlanGreedyPerSide() {
        // 100 kg on a 20 kg bar -> 40 kg per side -> 25 + 15.
        val plan = computePlatePlan(targetKg = 100.0)
        assertEquals(listOf(25.0, 15.0), plan.perSide)
        assertEquals(100.0, plan.achievable, 0.001)
        assertEquals(0.0, plan.leftover, 0.001)
    }

    @Test
    fun platePlanReportsLeftoverForUnreachableTarget() {
        // 61 kg -> 20.5 per side -> 20 fits, 0.5 leftover (no plate smaller than 1.25).
        val plan = computePlatePlan(targetKg = 61.0)
        assertEquals(listOf(20.0), plan.perSide)
        assertEquals(60.0, plan.achievable, 0.001)
        assertTrue(plan.leftover > 0.0)
    }
}
