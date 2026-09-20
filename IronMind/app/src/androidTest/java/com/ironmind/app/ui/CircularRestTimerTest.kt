package com.ironmind.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Verifies the timer exposes the formatted mm:ss remaining time and running/idle labels through
 * its accessibility content description. [CircularRestTimer] wraps its dial in
 * `clearAndSetSemantics { contentDescription = spoken }`, which replaces the whole subtree's
 * semantics with that single description — so the inner mm:ss/label Text nodes are not visible to
 * onNodeWithText, only onNodeWithContentDescription. It also has no onStop/onSelect* callbacks or
 * preset buttons; that control surface doesn't exist on this composable.
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

        val expected = context.getString(R.string.rest_dial_cd, "00:45")
        composeTestRule.onNodeWithContentDescription(expected).assertIsDisplayed()
    }

    @Test
    fun displaysRestLabelWhenRunning() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(remaining = 30, total = 60)
            }
        }

        val expected = context.getString(R.string.rest_dial_cd, "00:30")
        composeTestRule.onNodeWithContentDescription(expected).assertIsDisplayed()
    }

    @Test
    fun displaysReadyLabelWhenNotRunning() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(remaining = 0, total = 60)
            }
        }

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.rest_dial_ready)).assertIsDisplayed()
    }

    @Test
    fun formatsMinutesAndSecondsForLongerDurations() {
        composeTestRule.setContent {
            IronMindTheme {
                CircularRestTimer(remaining = 90, total = 120)
            }
        }

        val expected = context.getString(R.string.rest_dial_cd, "01:30")
        composeTestRule.onNodeWithContentDescription(expected).assertIsDisplayed()
    }
}
