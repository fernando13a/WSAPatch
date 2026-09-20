package com.ironmind.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironmind.app.FakeAppPreferences
import com.ironmind.app.FakeLlmInferenceService
import com.ironmind.app.FakeWorkoutRepository
import com.ironmind.app.R
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.domain.model.Routine
import com.ironmind.app.domain.model.RoutinePlan
import com.ironmind.app.domain.model.RoutineSplit
import com.ironmind.app.domain.model.SessionDetail
import com.ironmind.app.domain.model.WorkoutSession
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.usecase.GetProgressionSuggestionUseCase
import com.ironmind.app.domain.usecase.GetTrainingInsightsUseCase
import com.ironmind.app.ui.dashboard.DashboardScreen
import com.ironmind.app.ui.dashboard.DashboardViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * UI tests for DashboardScreen. [DashboardScreen] takes a real [DashboardViewModel] (defaulting
 * to `hiltViewModel()`), so tests build one directly from the shared fakes in `src/testShared`,
 * the same pattern [SessionScreenTest] uses.
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun viewModel(repo: FakeWorkoutRepository = FakeWorkoutRepository()): DashboardViewModel {
        val llm = FakeLlmInferenceService()
        return DashboardViewModel(
            repo,
            GetProgressionSuggestionUseCase(repo, llm),
            GetTrainingInsightsUseCase(repo, llm),
            llm,
            FakeAppPreferences(),
        )
    }

    private fun setDashboard(repo: FakeWorkoutRepository = FakeWorkoutRepository()) {
        composeTestRule.setContent {
            DashboardScreen(
                onStartSession = {},
                onOpenProgress = {},
                onNewRoutine = {},
                onGenerateRoutine = {},
                onEditRoutine = {},
                onDownloadModel = {},
                onOpenBackup = {},
                viewModel = viewModel(repo),
            )
        }
    }

    @Test
    fun displaysHeaderAndCoachTitle() {
        setDashboard()

        composeTestRule.onNodeWithText(context.getString(R.string.dash_engine_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.ai_coach_title)).assertIsDisplayed()
    }

    @Test
    fun displaysStreakWhenGreaterThanZero() {
        val now = System.currentTimeMillis()
        val dayMillis = TimeUnit.DAYS.toMillis(1)
        val repo = FakeWorkoutRepository().apply {
            // 3 consecutive days (today, yesterday, day before) for a streak of 3, plus 2 more
            // sessions far enough in the past to not extend that streak — so totalSessions (5)
            // and streak (3) land on different numbers and don't collide in onNodeWithText("3").
            val streakSessions = (0..2).map { daysAgo ->
                SessionDetail(
                    session = WorkoutSession(id = daysAgo.toLong() + 1, startedAt = now - daysAgo * dayMillis),
                    sets = emptyList(),
                )
            }
            val olderSessions = listOf(10L, 11L).map { daysAgo ->
                SessionDetail(
                    session = WorkoutSession(id = daysAgo + 100, startedAt = now - daysAgo * dayMillis),
                    sets = emptyList(),
                )
            }
            sessionDetailsFlow.value = streakSessions + olderSessions
        }

        setDashboard(repo)

        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun displaysStartFreeSessionButton() {
        setDashboard()

        val buttonText = context.getString(R.string.start_free_session)
        composeTestRule.onNodeWithTag("dashboardList").performScrollToNode(hasText(buttonText))
        composeTestRule.onNodeWithText(buttonText).assertIsDisplayed()
    }

    @Test
    fun displaysRoutineCards() {
        val sampleExercise = Exercise(
            id = 1,
            name = "Barbell Bench Press",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
        )
        val routine = Routine(id = 1, name = "Push Day", split = RoutineSplit.PUSH)
        val repo = FakeWorkoutRepository().apply {
            routinePlansFlow.value = listOf(RoutinePlan(routine = routine, exercises = listOf(sampleExercise)))
        }

        setDashboard(repo)

        composeTestRule.onNodeWithTag("dashboardList").performScrollToNode(hasText("Push Day"))
        composeTestRule.onNodeWithText("Push Day").assertIsDisplayed()
    }
}
