package `fun`.kirari.hanako.network.search

/**
 * 一次搜索请求的参数。
 *
 * @param apiKey        搜索引擎 API 密钥
 * @param baseUrl       搜索引擎接口地址
 * @param query         搜索关键词
 * @param maxResults    期望返回的最大结果数
 * @param trustAllHttps 是否信任所有 HTTPS 证书
 */
internal data class SearchRequest(
    val apiKey: String,
    val baseUrl: String,
    val query: String,
    val maxResults: Int,
    val trustAllHttps: Boolean = false
)

/**
 * 单条搜索结果。
 *
 * @param title   结果标题
 * @param content 结果摘要正文
 */
internal data class SearchHit(
    val title: String,
    val content: String
)

/**
 * 搜索引擎适配器策略接口。
 *
 * 每个具体搜索引擎（Tavily / Brave / Serper / Custom）实现此接口，
 * 由 [SearchClient] 根据 [SearchProviderKind] 工厂分发。
 */
internal interface SearchEngineAdapter {
    suspend fun search(request: SearchRequest): SearchResponse
}
