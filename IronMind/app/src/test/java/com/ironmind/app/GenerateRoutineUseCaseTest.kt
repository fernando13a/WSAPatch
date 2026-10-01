package com.ironmind.app

import com.ironmind.app.domain.ai.LlmInferenceService
import com.ironmind.app.domain.ai.LlmModelNotFoundException
import com.ironmind.app.domain.ai.RoutineGeneratorPromptBuilder
import com.ironmind.app.domain.ai.RoutineRefinePromptBuilder
import com.ironmind.app.domain.model.RoutineDraftExercise
import com.ironmind.app.domain.model.RoutineRefineState
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Limitation
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.SetLog
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

    private companion object {
        const val HOUR = 3_600_000L
        const val NOW = 1_000_000L * HOUR
    }

    private val bench = Exercise(id = 11, name = "Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
    private val ohp = Exercise(id = 22, name = "Overhead Press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL)
    private val lateral = Exercise(id = 33, name = "Lateral Raise", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.DUMBBELL)
    private val pushdown = Exercise(id = 44, name = "Triceps Pushdown", muscleGroup = MuscleGroup.TRICEPS, equipment = Equipment.CABLE)

    private fun repository(exercises: List<Exercise>) = FakeWorkoutRepository().apply {
        exercisesFlow.value = exercises
    }

    private suspend fun generate(
        llm: LlmInferenceService,
        exercises: List<Exercise> = listOf(bench, ohp, lateral, pushdown),
        repo: FakeWorkoutRepository = repository(exercises),
        split: RoutineSplit = RoutineSplit.PUSH,
        timeBudgetMinutes: Int? = null,
        avoid: Set<Limitation> = emptySet(),
    ) = GenerateRoutineUseCase(repo, llm)(
        split = split,
        goal = TrainingGoal.HYPERTROPHY,
        timeBudgetMinutes = timeBudgetMinutes,
        avoid = avoid,
        now = NOW,
    ).toList()

    private fun loggedSet(exercise: Exercise, hoursAgo: Long, weight: Double = 60.0, reps: Int = 8) = SetLog(
        sessionId = hoursAgo, exerciseId = exercise.id, setNumber = 1, weightKg = weight, reps = reps,
        performedAt = NOW - hoursAgo * HOUR,
    )

    private val unusable = FakeLlmInferenceService(chunks = listOf("no sé"))

    @Test
    fun eachRowCarriesTheLastTopSetForItsExercise() = runTest {
        val repo = repository(listOf(bench, ohp, lateral, pushdown)).apply {
            recentActivity = listOf(loggedSet(bench, hoursAgo = 100, weight = 80.0, reps = 5))
        }

        val draft = draftOf(generate(unusable, repo = repo))

        val benchRow = draft.exercises.first { it.exerciseId == bench.id }
        assertEquals(80.0, benchRow.lastWeightKg!!, 0.0)
        assertEquals(5, benchRow.lastReps)
        assertNull("never logged", draft.exercises.first { it.exerciseId == ohp.id }.lastWeightKg)
    }

    @Test
    fun aRecentlyTrainedMuscleIsReducedAndExplained() = runTest {
        val incline = Exercise(id = 55, name = "Incline Bench Press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
        val repo = repository(listOf(bench, incline, ohp, lateral, pushdown)).apply {
            recentActivity = listOf(loggedSet(bench, hoursAgo = 10))
        }

        val draft = draftOf(generate(unusable, repo = repo))

        val chestRows = draft.exercises.count { it.exerciseId == bench.id || it.exerciseId == incline.id }
        assertEquals(1, chestRows)
        assertTrue(draft.adjustments.toString(), draft.adjustments.any { it.startsWith("Pecho: lo entrenaste hace 10 h") })
    }

    @Test
    fun avoidedJointsAreLeftOutAndExplained() = runTest {
        val squat = Exercise(id = 61, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)
        val curl = Exercise(id = 62, name = "Lying Leg Curl", muscleGroup = MuscleGroup.HAMSTRINGS, equipment = Equipment.MACHINE)

        val draft = draftOf(
            generate(unusable, exercises = listOf(squat, curl), split = RoutineSplit.LEGS, avoid = setOf(Limitation.KNEE)),
        )

        assertEquals(listOf(62L), draft.exercises.map { it.exerciseId })
        assertTrue(draft.adjustments.toString(), draft.adjustments.any { it.contains("la rodilla") })
    }

    /** Avoiding every option leaves nothing to build from — that's the one real error. */
    @Test
    fun avoidingEverythingIsAnErrorThatSaysWhy() = runTest {
        val squat = Exercise(id = 61, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL)

        val last = generate(unusable, exercises = listOf(squat), split = RoutineSplit.LEGS, avoid = setOf(Limitation.KNEE)).last()

        assertTrue(last is RoutineDraftState.Error)
        assertTrue((last as RoutineDraftState.Error).message.contains("molestias"))
    }

    @Test
    fun familiarExercisesAreCountedInTheExplanation() = runTest {
        val repo = repository(listOf(bench, ohp, lateral, pushdown)).apply {
            recentActivity = listOf(loggedSet(bench, hoursAgo = 200), loggedSet(ohp, hoursAgo = 200))
        }

        val draft = draftOf(generate(unusable, repo = repo))

        assertTrue(draft.adjustments.toString(), draft.adjustments.contains("Incluye 2 ejercicios que ya haces."))
    }

    @Test
    fun theTimeBudgetReachesTheAssembler() = runTest {
        val unlimited = draftOf(generate(unusable))
        val short = draftOf(generate(unusable, timeBudgetMinutes = 30))

        assertTrue("${short.exercises.size} vs ${unlimited.exercises.size}", short.exercises.size < unlimited.exercises.size)
    }

    /** A list of numbers to parse is asked for at low temperature, not the chat's 0.8. */
    @Test
    fun theChoiceIsSampledAtTheRoutineTemperature() = runTest {
        val llm = FakeLlmInferenceService(chunks = listOf("1, 2"))

        generate(llm)

        assertEquals(RoutineGeneratorPromptBuilder.TEMPERATURE, llm.lastTemperature)
        assertTrue(RoutineGeneratorPromptBuilder.TEMPERATURE < 0.5f)
    }

    // ---- refine ---------------------------------------------------------------------------

    private suspend fun refine(
        llm: LlmInferenceService,
        current: List<RoutineDraftExercise>,
        instruction: String = "más corta",
        exercises: List<Exercise> = listOf(bench, ohp, lateral, pushdown),
    ) = GenerateRoutineUseCase(repository(exercises), llm)
        .refine(RoutineSplit.PUSH, TrainingGoal.HYPERTROPHY, current, instruction, now = NOW)
        .toList()

    private fun row(exercise: Exercise, sets: Int = 3) =
        RoutineDraftExercise(exerciseId = exercise.id, sets = sets, reps = 10, restSeconds = 60)

    /** The current session is listed first, so dropping one is dropping its number. */
    @Test
    fun refiningCanDropAnExerciseAndKeepsTheOthersAsTheyWere() = runTest {
        val current = listOf(row(bench, sets = 5), row(ohp), row(pushdown))

        val last = refine(FakeLlmInferenceService(chunks = listOf("1, 3")), current).last()

        val rows = (last as RoutineRefineState.Applied).rows
        assertEquals(listOf(bench.id, pushdown.id), rows.map { it.exerciseId })
        assertEquals("the athlete's edit survives", 5, rows.first { it.exerciseId == bench.id }.sets)
    }

    /** "Sin hombro" must not get a shoulder exercise put back for coverage. */
    @Test
    fun refiningDoesNotRefillForCoverage() = runTest {
        val current = listOf(row(bench), row(ohp), row(pushdown))

        val last = refine(FakeLlmInferenceService(chunks = listOf("1, 3")), current, "sin hombro").last()

        val ids = (last as RoutineRefineState.Applied).rows.map { it.exerciseId }
        assertTrue(ohp.id !in ids && lateral.id !in ids)
    }

    @Test
    fun refiningCanBringInAnExerciseFromTheList() = runTest {
        // Current: bench(1), ohp(2), pushdown(3); the shortlist adds lateral as 4.
        val current = listOf(row(bench), row(ohp), row(pushdown))

        val last = refine(FakeLlmInferenceService(chunks = listOf("1, 2, 3, 4")), current, "más hombro").last()

        val rows = (last as RoutineRefineState.Applied).rows
        assertTrue(lateral.id in rows.map { it.exerciseId })
        val added = rows.first { it.exerciseId == lateral.id }
        assertEquals("new rows get the goal's prescription", RoutinePrescription.forExercise(TrainingGoal.HYPERTROPHY, false).reps, added.reps)
    }

    /** A failed adjustment leaves the draft alone and says why — never falls back to rules. */
    @Test
    fun aFailedRefineLeavesTheDraftAndSaysWhy() = runTest {
        val current = listOf(row(bench), row(ohp))

        val last = refine(FakeLlmInferenceService(chunks = listOf("no entiendo")), current).last()

        val message = (last as RoutineRefineState.Failed).message
        assertTrue(message, message.contains("tu rutina quedó igual"))
        assertTrue(message, message.contains("no entiendo"))
    }

    @Test
    fun theSameRoutineBackIsReportedAsNoChange() = runTest {
        val current = listOf(row(bench), row(ohp), row(pushdown))

        val last = refine(FakeLlmInferenceService(chunks = listOf("1, 2, 3")), current).last()

        assertTrue((last as RoutineRefineState.Failed).message.contains("misma rutina"))
    }

    @Test
    fun aBlankInstructionAsksForOneWithoutCallingTheModel() = runTest {
        val llm = FakeLlmInferenceService(chunks = listOf("1"))

        val last = refine(llm, listOf(row(bench)), instruction = "   ").last()

        assertTrue(last is RoutineRefineState.Failed)
        assertTrue("the model must not be asked", llm.prompts.isEmpty())
    }

    @Test
    fun theRefinePromptCarriesTheRequestAndTheCurrentSession() = runTest {
        val llm = FakeLlmInferenceService(chunks = listOf("1"))

        refine(llm, listOf(row(bench), row(ohp)), instruction = "sin press militar")

        val prompt = llm.prompts.single()
        assertTrue(prompt, prompt.contains("«sin press militar»"))
        assertTrue(prompt, prompt.contains("Sesión actual: 1, 2"))
        assertEquals(RoutineRefinePromptBuilder.TEMPERATURE, llm.lastTemperature)
    }

    @Test
    fun withNoHistoryThereIsNothingToExplain() = runTest {
        assertTrue(draftOf(generate(unusable)).adjustments.isEmpty())
    }

    private fun draftOf(states: List<RoutineDraftState>): RoutineDraft {
        val last = states.last()
        assertTrue("expected Success, was $last", last is RoutineDraftState.Success)
        return (last as RoutineDraftState.Success).draft
    }

    @Test
    fun theModelsPickBecomesAnAiDraftPrescribedByGoal() = runTest {
        // Shortlist order is round-robin: bench(1), ohp(2), pushdown(3), lateral(4).
        val states = generate(FakeLlmInferenceService(chunks = listOf("1, ", "2, 3, 4")))

        // Says what it's doing while it works: reading history, then waiting on the model.
        assertEquals(
            listOf(RoutineDraftState.Loading.Phase.READING_HISTORY, RoutineDraftState.Loading.Phase.ASKING_MODEL),
            states.filterIsInstance<RoutineDraftState.Loading>().map { it.phase },
        )
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
