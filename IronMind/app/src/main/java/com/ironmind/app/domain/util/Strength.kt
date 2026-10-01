package com.ironmind.app.domain.util

/**
 * Estimated one-rep max via the Epley formula: `w · (1 + reps/30)`.
 * Returns the given weight unchanged for a single rep, and 0 for non-positive reps.
 */
fun estimateOneRepMax(weightKg: Double, reps: Int): Double = when {
    reps <= 0 -> 0.0
    reps == 1 -> weightKg
    else -> weightKg * (1.0 + reps / 30.0)
}

/** The plates to load on each side of a barbell for a target total weight. */
data class PlatePlan(
    /** Plates for one side, heaviest first. */
    val perSide: List<Double>,
    /** The total weight actually achievable with the available plates (bar + both sides). */
    val achievable: Double,
    /** Weight that could not be matched with the available plate granularity (>= 0). */
    val leftover: Double,
)

/**
 * Greedily computes the plates per side to reach [targetKg] on a barbell of [barKg], using the
 * given [plates] inventory (kg, per plate). Pure so it is unit-testable.
 */
fun computePlatePlan(
    targetKg: Double,
    barKg: Double = 20.0,
    plates: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
): PlatePlan {
    val perSideTarget = (targetKg - barKg) / 2.0
    if (perSideTarget <= 0.0) {
        return PlatePlan(emptyList(), barKg, (targetKg - barKg).coerceAtLeast(0.0))
    }
    val used = mutableListOf<Double>()
    var remaining = perSideTarget
    for (plate in plates.sortedDescending()) {
        while (remaining >= plate - 1e-9) {
            used.add(plate)
            remaining -= plate
        }
    }
    val achievable = barKg + used.sum() * 2.0
    return PlatePlan(used, achievable, (targetKg - achievable).coerceAtLeast(0.0))
}
