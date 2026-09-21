package com.ironmind.app.domain.model

/**
 * The training split a routine belongs to. Supports classic divisions such as
 * Push / Pull / Legs, as well as upper/lower and full-body layouts.
 */
enum class RoutineSplit {
    PUSH,
    PULL,
    LEGS,
    UPPER,
    LOWER,
    FULL_BODY,
    CHEST_TRICEPS,
    BACK_BICEPS,
    SHOULDERS,
    ARMS,
    CORE,
    CARDIO,
    CUSTOM,
}
