package com.ironmind.app.data.ai

import java.io.File
import java.security.MessageDigest

/**
 * Answers "is the model file on this device the one we expect?".
 *
 * Size alone cannot answer it. The bundle is 554,661,246 bytes, which integer-divides to the same
 * "528 MB" as a file truncated anywhere in the last megabyte, and a resumed download that appended
 * the wrong range can land on exactly the right length with the wrong bytes inside. The native
 * engine then fails deep in `model_data.cc` with "Error building tflite model", which is
 * indistinguishable from the model simply being unsupported — so the two were being confused for
 * each other across several rounds of debugging.
 */
object ModelIntegrity {

    /** What [check] concluded about a model file. */
    enum class Verdict {
        /** Nothing at that path. */
        MISSING,

        /** Present, but not the length of the default bundle — almost always a partial download. */
        WRONG_SIZE,

        /** Right length, wrong bytes. Re-downloading is the fix; nothing else will be. */
        CORRUPT,

        /** Byte-for-byte the bundle we published. A load failure here is the engine's, not the file's. */
        INTACT,

        /** Right length but no reference hash compiled in, so content can't be judged. */
        UNVERIFIABLE,
    }

    /**
     * Hashes [file] in 1 MiB blocks. Half a gigabyte takes a few seconds on a phone, so callers
     * keep this off the main thread and off the hot path — it runs after a download completes and
     * when the engine has already failed, never on every launch.
     */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Cheap checks first: a missing file and a wrong length are decided without reading 554 MB.
     * The hash only runs once the length already matches, which is the only case it can settle.
     */
    fun check(
        file: File,
        // Parameters rather than constants read inline so the branches are reachable from a test
        // without writing half a gigabyte to disk.
        expectedBytes: Long = AiConstants.EXPECTED_MODEL_BYTES,
        expectedSha256: String = AiConstants.EXPECTED_MODEL_SHA256,
    ): Verdict {
        if (!file.exists()) return Verdict.MISSING
        if (file.length() != expectedBytes) return Verdict.WRONG_SIZE
        if (expectedSha256.isBlank()) return Verdict.UNVERIFIABLE
        return if (sha256(file).equals(expectedSha256, ignoreCase = true)) {
            Verdict.INTACT
        } else {
            Verdict.CORRUPT
        }
    }
}
