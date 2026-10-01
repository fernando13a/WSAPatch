package com.ironmind.app.ui.session

import com.ironmind.app.domain.model.ExercisePrescription
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.domain.util.StartingWeight

/**
 * What the add-set form starts on for an exercise, so a set is usually one tap. Either field can
 * be null — nothing to go on — and the form is left blank there rather than guessed.
 */
data class SetPrefill(val weightKg: Double?, val reps: Int?) {

    companion object {
        /**
         * The routine's target weight when it has one. Otherwise last time's top set — converted
         * to the routine's reps when they differ: 100 kg × 5 last time is not a load for 10 reps.
         * A target weight of 0 (saved by a build that rounded light loads down to nothing) is no
         * target at all.
         */
        fun from(target: ExercisePrescription?, lastTopSet: SetLog?): SetPrefill {
            val reps = target?.reps ?: lastTopSet?.reps?.takeIf { it > 0 }
            val weighted = lastTopSet?.takeIf { it.weightKg > 0 && it.reps > 0 }
            val weightKg = target?.weightKg?.takeIf { it > 0 }
                ?: weighted?.let { last ->
                    if (reps == last.reps) last.weightKg else reps?.let { StartingWeight.suggestKg(last.weightKg, last.reps, it) }
                }
            return SetPrefill(weightKg, reps)
        }
    }
}
