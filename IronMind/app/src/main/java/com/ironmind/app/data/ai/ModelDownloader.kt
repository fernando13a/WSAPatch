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

    fun isReady(): Boolean = AiConstants.modelFile(context).let {
        it.exists() && it.length() >= AiConstants.MIN_PLAUSIBLE_MODEL_BYTES
    }

    /** Size of the model on disk in bytes (0 if not present). For "free up space" UI. */
    fun modelSizeBytes(): Long = AiConstants.modelFile(context).let { if (it.exists()) it.length() else 0L }

    /** Deletes the model (and any partial download) to reclaim storage. Returns true if a file went. */
    fun deleteModel(): Boolean {
        val dest = AiConstants.modelFile(context)
        val part = java.io.File(dest.parentFile, dest.name + ".part")
        val partGone = part.delete()
        val destGone = dest.delete()
        return partGone || destGone
    }

    fun download(url: String): Flow<ModelDownloadState> = flow {
        emit(ModelDownloadState.Downloading(null))

        val dest = AiConstants.modelFile(context)
        dest.parentFile?.mkdirs()
        val part = java.io.File(dest.parentFile, dest.name + ".part")

        // Resume a previous partial download by asking the server for the remaining byte range.
        val alreadyHave = if (part.exists()) part.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            if (alreadyHave > 0) setRequestProperty("Range", "bytes=$alreadyHave-")
        }
        connection.connect()
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("HTTP $code")
        }
        // 206 == server honored the Range and is sending only the tail; anything else is a full body,
        // so a stale .part must be discarded and the download restarted from zero.
        val append = code == HttpURLConnection.HTTP_PARTIAL && alreadyHave > 0
        if (!append && part.exists()) part.delete()

        val reported = connection.contentLengthLong
        val total = if (append && reported > 0) alreadyHave + reported else reported
        connection.inputStream.use { input ->
            java.io.FileOutputStream(part, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = if (append) alreadyHave else 0L
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

                // A dropped connection ends the stream with -1 rather than an exception, so
                // without this check a truncated file would be renamed into place and reported
                // Ready — and only fail much later, inside the inference engine, as an opaque
                // "Error building tflite model". The .part is kept so the next attempt resumes.
                if (total > 0 && downloaded != total) {
                    throw IllegalStateException(
                        "Descarga incompleta (${downloaded / 1_000_000} MB de ${total / 1_000_000} MB). " +
                            "Vuelve a intentarlo para reanudarla.",
                    )
                }
                // Chunked responses report no length, so there is nothing to compare against and
                // a truncation here is indistinguishable from a clean finish. Verify the server
                // agrees on the size before trusting it.
                if (total <= 0) {
                    val actual = part.length()
                    val expected = headContentLength(url)
                    if (expected > 0 && actual != expected) {
                        throw IllegalStateException(
                            "Descarga incompleta (${actual / 1_000_000} MB de " +
                                "${expected / 1_000_000} MB). Vuelve a intentarlo para reanudarla.",
                        )
                    }
                }
            }
        }
        connection.disconnect()

        // Guards against a body that completed but isn't a model at all (an error page served
        // with 200, say): no real bundle is this small.
        if (part.length() < AiConstants.MIN_PLAUSIBLE_MODEL_BYTES) {
            part.delete()
            throw IllegalStateException(
                "El archivo descargado no parece un modelo válido (${part.length() / 1_000} KB). " +
                    "Revisa la URL.",
            )
        }

        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
        emit(ModelDownloadState.Ready)
    }.catch { throwable ->
        emit(ModelDownloadState.Error(throwable.message ?: "Error de descarga"))
    }.flowOn(dispatchers.io)

    /** The server's size for [url], or -1 when it won't say. Used only to verify a chunked body. */
    private fun headContentLength(url: String): Long = runCatching {
        val head = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            head.connect()
            if (head.responseCode in 200..299) head.contentLengthLong else -1L
        } finally {
            head.disconnect()
        }
    }.getOrDefault(-1L)

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

        if (part.length() < AiConstants.MIN_PLAUSIBLE_MODEL_BYTES) {
            part.delete()
            throw IllegalStateException(
                "Ese archivo no parece un modelo válido (${part.length() / 1_000} KB). " +
                    "Debe ser un .task o .bin de MediaPipe.",
            )
        }

        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
        emit(ModelDownloadState.Ready)
    }.catch { throwable ->
        emit(ModelDownloadState.Error(throwable.message ?: "Error al importar"))
    }.flowOn(dispatchers.io)
}
