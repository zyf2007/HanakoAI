package `fun`.kirari.hanako.network.search

import `fun`.kirari.hanako.data.SearchProviderConfig
import `fun`.kirari.hanako.data.SearchProviderKind
import `fun`.kirari.hanako.network.NetworkClientProvider
import kotlinx.serialization.json.Json

/**
 * [SearchClient] 的默认实现。
 *
 * 根据 [SearchProviderConfig.kind] 工厂分发到对应的 [SearchEngineAdapter] 实现。
 *
 * @param clientProvider 共享的 HTTP 客户端提供者（必须由外部传入，避免创建独立连接池）
 * @param json           JSON 解析器
 */
internal class SearchClientImpl(
    private val clientProvider: NetworkClientProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SearchClient {

    override suspend fun search(
        config: SearchProviderConfig,
        query: String,
        maxResults: Int,
        trustAllHttps: Boolean
    ): SearchResponse {
        val adapter: SearchEngineAdapter = when (config.kind) {
            SearchProviderKind.TAVILY -> TavilySearchAdapter(clientProvider, json)
            SearchProviderKind.BRAVE -> BraveSearchAdapter(clientProvider, json)
            SearchProviderKind.SERPER -> SerperSearchAdapter(clientProvider, json)
            SearchProviderKind.CUSTOM -> CustomSearchAdapter(clientProvider, json)
        }
        return adapter.search(
            SearchRequest(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                query = query,
                maxResults = maxResults,
                trustAllHttps = trustAllHttps
            )
        )
    }
}
