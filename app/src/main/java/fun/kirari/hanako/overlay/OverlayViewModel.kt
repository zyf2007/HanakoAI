package `fun`.kirari.hanako.overlay

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.capture.ProjectionSessionManager
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.ModelSelection
import `fun`.kirari.hanako.data.resolveModelName
import `fun`.kirari.hanako.data.resolveModelProvider
import `fun`.kirari.hanako.data.SettingsStore
import `fun`.kirari.hanako.data.toHistoryBase64
import `fun`.kirari.hanako.network.AiGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OverlayViewModel(
    private val appContext: Context,
    private val store: SettingsStore,
    private val gateway: AiGateway
) : ViewModel() {
    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun openCropSheet() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ProjectionSessionManager.captureLatestBitmap() }
            }.onSuccess { bitmap ->
                Log.d("OverlayService", "openCropSheet success bitmap=${bitmap.width}x${bitmap.height}")
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
                        sheetMode = OverlaySheetMode.CROP
                    )
                }
            }.onFailure { error ->
                Log.e("OverlayService", "openCropSheet failed", error)
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

    fun process(bitmap: Bitmap) {
        val state = _uiState.value
        val assistant = state.settings.assistants.firstOrNull { it.id == state.settings.selectedAssistantId } ?: return
        val ocrProvider = state.settings.resolveModelProvider(ModelPurpose.OCR)
        val ocrModel = state.settings.resolveModelName(ModelPurpose.OCR)
        val textProvider = state.settings.resolveModelProvider(ModelPurpose.TEXT)
        val textModel = state.settings.resolveModelName(ModelPurpose.TEXT)
        val visionProvider = state.settings.resolveModelProvider(ModelPurpose.VISION)
        val visionModel = state.settings.resolveModelName(ModelPurpose.VISION)

        viewModelScope.launch {
            Log.d(
                "OverlayService",
                "process start bitmap=${bitmap.width}x${bitmap.height} route=${state.settings.processingRoute} assistant=${assistant.name}"
            )
            _uiState.update {
                it.copy(
                    selectedBitmap = bitmap,
                    liveOcrText = "",
                    liveAnswerText = "",
                    result = null,
                    error = null,
                    working = true,
                    sheetVisible = true,
                    sheetMode = OverlaySheetMode.RESULT
                )
            }
            Log.d("OverlayService", "process switched to RESULT sheet")
            runCatching {
                when (state.settings.processingRoute) {
                    ProcessingRoute.OCR_THEN_LLM -> {
                        if (ocrProvider == null || ocrModel.isBlank() || textProvider == null || textModel.isBlank()) {
                            error("请先在模型设置中配置 OCR 和文本模型")
                        }
                        val (ocrText, answer) = gateway.streamOcrThenChat(
                            ocrProvider = ocrProvider,
                            ocrModel = ocrModel,
                            textProvider = textProvider,
                            textModel = textModel,
                            assistant = assistant,
                            bitmap = bitmap,
                            onOcrDelta = { delta ->
                                _uiState.update { current ->
                                    current.copy(liveOcrText = current.liveOcrText + delta)
                                }
                                Log.d("OverlayService", "ocr delta len=${delta.length}")
                            },
                            onAnswerDelta = { delta ->
                                _uiState.update { current ->
                                    current.copy(liveAnswerText = current.liveAnswerText + delta)
                                }
                                Log.d("OverlayService", "answer delta len=${delta.length}")
                            }
                        )
                        ProcessingResult(
                            assistantName = assistant.name,
                            route = ProcessingRoute.OCR_THEN_LLM,
                            modelSummary = buildModelSummary(textModel, textProvider?.name),
                            extractedText = ocrText,
                            answer = answer,
                            screenshotBase64 = bitmap.toHistoryBase64()
                        )
                    }

                    ProcessingRoute.MULTIMODAL_DIRECT -> {
                        if (visionProvider == null || visionModel.isBlank()) {
                            error("请先在模型设置中配置多模态模型")
                        }
                        val answer = gateway.streamVisionDirect(
                            provider = visionProvider,
                            model = visionModel,
                            assistant = assistant,
                            bitmap = bitmap,
                            onAnswerDelta = { delta ->
                                _uiState.update { current ->
                                    current.copy(liveAnswerText = current.liveAnswerText + delta)
                                }
                                Log.d("OverlayService", "answer delta len=${delta.length}")
                            }
                        )
                        ProcessingResult(
                            assistantName = assistant.name,
                            route = ProcessingRoute.MULTIMODAL_DIRECT,
                            modelSummary = buildModelSummary(visionModel, visionProvider?.name),
                            answer = answer,
                            screenshotBase64 = bitmap.toHistoryBase64()
                        )
                    }
                }
            }.onSuccess { result ->
                Log.d("OverlayService", "process success answer=${result.answer.length} ocr=${result.extractedText.length}")
                store.update {
                    it.copy(
                        lastResult = result,
                        history = (listOf(result) + it.history).take(20)
                    )
                }
                _uiState.update {
                    it.copy(
                        working = false,
                        result = result,
                        liveOcrText = result.extractedText,
                        liveAnswerText = result.answer
                    )
                }
            }.onFailure { error ->
                Log.e("OverlayService", "process failed", error)
                _uiState.update {
                    it.copy(
                        working = false,
                        error = error.message ?: "处理失败"
                    )
                }
            }
        }
    }

    fun closeSheet() {
        _uiState.update { it.copy(sheetVisible = false, error = null) }
    }

    fun selectAssistant(assistantId: String) {
        viewModelScope.launch {
            store.update { current ->
                if (current.assistants.any { it.id == assistantId }) {
                    current.copy(selectedAssistantId = assistantId)
                } else {
                    current
                }
            }
        }
    }

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

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) {
        viewModelScope.launch {
            store.update { current ->
                when (purpose) {
                    ModelPurpose.TEXT -> current.copy(textModelSelection = selection)
                    ModelPurpose.VISION -> current.copy(visionModelSelection = selection)
                    ModelPurpose.OCR -> current.copy(ocrModelSelection = selection)
                }
            }
        }
    }

    fun toggleProcessingRoute() {
        viewModelScope.launch {
            store.update { current ->
                current.copy(
                    processingRoute = when (current.processingRoute) {
                        ProcessingRoute.OCR_THEN_LLM -> ProcessingRoute.MULTIMODAL_DIRECT
                        ProcessingRoute.MULTIMODAL_DIRECT -> ProcessingRoute.OCR_THEN_LLM
                    }
                )
            }
        }
    }

    private fun buildModelSummary(model: String, providerName: String?): String {
        val trimmedModel = model.trim()
        val trimmedProvider = providerName?.trim().orEmpty()
        if (trimmedModel.isBlank()) return ""
        return if (trimmedProvider.isBlank()) trimmedModel else "$trimmedModel（$trimmedProvider）"
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OverlayViewModel(
                        appContext = appContext,
                        store = SettingsStore(appContext),
                        gateway = AiGateway()
                    ) as T
                }
            }
    }
}
