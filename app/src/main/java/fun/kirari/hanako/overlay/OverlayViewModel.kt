package `fun`.kirari.hanako.overlay

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.AppContainer
import `fun`.kirari.hanako.automation.BubbleEvent
import `fun`.kirari.hanako.automation.BubbleMenuItem
import `fun`.kirari.hanako.automation.BubbleState
import `fun`.kirari.hanako.automation.BubbleStateMachine
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ProcessingEvent
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.ProcessingStatus
import `fun`.kirari.hanako.data.ModelSelection
import `fun`.kirari.hanako.data.SettingsRepository
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.localocr.LocalOcrManager
import `fun`.kirari.hanako.network.ProviderModelsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal class OverlayViewModel(
    private val appContext: Context,
    private val repository: SettingsRepository,
    private val pipeline: ProcessingPipeline,
    val providerModelsApi: ProviderModelsApi
) : ViewModel() {
    private val tag = "HanakoOverlayVM"
    private val processingTimeoutMillis = 90_000L
    
    // 新的状态机
    val bubbleStateMachine = BubbleStateMachine()
    
    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()
    private val multiPageCaptureController = MultiPageCaptureController(
        appContext = appContext,
        scope = viewModelScope,
        uiState = _uiState,
        bubbleStateMachine = bubbleStateMachine
    )
    private val autoProcessingController = AutoProcessingController(
        appContext = appContext,
        scope = viewModelScope,
        uiState = _uiState,
        pipeline = pipeline,
        bubbleStateMachine = bubbleStateMachine,
        processingTimeoutMillis = processingTimeoutMillis,
        upsertHistory = ::upsertHistory,
        handleError = ::handleError
    )

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        
        // 监听状态机变化，同步到 UI 状态
        viewModelScope.launch {
            bubbleStateMachine.state.collect { bubbleState ->
                _uiState.update { it.copy(bubbleState = bubbleState) }
            }
        }
    }

    fun setLaunchMode(mode: OverlayLaunchMode) {
        AppDebugLogStore.i(tag, "setLaunchMode mode=$mode")
        _uiState.update { state ->
            state.copy(
                launchMode = mode,
                autoRunState = if (mode == OverlayLaunchMode.NORMAL) AutoRunState.IDLE else state.autoRunState,
                autoCopiedLabel = if (mode == OverlayLaunchMode.NORMAL) null else state.autoCopiedLabel,
                pendingVibrationLetters = if (mode == OverlayLaunchMode.NORMAL) null else state.pendingVibrationLetters,
                error = null
            )
        }
        // 普通模式重置状态机
        if (mode == OverlayLaunchMode.NORMAL) {
            bubbleStateMachine.forceState(BubbleState.Idle)
        }
    }

    fun openCropSheet() {
        AppDebugLogStore.i(tag, "openCropSheet launchMode=${_uiState.value.launchMode}")
        if (_uiState.value.launchMode == OverlayLaunchMode.AUTO) {
            AppDebugLogStore.i(tag, "openCropSheet delegated to processFullScreen for auto mode")
            processFullScreen()
            return
        }
        viewModelScope.launch {
            runCatching {
                `fun`.kirari.hanako.capture.ScreenCaptureManager.captureLatestBitmap(appContext, _uiState.value.settings.screenCaptureMethod)
            }.onSuccess { bitmap ->
                AppDebugLogStore.i(tag, "openCropSheet capture success width=${bitmap.width} height=${bitmap.height}")
                _uiState.update {
                    it.copy(
                        screenshot = bitmap,
                        selectedBitmap = null,
                        liveOcrText = "",
                        liveAnswerText = "",
                        result = null,
                        error = null,
                        working = false,
                        sheetVisible = true,
                        sheetMode = OverlaySheetMode.CROP,
                        autoRunState = AutoRunState.IDLE,
                        autoCopiedLabel = null,
                        pendingVibrationLetters = null
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }.onFailure { error ->
                AppDebugLogStore.e(tag, "openCropSheet failed", error)
                _uiState.update {
                    it.copy(
                        error = error.message ?: "截屏失败",
                        sheetVisible = true,
                        sheetMode = OverlaySheetMode.CROP
                    )
                }
            }
        }
    }

    fun processFullScreen() {
        autoProcessingController.processFullScreen()
    }

    fun process(bitmap: Bitmap) {
        process(listOf(bitmap))
    }

    fun process(bitmaps: List<Bitmap>) {
        val state = _uiState.value
        val firstBitmap = bitmaps.firstOrNull() ?: return
        AppDebugLogStore.i(tag, "process start route=${state.settings.processingRoute} bitmapCount=${bitmaps.size}")

        val models = runCatching { pipeline.resolveModels(state) }.getOrElse { error ->
            _uiState.update { it.copy(error = error.message) }
            return
        }
        val (baseResult, historyId, screenshotPaths) = pipeline.createBaseResult(models, bitmaps, "请求已开始")

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedBitmap = firstBitmap,
                    liveOcrText = "",
                    liveAnswerText = "",
                    result = null,
                    error = null,
                    working = true,
                    sheetVisible = true,
                    sheetMode = OverlaySheetMode.RESULT
                )
            }
            upsertHistory(baseResult)
            runCatching {
                withTimeout(processingTimeoutMillis) {
                    when (models.route) {
                        ProcessingRoute.OCR_THEN_LLM -> {
                            pipeline.validateOcrThenLlmModels(models)
                            val (ocrText, answer, searchOutcome) = pipeline.streamOcrThenChat(
                                models = models,
                                bitmaps = bitmaps,
                                onOcrDelta = { delta ->
                                    _uiState.update { current -> current.copy(liveOcrText = current.liveOcrText + delta) }
                                },
                                onAnswerDelta = { delta ->
                                    _uiState.update { current -> current.copy(liveAnswerText = current.liveAnswerText + delta) }
                                }
                            )
                            pipeline.buildChatResult(baseResult, models, ocrText, answer, historyId, screenshotPaths, searchOutcome)
                        }

                        ProcessingRoute.MULTIMODAL_DIRECT -> {
                            pipeline.validateVisionModels(models)
                            val answer = pipeline.streamVisionDirect(
                                models = models,
                                bitmaps = bitmaps,
                                onAnswerDelta = { delta ->
                                    _uiState.update { current -> current.copy(liveAnswerText = current.liveAnswerText + delta) }
                                }
                            )
                            pipeline.buildChatResult(baseResult, models, "", answer, historyId, screenshotPaths, null)
                        }
                    }
                }
            }.onSuccess { result ->
                AppDebugLogStore.i(tag, "process success resultId=${result.id} answerLength=${result.answer.length}")
                upsertHistory(result)
                _uiState.update {
                    it.copy(
                        working = false,
                        result = result,
                        liveOcrText = result.extractedText,
                        liveAnswerText = result.answer,
                        autoRunState = AutoRunState.IDLE,
                        autoCopiedLabel = null,
                        pendingVibrationLetters = null
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }.onFailure { error ->
                AppDebugLogStore.e(tag, "process failed", error)
                handleError(error, baseResult)
            }
        }
    }

    fun closeSheet() {
        _uiState.update { it.copy(sheetVisible = false, error = null) }
    }

    fun consumeAutoCompletedState() {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.d(tag, "consumeAutoCompletedState state=${_uiState.value.autoRunState} bubble=${currentState::class.simpleName}")
        
        if (_uiState.value.launchMode == OverlayLaunchMode.AUTO && _uiState.value.autoRunState == AutoRunState.COMPLETED) {
            if (currentState is BubbleState.Copied ||
                (_uiState.value.settings.automation.staticModeEnabled && currentState is BubbleState.ShowingLetters)
            ) {
                _uiState.update { it.copy(autoRunState = AutoRunState.IDLE, pendingVibrationLetters = null) }
                bubbleStateMachine.forceState(BubbleState.Idle)
            } else {
                _uiState.update { it.copy(autoRunState = AutoRunState.IDLE, pendingVibrationLetters = null) }
            }
        }
    }

    fun consumePendingVibrationLetters() {
        _uiState.update { it.copy(pendingVibrationLetters = null) }
    }

    fun onBubbleTappedAfterLettersShown() {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.i(tag, "onBubbleTappedAfterLettersShown launchMode=${_uiState.value.launchMode} bubble=${currentState::class.simpleName}")
        
        if (_uiState.value.launchMode == OverlayLaunchMode.AUTO && currentState is BubbleState.ShowingLetters) {
            AppDebugLogStore.i(tag, "onBubbleTappedAfterLettersShown clearing letters and entering pending reset")
            bubbleStateMachine.dispatch(BubbleEvent.SingleTap)
        }
    }

    // 多页截图相关方法
    
    /**
     * 进入多页截图模式
     */
    fun enterMultiPageCaptureMode() {
        multiPageCaptureController.enter()
    }

    /**
     * 截图一次
     */
    fun capturePage() {
        multiPageCaptureController.capturePage()
    }

    /**
     * 发送截图给 AI
     */
    fun sendCaptures() {
        AppDebugLogStore.i(tag, "sendCaptures called state=${bubbleStateMachine.currentState::class.simpleName}")
        if (!multiPageCaptureController.canSendCaptures()) {
            AppDebugLogStore.i(tag, "sendCaptures called but cannot send")
            return
        }
        
        val bitmaps = multiPageCaptureController.capturedBitmaps()
        AppDebugLogStore.i(tag, "sendCaptures count=${bitmaps.size}")
        
        if (bitmaps.isEmpty()) {
            AppDebugLogStore.i(tag, "sendCaptures no bitmaps available")
            return
        }
        
        multiPageCaptureController.markSendCaptures()
        AppDebugLogStore.i(tag, "sendCaptures dispatched SendCaptures, new state=${bubbleStateMachine.currentState::class.simpleName}")
        autoProcessingController.processBitmaps(bitmaps)
    }

    /**
     * 退出多页截图模式
     */
    fun exitMultiPageCaptureMode() {
        multiPageCaptureController.exit()
    }

    /**
     * 处理单击事件（根据当前状态决定行为）
     */
    fun handleSingleTap() {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.i(tag, "handleSingleTap state=${currentState::class.simpleName}")

        when (currentState) {
            is BubbleState.MenuExpanded -> {
                bubbleStateMachine.dispatch(BubbleEvent.CloseMenu)
            }
            is BubbleState.MultiPageCapture -> {
                capturePage()
            }
            is BubbleState.MultiPageCapturing -> {
                AppDebugLogStore.i(tag, "handleSingleTap ignored, capturing in progress")
            }
            is BubbleState.MultiPageCaptureSuccess -> {
                AppDebugLogStore.i(tag, "handleSingleTap ignored, showing capture success")
            }
            is BubbleState.Copied -> {
                bubbleStateMachine.dispatch(BubbleEvent.SingleTap)
            }
            is BubbleState.Error -> {
                bubbleStateMachine.dispatch(BubbleEvent.SingleTap)
            }
            else -> {
                openCropSheet()
            }
        }
    }

    /**
     * 处理长按事件
     * - 多图模式下：发送已截图片
     * - Idle/ShowingLetters/Copied/Error：进入多图截图模式
     * - Processing 等：展开扇形菜单
     */
    fun handleLongPress(anchorX: Int = 0, anchorY: Int = 0) {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.i(tag, "handleLongPress state=${currentState::class.simpleName} anchor=($anchorX,$anchorY)")

        when (currentState) {
            is BubbleState.MultiPageCapture -> {
                if (currentState.capturedBitmaps.isNotEmpty()) {
                    sendCaptures()
                }
            }
            is BubbleState.MultiPageCapturing -> {
                AppDebugLogStore.i(tag, "handleLongPress ignored, capturing in progress")
            }
            is BubbleState.MultiPageCaptureSuccess -> {
                if (currentState.capturedBitmaps.isNotEmpty()) {
                    sendCaptures()
                }
            }
            is BubbleState.MenuExpanded -> {
                bubbleStateMachine.dispatch(BubbleEvent.CloseMenu)
            }
            is BubbleState.Idle, is BubbleState.ShowingLetters,
            is BubbleState.Copied, is BubbleState.Error -> {
                enterMultiPageCaptureMode()
            }
            else -> {
                // Processing 等状态长按展开菜单
                bubbleStateMachine.dispatch(BubbleEvent.LongPress(anchorX, anchorY))
            }
        }
    }

    /**
     * 处理双击事件
     * - 多图模式：退出多图
     * - Processing：取消处理
     * - Idle/ShowingLetters/Copied/Error：展开扇形菜单
     */
    fun handleDoubleTap(anchorX: Int = 0, anchorY: Int = 0) {
        val currentState = bubbleStateMachine.currentState
        AppDebugLogStore.i(tag, "handleDoubleTap state=${currentState::class.simpleName}")

        when (currentState) {
            is BubbleState.MultiPageCapture,
            is BubbleState.MultiPageCapturing,
            is BubbleState.MultiPageCaptureSuccess -> {
                exitMultiPageCaptureMode()
            }
            is BubbleState.Processing -> {
                autoProcessingController.cancelActiveProcessing()
            }
            is BubbleState.Idle,
            is BubbleState.ShowingLetters,
            is BubbleState.Copied,
            is BubbleState.Error -> {
                bubbleStateMachine.dispatch(BubbleEvent.LongPress(anchorX, anchorY))
            }
            is BubbleState.MenuExpanded -> {
                bubbleStateMachine.dispatch(BubbleEvent.CloseMenu)
            }
            else -> {}
        }
    }

    /**
     * 菜单关闭后的回调
     */
    fun onMenuDismissed() {
        val currentState = bubbleStateMachine.currentState
        if (currentState is BubbleState.MenuExpanded) {
            bubbleStateMachine.dispatch(BubbleEvent.CloseMenu)
        }
    }

    /**
     * 处理菜单项点击
     */
    fun handleMenuSelect(item: BubbleMenuItem) {
        AppDebugLogStore.i(tag, "handleMenuSelect item=$item")
        when (item) {
            BubbleMenuItem.ToggleRoute -> toggleProcessingRoute()
            BubbleMenuItem.ToggleSearch -> toggleWebSearch()
            BubbleMenuItem.Settings -> {
                `fun`.kirari.hanako.overlay.openMainActivity(appContext)
            }
            BubbleMenuItem.VoiceRecognition -> {
                // 语音功能暂未实现
            }
        }
        // 不在此处 dispatch CloseMenu。
        // 状态恢复统一由退场动画结束后的 onMenuDismissed() 处理，
        // 否则 Compose 会立刻移除 BubbleMenu 导致退场动画被取消、
        // overlay 窗口残留拦截触摸事件。
    }

    fun toggleWebSearch() {
        viewModelScope.launch {
            repository.update { current ->
                current.copy(
                    webSearch = current.webSearch.copy(enabled = !current.webSearch.enabled)
                )
            }
        }
    }

    fun selectAssistant(assistantId: String) = repository.selectAssistant(viewModelScope, assistantId)

    fun selectPreviousAssistant() {
        val current = _uiState.value.settings
        val assistants = current.assistants
        if (assistants.isEmpty()) return
        val selectedIndex = assistants.indexOfFirst { it.id == current.selectedAssistantId }.takeIf { it >= 0 } ?: 0
        val previousIndex = if (selectedIndex == 0) assistants.lastIndex else selectedIndex - 1
        selectAssistant(assistants[previousIndex].id)
    }

    fun selectNextAssistant() {
        val current = _uiState.value.settings
        val assistants = current.assistants
        if (assistants.isEmpty()) return
        val selectedIndex = assistants.indexOfFirst { it.id == current.selectedAssistantId }.takeIf { it >= 0 } ?: 0
        val nextIndex = if (selectedIndex == assistants.lastIndex) 0 else selectedIndex + 1
        selectAssistant(assistants[nextIndex].id)
    }

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) =
        repository.updateModelSelection(viewModelScope, purpose, selection)

    fun updateModelSelectionWithFavorite(purpose: ModelPurpose, selection: ModelSelection, favoriteModel: Boolean = false) =
        repository.updateModelSelectionWithFavorite(viewModelScope, purpose, selection, favoriteModel)

    fun toggleFavoriteModel(providerId: String, modelId: String) =
        repository.toggleFavoriteModel(viewModelScope, providerId, modelId)

    fun toggleProcessingRoute() {
        viewModelScope.launch {
            repository.update { current ->
                current.copy(
                    processingRoute = when (current.processingRoute) {
                        ProcessingRoute.OCR_THEN_LLM -> ProcessingRoute.MULTIMODAL_DIRECT
                        ProcessingRoute.MULTIMODAL_DIRECT -> ProcessingRoute.OCR_THEN_LLM
                    }
                )
            }
        }
    }

    private suspend fun handleError(error: Throwable, baseResult: ProcessingResult, isAutoMode: Boolean = false) {
        val isTimeout = error is TimeoutCancellationException
        val message = error.message?.ifBlank { null } ?: if (isTimeout) "请求超时（90 秒）" else "处理失败"
        upsertHistory(
            baseResult.copy(
                status = if (isTimeout) ProcessingStatus.TIMEOUT else ProcessingStatus.ERROR,
                detail = message,
                extractedText = _uiState.value.liveOcrText,
                answer = if (isAutoMode) "" else _uiState.value.liveAnswerText,
                automationThought = if (isAutoMode) _uiState.value.liveAnswerText.orEmpty() else "",
                events = baseResult.events + ProcessingEvent(
                    title = if (isTimeout) "请求超时" else "请求失败",
                    detail = message
                )
            )
        )
        _uiState.update {
            it.copy(
                working = false,
                autoRunState = AutoRunState.IDLE,
                pendingVibrationLetters = null,
                error = message
            )
        }
        if (isAutoMode) {
            bubbleStateMachine.forceState(BubbleState.Idle)
        }
    }

    private suspend fun upsertHistory(result: ProcessingResult) {
        repository.update { current ->
            val history = listOf(result) + current.history.filterNot { it.id == result.id }
            current.copy(lastResult = result, history = history)
        }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory {
            val container = (appContext.applicationContext as `fun`.kirari.hanako.HanakoApplication).container
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OverlayViewModel(
                        appContext = appContext,
                        repository = container.settingsRepository,
                        pipeline = ProcessingPipeline(
                            appContext = appContext,
                            unifiedClient = container.unifiedLLMClient,
                            localOcrManager = container.localOcrManager,
                            searchOrchestrator = container.searchOrchestrator
                        ),
                        providerModelsApi = container.providerModelsApi
                    ) as T
                }
            }
        }
    }
}
