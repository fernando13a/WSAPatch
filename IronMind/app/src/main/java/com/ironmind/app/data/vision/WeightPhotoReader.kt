package com.ironmind.app.data.vision

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ironmind.app.core.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads the text printed on a photo of weights (plate markings, dumbbell stamps) using ML Kit's
 * on-device text recognizer. The Latin model is bundled into the APK, so this never touches the
 * network — the app stays 100% offline.
 */
@Singleton
class WeightPhotoReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Creates a private cache file for the camera app to write the capture into. */
    fun newCaptureUri(): Uri {
        val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Runs OCR over the image and returns the raw recognized text (empty when nothing is found). */
    suspend fun readText(uri: Uri): String = withContext(dispatchers.io) {
        val image = InputImage.fromFilePath(context, uri)
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result -> continuation.resume(result.text) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
    }

    private companion object {
        const val CAPTURE_DIR = "weight_photos"
    }
}
