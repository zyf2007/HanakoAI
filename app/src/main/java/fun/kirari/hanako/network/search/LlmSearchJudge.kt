package `fun`.kirari.hanako.network.search

import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.network.UnifiedLLMClient
import `fun`.kirari.llm.core.LlmEvent
import kotlin.coroutines.cancellation.CancellationException

/**
 * [SearchJudge] 的默认实现，基于 LLM 输出做判断。
 */
internal class LlmSearchJudge(
    private val llmClient: UnifiedLLMClient
) : SearchJudge {

    private val tag = "HanakoLlmSearchJudge"

    override suspend fun decide(
        provider: ModelProviderConfig,
        model: String,
        questionText: String,
        trustAllHttps: Boolean,
        firstDeltaTimeoutMillis: Long
    ): SearchDecision {
        val response = try {
            collectLlmResponse(provider, model, questionText, trustAllHttps, firstDeltaTimeoutMillis)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            AppDebugLogStore.e(tag, "LLM 判断失败: ${e.message}")
            return SearchDecision(shouldSearch = false, keywords = null, failed = true)
        }

        val decision = parseResponse(response)
        if (decision.shouldSearch) {
            AppDebugLogStore.i(tag, "LLM 判断需要搜索，关键词: ${decision.keywords}")
        } else if (decision.failed) {
            AppDebugLogStore.e(tag, "LLM 判断需要搜索但未提供关键词")
        } else {
            AppDebugLogStore.i(tag, "LLM 判断无需搜索: ${response.trim()}")
        }
        return decision
    }

    private suspend fun collectLlmResponse(
        provider: ModelProviderConfig,
        model: String,
        questionText: String,
        trustAllHttps: Boolean,
        firstDeltaTimeoutMillis: Long
    ): String {
        val text = StringBuilder()
        llmClient.stream(
            provider = provider,
            model = model,
            systemPrompt = searchJudgePrompt(),
            userPrompt = questionText,
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = trustAllHttps
        ).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> text.append(event.text)
                is LlmEvent.ToolCall -> {}
                is LlmEvent.Done -> {}
            }
        }
        return text.toString()
    }

    companion object {
        internal fun parseResponse(response: String): SearchDecision {
            val trimmed = response.trim()

            val needSearch = extractJsonBoolean(trimmed, "need_search")
            if (needSearch != null) {
                if (!needSearch) {
                    return SearchDecision(shouldSearch = false, keywords = null)
                }
                val keywords = extractJsonString(trimmed, "keywords")
                return if (keywords.isNullOrBlank()) {
                    SearchDecision(shouldSearch = false, keywords = null, failed = true)
                } else {
                    SearchDecision(shouldSearch = true, keywords = keywords)
                }
            }

            if (trimmed.startsWith("SEARCH", ignoreCase = true)) {
                val keywords = trimmed.lineSequence()
                    .drop(1)
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()
                return if (keywords.isBlank()) {
                    SearchDecision(shouldSearch = false, keywords = null, failed = true)
                } else {
                    SearchDecision(shouldSearch = true, keywords = keywords)
                }
            }

            return SearchDecision(shouldSearch = false, keywords = null)
        }

        private fun extractJsonBoolean(text: String, key: String): Boolean? {
            val patterns = listOf(
                Regex(""""$key"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE),
                Regex("""$key\s*[:=]\s*(true|false)""", RegexOption.IGNORE_CASE)
            )
            for (pattern in patterns) {
                val match = pattern.find(text) ?: continue
                return match.groupValues[1].equals("true", ignoreCase = true)
            }
            return null
        }

        private fun extractJsonString(text: String, key: String): String? {
            val patterns = listOf(
                Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""", RegexOption.IGNORE_CASE),
                Regex("""$key\s*[:=]\s*"((?:[^"\\]|\\.)*)"""", RegexOption.IGNORE_CASE)
            )
            for (pattern in patterns) {
                val match = pattern.find(text) ?: continue
                return match.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim()
            }
            return null
        }

        private fun searchJudgePrompt(): String = """
            你是一个搜索判断器。分析题目是否需要联网搜索最新信息才能准确回答。

            需要搜索的情况（包括但不限于）：
            - 时政新闻、政策法规更新、国际事件
            - 体育赛事结果、最新排名
            - 最新统计数据、经济数据、人口数据
            - 人物最新动态、公众人物现状
            - 科技产品发布、软件版本、模型发布、公司最新动态
            - 任何涉及"最新"、"当前"、"现在"、"今年"、"近期"等时间限定词的事实性问题
            - 考试中涉及的时事政治、常识题中关于近期事件的部分

            不需要搜索的情况：
            - 数学计算、公式推导
            - 物理化学生物等自然科学原理
            - 历史事件（发生在多年以前、有定论的）
            - 语言翻译、语法
            - 编程逻辑、算法

            当题目中明确出现"联网搜索"字样，或用户明确要求查询最新信息时，必须搜索。

            请只输出一个 JSON 对象，不要输出其他内容：
            {"need_search": true, "keywords": "2-5个搜索关键词"}
            或
            {"need_search": false, "keywords": ""}
        """.trimIndent()
    }
}
