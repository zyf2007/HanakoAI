package `fun`.kirari.hanako.overlay

import android.graphics.Bitmap
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.automation.BubbleState
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.ProcessingResult

internal val SheetDockOffset = 88.dp
internal val PanelHandleYOffset = 38.dp
internal const val SheetAnimationDurationMs = 260

internal enum class OverlaySheetMode {
    CROP,
    RESULT
}

internal enum class OverlayLaunchMode {
    NORMAL,
    AUTO
}

internal enum class AutoRunState {
    IDLE,
    RUNNING,
    COMPLETED
}

internal data class OverlayUiState(
    val settings: AppSettings = AppSettings(),
    val screenshot: Bitmap? = null,
    val selectedBitmap: Bitmap? = null,
    val liveOcrText: String = "",
    val liveAnswerText: String = "",
    val result: ProcessingResult? = null,
    val error: String? = null,
    val working: Boolean = false,
    val sheetVisible: Boolean = false,
    val sheetMode: OverlaySheetMode = OverlaySheetMode.CROP,
    val launchMode: OverlayLaunchMode = OverlayLaunchMode.NORMAL,
    val autoRunState: AutoRunState = AutoRunState.IDLE,
    val autoCopiedLabel: String? = null,
    val pendingVibrationLetters: String? = null,
    val bubbleState: BubbleState = BubbleState.Idle
)

