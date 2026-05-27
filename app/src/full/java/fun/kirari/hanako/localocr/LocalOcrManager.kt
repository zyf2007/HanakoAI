package `fun`.kirari.hanako.localocr

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import `fun`.kirari.hanako.debug.AppDebugLogStore

data class LocalOcrInstallStatus(
    val installed: Boolean,
    val state: String? = null
)

class LocalOcrManager(@Suppress("UNUSED_PARAMETER") private val context: Context) {
    companion object {
        const val LOG_TAG = "LocalOcr"
    }

    private fun recognizer(): TextRecognizer {
        return TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    suspend fun installationStatus(): LocalOcrInstallStatus {
        AppDebugLogStore.i(LOG_TAG, "installationStatus bundled=true")
        return LocalOcrInstallStatus(
            installed = true,
            state = "BUNDLED"
        )
    }

    suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = recognizer()
        return try {
            AppDebugLogStore.i(LOG_TAG, "recognize start bitmap=${bitmap.width}x${bitmap.height}")
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = Tasks.await(recognizer.process(image))
            buildString {
                result.textBlocks.forEachIndexed { index, block ->
                    if (index > 0) append('\n')
                    append(block.text)
                }
            }.trim().also {
                AppDebugLogStore.i(LOG_TAG, "recognize done textLength=${it.length}")
            }
        } catch (t: Throwable) {
            AppDebugLogStore.e(LOG_TAG, "recognize failed", t)
            throw t
        } finally {
            recognizer.close()
        }
    }
}
