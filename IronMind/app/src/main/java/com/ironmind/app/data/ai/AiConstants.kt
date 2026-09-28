package com.ironmind.app.data.ai

import android.content.Context
import java.io.File

/** Configuration for the on-device MediaPipe LLM Inference engine. */
object AiConstants {

    /**
     * Model file name expected under `filesDir/models/`. It is just a container name — MediaPipe
     * identifies a bundle by its contents, not its extension — so it is deliberately left alone
     * even though [DEFAULT_MODEL_URL] now points at a Gemma 3 1B `.task`: renaming it would orphan
     * the half-gigabyte file already on every device that downloaded one. Any MediaPipe-compatible
     * bundle works. Keeping it out of the APK keeps the binary small while staying 100% offline at
     * runtime.
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
     * Exact byte count of the bundle [DEFAULT_MODEL_URL] serves. Used only to *word* a load
     * failure, never to reject a file: an imported model legitimately has a different size, and
     * the download path already verifies against the server's own Content-Length. Without it the
     * error message had to guess, and it guessed wrong — a complete 554,661,246-byte file prints
     * as "528 MB" once divided into MiB, which reads exactly like a truncated one.
     */
    const val EXPECTED_MODEL_BYTES = 554_661_246L

    /**
     * SHA-256 of that same bundle. The length is not enough on its own: a resumed download that
     * appended the wrong range — two taps on the download button writing into one `.part`, say —
     * lands on exactly [EXPECTED_MODEL_BYTES] with scrambled bytes inside, and the engine then
     * fails in `model_data.cc` with "Error building tflite model", which looks identical to the
     * model simply being unsupported. Verified against the published asset:
     * the inner TFLite parses as version 3, 770 subgraphs, signatures
     * decode / prefill_32 / prefill_128 / prefill_512 / prefill_1024.
     *
     * Blank disables content verification (see [ModelIntegrity.Verdict.UNVERIFIABLE]).
     */
    const val EXPECTED_MODEL_SHA256 = "ddfaf1210d8b4d1b812b5fadb6652999e852c8be6dd9abe353b9213a25262c10"

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
