package `fun`.kirari.hanako.automation

import android.graphics.Bitmap

/**
 * 悬浮球状态定义
 * 使用密封类确保状态的穷尽性检查
 */
sealed class BubbleState {
    /** 空闲状态 */
    data object Idle : BubbleState()

    /** 处理中状态（AI 正在处理） */
    data object Processing : BubbleState()

    /** 已复制状态（结果已复制到剪贴板） */
    data class Copied(val label: String?) : BubbleState()

    /** 显示字母状态（自动模式显示选项字母） */
    data class ShowingLetters(val letters: String) : BubbleState()

    /** 多页截图模式 - 等待点击 */
    data class MultiPageCapture(
        val capturedBitmaps: List<Bitmap> = emptyList(),
        val captureCount: Int = 0
    ) : BubbleState() {
        fun addCapture(bitmap: Bitmap): MultiPageCapture {
            return copy(
                capturedBitmaps = capturedBitmaps + bitmap,
                captureCount = captureCount + 1
            )
        }
    }

    /** 多页截图模式 - 正在截图 */
    data class MultiPageCapturing(
        val capturedBitmaps: List<Bitmap> = emptyList(),
        val captureCount: Int = 0
    ) : BubbleState()

    /** 多页截图模式 - 截图成功（短暂显示） */
    data class MultiPageCaptureSuccess(
        val capturedBitmaps: List<Bitmap> = emptyList(),
        val captureCount: Int = 0
    ) : BubbleState()

    /** 菜单展开状态（长按触发，记住之前的状态以便关闭后恢复） */
    data class MenuExpanded(
        val previousState: BubbleState,
        val anchorX: Int = 0,
        val anchorY: Int = 0
    ) : BubbleState()

    /** 错误状态（处理失败时短暂显示） */
    data class Error(val message: String? = null) : BubbleState()
}

/**
 * 悬浮球菜单项定义
 */
sealed class BubbleMenuItem {
    /** OCR/视觉模式切换 */
    data object ToggleRoute : BubbleMenuItem()
    /** 联网搜索开关 */
    data object ToggleSearch : BubbleMenuItem()
    /** 语音识别（占位） */
    data object VoiceRecognition : BubbleMenuItem()
    /** 设置 */
    data object Settings : BubbleMenuItem()
}

/**
 * 悬浮球事件定义
 * 所有可能触发状态转换的事件
 */
sealed class BubbleEvent {
    /** 开始处理（普通模式） */
    data object StartProcessing : BubbleEvent()

    /** 处理完成（带复制） */
    data class CopyComplete(val label: String) : BubbleEvent()

    /** 处理完成（带字母） */
    data class LettersComplete(val letters: String) : BubbleEvent()

    /** 单击事件 */
    data object SingleTap : BubbleEvent()

    /**
     * 长按事件
     * @param anchorX 气泡中心 X，用于菜单定位
     * @param anchorY 气泡中心 Y，用于菜单定位
     */
    data class LongPress(val anchorX: Int = 0, val anchorY: Int = 0) : BubbleEvent()

    /** 双击事件 */
    data object DoubleTap : BubbleEvent()

    /** 进入多图截图模式 */
    data object EnterMultiPageCapture : BubbleEvent()

    /** 取消处理事件 */
    data object CancelProcessing : BubbleEvent()

    /** 重置事件 */
    data object Reset : BubbleEvent()

    /** 超时事件 */
    data object Timeout : BubbleEvent()

    /** 开始截图事件 */
    data object CaptureStart : BubbleEvent()

    /** 截图完成 */
    data class CaptureTaken(val bitmap: Bitmap) : BubbleEvent()

    /** 截图失败事件 */
    data object CaptureFailed : BubbleEvent()

    /** 截图成功动画完成事件 */
    data object CaptureSuccessAnimationDone : BubbleEvent()

    /** 发送截图事件 */
    data object SendCaptures : BubbleEvent()

    /** 选择菜单项 */
    data class MenuSelect(val item: BubbleMenuItem) : BubbleEvent()

    /** 关闭菜单 */
    data object CloseMenu : BubbleEvent()

    /** 错误事件 */
    data class ErrorOccurred(val message: String?) : BubbleEvent()
}
