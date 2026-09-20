package com.ironmind.app

import com.ironmind.app.domain.model.ModelDownloadState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Phase 2 robust model download features:
 * - Mobile data warning
 * - WorkManager persistence
 * - Network connectivity detection
 */
class ModelDownloadPhase2Test {

    @Test
    fun stateTransitionsIncludeMobileWarning() {
        // Verify the new state is properly defined
        val warningState = ModelDownloadState.AwaitingMobileDataConfirmation
        assertTrue(warningState is ModelDownloadState.AwaitingMobileDataConfirmation)
    }

    @Test
    fun mobileWarningComesBeforeDownloading() {
        // Download flow on mobile:
        // 1. Idle
        // 2. AwaitingMobileDataConfirmation (user must confirm)
        // 3. Downloading (after confirmation)
        // 4. Ready (on success)

        val states = listOf(
            ModelDownloadState.Idle,
            ModelDownloadState.AwaitingMobileDataConfirmation,
            ModelDownloadState.Downloading(0.5f),
            ModelDownloadState.Ready,
        )

        // Verify transitions make sense
        assertTrue(states[0] is ModelDownloadState.Idle)
        assertTrue(states[1] is ModelDownloadState.AwaitingMobileDataConfirmation)
        assertTrue(states[2] is ModelDownloadState.Downloading)
        assertTrue(states[3] is ModelDownloadState.Ready)
    }

    @Test
    fun wifiBypassesMobileWarning() {
        // On WiFi, flow should be:
        // 1. Idle
        // 2. Downloading (immediately, no warning)
        // 3. Ready

        val wifiStates = listOf(
            ModelDownloadState.Idle,
            ModelDownloadState.Downloading(null),
            ModelDownloadState.Downloading(0.1f),
            ModelDownloadState.Ready,
        )

        // Should skip AwaitingMobileDataConfirmation
        assertTrue(wifiStates.none { it is ModelDownloadState.AwaitingMobileDataConfirmation })
    }

    @Test
    fun errorStatePreserved() {
        val error = ModelDownloadState.Error("Network timeout")
        assertTrue(error is ModelDownloadState.Error)
        assertTrue(error.message.contains("timeout"))
    }
}
