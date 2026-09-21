package com.ironmind.app

import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.domain.util.PhotoWeightMode
import com.ironmind.app.domain.util.parseDetectedWeights
import com.ironmind.app.domain.util.resolveWeightKg
import com.ironmind.app.domain.util.snapToPlate
import com.ironmind.app.domain.util.totalFromPlatesPerSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightOcrTest {

    @Test
    fun readsBareNumbersWithoutUnit() {
        val detected = parseDetectedWeights("20\n10\n2.5")
        assertEquals(listOf(20.0, 10.0, 2.5), detected.map { it.value })
        assertTrue(detected.all { it.unit == null })
    }

    @Test
    fun readsExplicitUnits() {
        val detected = parseDetectedWeights("45 LB   20KG")
        assertEquals(WeightUnit.LB, detected[0].unit)
        assertEquals(45.0, detected[0].value, 0.001)
        assertEquals(WeightUnit.KG, detected[1].unit)
        assertEquals(20.0, detected[1].value, 0.001)
    }

    @Test
    fun acceptsCommaDecimals() {
        assertEquals(2.5, parseDetectedWeights("2,5 kg").single().value, 0.001)
    }

    @Test
    fun dropsImplausibleValues() {
        // A year and a serial number are not plate weights.
        val detected = parseDetectedWeights("2024 SN 999 0.1")
        assertTrue(detected.none { it.value > 200.0 })
        assertTrue(detected.none { it.value < 0.5 })
    }

    @Test
    fun snapsNoisyReadingToNearestPlate() {
        assertEquals(20.0, snapToPlate(19.8, WeightUnit.KG)!!, 0.001)
        assertEquals(45.0, snapToPlate(44.6, WeightUnit.LB)!!, 0.001)
    }

    @Test
    fun doesNotSnapWhenNothingIsClose() {
        assertNull(snapToPlate(33.0, WeightUnit.KG))
    }

    @Test
    fun totalsPlatesOnBothSidesPlusBar() {
        // 20 + 10 per side, 20 kg bar -> 20 + 2*(30) = 80
        assertEquals(80.0, totalFromPlatesPerSide(listOf(20.0, 10.0), 20.0), 0.001)
    }

    @Test
    fun resolvesPlatesInPoundsBackToKilograms() {
        // 45 lb bar + 2 x 45 lb = 135 lb = 61.23 kg
        val kg = resolveWeightKg(
            values = listOf(45.0),
            unit = WeightUnit.LB,
            mode = PhotoWeightMode.PLATES_PER_SIDE,
            bar = 45.0,
        )
        assertEquals(61.235, kg, 0.01)
    }

    @Test
    fun resolvesDirectValueIgnoringTheBar() {
        val kg = resolveWeightKg(
            values = listOf(32.5),
            unit = WeightUnit.KG,
            mode = PhotoWeightMode.DIRECT,
            bar = 20.0,
        )
        assertEquals(32.5, kg, 0.001)
    }
}
