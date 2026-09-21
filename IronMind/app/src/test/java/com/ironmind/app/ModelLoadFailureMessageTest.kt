package com.ironmind.app

import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.data.ai.modelLoadFailureMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wording here is the whole point of the function: the previous message told everyone whose
 * engine failed to delete a half-gigabyte file and download it again, including the people whose
 * file was complete and correct. These assertions pin the two cases apart.
 *
 * Note the assertions avoid the substring "completo" on its own — "incompleto" contains it, so it
 * matches both branches and would pass no matter which one ran.
 */
class ModelLoadFailureMessageTest {

    /** Phrase that only ever appears when the file is being blamed. */
    private val reDownloadAdvice = "«Borrar modelo»"

    @Test
    fun aCompleteModelIsNotBlamedOnTheDownload() {
        val message = modelLoadFailureMessage(AiConstants.EXPECTED_MODEL_BYTES, "RET_CHECK failure")

        assertTrue("should clear the file, was: $message", message.contains("El modelo está completo"))
        assertFalse("must not send them back to re-download, was: $message", message.contains(reDownloadAdvice))
    }

    @Test
    fun aTruncatedModelIsSentBackToTheDownloadScreen() {
        val message = modelLoadFailureMessage(400L * 1024 * 1024, "RET_CHECK failure")

        assertTrue("should name the delete action, was: $message", message.contains(reDownloadAdvice))
        assertTrue("should show the actual size, was: $message", message.contains("400 MB"))
        assertFalse("must not also claim it is fine, was: $message", message.contains("El modelo está completo"))
    }

    /**
     * The bug this function exists for: 554,661,246 bytes is 528.95 MiB, and integer division
     * prints that as "528" — the same number a file truncated at 528.0 MiB prints. Deciding on the
     * rounded megabytes called a complete model corrupt, so the branch must key off the byte count.
     */
    @Test
    fun aCompleteFileAndAShortOnePrintTheSameMegabytesAndStillDiffer() {
        val expected = AiConstants.EXPECTED_MODEL_BYTES
        val shortButSameMegabytes = 528L * 1024 * 1024 // 553,648,128 B — also prints "528 MB"

        assertTrue("fixture is wrong: it must be shorter", shortButSameMegabytes < expected)
        assertEquals(
            "fixture is wrong: both sizes must print the same MB for this test to mean anything",
            expected / (1024 * 1024),
            shortButSameMegabytes / (1024 * 1024),
        )

        assertTrue(modelLoadFailureMessage(expected, null).contains("El modelo está completo"))
        assertTrue(modelLoadFailureMessage(shortButSameMegabytes, null).contains(reDownloadAdvice))
    }

    @Test
    fun onlyTheFirstLineOfTheNativeTraceSurvives() {
        val trace = """
            RET_CHECK failure (mediapipe/tasks/cc/genai/inference/utils/llm_utils/model_data.cc:334)
            Error building tflite model
            === Source Location Trace: ===
        """.trimIndent()

        val message = modelLoadFailureMessage(AiConstants.EXPECTED_MODEL_BYTES, trace)

        assertTrue(message.contains("RET_CHECK failure"))
        assertFalse("the trace tail is noise to an athlete, was: $message", message.contains("Source Location"))
    }

    @Test
    fun aBlankNativeMessageDoesNotLeaveADanglingLabel() {
        assertFalse(modelLoadFailureMessage(AiConstants.EXPECTED_MODEL_BYTES, null).contains("Detalle:"))
        assertFalse(modelLoadFailureMessage(AiConstants.EXPECTED_MODEL_BYTES, "   \n  ").contains("Detalle:"))
    }
}
