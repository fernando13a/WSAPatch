package com.ironmind.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import com.ironmind.app.domain.model.Equipment
import com.ironmind.app.domain.model.MuscleGroup
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.theme.IronMindTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Snapshot tests for core UI components using Roborazzi.
 * Captures visual state for regression detection.
 */
@RunWith(AndroidJUnit4::class)
class ComponentSnapshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule(
        captureRoot = true,
        roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(
            SizeSpec.CurrentSize,
        ),
    )

    @Test
    fun snapshotGlassCard() {
        composeTestRule.setContent {
            IronMindTheme {
                Box(
                    modifier = Modifier
                        .background(Color.Black)
                        .padding(16.dp)
                ) {
                    GlassCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Glass Card Component", color = Color.White)
                            Text("Kinetic Glass Obsidian design", color = Color.Gray)
                        }
                    }
                }
            }
        }

        roborazziRule.captureRoboImage()
    }

    @Test
    fun snapshotExerciseInfo() {
        composeTestRule.setContent {
            IronMindTheme {
                Box(
                    modifier = Modifier
                        .background(Color.Black)
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Barbell Bench Press", color = Color.White)
                        Text("Chest • Barbell", color = Color.Gray)
                    }
                }
            }
        }

        roborazziRule.captureRoboImage()
    }
}

// Roborazzi size spec for snapshot size
sealed interface SizeSpec {
    object CurrentSize : SizeSpec
}
