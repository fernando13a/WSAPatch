package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.WeightUnit
import kotlin.math.abs

/**
 * A weight value read from a photo. [unit] is set only when the text spelled it out
 * ("20 KG", "45 LB"); a bare number leaves it null so the user picks the unit.
 */
data class DetectedWeight(val value: Double, val unit: WeightUnit? = null)

/** How the detected numbers should be turned into a final weight. */
enum class PhotoWeightMode {
    /** Numbers are the plates loaded on ONE side of a barbell: total = bar + 2 × sum. */
    PLATES_PER_SIDE,

    /** The number is the weight itself (a dumbbell, a machine's stack, a single plate). */
    DIRECT,
}

/** Denominations found on real plates, used to snap noisy OCR readings to a plausible value. */
val STANDARD_PLATES_KG = listOf(0.5, 1.0, 1.25, 2.0, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0)
val STANDARD_PLATES_LB = listOf(2.5, 5.0, 10.0, 15.0, 25.0, 35.0, 45.0, 55.0)

private const val MIN_PLAUSIBLE = 0.5
private const val MAX_PLAUSIBLE = 200.0

// A number (optionally decimal, comma or dot) followed by an optional unit word. The digit
// boundaries matter: without them "2021" is read as "202" (dropped as implausible) and then "1",
// which is offered to the user as a 1 kg plate. A serial number has to be rejected whole.
private val TOKEN = Regex(
    """(?<!\d)(\d{1,3}(?:[.,]\d{1,2})?)(?!\d)\s*(kgs?|kilos?|lbs?|libras?|pounds?)?""",
    RegexOption.IGNORE_CASE,
)

/**
 * Extracts candidate weights from raw OCR text. Implausible values (serial numbers, years,
 * fractions of a kilo) are dropped; everything else is surfaced for the user to confirm, since
 * OCR on worn, angled plates is a best-effort read rather than a measurement.
 */
fun parseDetectedWeights(rawText: String): List<DetectedWeight> =
    TOKEN.findAll(rawText)
        .mapNotNull { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            if (value < MIN_PLAUSIBLE || value > MAX_PLAUSIBLE) return@mapNotNull null
            val suffix = match.groupValues[2].lowercase()
            val unit = when {
                suffix.isEmpty() -> null
                suffix.startsWith("lb") || suffix.startsWith("libra") || suffix.startsWith("pound") -> WeightUnit.LB
                else -> WeightUnit.KG
            }
            DetectedWeight(value, unit)
        }
        .toList()

/**
 * Snaps a reading to the nearest real plate denomination when it is close enough, so a "19.8"
 * or "2O" misread still lands on 20. Returns null when nothing is within [tolerance].
 */
fun snapToPlate(value: Double, unit: WeightUnit, tolerance: Double = 0.75): Double? {
    val plates = if (unit == WeightUnit.LB) STANDARD_PLATES_LB else STANDARD_PLATES_KG
    val nearest = plates.minByOrNull { abs(it - value) } ?: return null
    return if (abs(nearest - value) <= tolerance) nearest else null
}

/** Total loaded weight for plates on one side of a barbell. */
fun totalFromPlatesPerSide(platesPerSide: List<Double>, barWeight: Double): Double =
    barWeight + platesPerSide.sum() * 2.0

/**
 * Resolves the confirmed readings into a final weight **in kilograms**, ready to store.
 *
 * @param values the readings the user kept, expressed in [unit]
 * @param unit the unit those readings are printed in
 * @param bar the bar weight, expressed in [unit] (ignored for [PhotoWeightMode.DIRECT])
 */
fun resolveWeightKg(
    values: List<Double>,
    unit: WeightUnit,
    mode: PhotoWeightMode,
    bar: Double,
): Double {
    val totalInUnit = when (mode) {
        PhotoWeightMode.PLATES_PER_SIDE -> totalFromPlatesPerSide(values, bar)
        PhotoWeightMode.DIRECT -> values.sum()
    }
    return totalInUnit.displayUnitToKg(unit)
}
