package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import java.text.Normalizer

/**
 * Whether an exercise is a compound (multi-joint) lift rather than an isolation movement.
 *
 * The catalog has no such field, and adding one means a schema migration and re-seeding ~870
 * rows, so it is read off the name instead — the English one from the seed data and the Spanish
 * one a user is likely to type for a custom exercise. The answer only drives ordering (compounds
 * first, while the athlete is fresh) and the default prescription, both of which the athlete can
 * edit before saving, so an occasional misread costs a tap, not a bad session.
 *
 * Isolation keywords are checked first because they are the more specific ones: "Triceps
 * Pushdown", "Leg Extension" and "Triceps Pressdown" all contain a compound-sounding word too.
 * With neither, the muscle group decides: the big groups are mostly trained with compounds.
 */
fun Exercise.isCompound(): Boolean {
    val text = fold(listOfNotNull(name, nameEs).joinToString(" "))
    if (ISOLATION.containsMatchIn(text)) return false
    if (COMPOUND.containsMatchIn(text)) return true
    return muscleGroup in COMPOUND_BY_DEFAULT
}

/** Word-start matches, so plurals and suffixes count ("rows", "curls") but "throw" isn't a row. */
private fun keywords(vararg words: String) = Regex("""\b(?:${words.joinToString("|")})""")

private val ISOLATION = keywords(
    "curl", "fly", "flye", "raise", "extension", "hyperextension", "kickback", "pushdown",
    "pressdown", "crossover", "shrug", "calf", "pullover", "crunch", "twist", "adduct", "abduct",
    "wrist", "skull", "pec deck", "butterfly",
    // Spanish
    "elevacion", "apertura", "extension", "patada", "encogimiento", "gemelo", "pantorrilla",
    "abdominal", "cruce",
)

private val COMPOUND = keywords(
    "press", "squat", "deadlift", "row", "pull-up", "pullup", "pull up", "pulldown", "chin",
    "dip", "lunge", "clean", "snatch", "jerk", "thrust", "push-up", "pushup", "push up",
    "step-up", "step up", "good morning", "burpee",
    // Spanish
    "sentadilla", "peso muerto", "remo", "dominada", "fondo", "zancada", "jalon", "flexion", "prensa",
    "empuje", "levantamiento",
)

private val COMPOUND_BY_DEFAULT = setOf(
    MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS,
    MuscleGroup.GLUTES, MuscleGroup.FULL_BODY,
)

private val COMBINING_MARKS = Regex("""\p{Mn}+""")

private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()
