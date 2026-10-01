package com.ironmind.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ironmind.app.R

/**
 * "Kinetic Glass Obsidian" type families (Stitch design system):
 *  - Space Grotesk for titles, numeric readouts and labels (digital-instrument feel).
 *  - Manrope for running copy / body.
 *
 * Both are bundled as variable fonts. Declaring the same file at several weights lets the
 * platform pick the matching `wght` instance on API 26+ (minSdk here is 26).
 */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold),
)
