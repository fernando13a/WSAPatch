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
     * Default download URL for the model — a public, direct-download link to a MediaPipe-compatible
     * `.task` (here, Gemma 3 1B int4 hosted as a GitHub Release asset). Downloading uses the network
     * once; inference afterwards is fully offline. Leave blank to require the user to paste one.
     */
    const val DEFAULT_MODEL_URL =
        "https://github.com/fernando13a/WSAPatch/releases/download/model-gemma3-1b/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"

    // Inference / sampling parameters.
    const val MAX_TOKENS = 1024
    const val TOP_K = 40
    const val TEMPERATURE = 0.8f

    /** Absolute path where the model is expected/stored on this device. */
    fun modelFile(context: Context): File =
        File(File(context.filesDir, MODEL_SUBDIR), MODEL_FILE_NAME)
}
