package `fun`.kirari.hanako.data

import `fun`.kirari.hanako.BuildConfig
import `fun`.kirari.llm.core.ProviderKind
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ModelProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "OpenAI Compatible",
    val kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val baseUrl: String = kind.defaultBaseUrl,
    val apiKey: String = "",
    val chatModel: String = "gpt-4o-mini",
    val visionModel: String = "gpt-4o",
    val ocrModel: String = "gpt-4.1-mini",
    val favoriteModels: List<String> = emptyList(),
    val enabled: Boolean = true
)

@Serializable
enum class ModelPurpose {
    OCR,
    TEXT,
    VISION
}

@Serializable
data class ModelSelection(
    val providerId: String? = null,
    val model: String = ""
)

const val LOCAL_OCR_PROVIDER_ID = "__local_mlkit__"
const val LOCAL_OCR_MODEL_ID = "mlkit_chinese_ocr"
const val KIRARI_PROVIDER_ID = "__kirari_network__"

@Serializable
data class KirariSettings(
    val serverUrl: String = BuildConfig.KIRARI_SERVER_URL,
    val auth: KirariAuthState = KirariAuthState(),
    val profile: KirariUserProfile = KirariUserProfile()
)

@Serializable
data class KirariAuthState(
    val accessToken: String = "",
    val refreshToken: String = "",
    val idToken: String = "",
    val tokenType: String = "Bearer",
    val scope: String = "",
    val accessTokenExpiresAtMillis: Long = 0L
)

@Serializable
data class KirariUserProfile(
    val subject: String = "",
    val email: String = "",
    val name: String = "",
    val preferredUsername: String = "",
    val nickname: String = "",
    val lastSyncedAtMillis: Long = 0L
)

@Serializable
data class LocalOcrSettings(
    val installed: Boolean = false,
    val lastMessage: String? = null,
    val providerId: String = LOCAL_OCR_PROVIDER_ID,
    val modelId: String = LOCAL_OCR_MODEL_ID,
    val displayName: String = "ML Kit 中文 OCR"
)

val ProviderKind.displayName: String
    get() = when (this) {
        ProviderKind.OPENAI_COMPATIBLE -> "OpenAI Compatible"
        ProviderKind.OPENAI_RESPONSES -> "OpenAI Responses"
        ProviderKind.ANTHROPIC -> "Anthropic"
        ProviderKind.GOOGLE -> "Google Gemini"
        ProviderKind.KIRARI_NETWORK -> "The Kirari Network"
    }

val ProviderKind.defaultBaseUrl: String
    get() = when (this) {
        ProviderKind.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
        ProviderKind.OPENAI_RESPONSES -> "https://api.openai.com/v1"
        ProviderKind.ANTHROPIC -> "https://api.anthropic.com/v1"
        ProviderKind.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta"
        ProviderKind.KIRARI_NETWORK -> ""
    }

val ProviderKind.requestPathSuffix: String
    get() = when (this) {
        ProviderKind.OPENAI_COMPATIBLE -> "/chat/completions"
        ProviderKind.OPENAI_RESPONSES -> "/responses"
        ProviderKind.ANTHROPIC -> "/messages"
        ProviderKind.GOOGLE -> "/models"
        ProviderKind.KIRARI_NETWORK -> "/api/llm/chat/completions"
    }

fun ModelProviderConfig.requestPreviewUrl(): String = "${baseUrl.trimEnd('/')}${kind.requestPathSuffix}"

@Serializable
data class AssistantPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ocrPrompt: String,
    val textPrompt: String,
    val visionPrompt: String
)

@Serializable
enum class ProcessingRoute {
    OCR_THEN_LLM,
    MULTIMODAL_DIRECT
}

@Serializable
enum class ScreenCaptureMethod {
    MEDIA_PROJECTION,
    SHIZUKU_ADB
}

@Serializable
data class AutomationSettings(
    val completionNotificationEnabled: Boolean = true,
    val autoModeTimeoutSeconds: Int = 30,
    val staticModeEnabled: Boolean = false,
    val staticIntraLetterGapMs: Int = 400,
    val staticInterLetterGapMs: Int = 1000
)

@Serializable
enum class SearchProviderKind(val displayName: String) {
    TAVILY("Tavily"),
    BRAVE("Brave Search"),
    SERPER("Serper.dev"),
    CUSTOM("自定义 (Tavily 兼容)")
}

val SearchProviderKind.defaultBaseUrl: String
    get() = when (this) {
        SearchProviderKind.TAVILY -> "https://api.tavily.com/search"
        SearchProviderKind.BRAVE -> "https://api.search.brave.com/res/v1/web/search"
        SearchProviderKind.SERPER -> "https://google.serper.dev/search"
        SearchProviderKind.CUSTOM -> ""
    }

@Serializable
data class SearchProviderConfig(
    val kind: SearchProviderKind = SearchProviderKind.TAVILY,
    val baseUrl: String = kind.defaultBaseUrl,
    val apiKey: String = ""
)

@Serializable
data class WebSearchSettings(
    val enabled: Boolean = false,
    val provider: SearchProviderConfig = SearchProviderConfig(),
    val maxResults: Int = 3,
    val automationAlsoSearch: Boolean = false
)

@Serializable
data class AppSettings(
    val providers: List<ModelProviderConfig> = listOf(defaultProvider()),
    val selectedProviderId: String? = providers.firstOrNull()?.id,
    val assistants: List<AssistantPreset> = defaultAssistants(),
    val selectedAssistantId: String? = assistants.firstOrNull()?.id,
    val processingRoute: ProcessingRoute = ProcessingRoute.OCR_THEN_LLM,
    val screenCaptureMethod: ScreenCaptureMethod = ScreenCaptureMethod.MEDIA_PROJECTION,
    val automation: AutomationSettings = AutomationSettings(),
    val trustAllHttpsCertificates: Boolean = false,
    val textModelSelection: ModelSelection = ModelSelection(),
    val visionModelSelection: ModelSelection = ModelSelection(),
    val ocrModelSelection: ModelSelection = ModelSelection(),
    val localOcr: LocalOcrSettings = LocalOcrSettings(),
    val kirari: KirariSettings = KirariSettings(),
    val webSearch: WebSearchSettings = WebSearchSettings(),
    val lastResult: ProcessingResult? = null,
    val history: List<ProcessingResult> = emptyList()
)

@Serializable
data class ProcessingResult(
    val id: String = UUID.randomUUID().toString(),
    val assistantName: String,
    val route: ProcessingRoute,
    val status: ProcessingStatus = ProcessingStatus.SUCCESS,
    val modelSummary: String = "",
    val detail: String = "",
    val extractedText: String = "",
    val answer: String = "",
    val automationThought: String = "",
    val automationAction: AutomationActionRecord? = null,
    val screenshotBase64: String? = null,
    val screenshotPath: String? = null,
    val screenshotPaths: List<String> = emptyList(),
    val events: List<ProcessingEvent> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val allScreenshotPaths: List<String>
        get() = screenshotPaths.ifEmpty {
            screenshotPath?.let { listOf(it) } ?: emptyList()
        }
}

@Serializable
data class ProcessingEvent(
    val title: String,
    val detail: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
enum class ProcessingStatus {
    RUNNING,
    SUCCESS,
    ERROR,
    TIMEOUT
}

@Serializable
data class AutomationActionRecord(
    val type: AutomationActionType,
    val text: String
)

@Serializable
enum class AutomationActionType {
    SET_CLIPBOARD,
    SHOW_BUBBLE_LETTERS
}

fun defaultProvider(): ModelProviderConfig = ModelProviderConfig()

fun kirariProvider(settings: KirariSettings = KirariSettings()): ModelProviderConfig = ModelProviderConfig(
    id = KIRARI_PROVIDER_ID,
    name = "The Kirari Network",
    kind = ProviderKind.KIRARI_NETWORK,
    baseUrl = settings.serverUrl.trim(),
    apiKey = settings.auth.accessToken,
    chatModel = "",
    visionModel = "",
    ocrModel = ""
)

fun defaultAssistants(): List<AssistantPreset> = listOf(defaultAssistant())

fun defaultAssistant(): AssistantPreset = problemSolvingAssistantPreset()

private fun problemSolvingAssistantPreset(): AssistantPreset = AssistantPreset(
    name = "题目解答助手",
    ocrPrompt = "请准确提取图片中的题目、选项、公式和注释，尽量保持原有结构，不要解释。",
    textPrompt = "你是题目解答助手。请先识别题目内容，再给出解题思路、关键知识点和答案。",
    visionPrompt = "你是题目解答助手。请直接阅读图片中的题目内容，给出解题思路、关键知识点和答案。"
)

private fun legacyChatSummaryAssistantPreset(): AssistantPreset = AssistantPreset(
    name = "聊天记录总结助手",
    ocrPrompt = "请准确提取图片中的全部文字，按原有结构输出，不要解释。",
    textPrompt = "你是聊天记录总结助手。请提炼重点、待办、情绪倾向，并用简洁中文输出。",
    visionPrompt = "你是聊天记录总结助手。请直接阅读图片内容，提炼重点、待办、情绪倾向，并用简洁中文输出。"
)

private fun legacyDefaultAssistants(): List<AssistantPreset> = listOf(
    legacyChatSummaryAssistantPreset(),
    problemSolvingAssistantPreset()
)

fun AssistantPreset.previewPrompt(): String {
    return textPrompt.ifBlank {
        visionPrompt.ifBlank {
            ocrPrompt
        }
    }
}

val ModelPurpose.displayName: String
    get() = when (this) {
        ModelPurpose.OCR -> "OCR"
        ModelPurpose.TEXT -> "文本"
        ModelPurpose.VISION -> "多模态"
    }

val ScreenCaptureMethod.displayName: String
    get() = when (this) {
        ScreenCaptureMethod.MEDIA_PROJECTION -> "系统屏幕录制"
        ScreenCaptureMethod.SHIZUKU_ADB -> "Shizuku + adb 截屏"
    }

val ScreenCaptureMethod.description: String
    get() = when (this) {
        ScreenCaptureMethod.MEDIA_PROJECTION -> "通过系统屏幕录制权限建立截图会话，兼容当前悬浮球流程。"
        ScreenCaptureMethod.SHIZUKU_ADB -> "通过 Shizuku 授权后调用 adb 截屏，避免每次启动都请求屏幕录制。"
    }

fun AppSettings.modelSelectionFor(purpose: ModelPurpose): ModelSelection = when (purpose) {
    ModelPurpose.OCR -> ocrModelSelection
    ModelPurpose.TEXT -> textModelSelection
    ModelPurpose.VISION -> visionModelSelection
}

fun AppSettings.availableProviders(): List<ModelProviderConfig> {
    val custom = providers.filterNot { it.id == KIRARI_PROVIDER_ID }
    return if (BuildConfig.SHOW_KIRARI_ENTRY) {
        listOf(kirariProvider(kirari)) + custom
    } else {
        custom
    }
}

fun AppSettings.resolveModelProvider(purpose: ModelPurpose): ModelProviderConfig? {
    val selection = modelSelectionFor(purpose)
    if (purpose == ModelPurpose.OCR && selection.isLocalOcrSelection()) return null
    return availableProviders().firstOrNull { it.id == selection.providerId }
}

fun AppSettings.resolveModelName(purpose: ModelPurpose): String {
    val selection = modelSelectionFor(purpose)
    return if (purpose == ModelPurpose.OCR && selection.isLocalOcrSelection()) {
        localOcr.displayName
    } else {
        selection.model
    }
}

fun AppSettings.normalize(): AppSettings {
    val normalizedProviders = providers.filterNot { it.id == KIRARI_PROVIDER_ID }
    val normalizedAssistants = normalizeAssistants(
        assistants = assistants,
        selectedAssistantId = selectedAssistantId
    )
    val normalizedKirari = kirari.normalize()
    val availableProviders = copy(
        providers = normalizedProviders,
        kirari = normalizedKirari
    ).availableProviders()
    val fallbackProvider = availableProviders.firstOrNull { it.id == selectedProviderId } ?: availableProviders.firstOrNull()
    return copy(
        providers = normalizedProviders,
        kirari = normalizedKirari,
        selectedProviderId = selectedProviderId
            ?.takeIf { candidate -> availableProviders.any { it.id == candidate } }
            ?: fallbackProvider?.id,
        assistants = normalizedAssistants.assistants,
        selectedAssistantId = normalizedAssistants.selectedAssistantId,
        textModelSelection = textModelSelection.normalize(
            providers = availableProviders,
            fallbackProvider = fallbackProvider,
            fallbackModel = fallbackProvider?.chatModel.orEmpty()
        ),
        visionModelSelection = visionModelSelection.normalize(
            providers = availableProviders,
            fallbackProvider = fallbackProvider,
            fallbackModel = fallbackProvider?.visionModel.orEmpty()
        ),
        ocrModelSelection = ocrModelSelection.normalize(
            providers = availableProviders,
            fallbackProvider = fallbackProvider,
            fallbackModel = fallbackProvider?.ocrModel?.ifBlank {
                fallbackProvider.visionModel
            } ?: fallbackProvider?.visionModel.orEmpty()
        )
    )
}

private fun KirariSettings.normalize(): KirariSettings {
    val resolvedServerUrl = serverUrl
        .trim()
        .prependHttpSchemeIfMissing()
        .ifBlank { BuildConfig.KIRARI_SERVER_URL.trim() }
    return copy(serverUrl = resolvedServerUrl)
}

private fun String.prependHttpSchemeIfMissing(): String {
    val trimmed = trim()
    if (trimmed.isBlank()) return ""
    return if ("://" in trimmed) trimmed else "http://$trimmed"
}

private data class NormalizedAssistants(
    val assistants: List<AssistantPreset>,
    val selectedAssistantId: String?
)

private fun normalizeAssistants(
    assistants: List<AssistantPreset>,
    selectedAssistantId: String?
): NormalizedAssistants {
    val normalizedAssistants = when {
        assistants.isEmpty() -> defaultAssistants()
        assistants.matchesLegacyDefaultAssistants() -> defaultAssistants()
        else -> assistants
    }
    val normalizedSelectedAssistantId = normalizedAssistants.firstOrNull { it.id == selectedAssistantId }?.id
        ?: normalizedAssistants.firstOrNull()?.id
    return NormalizedAssistants(
        assistants = normalizedAssistants,
        selectedAssistantId = normalizedSelectedAssistantId
    )
}

private fun List<AssistantPreset>.matchesLegacyDefaultAssistants(): Boolean {
    val legacyDefaults = legacyDefaultAssistants()
    return size == legacyDefaults.size && zip(legacyDefaults).all { (current, legacy) ->
        current.samePromptProfileAs(legacy)
    }
}

private fun AssistantPreset.samePromptProfileAs(other: AssistantPreset): Boolean {
    return name == other.name &&
        ocrPrompt == other.ocrPrompt &&
        textPrompt == other.textPrompt &&
        visionPrompt == other.visionPrompt
}

private fun ModelSelection.normalize(
    providers: List<ModelProviderConfig>,
    fallbackProvider: ModelProviderConfig?,
    fallbackModel: String
): ModelSelection {
    if (isLocalOcrSelection()) {
        return copy(
            providerId = LOCAL_OCR_PROVIDER_ID,
            model = model.ifBlank { LOCAL_OCR_MODEL_ID }
        )
    }
    val currentProvider = providers.firstOrNull { it.id == providerId }
    return when {
        currentProvider != null && model.isNotBlank() -> this
        currentProvider != null -> copy(model = fallbackModelFrom(currentProvider, fallbackModel))
        fallbackProvider != null -> ModelSelection(
            providerId = fallbackProvider.id,
            model = model.ifBlank { fallbackModel }
        )
        else -> this
    }
}

private fun fallbackModelFrom(provider: ModelProviderConfig, fallbackModel: String): String {
    return fallbackModel.ifBlank { provider.chatModel.ifBlank { provider.visionModel.ifBlank { provider.ocrModel } } }
}

fun ModelSelection.isLocalOcrSelection(): Boolean = providerId == LOCAL_OCR_PROVIDER_ID
