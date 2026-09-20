package com.ironmind.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironmind.app.ui.components.CircularRestTimer
import com.ironmind.app.ui.theme.IronMindTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for CircularRestTimer component.
 * Verifies the timer displays time remaining and state labels correctly.
 */
@RunWith(AndroidJUnit4::class)
class CircularRestTimerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysTimeRemaining() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(
                    secondsRemaining = 45,
                    isRunning = true,
                    onStop = {},
                    onSelect60 = {},
                    onSelect90 = {},
                    onSelect120 = {},
                )
            }
        }

        composeTestRule.onNodeWithText("45").assertIsDisplayed()
    }

    @Test
    fun displaysRestLabelWhenRunning() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(
                    secondsRemaining = 30,
                    isRunning = true,
                    onStop = {},
                    onSelect60 = {},
                    onSelect90 = {},
                    onSelect120 = {},
                )
            }
        }

        composeTestRule.onNodeWithText("REST").assertIsDisplayed()
    }

    @Test
    fun displaysReadyLabelWhenNotRunning() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(
                    secondsRemaining = 0,
                    isRunning = false,
                    onStop = {},
                    onSelect60 = {},
                    onSelect90 = {},
                    onSelect120 = {},
                )
            }
        }

        composeTestRule.onNodeWithText("READY").assertIsDisplayed()
    }

    @Test
    fun displaysPresetButtons() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(
                    secondsRemaining = 60,
                    isRunning = false,
                    onStop = {},
                    onSelect60 = {},
                    onSelect90 = {},
                    onSelect120 = {},
                )
            }
        }

        // Timer should show preset buttons
        composeTestRule.onNodeWithText("60s").assertIsDisplayed()
        composeTestRule.onNodeWithText("90s").assertIsDisplayed()
        composeTestRule.onNodeWithText("120s").assertIsDisplayed()
    }
}
