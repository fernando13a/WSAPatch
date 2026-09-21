package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.util.RoutineDraftParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDraftParserTest {

    private val candidates = listOf(
        Exercise(id = 1, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
        Exercise(id = 2, name = "Overhead Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL),
        Exercise(id = 3, name = "Triceps Pushdown", muscleGroup = MuscleGroup.TRICEPS, equipment = Equipment.CABLE),
    )

    @Test
    fun parsesWellFormedLines() {
        val response = "1|3|10|90\n2|4|8|120\n"

        val result = RoutineDraftParser.parse(response, candidates)

        assertEquals(2, result.size)
        assertEquals(1L, result[0].exerciseId)
        assertEquals(3, result[0].sets)
        assertEquals(10, result[0].reps)
        assertEquals(90, result[0].restSeconds)
    }

    @Test
    fun toleratesLeadingBulletsOrNumberingBeforeTheData() {
        val response = "1. 1|3|10|90\n- 2|4|8|120\n"

        val result = RoutineDraftParser.parse(response, candidates)

        assertEquals(setOf(1L, 2L), result.map { it.exerciseId }.toSet())
    }

    @Test
    fun toleratesExtraWhitespaceAroundSeparators() {
        val response = "1 | 3 | 10 | 90"

        val result = RoutineDraftParser.parse(response, candidates)

        assertEquals(1, result.size)
        assertEquals(3, result.single().sets)
    }

    @Test
    fun ignoresProseLinesMixedWithValidData() {
        val response = """
            Aquí tienes tu rutina de empuje:
            1|3|10|90
            Espero que te sirva, ¡a entrenar!
            2|4|8|120
        """.trimIndent()

        val result = RoutineDraftParser.parse(response, candidates)

        assertEquals(2, result.size)
    }

    @Test
    fun rejectsExerciseIdsNotInCandidates() {
        val response = "999|3|10|90\n1|3|10|90"

        val result = RoutineDraftParser.parse(response, candidates)

        assertEquals(listOf(1L), result.map { it.exerciseId })
    }

    @Test
    fun keepsOnlyTheFirstOccurrenceOfADuplicateExerciseId() {
        val response = "1|3|10|90\n1|5|20|180"

        val result = RoutineDraftParser.parse(response, candidates)

        assertEquals(1, result.size)
        assertEquals(3, result.single().sets) // the first occurrence's values, not the second
    }

    @Test
    fun clampsSetsRepsAndRestToSaneRangesInsteadOfRejecting() {
        val response = "1|99|999|9999"

        val result = RoutineDraftParser.parse(response, candidates)

        assertEquals(1, result.size)
        val row = result.single()
        assertTrue(row.sets <= 6)
        assertTrue(row.reps <= 30)
        assertTrue(row.restSeconds <= 240)
    }

    @Test
    fun clampsValuesBelowTheMinimumUpward() {
        val response = "1|0|0|0"

        val result = RoutineDraftParser.parse(response, candidates)

        val row = result.single()
        assertTrue(row.sets >= 1)
        assertTrue(row.reps >= 1)
        assertTrue(row.restSeconds >= 15)
    }

    @Test
    fun returnsEmptyForCompletelyMalformedResponse() {
        val response = "Lo siento, no puedo generar una rutina en este momento."

        val result = RoutineDraftParser.parse(response, candidates)

        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmptyForBlankResponse() {
        assertTrue(RoutineDraftParser.parse("", candidates).isEmpty())
        assertTrue(RoutineDraftParser.parse("   \n  \n", candidates).isEmpty())
    }

    @Test
    fun doesNotCrashOnAnAbsurdlyLargeNumber() {
        val response = "1|99999999999999999999|10|90"

        // Must not throw (NumberFormatException on Int overflow) — the whole batch shouldn't
        // die because one field overflowed.
        val result = RoutineDraftParser.parse(response, candidates)

        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmptyCandidateListWhenNoCandidatesProvided() {
        val response = "1|3|10|90"

        val result = RoutineDraftParser.parse(response, emptyList())

        assertTrue(result.isEmpty())
    }
}
