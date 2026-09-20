package com.ironmind.app

import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.domain.util.displayUnitToKg
import com.ironmind.app.domain.util.toDisplayUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightTest {

    @Test
    fun kgUnitIsIdentity() {
        assertEquals(100.0, 100.0.toDisplayUnit(WeightUnit.KG), 0.0001)
        assertEquals(100.0, 100.0.displayUnitToKg(WeightUnit.KG), 0.0001)
    }

    @Test
    fun kgConvertsToPounds() {
        assertEquals(220.462, 100.0.toDisplayUnit(WeightUnit.LB), 0.001)
    }

    @Test
    fun poundsConvertBackToKg() {
        assertEquals(100.0, 220.462.displayUnitToKg(WeightUnit.LB), 0.001)
    }

    @Test
    fun roundTripPreservesValue() {
        val kg = 142.5
        val backToKg = kg.toDisplayUnit(WeightUnit.LB).displayUnitToKg(WeightUnit.LB)
        assertEquals(kg, backToKg, 0.0001)
    }
}
