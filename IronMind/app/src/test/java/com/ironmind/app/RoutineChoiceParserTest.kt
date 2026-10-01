package com.ironmind.app

import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.util.RoutineChoiceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineChoiceParserTest {

    private val candidates = listOf(
        Exercise(id = 101, name = "Bench Press", nameEs = "Press de banca", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
        Exercise(id = 102, name = "Overhead Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL),
        Exercise(id = 103, name = "Triceps Pushdown", nameEs = "Extensión de tríceps en polea", muscleGroup = MuscleGroup.TRICEPS, equipment = Equipment.CABLE),
        Exercise(id = 104, name = "Incline Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL),
        Exercise(id = 105, name = "Lateral Raise", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.DUMBBELL),
    )

    private fun parse(text: String) = RoutineChoiceParser.parse(text, candidates).map { it.id }

    @Test
    fun readsTheRequestedCommaList() {
        assertEquals(listOf(104L, 101L, 105L), parse("4, 1, 5"))
    }

    /** Positions, not catalog ids: "3" is the third candidate. */
    @Test
    fun numbersArePositionsInTheListNotIds() {
        assertEquals(listOf(103L), parse("3"))
        assertTrue("an id is not a position", parse("101").isEmpty())
    }

    @Test
    fun readsOneNumberPerLine() {
        assertEquals(listOf(102L, 101L), parse("2\n1"))
    }

    /** The model numbering its own answers must not turn the numbering into picks. */
    @Test
    fun ignoresTheModelsOwnListNumbering() {
        assertEquals(listOf(104L, 102L, 105L), parse("1. 4\n2. 2\n3. 5"))
    }

    @Test
    fun readsNamesWhenTheModelWritesThemInsteadOfNumbers() {
        assertEquals(listOf(102L, 103L), parse("Overhead Press\nTriceps Pushdown"))
    }

    /** A line that names an exercise means that one, whatever list number precedes it. */
    @Test
    fun aNamedLineWinsOverItsNumbers() {
        assertEquals(listOf(105L, 101L), parse("1. Lateral Raise\n2. Bench Press"))
    }

    @Test
    fun matchesSpanishNamesIgnoringAccentsCaseAndMarkdown() {
        assertEquals(listOf(101L, 103L), parse("**press de banca**\nEXTENSION DE TRICEPS EN POLEA"))
    }

    /** "Incline Bench Press" contains "Bench Press"; the longer, exact name has to win. */
    @Test
    fun theLongestNameWins() {
        assertEquals(listOf(104L), parse("Incline Bench Press"))
    }

    @Test
    fun severalNamesOnOneLineKeepTheirOrder() {
        assertEquals(listOf(105L, 101L), parse("Lateral Raise, luego Bench Press"))
    }

    @Test
    fun outOfRangeNumbersAndRepeatsAreIgnored() {
        assertEquals(listOf(102L, 101L), parse("2, 9, 0, 2, 1, 40"))
    }

    /**
     * Sets and reps are not positions. Taking "3 series de 10" as picks 3 and 10 would quietly add
     * unrelated exercises, so a line that reads like a prescription contributes no numbers.
     */
    @Test
    fun aPrescriptionLineContributesNoNumbers() {
        assertTrue(parse("3 series de 10 repeticiones, 90 segundos de descanso").isEmpty())
        assertTrue(parse("4x8").isEmpty())
        assertEquals(listOf(101L), parse("Bench Press: 3 series de 10"))
    }

    @Test
    fun proseAroundTheAnswerIsTolerated() {
        assertEquals(listOf(102L, 104L), parse("Los ejercicios elegidos son: 2, 4.\n¡A entrenar!"))
    }

    @Test
    fun blankOrUnrelatedTextPicksNothing() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("Lo siento, no puedo ayudar con eso.").isEmpty())
    }
}
