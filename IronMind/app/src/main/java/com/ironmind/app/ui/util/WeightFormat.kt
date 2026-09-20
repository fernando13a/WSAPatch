package com.ironmind.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ironmind.app.R
import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.domain.util.toDisplayUnit
import kotlin.math.roundToInt

/** Localized unit suffix ("kg" / "lb"). */
@Composable
fun WeightUnit.suffix(): String =
    stringResource(if (this == WeightUnit.LB) R.string.unit_lb else R.string.unit_kg)

/** Formats a canonical kg value for display, e.g. "100 kg" or "221 lb". */
@Composable
fun weightLabel(kg: Double, unit: WeightUnit): String =
    "${kg.toDisplayUnit(unit).roundToInt()} ${unit.suffix()}"

@Composable
fun weightLabel(kg: Float, unit: WeightUnit): String = weightLabel(kg.toDouble(), unit)
