package com.ironmind.app

import com.ironmind.app.domain.util.computePlatePlan
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for plate calculator edge cases.
 * Verifies the snapping algorithm handles boundary conditions and common lifts.
 */
class PlateCalculatorTest {

    private val EPSILON = 0.01

    @Test
    fun calculatesPlatesFor100KgWithStandardBar() {
        val plan = computePlatePlan(targetKg = 100.0, barKg = 20.0)

        // 100kg = 20kg (bar) + 2 × 40kg per side
        // 40kg = 25 + 15
        assertEquals(2, plan.perSide.size)
        assertEquals(listOf(25.0, 15.0), plan.perSide)
        assertEquals(100.0, plan.achievable, EPSILON)
        assertEquals(0.0, plan.leftover, EPSILON)
    }

    @Test
    fun calculatesPlatesFor60KgWithStandardBar() {
        val plan = computePlatePlan(targetKg = 60.0, barKg = 20.0)

        // 60kg = 20kg (bar) + 2 × 20kg per side
        assertEquals(1, plan.perSide.size)
        assertEquals(listOf(20.0), plan.perSide)
        assertEquals(60.0, plan.achievable, EPSILON)
        assertEquals(0.0, plan.leftover, EPSILON)
    }

    @Test
    fun handlesTargetBelowBarWeight() {
        val plan = computePlatePlan(targetKg = 10.0, barKg = 20.0)

        // Cannot load below bar weight
        assertEquals(0, plan.perSide.size)
        assertEquals(20.0, plan.achievable, EPSILON)
        assertEquals(0.0, plan.leftover, EPSILON)
    }

    @Test
    fun snapsToAvailablePlatesWhenExactMatchImpossible() {
        val plan = computePlatePlan(targetKg = 95.5, barKg = 20.0)

        // 95.5kg cannot be achieved exactly, should snap down
        assertEquals(95.0, plan.achievable, EPSILON)
        assertEquals(0.5, plan.leftover, EPSILON)
    }

    @Test
    fun calculatesPlatesForHighWeight() {
        val plan = computePlatePlan(targetKg = 200.0, barKg = 20.0)

        // 200kg = 20kg (bar) + 2 × 90kg per side
        // 90kg = 25 + 25 + 20 + 20
        assertEquals(4, plan.perSide.size)
        assertEquals(200.0, plan.achievable, EPSILON)
        assertEquals(0.0, plan.leftover, EPSILON)
    }

    @Test
    fun calculatesPatesForSmallSmallWeight() {
        val plan = computePlatePlan(targetKg = 22.5, barKg = 20.0)

        // 22.5kg = 20kg (bar) + 2 × 1.25kg per side
        assertEquals(1, plan.perSide.size)
        assertEquals(listOf(1.25), plan.perSide)
        assertEquals(22.5, plan.achievable, EPSILON)
        assertEquals(0.0, plan.leftover, EPSILON)
    }

    @Test
    fun handlesBarWeight45LbProperly() {
        val plan = computePlatePlan(targetKg = 100.0, barKg = 20.411)

        // Using ~45 lb bar (20.411 kg) with standard plates
        // Should still calculate correctly
        assertEquals(100.0, plan.achievable, EPSILON)
    }
}
