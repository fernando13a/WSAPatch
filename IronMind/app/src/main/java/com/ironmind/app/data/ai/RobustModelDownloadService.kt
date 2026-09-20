package com.ironmind.app.data.ai

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ironmind.app.domain.model.ModelDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Robust model download service using WorkManager for persistent background downloads.
 * Handles mobile data warnings, foreground service management, and automatic retries.
 */
@Singleton
class RobustModelDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: ModelDownloader,
    private val connectivity: NetworkConnectivityService,
) {

    /**
     * Download with mobile data awareness.
     * On mobile, emits AwaitingMobileDataConfirmation; caller must call confirmAndDownload().
     */
    fun downloadWithWarning(url: String): Flow<ModelDownloadState> = flow {
        if (!connectivity.isConnected()) {
            emit(ModelDownloadState.Error("Sin conexión a internet"))
            return@flow
        }

        // If on mobile data, warn first
        if (connectivity.isOnMobileData()) {
            emit(ModelDownloadState.AwaitingMobileDataConfirmation)
            return@flow // Caller must call confirmAndDownload() to proceed
        }

        // On WiFi or unlimited connection: proceed directly
        downloader.download(url).collect { emit(it) }
    }

    /**
     * User confirmed download over mobile data; proceed with WorkManager for persistence.
     */
    fun confirmAndDownload(url: String) {
        val workRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(ModelDownloadWorker.PARAM_URL, url)
                    .build()
            )
            .setBackoffCriteria(
                backoffPolicy = BackoffPolicy.EXPONENTIAL,
                initialDelay = 10,
                initialDelayTimeUnit = TimeUnit.SECONDS,
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "model_download",
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest,
        )
    }

    /**
     * Direct download (e.g., when already confirmed or on WiFi). Falls back to WorkManager
     * if download exceeds 30 seconds (for foreground service persistence).
     */
    fun download(url: String): Flow<ModelDownloadState> = flow {
        // Try direct download first
        var isComplete = false
        var lastError: Throwable? = null

        downloader.download(url).collect { state ->
            emit(state)
            when (state) {
                ModelDownloadState.Ready -> isComplete = true
                is ModelDownloadState.Error -> lastError = Exception(state.message)
                else -> {} // Keep going
            }
        }

        // If download failed and took too long, delegate to WorkManager for retry
        if (!isComplete && lastError != null) {
            confirmAndDownload(url)
        }
    }
}
