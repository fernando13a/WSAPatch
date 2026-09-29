package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.RoutineDraftExercise
import java.text.Normalizer

/**
 * Parses the on-device model's raw `id|sets|reps|rest` response into validated draft rows.
 *
 * A small on-device model (Gemma 3 1B) follows a requested format *approximately*, not exactly.
 * Besides bullets, numbering, stray whitespace and a sentence of commentary, the deviation that
 * matters most is the exercise column: the candidate list it was shown is `id|name`, so it tends
 * to echo that shape back — `12|Bench Press|3|10|90` — or to write the name in place of the id.
 * A parser that only accepted four consecutive numbers threw every one of those lines away, and a
 * response full of sensible rows came back as "the AI did not return a valid routine".
 *
 * So each line is read as pipe-separated fields: the last three are sets, reps and rest (each may
 * carry a unit, "90s"), and the exercise is whatever comes before them, identified by, in order:
 *  1. a field that is only an id, after stripping a leading bullet or list number;
 *  2. a field equal to a candidate's name or Spanish name, ignoring case, accents and markdown;
 *  3. an id trailing the first field ("Press de banca: 12"), the old parser's reading of a
 *     prefixed line — tried last so a name ending in a number isn't mistaken for an id.
 *
 * Every other failure is "skip this line", never "fail the whole batch": one bad line should not
 * cost the user seven good ones. Hard rejections, because accepting them would be unsafe: an
 * exercise that isn't in [candidates], and a repeated exercise (the first occurrence wins).
 * Numbers outside the sane range are clamped, not rejected — the row lands in an editable draft
 * the user reviews before anything is saved.
 */
object RoutineDraftParser {

    private const val MIN_SETS = 1
    private const val MAX_SETS = 6
    private const val MIN_REPS = 1
    private const val MAX_REPS = 30
    private const val MIN_REST_SECONDS = 15
    private const val MAX_REST_SECONDS = 240

    /** "- ", "* ", "• ", "1. ", "2) " — any run of them at the start of a line. */
    private val LEADING_BULLETS = Regex("""^\s*(?:(?:[-*•·]|\d+[.)])\s+)+""")
    private val ALL_DIGITS = Regex("""^\d+$""")
    private val LEADING_INT = Regex("""^\d+""")
    private val TRAILING_INT = Regex("""(\d+)$""")
    private val MARKDOWN = Regex("""[*_`#]""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val WHITESPACE = Regex("""\s+""")

    fun parse(rawResponse: String, candidates: List<Exercise>): List<RoutineDraftExercise> {
        val byId = candidates.associateBy { it.id }
        val byName = buildMap<String, Long> {
            // Spanish first so an English name wins if the two ever collide on another exercise.
            candidates.forEach { ex -> ex.nameEs?.let { put(fold(it), ex.id) } }
            candidates.forEach { ex -> put(fold(ex.name), ex.id) }
        }
        val seenIds = mutableSetOf<Long>()
        val result = mutableListOf<RoutineDraftExercise>()

        rawResponse.lineSequence().forEach { line ->
            val fields = line.replace(LEADING_BULLETS, "")
                .split('|')
                .map(String::trim)
                .filter(String::isNotEmpty) // markdown tables open and close rows with a pipe
            if (fields.size < 4) return@forEach

            // toIntOrNull (not toInt): a model hallucinating an absurdly long number must not
            // crash parsing — it just loses that one line.
            val numbers = fields.takeLast(3).map { LEADING_INT.find(it)?.value?.toIntOrNull() }
            val sets = numbers[0]?.coerceIn(MIN_SETS, MAX_SETS) ?: return@forEach
            val reps = numbers[1]?.coerceIn(MIN_REPS, MAX_REPS) ?: return@forEach
            val rest = numbers[2]?.coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS) ?: return@forEach

            val exerciseId = resolveExercise(fields.dropLast(3), byId, byName) ?: return@forEach
            if (!seenIds.add(exerciseId)) return@forEach

            result += RoutineDraftExercise(exerciseId = exerciseId, sets = sets, reps = reps, restSeconds = rest)
        }

        return result
    }

    private fun resolveExercise(
        exerciseFields: List<String>,
        byId: Map<Long, Exercise>,
        byName: Map<String, Long>,
    ): Long? {
        exerciseFields.firstNotNullOfOrNull { field ->
            field.takeIf { ALL_DIGITS.matches(it) }?.toLongOrNull()?.takeIf { it in byId }
        }?.let { return it }

        exerciseFields.firstNotNullOfOrNull { byName[fold(it)] }?.let { return it }

        return exerciseFields.firstOrNull()
            ?.let { TRAILING_INT.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            ?.takeIf { it in byId }
    }

    /** Case-, accent-, markdown- and spacing-insensitive form used to match names. */
    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(MARKDOWN, "")
            .replace(WHITESPACE, " ")
            .trim()
            .lowercase()
}
