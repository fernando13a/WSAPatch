package com.ironmind.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ironmind.app.R

/**
 * "Kinetic Glass Obsidian" type families (Stitch design system):
 *  - Space Grotesk for titles, numeric readouts and labels (digital-instrument feel).
 *  - Manrope for running copy / body.
 *
 * Both are bundled as variable fonts; each weight sets the `wght` axis explicitly so the exact
 * weight renders (no fake-bold synthesis).
 */
private fun spaceGrotesk(weight: FontWeight, axis: Int) =
    Font(
        R.font.space_grotesk,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
    )

private fun manrope(weight: FontWeight, axis: Int) =
    Font(
        R.font.manrope,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
    )

val SpaceGrotesk = FontFamily(
    spaceGrotesk(FontWeight.Normal, 400),
    spaceGrotesk(FontWeight.Medium, 500),
    spaceGrotesk(FontWeight.SemiBold, 600),
    spaceGrotesk(FontWeight.Bold, 700),
)

val Manrope = FontFamily(
    manrope(FontWeight.Normal, 400),
    manrope(FontWeight.Medium, 500),
    manrope(FontWeight.SemiBold, 600),
    manrope(FontWeight.Bold, 700),
)
