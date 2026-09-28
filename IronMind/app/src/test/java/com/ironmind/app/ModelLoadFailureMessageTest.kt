package com.ironmind.app

import com.ironmind.app.data.ai.AiConstants
import com.ironmind.app.data.ai.ModelIntegrity.Verdict
import com.ironmind.app.data.ai.modelLoadFailureMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wording is the whole point of this function. The native engine reports an unsupported bundle
 * and a damaged one with the identical RET_CHECK trace, so the message is the only thing that can
 * tell someone whether re-downloading half a gigabyte will help. An earlier version guessed, and
 * guessed wrong in both directions: it blamed the download for a file that was intact, then
 * vouched for a file whose contents were scrambled.
 *
 * Assertions avoid the bare substring "completo", since "incompleto" contains it and would match
 * either branch.
 */
class ModelLoadFailureMessageTest {

    private val redownloadAdvice = "«Borrar modelo»"
    private val fileIsFine = "completo y verificado"
    private val size = AiConstants.EXPECTED_MODEL_BYTES

    @Test
    fun anIntactModelIsNotBlamedOnTheDownload() {
        val message = modelLoadFailureMessage(Verdict.INTACT, size, "RET_CHECK failure")

        assertTrue("should clear the file, was: $message", message.contains(fileIsFine))
        assertFalse("re-downloading it would change nothing, was: $message", message.contains(redownloadAdvice))
    }

    /**
     * Right length, wrong bytes — every size check passes and the file is still ruined. This is the
     * one case where deleting and re-downloading is the actual fix, so it has to say so.
     */
    @Test
    fun aCorruptModelIsSentBackToTheDownloadScreen() {
        val message = modelLoadFailureMessage(Verdict.CORRUPT, size, "RET_CHECK failure")

        assertTrue("should name the delete action, was: $message", message.contains(redownloadAdvice))
        assertFalse("must not vouch for the file, was: $message", message.contains(fileIsFine))
    }

    @Test
    fun aTruncatedModelReportsBothSizes() {
        val message = modelLoadFailureMessage(Verdict.WRONG_SIZE, 400L * 1024 * 1024, null)

        assertTrue("should show what it has, was: $message", message.contains("400 MB"))
        assertTrue("should show what it expected, was: $message", message.contains("528 MB"))
        assertTrue(message.contains(redownloadAdvice))
    }

    @Test
    fun aMissingModelAsksForADownloadRatherThanADelete() {
        val message = modelLoadFailureMessage(Verdict.MISSING, 0, null)

        assertFalse("nothing to delete, was: $message", message.contains(redownloadAdvice))
        assertTrue(message.contains("descárgalo"))
    }

    /** Without a reference hash the file can't be judged, so it gets the benefit of the doubt. */
    @Test
    fun anUnverifiableModelIsTreatedLikeAnIntactOne() {
        val message = modelLoadFailureMessage(Verdict.UNVERIFIABLE, size, "RET_CHECK failure")

        assertFalse(message.contains(redownloadAdvice))
    }

    /**
     * The real trace: the first 140 characters are the RET_CHECK header and the source path, and
     * the old cap landed on the word right before the reason. Everything up to the cap must survive.
     */
    @Test
    fun theNativeReasonSurvivesPastTheHeaderAndPath() {
        val trace = "Failed to initialize engine: %sINTERNAL: RET_CHECK failure " +
            "(third_party/odml/infra/genai/inference/utils/llm_utils/model_data.cc:424) " +
            "model\nError building tflite model"

        val message = modelLoadFailureMessage(Verdict.INTACT, size, trace)

        assertTrue("the reason was cut off again, was: $message", message.contains("Error building tflite model"))
        assertTrue(message.contains("model_data.cc:424"))
        assertFalse("newlines make the card unreadable, was: $message", message.contains("\n"))
    }

    @Test
    fun aBlankNativeMessageDoesNotLeaveADanglingLabel() {
        assertFalse(modelLoadFailureMessage(Verdict.INTACT, size, null).contains("Detalle:"))
        assertFalse(modelLoadFailureMessage(Verdict.INTACT, size, "  \n  ").contains("Detalle:"))
    }
}
