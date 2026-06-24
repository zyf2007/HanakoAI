package `fun`.kirari.hanako.overlay

import android.content.Context
import android.graphics.Bitmap
import `fun`.kirari.hanako.automation.BubbleEvent
import `fun`.kirari.hanako.automation.BubbleState
import `fun`.kirari.hanako.automation.BubbleStateMachine
import `fun`.kirari.hanako.capture.ScreenCaptureManager
import `fun`.kirari.hanako.data.AutomationActionRecord
import `fun`.kirari.hanako.data.AutomationActionType
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class AutoProcessingController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<OverlayUiState>,
    private val pipeline: ProcessingPipeline,
    private val bubbleStateMachine: BubbleStateMachine,
    private val processingTimeoutMillis: Long,
    private val upsertHistory: suspend (ProcessingResult) -> Unit,
    private val handleError: suspend (Throwable, ProcessingResult, Boolean) -> Unit
) {
    private val tag = "HanakoAutoProcessing"
    private var activeJob: Job? = null

    fun processFullScreen() {
        AppDebugLogStore.i(tag, "processFullScreen start launchMode=${uiState.value.launchMode}")
        activeJob?.cancel()
        val job = scope.launch {
            uiState.update {
                it.copy(
                    liveOcrText = "",
                    liveAnswerText = "",
                    result = null,
                    error = null,
                    working = true,
                    sheetVisible = false,
                    autoRunState = AutoRunState.RUNNING,
                    autoCopiedLabel = null,
                    pendingVibrationLetters = null
                )
            }
            bubbleStateMachine.dispatch(BubbleEvent.StartProcessing)

            runCatching {
                withContext(Dispatchers.IO) {
                    ScreenCaptureManager.captureLatestBitmap(appContext, uiState.value.settings.screenCaptureMethod)
                }
            }.onSuccess { bitmap ->
                AppDebugLogStore.i(tag, "processFullScreen capture success width=${bitmap.width} height=${bitmap.height}")
                processBitmapsNow(listOf(bitmap))
            }.onFailure { error ->
                if (error is CancellationException) {
                    AppDebugLogStore.i(tag, "processFullScreen cancelled")
                    return@onFailure
                }
                AppDebugLogStore.e(tag, "processFullScreen failed", error)
                uiState.update {
                    it.copy(
                        working = false,
                        autoRunState = AutoRunState.IDLE,
                        pendingVibrationLetters = null,
                        error = error.message ?: "截屏失败"
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }
        }
        activeJob = job
        job.invokeOnCompletion {
            if (activeJob === job) activeJob = null
        }
    }

    fun processBitmaps(bitmaps: List<Bitmap>) {
        activeJob?.cancel()
        val job = scope.launch {
            processBitmapsNow(bitmaps)
        }
        activeJob = job
        job.invokeOnCompletion {
            if (activeJob === job) activeJob = null
        }
    }

    fun cancelActiveProcessing() {
        AppDebugLogStore.i(tag, "cancelActiveProcessing")
        activeJob?.cancel()
        activeJob = null
        uiState.update {
            it.copy(
                working = false,
                autoRunState = AutoRunState.IDLE,
                autoCopiedLabel = null,
                pendingVibrationLetters = null,
                error = null
            )
        }
        bubbleStateMachine.dispatch(BubbleEvent.CancelProcessing)
        if (bubbleStateMachine.currentState !is BubbleState.Idle) {
            bubbleStateMachine.forceState(BubbleState.Idle)
        }
    }

    private suspend fun processBitmapsNow(bitmaps: List<Bitmap>) {
        val state = uiState.value
        val firstBitmap = bitmaps.firstOrNull() ?: return
        AppDebugLogStore.i(tag, "processBitmaps start route=${state.settings.processingRoute} bitmapCount=${bitmaps.size}")

        val models = runCatching { pipeline.resolveModels(state) }.getOrElse { error ->
            uiState.update { it.copy(working = false, autoRunState = AutoRunState.IDLE, error = error.message) }
            bubbleStateMachine.forceState(BubbleState.Idle)
            return
        }
        val (baseResult, historyId, screenshotPaths) = pipeline.createBaseResult(models, bitmaps, "自动流程已开始")
        upsertHistory(baseResult)

        runCatching<Pair<AutomationActionRecord, ProcessingResult>> {
            withTimeout(processingTimeoutMillis) {
                val (action, result) = when (models.route) {
                    ProcessingRoute.OCR_THEN_LLM -> {
                        pipeline.validateOcrThenLlmModels(models)
                        val (ocrText, automationResult, searchOutcome) = pipeline.streamOcrThenAutomation(
                            models = models,
                            bitmaps = bitmaps,
                            onOcrDelta = { delta ->
                                uiState.update { current -> current.copy(liveOcrText = current.liveOcrText + delta) }
                            },
                            onThoughtDelta = { delta ->
                                uiState.update { current -> current.copy(liveAnswerText = current.liveAnswerText + delta) }
                            }
                        )
                        pipeline.buildAutomationResult(baseResult, models, ocrText, automationResult, historyId, screenshotPaths, searchOutcome)
                    }
                    ProcessingRoute.MULTIMODAL_DIRECT -> {
                        pipeline.validateVisionModels(models)
                        val automationResult = pipeline.streamAutomationDirect(
                            models = models,
                            bitmaps = bitmaps,
                            onThoughtDelta = { delta ->
                                uiState.update { current -> current.copy(liveAnswerText = current.liveAnswerText + delta) }
                            }
                        )
                        pipeline.buildAutomationResult(baseResult, models, "", automationResult, historyId, screenshotPaths, null)
                    }
                }
                AppDebugLogStore.i(tag, "processBitmaps gateway success resultId=${result.id} action=${action.type}")
                upsertHistory(result)
                action to result
            }
        }.onSuccess { (action, result) ->
            applyAutomationAction(action, result, firstBitmap)
        }.onFailure { error ->
            if (error is CancellationException) {
                AppDebugLogStore.i(tag, "processBitmaps cancelled")
                return
            }
            AppDebugLogStore.e(tag, "processBitmaps failed", error)
            handleError(error, baseResult, true)
        }
    }

    private fun applyAutomationAction(
        action: AutomationActionRecord,
        result: ProcessingResult,
        firstBitmap: Bitmap
    ) {
        when (action.type) {
            AutomationActionType.SET_CLIPBOARD -> {
                uiState.update {
                    it.copy(
                        screenshot = firstBitmap,
                        selectedBitmap = firstBitmap,
                        working = false,
                        result = result,
                        liveAnswerText = result.automationThought,
                        autoRunState = AutoRunState.COMPLETED,
                        autoCopiedLabel = action.text,
                        pendingVibrationLetters = null,
                        error = null
                    )
                }
                bubbleStateMachine.dispatch(BubbleEvent.CopyComplete(action.text))
            }
            AutomationActionType.SHOW_BUBBLE_LETTERS -> {
                uiState.update {
                    it.copy(
                        screenshot = firstBitmap,
                        selectedBitmap = firstBitmap,
                        working = false,
                        result = result,
                        liveAnswerText = result.automationThought,
                        autoRunState = AutoRunState.COMPLETED,
                        autoCopiedLabel = null,
                        pendingVibrationLetters = action.text,
                        error = null
                    )
                }
                bubbleStateMachine.dispatch(BubbleEvent.LettersComplete(action.text))
            }
        }
    }
}
