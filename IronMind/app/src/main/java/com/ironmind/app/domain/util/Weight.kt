package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.WeightUnit

/** Pounds per kilogram (exact conversion factor). */
const val LB_PER_KG = 2.2046226218

/** Converts a canonical kilogram value into the user's display [unit]. */
fun Double.toDisplayUnit(unit: WeightUnit): Double =
    if (unit == WeightUnit.LB) this * LB_PER_KG else this

/** Converts a value the user entered in the display [unit] back into canonical kilograms. */
fun Double.displayUnitToKg(unit: WeightUnit): Double =
    if (unit == WeightUnit.LB) this / LB_PER_KG else this
