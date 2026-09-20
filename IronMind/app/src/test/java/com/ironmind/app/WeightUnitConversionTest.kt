package com.ironmind.app

import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.domain.util.displayUnitToKg
import com.ironmind.app.domain.util.toDisplayUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for weight unit conversion edge cases.
 * Verifies kg/lb conversions are accurate across boundary conditions.
 */
class WeightUnitConversionTest {

    private val EPSILON = 0.01

    @Test
    fun convertsZeroKgToZeroLb() {
        val result = 0.0.toDisplayUnit(WeightUnit.LB)
        assertEquals(0.0, result, EPSILON)
    }

    @Test
    fun convertsZeroLbToZeroKg() {
        val result = 0.0.displayUnitToKg(WeightUnit.LB)
        assertEquals(0.0, result, EPSILON)
    }

    @Test
    fun converts20KgTo44Lb() {
        val result = 20.0.toDisplayUnit(WeightUnit.LB)
        assertEquals(44.09, result, EPSILON)
    }

    @Test
    fun converts45LbTo20Kg() {
        val result = 45.0.displayUnitToKg(WeightUnit.LB)
        assertEquals(20.41, result, EPSILON)
    }

    @Test
    fun convertsLargeWeightKgToLb() {
        val result = 150.0.toDisplayUnit(WeightUnit.LB)
        assertEquals(330.69, result, EPSILON)
    }

    @Test
    fun convertsLargeWeightLbToKg() {
        val result = 300.0.displayUnitToKg(WeightUnit.LB)
        assertEquals(136.08, result, EPSILON)
    }

    @Test
    fun convertsSmallDecimalWeightKgToLb() {
        val result = 1.25.toDisplayUnit(WeightUnit.LB)
        assertEquals(2.76, result, EPSILON)
    }

    @Test
    fun convertsSmallDecimalWeightLbToKg() {
        val result = 2.5.displayUnitToKg(WeightUnit.LB)
        assertEquals(1.13, result, EPSILON)
    }

    @Test
    fun kgToKgReturnsUnchanged() {
        val result = 50.0.toDisplayUnit(WeightUnit.KG)
        assertEquals(50.0, result, EPSILON)
    }

    @Test
    fun lbToLbReturnsUnchanged() {
        val result = 100.0.displayUnitToKg(WeightUnit.KG)
        assertEquals(100.0, result, EPSILON)
    }
}
