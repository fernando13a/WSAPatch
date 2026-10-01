package com.ironmind.app.domain.model

/** State of provisioning the on-device model. */
sealed interface ModelDownloadState {
    /** No model present yet and no download in progress. */
    data object Idle : ModelDownloadState

    /** Waiting for user confirmation to download over mobile data (~555 MB). */
    data object AwaitingMobileDataConfirmation : ModelDownloadState

    /** Download running; [progress] is 0f..1f (or null when the size is unknown). */
    data class Downloading(val progress: Float?) : ModelDownloadState

    /**
     * Bytes are all here; the checksum is being computed. Its own state because hashing half a
     * gigabyte takes seconds, and a progress bar frozen at 100% with no explanation is what makes
     * someone tap Download a second time — and two writers appending into one `.part` would
     * produce a file of the right length with scrambled contents.
     */
    data object Verifying : ModelDownloadState

    /** Model file present and ready for inference. */
    data object Ready : ModelDownloadState

    data class Error(val message: String) : ModelDownloadState
}
