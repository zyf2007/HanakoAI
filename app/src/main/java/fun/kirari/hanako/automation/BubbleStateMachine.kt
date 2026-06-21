package `fun`.kirari.hanako.automation

import android.graphics.Bitmap
import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮球状态机
 * 管理状态转换逻辑，确保状态转换的正确性
 */
class BubbleStateMachine {
    private val tag = "BubbleStateMachine"

    private val _state = MutableStateFlow<BubbleState>(BubbleState.Idle)
    val state: StateFlow<BubbleState> = _state.asStateFlow()

    /**
     * 获取当前状态
     */
    val currentState: BubbleState
        get() = _state.value

    /**
     * 分发事件，触发状态转换
     */
    fun dispatch(event: BubbleEvent) {
        val currentState = _state.value
        val newState = transition(currentState, event)

        if (currentState != newState) {
            AppDebugLogStore.i(
                tag,
                "State transition: ${currentState::class.simpleName} -> ${newState::class.simpleName} (event: ${event::class.simpleName})"
            )
            _state.value = newState
        }
    }

    /**
     * 强制设置状态（仅用于初始化或重置）
     */
    fun forceState(state: BubbleState) {
        AppDebugLogStore.i(tag, "Force state: ${state::class.simpleName}")
        _state.value = state
    }

    /**
     * 状态转换逻辑
     * 使用 when 表达式确保穷尽性检查
     */
    private fun transition(current: BubbleState, event: BubbleEvent): BubbleState {
        return when {
            // Idle 状态的转换
            current is BubbleState.Idle && event is BubbleEvent.StartProcessing ->
                BubbleState.Processing

            current is BubbleState.Idle && event is BubbleEvent.LongPress ->
                BubbleState.MenuExpanded(BubbleState.Idle, event.anchorX, event.anchorY)

            current is BubbleState.Idle && event is BubbleEvent.EnterMultiPageCapture ->
                BubbleState.MultiPageCapture()

            // ShowingLetters 状态的转换
            current is BubbleState.ShowingLetters && event is BubbleEvent.StartProcessing ->
                BubbleState.Processing

            current is BubbleState.ShowingLetters && event is BubbleEvent.LongPress ->
                BubbleState.MenuExpanded(current, event.anchorX, event.anchorY)

            current is BubbleState.ShowingLetters && event is BubbleEvent.EnterMultiPageCapture ->
                BubbleState.MultiPageCapture()

            current is BubbleState.ShowingLetters && event is BubbleEvent.Reset ->
                BubbleState.Idle

            // Processing 状态的转换
            current is BubbleState.Processing && event is BubbleEvent.CopyComplete ->
                BubbleState.Copied(event.label)

            current is BubbleState.Processing && event is BubbleEvent.LettersComplete ->
                BubbleState.ShowingLetters(event.letters)

            current is BubbleState.Processing && event is BubbleEvent.Timeout ->
                BubbleState.Idle

            current is BubbleState.Processing && event is BubbleEvent.CancelProcessing ->
                BubbleState.Idle

            // Processing 长按 -> 弹菜单
            current is BubbleState.Processing && event is BubbleEvent.LongPress ->
                BubbleState.MenuExpanded(current, event.anchorX, event.anchorY)

            // Processing 出错 -> Error 状态
            current is BubbleState.Processing && event is BubbleEvent.ErrorOccurred ->
                BubbleState.Error(event.message)

            // Copied 状态的转换
            current is BubbleState.Copied && event is BubbleEvent.SingleTap ->
                BubbleState.Idle

            current is BubbleState.Copied && event is BubbleEvent.Reset ->
                BubbleState.Idle

            current is BubbleState.Copied && event is BubbleEvent.LongPress ->
                BubbleState.MenuExpanded(current, event.anchorX, event.anchorY)

            current is BubbleState.Copied && event is BubbleEvent.EnterMultiPageCapture ->
                BubbleState.MultiPageCapture()

            // MultiPageCapture 状态的转换（等待点击）
            current is BubbleState.MultiPageCapture && event is BubbleEvent.CaptureStart ->
                BubbleState.MultiPageCapturing(current.capturedBitmaps, current.captureCount)

            current is BubbleState.MultiPageCapture && event is BubbleEvent.DoubleTap ->
                BubbleState.Idle

            current is BubbleState.MultiPageCapture && event is BubbleEvent.SendCaptures ->
                BubbleState.Processing

            // MultiPageCapturing 状态的转换（正在截图）
            current is BubbleState.MultiPageCapturing && event is BubbleEvent.CaptureTaken ->
                BubbleState.MultiPageCaptureSuccess(
                    current.capturedBitmaps + event.bitmap,
                    current.captureCount + 1
                )

            current is BubbleState.MultiPageCapturing && event is BubbleEvent.CaptureFailed ->
                BubbleState.MultiPageCapture(current.capturedBitmaps, current.captureCount)

            current is BubbleState.MultiPageCapturing && event is BubbleEvent.DoubleTap ->
                BubbleState.Idle

            // MultiPageCaptureSuccess 状态的转换（截图成功显示）
            current is BubbleState.MultiPageCaptureSuccess && event is BubbleEvent.CaptureSuccessAnimationDone ->
                BubbleState.MultiPageCapture(current.capturedBitmaps, current.captureCount)

            current is BubbleState.MultiPageCaptureSuccess && event is BubbleEvent.DoubleTap ->
                BubbleState.Idle

            current is BubbleState.MultiPageCaptureSuccess && event is BubbleEvent.SendCaptures ->
                BubbleState.Processing

            // Error 状态的转换
            current is BubbleState.Error && event is BubbleEvent.LongPress ->
                BubbleState.MenuExpanded(current, event.anchorX, event.anchorY)

            current is BubbleState.Error && event is BubbleEvent.EnterMultiPageCapture ->
                BubbleState.MultiPageCapture()

            current is BubbleState.Error && event is BubbleEvent.SingleTap ->
                BubbleState.Idle

            current is BubbleState.Error && event is BubbleEvent.Reset ->
                BubbleState.Idle

            // MenuExpanded 状态的转换
            current is BubbleState.MenuExpanded && event is BubbleEvent.CloseMenu ->
                current.previousState

            current is BubbleState.MenuExpanded && event is BubbleEvent.MenuSelect ->
                current.previousState

            current is BubbleState.MenuExpanded && event is BubbleEvent.SingleTap ->
                current.previousState

            current is BubbleState.MenuExpanded && event is BubbleEvent.DoubleTap ->
                current.previousState

            current is BubbleState.MenuExpanded && event is BubbleEvent.ErrorOccurred ->
                BubbleState.Error(event.message)

            // 无效转换，保持当前状态
            else -> {
                AppDebugLogStore.i(
                    tag,
                    "Invalid transition: ${current::class.simpleName} + ${event::class.simpleName}"
                )
                current
            }
        }
    }

    /**
     * 检查是否可以处理截图
     */
    fun canCapture(): Boolean {
        return _state.value is BubbleState.MultiPageCapture
    }

    /**
     * 检查是否可以发送截图
     */
    fun canSendCaptures(): Boolean {
        val state = _state.value
        return (state is BubbleState.MultiPageCapture && state.capturedBitmaps.isNotEmpty()) ||
               (state is BubbleState.MultiPageCaptureSuccess && state.capturedBitmaps.isNotEmpty())
    }

    /**
     * 获取已截图列表
     */
    fun getCapturedBitmaps(): List<Bitmap> {
        val state = _state.value
        return when (state) {
            is BubbleState.MultiPageCapture -> state.capturedBitmaps
            is BubbleState.MultiPageCapturing -> state.capturedBitmaps
            is BubbleState.MultiPageCaptureSuccess -> state.capturedBitmaps
            else -> emptyList()
        }
    }
}
