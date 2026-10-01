package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Limitation

/**
 * Replacements for one exercise of a draft — the "Cambiar" button. Deterministic and instant: no
 * model, just the catalog already in memory.
 *
 * Unlike [filterAlternatives] (same muscle, *different* equipment: "the machine is taken"), this
 * keeps to the equipment the athlete said they have, and so may well offer the same equipment.
 * It respects the joints they asked to spare and never offers something already in the draft.
 * The same kind of movement comes first — swapping a press for a press keeps the session's shape,
 * a press for a raise changes it — then exercises they actually do, then curated ones.
 */
object RoutineSwap {

    const val MAX_OPTIONS = 6

    fun options(
        current: Exercise,
        catalog: Collection<Exercise>,
        inDraft: Set<Long>,
        availableEquipment: Set<Equipment>,
        avoid: Set<Limitation> = emptySet(),
        history: TrainingHistory = TrainingHistory.EMPTY,
    ): List<Exercise> {
        val compound = current.isCompound()
        return catalog
            .filter { candidate ->
                candidate.id != current.id &&
                    candidate.id !in inDraft &&
                    candidate.muscleGroup == current.muscleGroup &&
                    candidate.equipment in availableEquipment &&
                    avoid.none { candidate.stresses(it) }
            }
            .sortedWith(
                compareByDescending<Exercise> { it.isCompound() == compound }
                    .thenByDescending { history.familiarity(it.id) }
                    .thenByDescending { !it.instructions.isNullOrBlank() }
                    .thenBy { it.name },
            )
            .take(MAX_OPTIONS)
    }
}
