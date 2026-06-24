package `fun`.kirari.hanako.network.search

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.WebSearchSettings
import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlin.coroutines.cancellation.CancellationException

/**
 * 搜索编排器的输入上下文。
 *
 * @param questionText  题目文本（OCR 结果）
 * @param settings      联网搜索设置
 * @param llmProvider   用于关键词判断/提取的 LLM 提供方
 * @param llmModel      用于关键词判断/提取的 LLM 模型名
 * @param trustAllHttps 是否信任所有 HTTPS 证书
 * @param isAutomation  是否为自动模式调用
 */
internal data class SearchContext(
    val questionText: String,
    val settings: WebSearchSettings,
    val llmProvider: ModelProviderConfig,
    val llmModel: String,
    val trustAllHttps: Boolean,
    val isAutomation: Boolean,
    val firstDeltaTimeoutMillis: Long = 30_000L
)

/**
 * 搜索被跳过的原因。
 */
internal enum class SearchSkipReason(val displayText: String) {
    DISABLED("开关未开启"),
    AUTOMATION_DISABLED("自动模式未开启搜索"),
    API_KEY_MISSING("API Key 未配置"),
    API_URL_MISSING("API URL 未配置"),
    LLM_JUDGE_NO_SEARCH("LLM 判断无需搜索"),
    LLM_JUDGE_FAILED("LLM 判断失败"),
    LLM_NO_KEYWORDS("LLM 未提供搜索关键词"),
    SEARCH_NO_RESULTS("搜索无结果"),
    SEARCH_API_ERROR("搜索 API 错误")
}

/**
 * 搜索编排器的输出结果。
 *
 * @param performed     是否实际执行了搜索 API 调用
 * @param results       搜索结果列表（空列表 = 搜索失败或无结果）
 * @param formattedText 格式化后可注入 prompt 的文本，null 表示无可用搜索结果
 * @param keywords      提取的关键词
 * @param skipReason    未搜索的原因，null 表示搜索已执行
 */
internal data class SearchOutcome(
    val performed: Boolean,
    val results: List<SearchHit>,
    val formattedText: String?,
    val keywords: String?,
    val skipReason: SearchSkipReason?
)

/**
 * 搜索编排器：搜索层对外的唯一接口。
 *
 * 封装完整的"前置检查 -> LLM 判断 -> 搜索 -> 格式化"流程。
 * Pipeline 只调 [execute]，不关心内部细节。
 *
 * @param searchClient 搜索客户端
 * @param searchJudge  搜索判断器
 */
internal class SearchOrchestrator(
    private val searchClient: SearchClient,
    private val searchJudge: SearchJudge
) {
    private val tag = "HanakoSearchOrchestrator"

    /**
     * 执行搜索编排。
     *
     * 流程：
     * 1. 前置检查（开关、自动模式、API Key、API URL）
     * 2. 搜索判断器决定是否需要搜索 + 提取关键词
     * 3. 执行搜索 API 调用
     * 4. 格式化搜索结果
     *
     * @return 搜索结果，任何步骤失败都不抛异常，返回降级的 [SearchOutcome]
     */
    suspend fun execute(context: SearchContext): SearchOutcome {
        if (!context.settings.enabled) {
            return skip(SearchSkipReason.DISABLED)
        }
        if (context.isAutomation && !context.settings.automationAlsoSearch) {
            return skip(SearchSkipReason.AUTOMATION_DISABLED)
        }
        if (context.settings.provider.apiKey.isBlank()) {
            return skip(SearchSkipReason.API_KEY_MISSING)
        }
        if (context.settings.provider.baseUrl.isBlank()) {
            return skip(SearchSkipReason.API_URL_MISSING)
        }

        val decision = searchJudge.decide(
            provider = context.llmProvider,
            model = context.llmModel,
            questionText = context.questionText,
            trustAllHttps = context.trustAllHttps,
            firstDeltaTimeoutMillis = context.firstDeltaTimeoutMillis
        )
        if (decision.failed) {
            return skip(SearchSkipReason.LLM_JUDGE_FAILED)
        }
        if (!decision.shouldSearch) {
            return skip(SearchSkipReason.LLM_JUDGE_NO_SEARCH)
        }
        val keywords = decision.keywords
            ?: return skip(SearchSkipReason.LLM_NO_KEYWORDS)

        val searchResponse = try {
            searchClient.search(
                config = context.settings.provider,
                query = keywords,
                maxResults = context.settings.maxResults,
                trustAllHttps = context.trustAllHttps
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            AppDebugLogStore.e(tag, "搜索 API 调用异常: ${e.message}")
            return SearchOutcome(
                performed = true,
                results = emptyList(),
                formattedText = null,
                keywords = keywords,
                skipReason = SearchSkipReason.SEARCH_API_ERROR
            )
        }

        if (searchResponse.errorCode != 0) {
            AppDebugLogStore.e(tag, "搜索 API 错误: ${searchResponse.errorCode} ${searchResponse.errorMessage}")
            return SearchOutcome(
                performed = true,
                results = emptyList(),
                formattedText = null,
                keywords = keywords,
                skipReason = SearchSkipReason.SEARCH_API_ERROR
            )
        }

        val hits = searchResponse.hits
        if (hits.isEmpty()) {
            return SearchOutcome(
                performed = true,
                results = emptyList(),
                formattedText = null,
                keywords = keywords,
                skipReason = SearchSkipReason.SEARCH_NO_RESULTS
            )
        }

        val formatted = formatResults(hits)
        AppDebugLogStore.i(tag, "搜索完成，获取 ${hits.size} 条结果")
        return SearchOutcome(
            performed = true,
            results = hits,
            formattedText = formatted,
            keywords = keywords,
            skipReason = null
        )
    }

    private fun formatResults(hits: List<SearchHit>): String {
        val sb = StringBuilder()
        sb.append("以下是网络搜索结果（供参考，可能不准确）：")
        hits.forEachIndexed { index, hit ->
            sb.append("\n[").append(index + 1).append("] ").append(hit.title)
            sb.append("\n").append(hit.content)
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun skip(reason: SearchSkipReason): SearchOutcome {
        AppDebugLogStore.i(tag, "跳过搜索: ${reason.displayText}")
        return SearchOutcome(
            performed = false,
            results = emptyList(),
            formattedText = null,
            keywords = null,
            skipReason = reason
        )
    }
}
