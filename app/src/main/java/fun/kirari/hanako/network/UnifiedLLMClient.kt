package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.KIRARI_PROVIDER_ID
import `fun`.kirari.hanako.data.SettingsStore
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.llm.core.LlmClient
import `fun`.kirari.llm.core.LlmEvent
import `fun`.kirari.llm.core.ProviderConfig
import `fun`.kirari.llm.core.ProviderKind
import `fun`.kirari.llm.core.StreamRequest
import `fun`.kirari.llm.core.ToolDef
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

internal class UnifiedLLMClient(
    private val clientProvider: NetworkClientProvider = NetworkClientProvider(),
    private val kirariAuthManager: KirariAuthManager? = null,
    private val settingsStore: SettingsStore? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val tag = "HanakoUnifiedLLM"
    private val coreClient = LlmClient(clientProvider, HanakoLlmLogger)

    suspend fun stream(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String> = emptyList(),
        tools: List<ToolDef>? = null,
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean = false
    ): Flow<LlmEvent> {
        AppDebugLogStore.i(tag, "stream provider=${provider.kind} model=$model imageCount=${imagesBase64.size} hasTools=${tools != null} trustAllHttps=$trustAllHttpsCertificates")
        val resolvedProvider = when (provider.kind) {
            ProviderKind.KIRARI_NETWORK -> {
                val manager = requireNotNull(kirariAuthManager) { "KirariAuthManager is required for Kirari provider" }
                val store = requireNotNull(settingsStore) { "SettingsStore is required for Kirari provider" }
                val settings = store.read()
                val accessToken = manager.ensureValidAccessToken(
                    settings = settings,
                    trustAllHttpsCertificates = trustAllHttpsCertificates
                )
                require(accessToken.isNotBlank()) { "请先登录 The Kirari Network" }
                val baseUrl = settings.availableKirariServerUrl()
                ProviderConfig(
                    kind = ProviderKind.KIRARI_NETWORK,
                    baseUrl = baseUrl.trimEnd('/') + "/api/llm",
                    apiKey = accessToken,
                    headers = mapOf("Accept" to "application/json, text/event-stream")
                )
            }
            else -> provider.toCoreProvider()
        }

        return coreClient.stream(
            StreamRequest(
                provider = resolvedProvider,
                model = model,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                imagesBase64 = imagesBase64,
                tools = tools,
                firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
                trustAllHttpsCertificates = trustAllHttpsCertificates
            )
        )
    }

    private fun ModelProviderConfig.toCoreProvider(): ProviderConfig {
        return ProviderConfig(
            kind = kind,
            baseUrl = baseUrl,
            apiKey = apiKey
        )
    }

    private fun `fun`.kirari.hanako.data.AppSettings.availableKirariServerUrl(): String {
        return kirari.serverUrl.trim().ifBlank {
            providers.firstOrNull { it.id == KIRARI_PROVIDER_ID }?.baseUrl?.trim().orEmpty()
        }
    }
}
