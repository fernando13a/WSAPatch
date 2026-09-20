package com.ironmind.app.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ironmind.app.R
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit

/**
 * Localized display labels for the domain taxonomy. The enums stay English (they are the stable,
 * serialized identity used in Room and backups); only the on-screen label is translated, so the UI
 * reads in Spanish (or English) without ever changing the persisted value.
 */

@StringRes
fun MuscleGroup.labelRes(): Int = when (this) {
    MuscleGroup.CHEST -> R.string.muscle_chest
    MuscleGroup.BACK -> R.string.muscle_back
    MuscleGroup.SHOULDERS -> R.string.muscle_shoulders
    MuscleGroup.BICEPS -> R.string.muscle_biceps
    MuscleGroup.TRICEPS -> R.string.muscle_triceps
    MuscleGroup.FOREARMS -> R.string.muscle_forearms
    MuscleGroup.QUADS -> R.string.muscle_quads
    MuscleGroup.HAMSTRINGS -> R.string.muscle_hamstrings
    MuscleGroup.GLUTES -> R.string.muscle_glutes
    MuscleGroup.CALVES -> R.string.muscle_calves
    MuscleGroup.ABS -> R.string.muscle_abs
    MuscleGroup.FULL_BODY -> R.string.muscle_full_body
    MuscleGroup.OTHER -> R.string.muscle_other
}

@Composable
fun MuscleGroup.label(): String = stringResource(labelRes())

@StringRes
fun Equipment.labelRes(): Int = when (this) {
    Equipment.BARBELL -> R.string.equipment_barbell
    Equipment.DUMBBELL -> R.string.equipment_dumbbell
    Equipment.MACHINE -> R.string.equipment_machine
    Equipment.CABLE -> R.string.equipment_cable
    Equipment.KETTLEBELL -> R.string.equipment_kettlebell
    Equipment.BODYWEIGHT -> R.string.equipment_bodyweight
    Equipment.BAND -> R.string.equipment_band
    Equipment.OTHER -> R.string.equipment_other
}

@Composable
fun Equipment.label(): String = stringResource(labelRes())

@StringRes
fun RoutineSplit.labelRes(): Int = when (this) {
    RoutineSplit.PUSH -> R.string.split_push
    RoutineSplit.PULL -> R.string.split_pull
    RoutineSplit.LEGS -> R.string.split_legs
    RoutineSplit.UPPER -> R.string.split_upper
    RoutineSplit.LOWER -> R.string.split_lower
    RoutineSplit.FULL_BODY -> R.string.split_full_body
    RoutineSplit.CHEST_TRICEPS -> R.string.split_chest_triceps
    RoutineSplit.BACK_BICEPS -> R.string.split_back_biceps
    RoutineSplit.SHOULDERS -> R.string.split_shoulders
    RoutineSplit.ARMS -> R.string.split_arms
    RoutineSplit.CORE -> R.string.split_core
    RoutineSplit.CARDIO -> R.string.split_cardio
    RoutineSplit.CUSTOM -> R.string.split_custom
}

@Composable
fun RoutineSplit.label(): String = stringResource(labelRes())
