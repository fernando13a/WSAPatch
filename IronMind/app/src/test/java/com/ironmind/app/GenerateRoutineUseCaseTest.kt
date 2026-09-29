package com.ironmind.app

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.RoutineDraft
import com.ironmind.app.domain.model.RoutineDraftState
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.TrainingGoal
import com.ironmind.app.domain.usecase.GenerateRoutineUseCase
import com.ironmind.app.domain.util.RoutinePrescription
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateRoutineUseCaseTest {

    private val bench = Exercise(id = 11, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val ohp = Exercise(id = 22, name = "Overhead Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL)
    private val lateral = Exercise(id = 33, name = "Lateral Raise", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.DUMBBELL)
    private val pushdown = Exercise(id = 44, name = "Triceps Pushdown", muscleGroup = MuscleGroup.TRICEPS, equipment = Equipment.CABLE)

    private fun repository(exercises: List<Exercise>) = FakeWorkoutRepository().apply {
        exercisesFlow.value = exercises
    }

    private suspend fun generate(llm: LlmInferenceService, exercises: List<Exercise> = listOf(bench, ohp, lateral, pushdown)) =
        GenerateRoutineUseCase(repository(exercises), llm)(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY).toList()

    private fun draftOf(states: List<RoutineDraftState>): RoutineDraft {
        val last = states.last()
        assertTrue("expected Success, was $last", last is RoutineDraftState.Success)
        return (last as RoutineDraftState.Success).draft
    }

    @Test
    fun theModelsPickBecomesAnAiDraftPrescribedByGoal() = runTest {
        // Shortlist order is round-robin: bench(1), ohp(2), pushdown(3), lateral(4).
        val states = generate(FakeLlmInferenceService(chunks = listOf("1, ", "2, 3, 4")))

        assertEquals(RoutineDraftState.Loading, states.first())
        val draft = draftOf(states)
        assertEquals(RoutineDraft.Source.AI, draft.source)
        assertNull(draft.notice)
        assertEquals(setOf(11L, 22L, 33L, 44L), draft.exercises.map { it.exerciseId }.toSet())

        // Numbers come from the goal, not from the model.
        val benchRow = draft.exercises.first { it.exerciseId == bench.id }
        val expected = RoutinePrescription.forExercise(TrainingGoal.HYPERTROPHY, compound = true)
        assertEquals(expected.reps, benchRow.reps)
        assertEquals(expected.restSeconds, benchRow.restSeconds)
    }

    @Test
    fun noCandidatesIsTheOnlyError() = runTest {
        val squat = Exercise(id = 9, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)

        val last = generate(FakeLlmInferenceService(), exercises = listOf(squat)).last()

        assertTrue(last is RoutineDraftState.Error)
    }

    /** Never a dead end: an unusable answer still produces a routine, and says why. */
    @Test
    fun anUnusableAnswerFallsBackToRulesAndQuotesIt() = runTest {
        val draft = draftOf(generate(FakeLlmInferenceService(chunks = listOf("Claro, aquí tienes una rutina genial"))))

        assertEquals(RoutineDraft.Source.RULES, draft.source)
        assertTrue(draft.exercises.isNotEmpty())
        assertTrue(draft.notice!!, draft.notice!!.contains("Claro, aquí tienes una rutina genial"))
    }

    @Test
    fun anEmptyAnswerFallsBackAndSaysItWasEmpty() = runTest {
        val draft = draftOf(generate(FakeLlmInferenceService(chunks = listOf("  ", "\n"))))

        assertEquals(RoutineDraft.Source.RULES, draft.source)
        assertTrue(draft.notice!!, draft.notice!!.contains("sin responder nada"))
    }

    @Test
    fun aMissingModelFallsBackAndPointsAtTheDownload() = runTest {
        val draft = draftOf(generate(FakeLlmInferenceService(error = LlmModelNotFoundException("/models/x.task"))))

        assertEquals(RoutineDraft.Source.RULES, draft.source)
        assertTrue(draft.exercises.isNotEmpty())
        assertTrue(draft.notice!!, draft.notice!!.contains("Modelo de IA"))
    }

    @Test
    fun anEngineFailureFallsBackAndCarriesTheReason() = runTest {
        val draft = draftOf(generate(FakeLlmInferenceService(error = IllegalStateException("motor sin memoria"))))

        assertEquals(RoutineDraft.Source.RULES, draft.source)
        assertTrue(draft.notice!!, draft.notice!!.contains("motor sin memoria"))
    }

    /** A generation that never finishes must not leave the screen spinning forever. */
    @Test
    fun aHungGenerationTimesOutIntoRules() = runTest {
        val hung = object : LlmInferenceService {
            override suspend fun isModelAvailable() = true
            override fun generateResponseStream(prompt: String): Flow<String> = flow { awaitCancellation() }
            override fun close() = Unit
        }

        val draft = draftOf(generate(hung))

        assertEquals(RoutineDraft.Source.RULES, draft.source)
        assertTrue(draft.notice!!, draft.notice!!.contains("tardó demasiado"))
    }

    /** Reciting every candidate back isn't a choice; it would otherwise pass as one. */
    @Test
    fun reciteTheWholeListFallsBackToRules() = runTest {
        val many = (1L..14L).map { Exercise(id = it, name = "Chest Press Variation $it", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL) }
        val recital = (1..14).joinToString(", ")

        val draft = draftOf(generate(FakeLlmInferenceService(chunks = listOf(recital)), exercises = many))

        assertEquals(RoutineDraft.Source.RULES, draft.source)
    }

    @Test
    fun aLongAnswerIsCutShortInTheNotice() = runTest {
        val draft = draftOf(generate(FakeLlmInferenceService(chunks = listOf("x".repeat(5_000)))))

        assertTrue("was ${draft.notice!!.length} chars", draft.notice!!.length < 500)
    }

    @Test
    fun respectsTheEquipmentFilter() = runTest {
        val dumbbellPress = Exercise(id = 3, name = "Dumbbell Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.DUMBBELL)
        val useCase = GenerateRoutineUseCase(repository(listOf(bench, dumbbellPress)), FakeLlmInferenceService(chunks = listOf("1")))

        val last = useCase(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, availableEquipment = setOf(Equipment.DUMBBELL)).toList().last()

        assertEquals(listOf(3L), (last as RoutineDraftState.Success).draft.exercises.map { it.exerciseId })
    }
}
