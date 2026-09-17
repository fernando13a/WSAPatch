package com.ironmind.app.data.ai

/** Configuration for the on-device MediaPipe LLM Inference engine. */
object AiConstants {

    /**
     * Model file name expected under `filesDir/models/`. Any MediaPipe-compatible model works
     * (e.g. a Gemma `.bin`/`.task` bundle). The download/placement step is handled by the UI in
     * Stage 3; keeping it out of the APK keeps the binary small while staying 100% offline at
     * runtime. Adjust to match the model you ship.
     */
    const val MODEL_FILE_NAME = "gemma-2b-it-int4.bin"
    const val MODEL_SUBDIR = "models"

    // Inference / sampling parameters.
    const val MAX_TOKENS = 1024
    const val TOP_K = 40
    const val TEMPERATURE = 0.8f
}
