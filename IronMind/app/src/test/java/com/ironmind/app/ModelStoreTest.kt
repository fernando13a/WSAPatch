package com.ironmind.app

import com.ironmind.app.data.ai.ModelStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val store by lazy { ModelStore(temp.root) }

    /** Starts the way Google's bundler writes one: four zero bytes, then the zip local header. */
    private val taskBundle = byteArrayOf(0, 0, 0, 0, 0x50, 0x4B, 0x03, 0x04) + "TF_LITE_PREFILL_DECODE".toByteArray()

    /** Starts the way a bare TFLite flatbuffer does: a root offset, then the "TFL3" identifier. */
    private val flatbuffer = byteArrayOf(0x20, 0, 0, 0) + "TFL3".toByteArray() + ByteArray(32)

    private fun write(name: String, bytes: ByteArray): File = File(temp.root, name).apply { writeBytes(bytes) }

    /**
     * The device this was debugged on: a valid `.task` stored under the old `.bin` name, which made
     * MediaPipe read the whole zip as a flatbuffer. Upgrading has to fix the name in place — the
     * file itself was fine, and re-downloading 554 MB would change nothing.
     */
    @Test
    fun aBundleLeftUnderTheOldBinNameIsRenamedToTask() {
        write(ModelStore.LEGACY_NAME, taskBundle)

        val installed = store.installed()

        assertEquals(ModelStore.BUNDLE_NAME, installed?.name)
        assertTrue(installed!!.name.endsWith(".task"))
        assertArrayEquals("renaming must not touch the contents", taskBundle, installed.readBytes())
        assertFalse(File(temp.root, ModelStore.LEGACY_NAME).exists())
    }

    @Test
    fun aRealFlatbufferLeftUnderTheOldNameStaysAFlatbuffer() {
        write(ModelStore.LEGACY_NAME, flatbuffer)

        assertEquals(ModelStore.RAW_NAME, store.installed()?.name)
    }

    @Test
    fun anOldPartialDownloadIsDiscardedRatherThanResumed() {
        write("${ModelStore.LEGACY_NAME}.part", taskBundle)

        store.installed()

        assertFalse(File(temp.root, "${ModelStore.LEGACY_NAME}.part").exists())
    }

    @Test
    fun whenBothExistTheNewNameWinsAndTheLegacyCopyIsFreed() {
        write(ModelStore.BUNDLE_NAME, taskBundle)
        write(ModelStore.LEGACY_NAME, taskBundle)

        assertEquals(ModelStore.BUNDLE_NAME, store.installed()?.name)
        assertFalse("half a gigabyte left behind", File(temp.root, ModelStore.LEGACY_NAME).exists())
    }

    @Test
    fun nothingInstalledIsNull() {
        assertNull(store.installed())
    }

    @Test
    fun installNamesADownloadByItsContent() {
        write(ModelStore.PART_NAME, taskBundle)
        assertEquals(ModelStore.BUNDLE_NAME, store.install(store.part).name)

        write(ModelStore.PART_NAME, flatbuffer)
        assertEquals(ModelStore.RAW_NAME, store.install(store.part).name)
    }

    @Test
    fun installingOneFormatRemovesTheOther() {
        write(ModelStore.RAW_NAME, flatbuffer)
        write(ModelStore.PART_NAME, taskBundle)

        store.install(store.part)

        assertFalse("a stale raw model could be picked up instead", store.raw.exists())
        assertEquals(ModelStore.BUNDLE_NAME, store.installed()?.name)
        assertFalse(store.part.exists())
    }

    /** Google's bundler pads the start so the payload is 8-byte aligned; offset 0 isn't "PK". */
    @Test
    fun theZipHeaderIsFoundPastTheAlignmentPadding() {
        assertTrue(ModelStore.isTaskBundle(write("padded.task", taskBundle)))
        assertTrue(ModelStore.isTaskBundle(write("plain.task", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3))))
        assertFalse(ModelStore.isTaskBundle(write("model.bin", flatbuffer)))
        assertFalse(ModelStore.isTaskBundle(write("tiny", byteArrayOf(0x50))))
        assertFalse(ModelStore.isTaskBundle(File(temp.root, "missing")))
    }

    @Test
    fun deleteAllClearsEveryNameIncludingTheLegacyOnes() {
        write(ModelStore.BUNDLE_NAME, taskBundle)
        write(ModelStore.RAW_NAME, flatbuffer)
        write(ModelStore.PART_NAME, taskBundle)
        write(ModelStore.LEGACY_NAME, taskBundle)
        write("${ModelStore.LEGACY_NAME}.part", taskBundle)

        assertTrue(store.deleteAll())
        assertEquals(emptyList<String>(), temp.root.list()!!.toList())
        assertFalse("nothing left to delete", store.deleteAll())
    }
}
