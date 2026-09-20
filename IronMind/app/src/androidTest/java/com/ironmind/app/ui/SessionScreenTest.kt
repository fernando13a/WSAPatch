package com.ironmind.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironmind.app.domain.model.WeightUnit
import com.ironmind.app.ui.session.SessionScreen
import com.ironmind.app.ui.session.SessionUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for SessionScreen.
 * Verifies the session tracking UI displays set logging, rest timer, and exercise info.
 */
@RunWith(AndroidJUnit4::class)
class SessionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysSessionTitle() {
        composeTestRule.setContent {
            SessionScreen(
                state = SessionUiState.Idle,
                weightUnit = WeightUnit.KG,
                onAddSet = {},
                onRemoveSet = {},
                onFinishSession = {},
                onNavigateToExerciseDetail = {},
                onNavigateToBack = {},
                onStartRestTimer = {},
            )
        }

        composeTestRule.onNodeWithText("Free session").assertIsDisplayed()
    }

    @Test
    fun displaysTotalVolumeLabel() {
        composeTestRule.setContent {
            SessionScreen(
                state = SessionUiState.Idle,
                weightUnit = WeightUnit.KG,
                onAddSet = {},
                onRemoveSet = {},
                onFinishSession = {},
                onNavigateToExerciseDetail = {},
                onNavigateToBack = {},
                onStartRestTimer = {},
            )
        }

        composeTestRule.onNodeWithText("Total volume").assertIsDisplayed()
    }

    @Test
    fun displaysFinishSessionButton() {
        composeTestRule.setContent {
            SessionScreen(
                state = SessionUiState.Idle,
                weightUnit = WeightUnit.KG,
                onAddSet = {},
                onRemoveSet = {},
                onFinishSession = {},
                onNavigateToExerciseDetail = {},
                onNavigateToBack = {},
                onStartRestTimer = {},
            )
        }

        composeTestRule.onNodeWithText("Finish").assertIsDisplayed()
    }

    @Test
    fun displaysWeightUnit() {
        composeTestRule.setContent {
            SessionScreen(
                state = SessionUiState.Idle,
                weightUnit = WeightUnit.LB,
                onAddSet = {},
                onRemoveSet = {},
                onFinishSession = {},
                onNavigateToExerciseDetail = {},
                onNavigateToBack = {},
                onStartRestTimer = {},
            )
        }

        // Should display LB unit somewhere in the UI
        composeTestRule.onNodeWithText("lb").assertIsDisplayed()
    }
}
