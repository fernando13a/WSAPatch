package com.ironmind.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironmind.app.R
import com.ironmind.app.ui.components.CircularRestTimer
import com.ironmind.app.ui.theme.IronMindTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for CircularRestTimer component.
 * Verifies the timer displays the formatted mm:ss remaining time and the running/idle labels.
 * [CircularRestTimer] is a pure display composable driven by (remaining, total) — it has no
 * onStop/onSelect* callbacks or preset buttons; that control surface doesn't exist in the app.
 */
@RunWith(AndroidJUnit4::class)
class CircularRestTimerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun displaysTimeRemaining() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(remaining = 45, total = 60)
            }
        }

        composeTestRule.onNodeWithText("00:45").assertIsDisplayed()
    }

    @Test
    fun displaysRestLabelWhenRunning() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(remaining = 30, total = 60)
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.rest_dial_active)).assertIsDisplayed()
    }

    @Test
    fun displaysReadyLabelWhenNotRunning() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(remaining = 0, total = 60)
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.rest_dial_ready)).assertIsDisplayed()
    }

    @Test
    fun formatsMinutesAndSecondsForLongerDurations() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(remaining = 90, total = 120)
            }
        }

        composeTestRule.onNodeWithText("01:30").assertIsDisplayed()
    }
}
