package com.ironmind.app.data.ai

import android.content.Context
import android.net.Uri
import com.ironmind.app.core.util.DispatcherProvider
import com.ironmind.app.domain.model.ModelDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the on-device model to `filesDir/models/` over HTTPS (the only network use in the app;
 * inference itself is fully offline). Writes to a temporary `.part` file and renames on success so a
 * partial download is never mistaken for a ready model.
 */
@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    fun isReady(): Boolean = AiConstants.modelFile(context).exists()

    fun download(url: String): Flow<ModelDownloadState> = flow {
        emit(ModelDownloadState.Downloading(null))

        val dest = AiConstants.modelFile(context)
        dest.parentFile?.mkdirs()
        val part = java.io.File(dest.parentFile, dest.name + ".part")

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }

        val total = connection.contentLengthLong
        connection.inputStream.use { input ->
            part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                var lastPercent = -1
                var read = input.read(buffer)
                while (read >= 0) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val percent = (downloaded * 100 / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            emit(ModelDownloadState.Downloading(percent / 100f))
                        }
                    }
                    read = input.read(buffer)
                }
            }
        }
        connection.disconnect()

        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
        emit(ModelDownloadState.Ready)
    }.catch { throwable ->
        emit(ModelDownloadState.Error(throwable.message ?: "Error de descarga"))
    }.flowOn(dispatchers.io)

    /**
     * Copies a model the user already downloaded to their device (picked via the Storage Access
     * Framework) into `filesDir/models/`. Same `.part` → rename safety as [download], so an
     * interrupted copy never leaves a half-written model in place.
     */
    fun importFromFile(uri: Uri): Flow<ModelDownloadState> = flow {
        emit(ModelDownloadState.Downloading(null))

        val dest = AiConstants.modelFile(context)
        dest.parentFile?.mkdirs()
        val part = java.io.File(dest.parentFile, dest.name + ".part")

        context.contentResolver.openInputStream(uri)?.use { input ->
            part.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        } ?: throw IllegalStateException("No se pudo abrir el archivo")

        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
        emit(ModelDownloadState.Ready)
    }.catch { throwable ->
        emit(ModelDownloadState.Error(throwable.message ?: "Error al importar"))
    }.flowOn(dispatchers.io)
}
