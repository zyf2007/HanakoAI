package `fun`.kirari.hanako.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.HanakoApplication
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.AutomationSettings
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ModelSelection
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.ScreenCaptureMethod
import `fun`.kirari.hanako.data.SettingsRepository
import `fun`.kirari.hanako.data.defaultAssistant
import `fun`.kirari.hanako.data.defaultProvider
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.data.LOCAL_OCR_MODEL_ID
import `fun`.kirari.hanako.data.LOCAL_OCR_PROVIDER_ID
import `fun`.kirari.hanako.data.modelSelectionFor
import `fun`.kirari.hanako.localocr.LocalOcrManager
import `fun`.kirari.hanako.network.ConnectionTestResult
import `fun`.kirari.hanako.network.ProviderModelsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ConnectionTestStatus {
    IDLE, TESTING, SUCCESS, FAILED
}

data class ConnectionTestState(
    val status: ConnectionTestStatus = ConnectionTestStatus.IDLE,
    val latencyMs: Long = 0,
    val errorMessage: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as HanakoApplication).container
    private val repository: SettingsRepository = container.settingsRepository
    private val localOcrManager: LocalOcrManager = container.localOcrManager
    private val providerModelsApi = ProviderModelsApi(container.networkClientProvider)

    private val _connectionTestState = MutableStateFlow(ConnectionTestState())
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()
    private var connectionTestJob: Job? = null

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    init {
        syncLocalOcrInstallation()
    }

    fun updateProvider(provider: ModelProviderConfig) {
        viewModelScope.launch {
            repository.update { current ->
                current.copy(
                    providers = current.providers.map { if (it.id == provider.id) provider else it }
                )
            }
        }
    }

    fun addProvider() {
        viewModelScope.launch {
            repository.update { current ->
                val provider = ModelProviderConfig(name = "自定义提供方 ${current.providers.size + 1}")
                current.copy(
                    providers = current.providers + provider,
                    selectedProviderId = provider.id
                )
            }
        }
    }

    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            repository.update { it.copy(selectedProviderId = providerId) }
        }
    }

    fun deleteProvider(providerId: String) {
        viewModelScope.launch {
            repository.update { current ->
                val remaining = current.providers.filterNot { it.id == providerId }
                val providers = if (remaining.isEmpty()) listOf(defaultProvider()) else remaining
                val selectedProviderId = providers.firstOrNull()?.id
                val fallbackProvider = providers.firstOrNull()
                current.copy(
                    providers = providers,
                    selectedProviderId = selectedProviderId,
                    textModelSelection = remapSelection(
                        current.modelSelectionFor(ModelPurpose.TEXT),
                        providers,
                        fallbackProvider
                    ),
                    visionModelSelection = remapSelection(
                        current.modelSelectionFor(ModelPurpose.VISION),
                        providers,
                        fallbackProvider
                    ),
                    ocrModelSelection = remapSelection(
                        current.modelSelectionFor(ModelPurpose.OCR),
                        providers,
                        fallbackProvider
                    )
                )
            }
        }
    }

    fun updateAssistant(assistant: AssistantPreset) {
        viewModelScope.launch {
            repository.update { current ->
                current.copy(
                    assistants = current.assistants.map { if (it.id == assistant.id) assistant else it }
                )
            }
        }
    }

    fun addAssistant() {
        viewModelScope.launch {
            repository.update { current ->
                val assistant = AssistantPreset(
                    id = UUID.randomUUID().toString(),
                    name = "自定义助手 ${current.assistants.size + 1}",
                    ocrPrompt = "请准确提取图片中的全部文字，按原有结构输出，不要解释。",
                    textPrompt = "你是一个乐于助人的中文助手。",
                    visionPrompt = "你是一个乐于助人的中文助手。请直接根据图片内容完成用户任务。"
                )
                current.copy(
                    assistants = current.assistants + assistant,
                    selectedAssistantId = assistant.id
                )
            }
        }
    }

    fun selectAssistant(assistantId: String) = repository.selectAssistant(viewModelScope, assistantId)

    fun deleteAssistant(assistantId: String) {
        viewModelScope.launch {
            repository.update { current ->
                val remaining = current.assistants.filterNot { it.id == assistantId }
                val assistants = if (remaining.isEmpty()) listOf(defaultAssistant()) else remaining
                val selectedAssistantId = assistants.firstOrNull()?.id
                current.copy(
                    assistants = assistants,
                    selectedAssistantId = selectedAssistantId
                )
            }
        }
    }

    fun setRoute(route: ProcessingRoute) {
        viewModelScope.launch {
            repository.update { it.copy(processingRoute = route) }
        }
    }

    fun setScreenCaptureMethod(method: ScreenCaptureMethod) {
        viewModelScope.launch {
            repository.update { it.copy(screenCaptureMethod = method) }
        }
    }

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) =
        repository.updateModelSelection(viewModelScope, purpose, selection)

    fun syncLocalOcrInstallation() {
        viewModelScope.launch {
            AppDebugLogStore.i("LocalOcrUi", "syncLocalOcrInstallation start")
            val status = withContext(Dispatchers.IO) { localOcrManager.installationStatus() }
            AppDebugLogStore.i("LocalOcrUi", "syncLocalOcrInstallation done installed=${status.installed}")
            repository.update { current ->
                current.copy(
                    localOcr = current.localOcr.copy(
                        installed = status.installed,
                        lastMessage = if (status.installed) "本地 ML Kit 已内置，可直接使用" else "本地 ML Kit 当前不可用"
                    )
                )
            }
        }
    }

    fun updateModelSelectionWithFavorite(
        purpose: ModelPurpose,
        selection: ModelSelection,
        favoriteModel: Boolean = false
    ) = repository.updateModelSelectionWithFavorite(viewModelScope, purpose, selection, favoriteModel)

    fun toggleFavoriteModel(providerId: String, modelId: String) =
        repository.toggleFavoriteModel(viewModelScope, providerId, modelId)

    fun removeFavoriteModel(providerId: String, modelId: String) =
        repository.removeFavoriteModel(viewModelScope, providerId, modelId)

    fun updateAutomationSettings(transform: (AutomationSettings) -> AutomationSettings) {
        viewModelScope.launch {
            repository.update { current ->
                val next = transform(current.automation)
                current.copy(
                    automation = next.copy(autoModeTimeoutSeconds = next.autoModeTimeoutSeconds.coerceAtLeast(1))
                )
            }
        }
    }

    fun setTrustAllHttpsCertificates(enabled: Boolean) {
        viewModelScope.launch {
            repository.update { it.copy(trustAllHttpsCertificates = enabled) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.update { it.copy(history = emptyList(), lastResult = null) }
        }
    }

    fun deleteHistoryItem(resultId: String) {
        viewModelScope.launch {
            repository.update { current ->
                val history = current.history.filterNot { it.id == resultId }
                val lastResult = current.lastResult?.takeUnless { it.id == resultId }
                current.copy(
                    history = history,
                    lastResult = lastResult
                )
            }
        }
    }

    fun saveResult(result: ProcessingResult) {
        viewModelScope.launch {
            repository.update { it.copy(
                lastResult = result,
                history = listOf(result) + it.history
            ) }
        }
    }

    fun testProviderConnection(provider: ModelProviderConfig) {
        connectionTestJob?.cancel()
        _connectionTestState.value = ConnectionTestState(status = ConnectionTestStatus.TESTING)
        connectionTestJob = viewModelScope.launch {
            val trustAll = settings.value.trustAllHttpsCertificates
            val result = providerModelsApi.testConnection(provider, trustAll)
            if (!isActive) return@launch
            _connectionTestState.value = if (result.success) {
                ConnectionTestState(
                    status = ConnectionTestStatus.SUCCESS,
                    latencyMs = result.latencyMs
                )
            } else {
                ConnectionTestState(
                    status = ConnectionTestStatus.FAILED,
                    latencyMs = result.latencyMs,
                    errorMessage = result.errorMessage
                )
            }
        }
    }

    fun resetConnectionTest() {
        connectionTestJob?.cancel()
        connectionTestJob = null
        _connectionTestState.value = ConnectionTestState()
    }

    fun clearDebugLogs() {
        AppDebugLogStore.clear()
    }

    private fun remapSelection(
        selection: ModelSelection,
        providers: List<ModelProviderConfig>,
        fallbackProvider: ModelProviderConfig?
    ): ModelSelection {
        val providerExists = providers.any { it.id == selection.providerId }
        return when {
            providerExists -> selection
            fallbackProvider != null -> selection.copy(providerId = fallbackProvider.id)
            else -> selection.copy(providerId = null, model = "")
        }
    }
}
