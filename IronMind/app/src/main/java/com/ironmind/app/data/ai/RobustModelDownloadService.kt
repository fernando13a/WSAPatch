package com.ironmind.app.data.ai

import com.ironmind.app.domain.model.ModelDownloadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [ModelDownloader] with the metered-connection check: the model is ~550 MB, so the user is
 * asked before it goes over a connection that bills for it.
 *
 * Downloads run in-process and resume via HTTP Range on the next attempt. An earlier version
 * handed the metered path to a WorkManager worker for background persistence, but that worker
 * could never be instantiated (it took a constructor argument no WorkerFactory supplied), reported
 * failures to WorkManager as successes, and nothing observed its result — so the screen hung
 * forever on "downloading". The direct path is what actually worked, so it is the only one now.
 */
@Singleton
class RobustModelDownloadService @Inject constructor(
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

    /** The user accepted the data cost; download over the metered connection. */
    fun confirmAndDownload(url: String): Flow<ModelDownloadState> = downloader.download(url)
}
