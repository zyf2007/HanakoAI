package `fun`.kirari.hanako.localocr

import android.content.Context
import android.graphics.Bitmap

data class LocalOcrInstallStatus(
    val installed: Boolean,
    val state: String? = null
)

class LocalOcrManager(@Suppress("UNUSED_PARAMETER") private val context: Context) {
    companion object {
        const val LOG_TAG = "LocalOcr"
    }

    suspend fun installationStatus(): LocalOcrInstallStatus {
        return LocalOcrInstallStatus(
            installed = false,
            state = "NOT_AVAILABLE"
        )
    }

    suspend fun recognize(bitmap: Bitmap): String {
        throw UnsupportedOperationException("Local OCR is not available in lite version")
    }
}
