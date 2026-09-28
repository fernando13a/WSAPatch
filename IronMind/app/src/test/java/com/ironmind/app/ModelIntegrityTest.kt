package com.ironmind.app

import com.ironmind.app.data.ai.ModelIntegrity
import com.ironmind.app.data.ai.ModelIntegrity.Verdict
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelIntegrityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun fileOf(bytes: ByteArray): File =
        temp.newFile().apply { writeBytes(bytes) }

    /** Known vector, so a bug in the byte-to-hex formatting can't pass unnoticed. */
    @Test
    fun sha256MatchesTheKnownDigestForAbc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ModelIntegrity.sha256(fileOf("abc".toByteArray())),
        )
    }

    /** Bytes above 0x7f are negative in Kotlin; formatted naively they'd come out sign-extended. */
    @Test
    fun sha256HexIsUnsignedAndTwoDigitsPerByte() {
        val digest = ModelIntegrity.sha256(fileOf(ByteArray(64) { 0xFF.toByte() }))

        assertEquals(64, digest.length)
        assertEquals(digest, digest.lowercase())
        assertEquals(emptyList<Char>(), digest.filterNot { it.isDigit() || it in 'a'..'f' }.toList())
    }

    @Test
    fun aMissingFileIsMissing() {
        val absent = File(temp.root, "not-there.task")

        assertEquals(Verdict.MISSING, ModelIntegrity.check(absent, expectedBytes = 3))
    }

    @Test
    fun aShortFileIsWrongSizeAndIsNotHashed() {
        val file = fileOf("ab".toByteArray())

        assertEquals(Verdict.WRONG_SIZE, ModelIntegrity.check(file, expectedBytes = 3, expectedSha256 = "irrelevant"))
    }

    /**
     * The case that cost several debugging rounds: the length matches exactly, so every size check
     * passes, and only the content says the file is ruined.
     */
    @Test
    fun rightLengthWithWrongBytesIsCorrupt() {
        val expected = ModelIntegrity.sha256(fileOf("abc".toByteArray()))
        val scrambled = fileOf("abd".toByteArray()) // same length, different content

        assertEquals(
            Verdict.CORRUPT,
            ModelIntegrity.check(scrambled, expectedBytes = 3, expectedSha256 = expected),
        )
    }

    @Test
    fun theRealFileIsIntact() {
        val file = fileOf("abc".toByteArray())

        assertEquals(
            Verdict.INTACT,
            ModelIntegrity.check(file, expectedBytes = 3, expectedSha256 = ModelIntegrity.sha256(file)),
        )
    }

    @Test
    fun hashComparisonIgnoresCase() {
        val file = fileOf("abc".toByteArray())
        val upper = ModelIntegrity.sha256(file).uppercase()

        assertEquals(Verdict.INTACT, ModelIntegrity.check(file, expectedBytes = 3, expectedSha256 = upper))
    }

    @Test
    fun noReferenceHashMeansUnverifiableRatherThanCorrupt() {
        val file = fileOf("abc".toByteArray())

        assertEquals(Verdict.UNVERIFIABLE, ModelIntegrity.check(file, expectedBytes = 3, expectedSha256 = ""))
    }
}
