package com.ironmind.app.data.ai

import android.content.Context
import java.io.File

/** Configuration for the on-device MediaPipe LLM Inference engine. */
object AiConstants {

    /**
     * Model file name expected under `filesDir/models/`. Any MediaPipe-compatible model works
     * (e.g. a Gemma `.bin`/`.task` bundle). The download/placement step is handled by the UI;
     * keeping it out of the APK keeps the binary small while staying 100% offline at runtime.
     */
    const val MODEL_FILE_NAME = "gemma-2b-it-int4.bin"
    const val MODEL_SUBDIR = "models"

    /**
     * Optional default download URL for the model. Leave blank and let the user paste one, or set
     * a direct-download URL to a MediaPipe-compatible model here. Downloading uses the network once;
     * inference afterwards is fully offline.
     */
    const val DEFAULT_MODEL_URL = ""

    // Inference / sampling parameters.
    const val MAX_TOKENS = 1024
    const val TOP_K = 40
    const val TEMPERATURE = 0.8f

    /** Absolute path where the model is expected/stored on this device. */
    fun modelFile(context: Context): File =
        File(File(context.filesDir, MODEL_SUBDIR), MODEL_FILE_NAME)
}
