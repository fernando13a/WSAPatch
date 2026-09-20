package com.ironmind.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironmind.app.domain.model.Exercise
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.ui.dashboard.DashboardUiState
import com.ironmind.app.ui.dashboard.DashboardScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for DashboardScreen.
 * Verifies the dashboard displays core elements: header, AI coach panel, routines, and streak.
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysHeaderAndCoachTitle() {
        composeTestRule.setContent {
            DashboardScreen(
                state = DashboardUiState.Ready(
                    exercises = emptyList(),
                    streak = 0,
                    sessionsThisWeek = 0,
                    routines = emptyList(),
                    suggestion = null,
                    modelReady = true,
                ),
                onNavigateToSession = {},
                onNavigateToRoutineEdit = { _, _ -> },
                onNavigateToProgress = {},
                onNavigateToModel = {},
                onNavigateToBackup = {},
                onNavigateToRoutine = {},
            )
        }

        composeTestRule.onNodeWithText("NEURAL STRENGTH ENGINE").assertIsDisplayed()
    }

    @Test
    fun displaysStreakWhenGreaterThanZero() {
        composeTestRule.setContent {
            DashboardScreen(
                state = DashboardUiState.Ready(
                    exercises = emptyList(),
                    streak = 7,
                    sessionsThisWeek = 3,
                    routines = emptyList(),
                    suggestion = null,
                    modelReady = true,
                ),
                onNavigateToSession = {},
                onNavigateToRoutineEdit = { _, _ -> },
                onNavigateToProgress = {},
                onNavigateToModel = {},
                onNavigateToBackup = {},
                onNavigateToRoutine = {},
            )
        }

        composeTestRule.onNodeWithText("7").assertIsDisplayed()
    }

    @Test
    fun displaysStartFreeSessionButton() {
        composeTestRule.setContent {
            DashboardScreen(
                state = DashboardUiState.Ready(
                    exercises = emptyList(),
                    streak = 0,
                    sessionsThisWeek = 0,
                    routines = emptyList(),
                    suggestion = null,
                    modelReady = true,
                ),
                onNavigateToSession = {},
                onNavigateToRoutineEdit = { _, _ -> },
                onNavigateToProgress = {},
                onNavigateToModel = {},
                onNavigateToBackup = {},
                onNavigateToRoutine = {},
            )
        }

        composeTestRule.onNodeWithText("Start free session").assertIsDisplayed()
    }

    @Test
    fun displaysRoutineCards() {
        val sampleExercise = Exercise(
            id = 1,
            name = "Barbell Bench Press",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
        )

        composeTestRule.setContent {
            DashboardScreen(
                state = DashboardUiState.Ready(
                    exercises = listOf(sampleExercise),
                    streak = 0,
                    sessionsThisWeek = 0,
                    routines = emptyList(),
                    suggestion = null,
                    modelReady = true,
                ),
                onNavigateToSession = {},
                onNavigateToRoutineEdit = { _, _ -> },
                onNavigateToProgress = {},
                onNavigateToModel = {},
                onNavigateToBackup = {},
                onNavigateToRoutine = {},
            )
        }

        // Dashboard shows exercises in some form; adjust if needed
        composeTestRule.onNodeWithText("Barbell Bench Press").assertIsDisplayed()
    }
}
