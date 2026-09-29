package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Limitation
import com.ironmind.app.domain.model.RoutineSplit

/**
 * Deterministically narrows the full exercise catalog down to a short candidate list for the AI
 * routine generator. This is the mitigation for the on-device model's tiny token budget: the
 * catalog has 870+ exercises (tens of thousands of characters as `id|name` pairs), which cannot
 * fit in [com.ironmind.app.data.ai.AiConstants.PROMPT_CHAR_BUDGET] — so Kotlin does the filtering
 * and the model only ever *picks from* and *prescribes* a pre-vetted shortlist, never invents
 * exercises outside it.
 */
object RoutineCandidateSelector {

    /** Keeps the embedded candidate list (as `id|name` lines) comfortably within the prompt budget. */
    const val MAX_CANDIDATES = 24

    /**
     * @param availableEquipment restricts candidates to this equipment; `null` means "any equipment".
     * @param history ranks exercises the athlete has logged ahead of ones they never do.
     * @param avoid drops every exercise that loads one of these joints.
     */
    fun select(
        split: RoutineSplit,
        catalog: List<Exercise>,
        availableEquipment: Set<Equipment>? = null,
        history: TrainingHistory = TrainingHistory.EMPTY,
        avoid: Set<Limitation> = emptySet(),
    ): List<Exercise> {
        val muscleGroups = split.muscleGroups()
        val matching = catalog.filter { exercise ->
            exercise.muscleGroup in muscleGroups &&
                (availableEquipment == null || exercise.equipment in availableEquipment) &&
                avoid.none { exercise.stresses(it) }
        }

        // Round-robin across muscle groups so the shortlist has real variety instead of being
        // dominated by whichever muscle group happens to have the most catalog entries. Within a
        // group: what the athlete actually does first — with ~870 entries, the shortlist would
        // otherwise hand the model 24 exercises they've never touched — then curated ones.
        val queues = muscleGroups
            .mapNotNull { group ->
                val forGroup = matching
                    .filter { it.muscleGroup == group }
                    .sortedWith(
                        compareByDescending<Exercise> { history.familiarity(it.id) }
                            .thenByDescending { !it.instructions.isNullOrBlank() },
                    )
                forGroup.takeIf { it.isNotEmpty() }?.let { ArrayDeque(it) }
            }

        val result = mutableListOf<Exercise>()
        while (result.size < MAX_CANDIDATES && queues.any { it.isNotEmpty() }) {
            for (queue in queues) {
                if (result.size >= MAX_CANDIDATES) break
                queue.removeFirstOrNull()?.let { result += it }
            }
        }
        return result
    }
}
