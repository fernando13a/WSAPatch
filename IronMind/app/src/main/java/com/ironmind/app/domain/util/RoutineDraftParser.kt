package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.RoutineDraftExercise

/**
 * Parses the on-device model's raw `id|sets|reps|rest` response into validated draft rows.
 *
 * A small on-device model (Gemma 3 1B) follows a requested format *approximately*, not exactly —
 * it may prefix a line with a bullet or number, add stray whitespace, or throw in a sentence of
 * commentary despite being told not to. This parser is deliberately tolerant of all of that: it
 * searches each line for the four-number pattern rather than requiring the line to match it
 * exactly, and treats every other failure mode as "skip this line", never "fail the whole batch" —
 * one bad line should not cost the user seven good ones.
 *
 * What IS a hard rejection, because accepting it would be unsafe or nonsensical: an exercise id
 * that isn't in [candidates] (the model inventing or mistyping an id), and a repeated id (keeps
 * only the first occurrence). Numeric fields outside the sane range are clamped, not rejected —
 * the row surfaces in an editable draft the user reviews before anything is saved, so a slightly
 * out-of-range number is better fixed there than silently thrown away.
 */
object RoutineDraftParser {

    private val LINE_PATTERN = Regex("""(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)""")

    private const val MIN_SETS = 1
    private const val MAX_SETS = 6
    private const val MIN_REPS = 1
    private const val MAX_REPS = 30
    private const val MIN_REST_SECONDS = 15
    private const val MAX_REST_SECONDS = 240

    fun parse(rawResponse: String, candidates: List<Exercise>): List<RoutineDraftExercise> {
        val candidateIds = candidates.mapTo(mutableSetOf()) { it.id }
        val seenIds = mutableSetOf<Long>()
        val result = mutableListOf<RoutineDraftExercise>()

        rawResponse.lineSequence().forEach { line ->
            val match = LINE_PATTERN.find(line) ?: return@forEach
            val (idText, setsText, repsText, restText) = match.destructured
            val exerciseId = idText.toLongOrNull() ?: return@forEach
            if (exerciseId !in candidateIds || exerciseId in seenIds) return@forEach

            // toIntOrNull (not toInt): the regex only guarantees digits, not that they fit in an
            // Int — a model hallucinating an absurdly long number must not crash parsing.
            val sets = setsText.toIntOrNull()?.coerceIn(MIN_SETS, MAX_SETS) ?: return@forEach
            val reps = repsText.toIntOrNull()?.coerceIn(MIN_REPS, MAX_REPS) ?: return@forEach
            val rest = restText.toIntOrNull()?.coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS) ?: return@forEach

            seenIds += exerciseId
            result += RoutineDraftExercise(exerciseId = exerciseId, sets = sets, reps = reps, restSeconds = rest)
        }

        return result
    }
}
