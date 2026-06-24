package `fun`.kirari.hanako.overlay

import android.content.Context
import `fun`.kirari.hanako.automation.BubbleEvent
import `fun`.kirari.hanako.automation.BubbleState
import `fun`.kirari.hanako.automation.BubbleStateMachine
import `fun`.kirari.hanako.capture.ScreenCaptureManager
import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MultiPageCaptureController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<OverlayUiState>,
    private val bubbleStateMachine: BubbleStateMachine
) {
    private val tag = "HanakoMultiPageCapture"

    fun enter() {
        AppDebugLogStore.i(tag, "enter")
        bubbleStateMachine.dispatch(BubbleEvent.EnterMultiPageCapture)
    }

    fun capturePage() {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.i(tag, "capturePage called state=${currentState::class.simpleName}")
        if (currentState !is BubbleState.MultiPageCapture) {
            AppDebugLogStore.i(tag, "capturePage called but not in MultiPageCapture state")
            return
        }

        bubbleStateMachine.dispatch(BubbleEvent.CaptureStart)
        AppDebugLogStore.i(tag, "capturePage dispatched CaptureStart, new state=${bubbleStateMachine.currentState::class.simpleName}")

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ScreenCaptureManager.captureLatestBitmap(appContext, uiState.value.settings.screenCaptureMethod)
                }
            }.onSuccess { bitmap ->
                AppDebugLogStore.i(tag, "capturePage success width=${bitmap.width} height=${bitmap.height}")
                bubbleStateMachine.dispatch(BubbleEvent.CaptureTaken(bitmap))
                AppDebugLogStore.i(tag, "capturePage dispatched CaptureTaken, new state=${bubbleStateMachine.currentState::class.simpleName}")
                launch {
                    kotlinx.coroutines.delay(2000)
                    bubbleStateMachine.dispatch(BubbleEvent.CaptureSuccessAnimationDone)
                    AppDebugLogStore.i(tag, "capturePage dispatched CaptureSuccessAnimationDone, new state=${bubbleStateMachine.currentState::class.simpleName}")
                }
            }.onFailure { error ->
                AppDebugLogStore.e(tag, "capturePage failed", error)
                uiState.update { it.copy(error = error.message ?: "截图失败") }
                bubbleStateMachine.dispatch(BubbleEvent.CaptureFailed)
            }
        }
    }

    fun capturedBitmaps() = bubbleStateMachine.getCapturedBitmaps()

    fun canSendCaptures(): Boolean = bubbleStateMachine.canSendCaptures()

    fun markSendCaptures() {
        bubbleStateMachine.dispatch(BubbleEvent.SendCaptures)
    }

    fun exit() {
        AppDebugLogStore.i(tag, "exit")
        bubbleStateMachine.dispatch(BubbleEvent.DoubleTap)
    }
}
