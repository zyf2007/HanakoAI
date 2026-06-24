package `fun`.kirari.hanako.network.search

import `fun`.kirari.hanako.data.ModelProviderConfig

/**
 * 搜索判断器接口。
 *
 * 负责分析题目文本，决定是否需要搜索，并返回搜索关键词。
 * 独立于具体 LLM 实现，便于测试和未来替换策略。
 */
internal interface SearchJudge {
    suspend fun decide(
        provider: ModelProviderConfig,
        model: String,
        questionText: String,
        trustAllHttps: Boolean,
        firstDeltaTimeoutMillis: Long = 30_000L
    ): SearchDecision
}

/**
 * 搜索判断结果。
 *
 * @param shouldSearch 是否需要搜索
 * @param keywords     搜索关键词（仅当 [shouldSearch] 为 true 时有效）
 * @param failed       判断过程是否出错（如 LLM 调用失败），区别于"判断不需要搜索"
 */
internal data class SearchDecision(
    val shouldSearch: Boolean,
    val keywords: String? = null,
    val failed: Boolean = false
)
