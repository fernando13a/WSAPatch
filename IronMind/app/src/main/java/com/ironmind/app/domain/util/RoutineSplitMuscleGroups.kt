package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineSplit

/**
 * The muscle groups a given [RoutineSplit] trains — used to filter the exercise catalog down to
 * candidates the AI routine generator may choose from, so a "Push day" request can never surface
 * a leg exercise regardless of what the model does with the prompt.
 */
fun RoutineSplit.muscleGroups(): Set<MuscleGroup> = when (this) {
    RoutineSplit.PUSH -> setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)
    RoutineSplit.PULL -> setOf(MuscleGroup.BACK, MuscleGroup.BICEPS)
    RoutineSplit.LEGS -> setOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)
    RoutineSplit.UPPER -> setOf(
        MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.BICEPS, MuscleGroup.TRICEPS,
    )
    RoutineSplit.LOWER -> setOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES)
    RoutineSplit.FULL_BODY -> setOf(
        MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS,
        MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES,
        MuscleGroup.ABS, MuscleGroup.FULL_BODY,
    )
    RoutineSplit.CHEST_TRICEPS -> setOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS)
    RoutineSplit.BACK_BICEPS -> setOf(MuscleGroup.BACK, MuscleGroup.BICEPS)
    RoutineSplit.SHOULDERS -> setOf(MuscleGroup.SHOULDERS)
    RoutineSplit.ARMS -> setOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.FOREARMS)
    RoutineSplit.CORE -> setOf(MuscleGroup.ABS)
    RoutineSplit.CARDIO -> setOf(MuscleGroup.FULL_BODY, MuscleGroup.OTHER)
    // No single focus — let the generator draw from the whole catalog.
    RoutineSplit.CUSTOM -> MuscleGroup.entries.toSet()
}
