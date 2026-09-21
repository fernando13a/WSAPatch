package com.ironmind.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironmind.app.FakeAppPreferences
import com.ironmind.app.FakeLlmInferenceService
import com.ironmind.app.FakeRestTimerNotifier
import com.ironmind.app.FakeWorkoutRepository
import com.ironmind.app.R
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.domain.usecase.GetRecoveryAdviceUseCase
import com.ironmind.app.ui.navigation.Destinations
import com.ironmind.app.ui.session.SessionScreen
import com.ironmind.app.ui.session.SessionViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for SessionScreen. [SessionScreen] takes a real [SessionViewModel] (defaulting to
 * `hiltViewModel()`), so tests build one directly from the shared fakes in `src/testShared`
 * instead of needing Hilt test infrastructure (no HiltTestRunner/HiltAndroidRule exists in this
 * project) — the same pattern the JVM SessionViewModelTest uses, just also reachable from
 * androidTest now that src/testShared is wired into both source sets.
 */
@RunWith(AndroidJUnit4::class)
class SessionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun viewModel(
        repo: FakeWorkoutRepository = FakeWorkoutRepository(),
        appPreferences: FakeAppPreferences = FakeAppPreferences(),
    ): SessionViewModel {
        val llm = FakeLlmInferenceService()
        return SessionViewModel(
            repo,
            GetRecoveryAdviceUseCase(repo, llm),
            FakeRestTimerNotifier(),
            appPreferences,
            SavedStateHandle(mapOf(Destinations.ARG_SESSION_ID to 0L, Destinations.ARG_ROUTINE_ID to 0L)),
        )
    }

    @Test
    fun displaysFreeSessionTitleWhenNoRoutine() {
        composeTestRule.setContent {
            SessionScreen(onBack = {}, viewModel = viewModel())
        }

        composeTestRule.onNodeWithText(context.getString(R.string.session_free_title)).assertIsDisplayed()
    }

    @Test
    fun displaysTotalVolumeLabel() {
        composeTestRule.setContent {
            SessionScreen(onBack = {}, viewModel = viewModel())
        }

        composeTestRule.onNodeWithText(context.getString(R.string.session_total_volume)).assertIsDisplayed()
    }

    @Test
    fun displaysFinishSessionButton() {
        composeTestRule.setContent {
            SessionScreen(onBack = {}, viewModel = viewModel())
        }

        composeTestRule.onNodeWithText(context.getString(R.string.session_finish)).assertIsDisplayed()
    }

    @Test
    fun displaysSelectedWeightUnitInInputLabel() {
        val repo = FakeWorkoutRepository().apply {
            exercisesFlow.value = listOf(
                Exercise(id = 1, name = "Back Squat", muscleGroup = MuscleGroup.QUADS, equipment = Equipment.BARBELL),
            )
        }
        composeTestRule.setContent {
            SessionScreen(onBack = {}, viewModel = viewModel(repo, FakeAppPreferences(initialWeightUnit = WeightUnit.LB)))
        }

        val expectedLabel = context.getString(R.string.weight_input_label, context.getString(R.string.unit_lb))
        composeTestRule.onNodeWithText(expectedLabel).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun showsSelectExerciseHintWhenCatalogIsEmpty() {
        composeTestRule.setContent {
            SessionScreen(onBack = {}, viewModel = viewModel())
        }

        composeTestRule.onNodeWithText(context.getString(R.string.select_exercise)).performScrollTo().assertIsDisplayed()
    }
}
