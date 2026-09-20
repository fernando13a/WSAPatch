package com.ironmind.app.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.ironmind.app.R
import com.ironmind.app.domain.model.ModelDownloadState
import kotlinx.coroutines.flow.first

/**
 * WorkManager Worker for persistent model downloads.
 * Runs in the background even if the app is closed; uses a foreground service to survive system
 * resource constraints and provides progress updates to a notification.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val modelDownloader: ModelDownloader,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(PARAM_URL)
            ?: return Result.failure()

        ensureNotificationChannel()
        setForegroundAsync(createForegroundInfo())

        return try {
            modelDownloader.download(url)
                .first { state ->
                    when (state) {
                        ModelDownloadState.Ready -> {
                            updateNotification(100)
                            true // Emit and stop
                        }
                        is ModelDownloadState.Downloading -> {
                            val percent = (state.progress?.times(100f)?.toInt()) ?: 0
                            updateNotification(percent)
                            false // Keep waiting
                        }
                        is ModelDownloadState.Error -> true // Stop on error
                        ModelDownloadState.Idle -> false // Keep waiting
                        ModelDownloadState.AwaitingMobileDataConfirmation -> true // Stop; caller must confirm
                    }
                }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = buildNotification(0)
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private suspend fun updateNotification(percent: Int) {
        setForegroundAsync(ForegroundInfo(NOTIFICATION_ID, buildNotification(percent)))
    }

    private fun buildNotification(percent: Int): Notification =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use app icon (adjust if needed)
            .setContentTitle("Descargando modelo de IA")
            .setContentText("$percent% completado")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .build()

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Descargas de modelo",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progreso de descarga del modelo de IA"
        }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    companion object {
        const val PARAM_URL = "model_url"
        const val NOTIFICATION_ID = 9991
        const val CHANNEL_ID = "model_downloads"
    }
}
