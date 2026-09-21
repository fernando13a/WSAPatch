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
     * Floor for "this file could plausibly be a model". Real MediaPipe bundles run to hundreds of
     * megabytes, so anything under this is an error page served with a 200, or a download that
     * barely started — both of which would otherwise be stored as the model and only surface later
     * as an unreadable "Error building tflite model" from the native engine.
     */
    const val MIN_PLAUSIBLE_MODEL_BYTES = 20L * 1024 * 1024

    /**
     * Default download URL for the model — a public, direct-download link to a MediaPipe-compatible
     * `.task` (here, Gemma 3 1B int4 hosted as a GitHub Release asset). Downloading uses the network
     * once; inference afterwards is fully offline. Leave blank to require the user to paste one.
     */
    const val DEFAULT_MODEL_URL =
        "https://github.com/fernando13a/WSAPatch/releases/download/model-gemma3-1b/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"

    // Inference / sampling parameters.
    /** Total token budget for the engine — covers prompt AND response combined, not per-call. */
    const val MAX_TOKENS = 1024
    const val TOP_K = 40
    const val TEMPERATURE = 0.8f

    /**
     * Rough character budget every [com.ironmind.app.domain.ai] prompt builder should stay under.
     * Gemma tokenizes at roughly 4 chars/token; reserving ~300 tokens for the response leaves
     * ~700 tokens (~2800 chars) for the prompt itself within [MAX_TOKENS]. Prompt builders that
     * embed variable-length data (history, catalogs) must cap/truncate against this so the
     * response never gets silently cut off by the shared token ceiling.
     */
    const val PROMPT_CHAR_BUDGET = 2800

    /** Absolute path where the model is expected/stored on this device. */
    fun modelFile(context: Context): File =
        File(File(context.filesDir, MODEL_SUBDIR), MODEL_FILE_NAME)
}
