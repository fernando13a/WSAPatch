package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Limitation
import java.text.Normalizer

/**
 * Whether [limitation]'s joint takes a heavy or awkward load in this exercise, read off its English
 * and Spanish names.
 *
 * Conservative on purpose — it errs towards leaving a movement out — and not medical advice: it
 * keeps the obvious offenders (deep knee flexion under load, overhead pressing, loaded spinal
 * flexion) out of a generated draft, which the athlete still reviews before saving. The muscle
 * group isn't enough on its own: a knee problem rules out squats and lunges, not leg curls.
 */
fun Exercise.stresses(limitation: Limitation): Boolean {
    val text = foldForLimitations(listOfNotNull(name, nameEs).joinToString(" "))
    return PATTERNS.getValue(limitation).containsMatchIn(text)
}

private fun wordStarts(vararg words: String) = Regex("""\b(?:${words.joinToString("|")})""")

private val PATTERNS = mapOf(
    Limitation.KNEE to wordStarts(
        "squat", "lunge", "leg extension", "leg press", "step-up", "step up", "jump", "pistol",
        "hack", "sissy", "box jump", "split squat",
        "sentadilla", "zancada", "extension de pierna", "prensa", "salto", "subida al cajon",
    ),
    Limitation.SHOULDER to wordStarts(
        "overhead", "military", "behind the neck", "upright row", "dip", "arnold", "snatch",
        "jerk", "handstand", "push press", "seated shoulder press", "shoulder press",
        "press militar", "tras nuca", "remo al menton", "fondo", "arranque",
    ),
    Limitation.LOWER_BACK to wordStarts(
        "deadlift", "good morning", "bent over", "bent-over", "hyperextension",
        "back extension", "clean", "snatch", "t-bar", "pendlay", "romanian",
        "peso muerto", "buenos dias", "remo inclinado", "hiperextension", "extension lumbar",
    ),
)

private val MARKS = Regex("""\p{Mn}+""")

private fun foldForLimitations(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase()
