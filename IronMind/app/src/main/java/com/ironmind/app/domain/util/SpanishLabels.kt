package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Limitation
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal

/**
 * Spanish names for text the domain layer writes itself — model prompts and the explanations
 * attached to a generated draft. The UI has its own localized labels (string resources); these
 * exist because the domain can't reach resources and its messages are Spanish throughout.
 */
fun MuscleGroup.spanishName(): String = when (this) {
    MuscleGroup.CHEST -> "pecho"
    MuscleGroup.BACK -> "espalda"
    MuscleGroup.SHOULDERS -> "hombro"
    MuscleGroup.BICEPS -> "bíceps"
    MuscleGroup.TRICEPS -> "tríceps"
    MuscleGroup.FOREARMS -> "antebrazo"
    MuscleGroup.QUADS -> "cuádriceps"
    MuscleGroup.HAMSTRINGS -> "femoral"
    MuscleGroup.GLUTES -> "glúteo"
    MuscleGroup.CALVES -> "gemelo"
    MuscleGroup.ABS -> "abdomen"
    MuscleGroup.FULL_BODY -> "cuerpo completo"
    MuscleGroup.OTHER -> "otro"
}

fun RoutineSplit.spanishName(): String = when (this) {
    RoutineSplit.PUSH -> "empuje (pecho, hombro, tríceps)"
    RoutineSplit.PULL -> "tirón (espalda, bíceps)"
    RoutineSplit.LEGS -> "pierna"
    RoutineSplit.UPPER -> "torso"
    RoutineSplit.LOWER -> "tren inferior"
    RoutineSplit.FULL_BODY -> "cuerpo completo"
    RoutineSplit.CHEST_TRICEPS -> "pecho y tríceps"
    RoutineSplit.BACK_BICEPS -> "espalda y bíceps"
    RoutineSplit.SHOULDERS -> "hombro"
    RoutineSplit.ARMS -> "brazos"
    RoutineSplit.CORE -> "core y abdomen"
    RoutineSplit.CARDIO -> "cardio"
    RoutineSplit.CUSTOM -> "libre"
}

fun TrainingGoal.spanishName(): String = when (this) {
    TrainingGoal.STRENGTH -> "fuerza"
    TrainingGoal.HYPERTROPHY -> "hipertrofia"
    TrainingGoal.ENDURANCE -> "resistencia muscular"
}

fun Limitation.spanishName(): String = when (this) {
    Limitation.KNEE -> "la rodilla"
    Limitation.SHOULDER -> "el hombro"
    Limitation.LOWER_BACK -> "la espalda baja"
}

/** "a", "a y b", "a, b y c". */
fun List<String>.joinAsSpanishList(): String = when (size) {
    0 -> ""
    1 -> single()
    else -> dropLast(1).joinToString(", ") + " y " + last()
}
