package com.ironmind.app.domain.util

import com.ironmind.app.domain.model.Exercise
import java.text.Normalizer

/**
 * Reads which candidates the model picked, in its order, from an answer to
 * [com.ironmind.app.domain.ai.RoutineGeneratorPromptBuilder]'s numbered list.
 *
 * Asked for "3, 7, 1, 12", a 1B model answers that way most of the time, and otherwise in one of a
 * few recognisable shapes: one number per line, a numbered list of its picks ("1. 3"), or the
 * exercise names instead of numbers. Each line is read by name first — a line that names an
 * exercise means that exercise, whatever list numbering precedes it — and by its numbers only when
 * it names none. Numbers outside the list, and repeats, are ignored.
 *
 * A line that reads like a prescription ("3 series de 10") has its numbers ignored: those are sets
 * and reps, and taking them as list positions would silently pick unrelated exercises. The sets
 * and reps themselves are [RoutineAssembler]'s job, not the model's.
 */
object RoutineChoiceParser {

    /** Shorter names match inside unrelated words too often to be trusted. */
    private const val MIN_NAME_LENGTH = 3

    private val LIST_NUMBERING = Regex("""^\s*\d+[.)]\s+""")
    private val INTEGER = Regex("""\b\d+\b""")
    private val PRESCRIPTION_WORDS =
        Regex("""serie|\brep|\bseg|descanso|\bkg\b|\blbs?\b|\bmin\b|\d\s*x\s*\d|\bsets?\b""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val MARKDOWN = Regex("""[*_`#]""")
    private val WHITESPACE = Regex("""\s+""")

    fun parse(rawResponse: String, candidates: List<Exercise>): List<Exercise> {
        // Longest first, so "Incline Bench Press" wins over a bare "Bench Press" on the same line.
        val names = candidates
            .flatMap { ex -> listOfNotNull(ex.name, ex.nameEs).map { fold(it) to ex } }
            .filter { (name, _) -> name.length >= MIN_NAME_LENGTH }
            .sortedByDescending { (name, _) -> name.length }

        val picked = LinkedHashMap<Long, Exercise>()
        rawResponse.lineSequence().forEach { rawLine ->
            val line = fold(rawLine)
            val named = namesIn(line, names)
            val fromLine = named.ifEmpty { numbersIn(line, candidates) }
            fromLine.forEach { picked.putIfAbsent(it.id, it) }
        }
        return picked.values.toList()
    }

    /** Every candidate named on [line], in the order they appear, without overlapping matches. */
    private fun namesIn(line: String, names: List<Pair<String, Exercise>>): List<Exercise> {
        val claimed = BooleanArray(line.length)
        val found = mutableListOf<Pair<Int, Exercise>>()
        for ((name, exercise) in names) {
            var from = 0
            while (true) {
                val at = line.indexOf(name, from).takeIf { it >= 0 } ?: break
                val range = at until at + name.length
                if (range.none { claimed[it] }) {
                    range.forEach { claimed[it] = true }
                    found += at to exercise
                    break
                }
                from = at + 1
            }
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun numbersIn(line: String, candidates: List<Exercise>): List<Exercise> {
        if (PRESCRIPTION_WORDS.containsMatchIn(line)) return emptyList()
        return INTEGER.findAll(line.replace(LIST_NUMBERING, ""))
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..candidates.size }
            .map { candidates[it - 1] }
            .toList()
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(MARKDOWN, "")
            .replace(WHITESPACE, " ")
            .trim()
            .lowercase()
}
