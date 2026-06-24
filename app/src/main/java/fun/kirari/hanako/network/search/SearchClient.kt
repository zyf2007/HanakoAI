package `fun`.kirari.hanako.network.search

import `fun`.kirari.hanako.data.SearchProviderConfig

/**
 * 搜索结果响应。
 *
 * @param hits        搜索结果列表
 * @param errorCode   HTTP 错误码（仅当请求失败时有效，0 表示成功或无结果）
 * @param errorMessage 错误描述（仅当请求失败时有效）
 */
internal data class SearchResponse(
    val hits: List<SearchHit>,
    val errorCode: Int = 0,
    val errorMessage: String? = null
)

/**
 * 搜索客户端接口。
 *
 * 统一入口，屏蔽底层不同搜索引擎的差异。
 */
internal interface SearchClient {
    suspend fun search(
        config: SearchProviderConfig,
        query: String,
        maxResults: Int,
        trustAllHttps: Boolean = false
    ): SearchResponse
}
