package com.ironmind.app.data.ai

import java.io.File

/**
 * Owns the model files under `filesDir/models/` and, above all, their names.
 *
 * The name is not cosmetic. MediaPipe picks its loader from the path's extension: a `.task` is
 * opened as a zip bundle, while `.bin` and `.tflite` are read as a bare TFLite flatbuffer. The app
 * used to store every model as `gemma-2b-it-int4.bin`, so the Gemma 3 `.task` bundle it downloads
 * was handed to the flatbuffer loader whole, zip headers and all, and every load died with
 * "Error building tflite model." — from a file that was intact the entire time. The engine never
 * opened the archive, which is also why checking the bytes inside it could not explain the failure.
 *
 * So a finished download is named after what it actually contains, not after where it came from.
 *
 * Plain [File] operations on a directory passed in, so all of it runs in a JVM unit test.
 */
class ModelStore(private val dir: File) {

    /** A zip bundle — the format the default model ships in. */
    val bundle: File get() = File(dir, BUNDLE_NAME)

    /** A bare TFLite flatbuffer, for a model someone imports in the older single-file format. */
    val raw: File get() = File(dir, RAW_NAME)

    /** Where a download or an import accumulates until it has been checked. */
    val part: File get() = File(dir, PART_NAME)

    /**
     * The model to load, or null when none is installed. Renames a file left by an older build
     * first, so upgrading keeps the half-gigabyte already on the device instead of stranding it.
     */
    fun installed(): File? {
        migrateLegacy()
        return listOf(bundle, raw).firstOrNull { it.exists() }
            // Only reached if the rename below failed; loading it is no worse than before.
            ?: legacy.takeIf { it.exists() }
    }

    /**
     * Moves a finished, verified [from] into place under the name its contents call for, and
     * removes a model of the other format so [installed] can never pick a stale one.
     */
    fun install(from: File): File {
        dir.mkdirs()
        val target = if (isTaskBundle(from)) bundle else raw
        (if (target == bundle) raw else bundle).delete()
        if (!from.renameTo(target)) {
            from.copyTo(target, overwrite = true)
            from.delete()
        }
        return target
    }

    /** Deletes every model and partial file this store knows about. True if anything went. */
    fun deleteAll(): Boolean =
        listOf(bundle, raw, part, legacy, legacyPart).map { it.delete() }.any { it }

    private val legacy: File get() = File(dir, LEGACY_NAME)
    private val legacyPart: File get() = File(dir, "$LEGACY_NAME.part")

    private fun migrateLegacy() {
        // A partial download from an older build can't be trusted to resume: that downloader let
        // two runs append into the same .part, which is exactly how right-length, wrong-content
        // files were made. Starting over is cheaper than debugging another one.
        legacyPart.delete()

        if (!legacy.exists()) return
        if (bundle.exists() || raw.exists()) {
            // Only older builds write the legacy name, so the other file is the newer one.
            legacy.delete()
            return
        }
        legacy.renameTo(if (isTaskBundle(legacy)) bundle else raw)
    }

    companion object {
        const val BUNDLE_NAME = "gemma3-1b-it-int4.task"
        const val RAW_NAME = "model.bin"
        const val PART_NAME = "model.download.part"

        /** What every build before this one stored the model as, whatever format it was. */
        const val LEGACY_NAME = "gemma-2b-it-int4.bin"

        private val ZIP_LOCAL_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK\u0003\u0004"

        /**
         * Whether [file] is a zip-based `.task` bundle rather than a bare flatbuffer.
         *
         * Looks for the zip local-file header near the start rather than exactly at offset 0:
         * Google's bundler writes four zero bytes first so the TFLite payload that follows the
         * 30-byte header and 22-byte entry name lands on an 8-byte boundary (52 + 4 = 56) and can
         * be memory-mapped in place. A strict offset-0 check would call the real bundle "raw".
         */
        fun isTaskBundle(file: File): Boolean {
            if (!file.exists()) return false
            val head = ByteArray(64)
            val read = file.inputStream().use { it.read(head) }
            if (read < ZIP_LOCAL_HEADER.size) return false
            return (0..read - ZIP_LOCAL_HEADER.size).any { start ->
                ZIP_LOCAL_HEADER.indices.all { head[start + it] == ZIP_LOCAL_HEADER[it] }
            }
        }
    }
}
