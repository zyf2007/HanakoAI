package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.KIRARI_PROVIDER_ID
import `fun`.kirari.hanako.data.SettingsStore
import `fun`.kirari.llm.core.ConnectionTestResult
import `fun`.kirari.llm.core.ProviderCatalog
import `fun`.kirari.llm.core.ProviderConfig
import `fun`.kirari.llm.core.ProviderKind
import `fun`.kirari.llm.core.RemoteModelOption

internal class ProviderModelsApi(
    private val clientProvider: NetworkClientProvider = NetworkClientProvider(),
    private val kirariAuthManager: KirariAuthManager? = null,
    private val settingsStore: SettingsStore? = null
) {
    private val coreApi = `fun`.kirari.llm.core.ProviderModelsApi(clientProvider)

    suspend fun getCatalog(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ProviderCatalog = coreApi.getCatalog(
        provider = provider.toCoreProvider(trustAllHttpsCertificates),
        trustAllHttpsCertificates = trustAllHttpsCertificates
    )

    suspend fun listModels(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): List<RemoteModelOption> = coreApi.listModels(
        provider = provider.toCoreProvider(trustAllHttpsCertificates),
        trustAllHttpsCertificates = trustAllHttpsCertificates
    )

    suspend fun testConnection(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ConnectionTestResult = coreApi.testConnection(
        provider = provider.toCoreProvider(trustAllHttpsCertificates),
        trustAllHttpsCertificates = trustAllHttpsCertificates
    )

    private suspend fun ModelProviderConfig.toCoreProvider(
        trustAllHttpsCertificates: Boolean
    ): ProviderConfig {
        return when (kind) {
            ProviderKind.KIRARI_NETWORK -> {
                val manager = requireNotNull(kirariAuthManager) { "KirariAuthManager is required for Kirari provider" }
                val store = requireNotNull(settingsStore) { "SettingsStore is required for Kirari provider" }
                val settings = store.read()
                val token = manager.ensureValidAccessToken(settings, trustAllHttpsCertificates)
                require(token.isNotBlank()) { "请先登录 The Kirari Network" }
                val baseUrl = settings.availableKirariServerUrl()
                ProviderConfig(
                    kind = ProviderKind.KIRARI_NETWORK,
                    baseUrl = baseUrl.trimEnd('/') + "/api/llm",
                    apiKey = token
                )
            }

            else -> ProviderConfig(
                kind = kind,
                baseUrl = baseUrl,
                apiKey = apiKey
            )
        }
    }

    private fun `fun`.kirari.hanako.data.AppSettings.availableKirariServerUrl(): String {
        return kirari.serverUrl.trim().ifBlank {
            providers.firstOrNull { it.id == KIRARI_PROVIDER_ID }?.baseUrl?.trim().orEmpty()
        }
    }
}
