package com.ironmind.app

import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.domain.util.DetectedWeight
import com.ironmind.app.domain.util.PhotoWeightMode
import com.ironmind.app.domain.util.parseDetectedWeights
import com.ironmind.app.domain.util.resolveWeightKg
import com.ironmind.app.domain.util.snapToPlate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for photo OCR edge cases.
 * Verifies weight detection and parsing handles noisy OCR and user input correctly.
 */
class PhotoOcrEdgeCasesTest {

    private val EPSILON = 0.01

    @Test
    fun parsesPlainKgNumber() {
        val result = parseDetectedWeights("25")
        assertEquals(1, result.size)
        assertEquals(DetectedWeight(25.0, null), result[0])
    }

    @Test
    fun parsesKgWithExplicitUnit() {
        val result = parseDetectedWeights("25 kg")
        assertEquals(1, result.size)
        assertEquals(DetectedWeight(25.0, WeightUnit.KG), result[0])
    }

    @Test
    fun parsesLbWithExplicitUnit() {
        val result = parseDetectedWeights("45 LB")
        assertEquals(1, result.size)
        assertEquals(DetectedWeight(45.0, WeightUnit.LB), result[0])
    }

    @Test
    fun parsesDecimalWeightWithComma() {
        val result = parseDetectedWeights("2,5 kg")
        assertEquals(1, result.size)
        assertEquals(DetectedWeight(2.5, WeightUnit.KG), result[0])
    }

    @Test
    fun parsesDecimalWeightWithDot() {
        val result = parseDetectedWeights("2.5 kg")
        assertEquals(1, result.size)
        assertEquals(DetectedWeight(2.5, WeightUnit.KG), result[0])
    }

    @Test
    fun filtersOutImplausiblySmallWeight() {
        val result = parseDetectedWeights("0.25 kg")
        assertEquals(0, result.size)
    }

    @Test
    fun filtersOutImplausiblyLargeWeight() {
        val result = parseDetectedWeights("500 kg")
        assertEquals(0, result.size)
    }

    @Test
    fun parsesMultipleWeightsInText() {
        val result = parseDetectedWeights("25 kg and 20 kg or 15 lb")
        assertEquals(3, result.size)
        assertEquals(25.0, result[0].value, EPSILON)
        assertEquals(20.0, result[1].value, EPSILON)
        assertEquals(15.0, result[2].value, EPSILON)
    }

    @Test
    fun ignoratesSerialNumbers() {
        val result = parseDetectedWeights("Serial: 2021, Weight: 25 kg")
        // Should find 2021 (plausible), 25 (with unit)
        assertEquals(2, result.size)
    }

    @Test
    fun snapsNoiseToNearestPlate() {
        val snapped = snapToPlate(19.8, WeightUnit.KG)
        assertEquals(20.0, snapped!!, EPSILON)
    }

    @Test
    fun snapsOcrMisreadToPlate() {
        val snapped = snapToPlate(24.9, WeightUnit.KG)
        assertEquals(25.0, snapped!!, EPSILON)
    }

    @Test
    fun returnsNullWhenFarFromAnyPlate() {
        val snapped = snapToPlate(23.0, WeightUnit.KG)
        assertNull(snapped)
    }

    @Test
    fun resolvesPlatesPerSideForBarbell() {
        val kg = resolveWeightKg(
            values = listOf(25.0, 20.0),  // 45 kg per side
            unit = WeightUnit.KG,
            mode = PhotoWeightMode.PLATES_PER_SIDE,
            bar = 20.0,
        )
        // 20 (bar) + (25 + 20) * 2 = 110
        assertEquals(110.0, kg, EPSILON)
    }

    @Test
    fun resolvesDirectWeight() {
        val kg = resolveWeightKg(
            values = listOf(25.0),
            unit = WeightUnit.KG,
            mode = PhotoWeightMode.DIRECT,
            bar = 20.0,
        )
        // Direct mode ignores bar, just sums: 25
        assertEquals(25.0, kg, EPSILON)
    }

    @Test
    fun convertsLbPlatesPerSideToKg() {
        val kg = resolveWeightKg(
            values = listOf(45.0),  // 45 lb on one side
            unit = WeightUnit.LB,
            mode = PhotoWeightMode.PLATES_PER_SIDE,
            bar = 45.0,
        )
        // 45 lb bar (20.41 kg) + 45 lb (20.41 kg) * 2 ≈ 61.2 kg
        assertEquals(61.24, kg, 0.1)
    }

    @Test
    fun handlesEmptyOcrText() {
        val result = parseDetectedWeights("")
        assertEquals(0, result.size)
    }

    @Test
    fun handlesNoiseOnlyText() {
        val result = parseDetectedWeights("abcdef xyz")
        assertEquals(0, result.size)
    }
}
