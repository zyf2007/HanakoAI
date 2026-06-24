package `fun`.kirari.hanako

import android.content.Context
import `fun`.kirari.hanako.data.SettingsRepository
import `fun`.kirari.hanako.data.SettingsStore
import `fun`.kirari.hanako.localocr.LocalOcrManager
import `fun`.kirari.hanako.network.KirariAuthManager
import `fun`.kirari.hanako.network.NetworkClientProvider
import `fun`.kirari.hanako.network.ProviderModelsApi
import `fun`.kirari.hanako.network.UnifiedLLMClient
import `fun`.kirari.hanako.network.search.LlmSearchJudge
import `fun`.kirari.hanako.network.search.SearchClientImpl
import `fun`.kirari.hanako.network.search.SearchOrchestrator

internal class AppContainer(appContext: Context) {
    val networkClientProvider = NetworkClientProvider()
    val settingsStore = SettingsStore(appContext)
    val kirariAuthManager = KirariAuthManager(
        settingsStore = settingsStore,
        clientProvider = networkClientProvider
    )
    val providerModelsApi = ProviderModelsApi(
        clientProvider = networkClientProvider,
        kirariAuthManager = kirariAuthManager,
        settingsStore = settingsStore
    )
    val unifiedLLMClient = UnifiedLLMClient(
        clientProvider = networkClientProvider,
        kirariAuthManager = kirariAuthManager,
        settingsStore = settingsStore
    )
    val localOcrManager = LocalOcrManager(appContext)
    val settingsRepository = SettingsRepository(settingsStore)
    val searchOrchestrator = SearchOrchestrator(
        SearchClientImpl(networkClientProvider),
        LlmSearchJudge(unifiedLLMClient)
    )
}
