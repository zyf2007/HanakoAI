package `fun`.kirari.hanako.overlay

import android.content.Context
import android.graphics.Bitmap
import `fun`.kirari.hanako.automation.AutomationResult
import `fun`.kirari.hanako.automation.validateAutomationAction
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.AutomationActionRecord
import `fun`.kirari.hanako.data.AutomationActionType
import `fun`.kirari.hanako.data.LOCAL_OCR_PROVIDER_ID
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ProcessingEvent
import `fun`.kirari.hanako.data.ProcessingResult
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.ProcessingStatus
import `fun`.kirari.hanako.data.WebSearchSettings
import `fun`.kirari.hanako.data.resolveModelName
import `fun`.kirari.hanako.data.resolveModelProvider
import `fun`.kirari.hanako.data.saveToHistoryFile
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.localocr.LocalOcrManager
import `fun`.kirari.hanako.network.ToolDef
import `fun`.kirari.hanako.network.ToolRegistry
import `fun`.kirari.hanako.network.UnifiedLLMClient
import `fun`.kirari.hanako.network.search.SearchContext
import `fun`.kirari.hanako.network.search.SearchOrchestrator
import `fun`.kirari.hanako.network.search.SearchOutcome
import `fun`.kirari.hanako.network.search.SearchSkipReason
import `fun`.kirari.llm.core.LlmEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal class ProcessingPipeline(
    private val appContext: Context,
    private val unifiedClient: UnifiedLLMClient,
    private val localOcrManager: LocalOcrManager,
    private val searchOrchestrator: SearchOrchestrator? = null
) {
    private val tag = "HanakoPipeline"

    data class ResolvedModels(
        val assistant: AssistantPreset,
        val ocrProvider: ModelProviderConfig?,
        val ocrModel: String,
        val textProvider: ModelProviderConfig?,
        val textModel: String,
        val visionProvider: ModelProviderConfig?,
        val visionModel: String,
        val firstDeltaTimeoutMillis: Long,
        val route: ProcessingRoute,
        val usingLocalOcr: Boolean,
        val trustAllHttpsCertificates: Boolean,
        val webSearchSettings: WebSearchSettings,
        val searchProvider: ModelProviderConfig?,
        val searchModel: String
    )

    fun resolveModels(state: OverlayUiState): ResolvedModels {
        val assistant = state.settings.assistants.firstOrNull { it.id == state.settings.selectedAssistantId }
            ?: error("请先配置助手")
        return ResolvedModels(
            assistant = assistant,
            ocrProvider = state.settings.resolveModelProvider(ModelPurpose.OCR),
            ocrModel = state.settings.resolveModelName(ModelPurpose.OCR),
            textProvider = state.settings.resolveModelProvider(ModelPurpose.TEXT),
            textModel = state.settings.resolveModelName(ModelPurpose.TEXT),
            visionProvider = state.settings.resolveModelProvider(ModelPurpose.VISION),
            visionModel = state.settings.resolveModelName(ModelPurpose.VISION),
            firstDeltaTimeoutMillis = state.settings.automation.autoModeTimeoutSeconds.coerceAtLeast(1) * 1000L,
            route = state.settings.processingRoute,
            usingLocalOcr = state.settings.ocrModelSelection.providerId == LOCAL_OCR_PROVIDER_ID,
            trustAllHttpsCertificates = state.settings.trustAllHttpsCertificates,
            webSearchSettings = state.settings.webSearch,
            searchProvider = state.settings.resolveModelProvider(ModelPurpose.TEXT),
            searchModel = state.settings.resolveModelName(ModelPurpose.TEXT)
        )
    }

    fun buildModelSummary(model: String, providerName: String?): String {
        val trimmedModel = model.trim()
        val trimmedProvider = providerName?.trim().orEmpty()
        if (trimmedModel.isBlank()) return ""
        return if (trimmedProvider.isBlank()) trimmedModel else "$trimmedModel（$trimmedProvider）"
    }

    fun createBaseResult(
        models: ResolvedModels,
        bitmaps: List<Bitmap>,
        detail: String
    ): Triple<ProcessingResult, String, List<String>> {
        val historyId = java.util.UUID.randomUUID().toString()
        val screenshotPaths = bitmaps.mapIndexed { index, bitmap ->
            bitmap.saveToHistoryFile(appContext, "${historyId}_$index")
        }
        val baseResult = ProcessingResult(
            id = historyId,
            assistantName = models.assistant.name,
            route = models.route,
            status = ProcessingStatus.RUNNING,
            modelSummary = when (models.route) {
                ProcessingRoute.OCR_THEN_LLM -> buildModelSummary(models.textModel, models.textProvider?.name)
                ProcessingRoute.MULTIMODAL_DIRECT -> buildModelSummary(models.visionModel, models.visionProvider?.name)
            },
            detail = detail,
            screenshotPath = screenshotPaths.firstOrNull(),
            screenshotPaths = screenshotPaths,
            events = listOf(ProcessingEvent(title = "请求开始", detail = "已创建处理记录"))
        )
        return Triple(baseResult, historyId, screenshotPaths)
    }

    fun validateOcrThenLlmModels(models: ResolvedModels) {
        if ((!models.usingLocalOcr && (models.ocrProvider == null || models.ocrModel.isBlank())) ||
            models.textProvider == null || models.textModel.isBlank()
        ) {
            error("请先在模型设置中配置 OCR 和文本模型")
        }
    }

    fun validateVisionModels(models: ResolvedModels) {
        if (models.visionProvider == null || models.visionModel.isBlank()) {
            error("请先在模型设置中配置多模态模型")
        }
    }

    suspend fun runLocalOcr(bitmap: Bitmap): String {
        return withContext(Dispatchers.Default) {
            localOcrManager.recognize(bitmap)
        }
    }

    private suspend fun collectTextStream(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String> = emptyList(),
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean,
        onDelta: (String) -> Unit
    ): String {
        val text = StringBuilder()
        unifiedClient.stream(
            provider = provider,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            imagesBase64 = imagesBase64,
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = trustAllHttpsCertificates
        ).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> {
                    text.append(event.text)
                    onDelta(event.text)
                }
                is LlmEvent.Done -> {}
                else -> {}
            }
        }
        return text.toString()
    }

    private data class StreamResult(
        val thought: String,
        val toolCall: LlmEvent.ToolCall?
    )

    private suspend fun collectToolStream(
        provider: ModelProviderConfig,
        model: String,
        systemPrompt: String,
        userPrompt: String,
        imagesBase64: List<String> = emptyList(),
        firstDeltaTimeoutMillis: Long,
        trustAllHttpsCertificates: Boolean,
        onThoughtDelta: (String) -> Unit
    ): StreamResult {
        val thought = StringBuilder()
        var toolCall: LlmEvent.ToolCall? = null
        unifiedClient.stream(
            provider = provider,
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            imagesBase64 = imagesBase64,
            tools = ToolRegistry.AUTOMATION_TOOLS,
            firstDeltaTimeoutMillis = firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = trustAllHttpsCertificates
        ).collect { event ->
            when (event) {
                is LlmEvent.TextDelta -> {
                    thought.append(event.text)
                    onThoughtDelta(event.text)
                }
                is LlmEvent.ToolCall -> {
                    toolCall = event
                }
                is LlmEvent.Done -> {}
            }
        }
        return StreamResult(thought.toString().trim(), toolCall)
    }

    private fun buildAutomationResult(streamResult: StreamResult): AutomationResult {
        val tc = streamResult.toolCall
        val thought = streamResult.thought

        // 尝试解析 LLM 的工具调用
        if (tc != null) {
            val rawText = tc.arguments["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val action = try {
                validateAutomationAction(tc.name, rawText)
            } catch (e: IllegalArgumentException) {
                // 工具名未知或参数格式不合法，降级到剪贴板
                null
            }
            if (action != null) {
                return AutomationResult(thought = thought, action = action)
            }
        }

        // 兜底：无工具调用或工具调用无效。
        // 尝试从推理文本中提取简洁答案（最后一行非空文本），
        // 避免把整个推理过程塞进剪贴板。
        val fallbackText = extractFallbackAnswer(thought)
        return AutomationResult(
            thought = thought,
            action = AutomationActionRecord(
                type = AutomationActionType.SET_CLIPBOARD,
                text = fallbackText
            )
        )
    }

    /**
     * 从 LLM 推理文本中提取兜底答案。
     *
     * 策略：取最后一个非空行，去掉常见的前缀标记（"答案:"、"所以"等）。
     * 如果整个推理为空则返回空字符串，不抛异常。
     */
    private fun extractFallbackAnswer(thought: String): String {
        val trimmed = thought.trim()
        if (trimmed.isBlank()) return ""

        // 尝试取最后一行非空文本
        val lastLine = trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .lastOrNull()
            ?: return trimmed

        // 去掉常见前缀
        val prefixes = listOf("答案：", "答案:", "所以：", "所以:", "因此：", "因此:", "最终答案：", "最终答案:")
        for (prefix in prefixes) {
            if (lastLine.startsWith(prefix, ignoreCase = true)) {
                val stripped = lastLine.substring(prefix.length).trim()
                if (stripped.isNotBlank()) return stripped
            }
        }
        return lastLine
    }

    suspend fun streamOcrThenChat(
        models: ResolvedModels,
        bitmaps: List<Bitmap>,
        onOcrDelta: (String) -> Unit,
        onAnswerDelta: (String) -> Unit
    ): Triple<String, String, SearchOutcome?> {
        AppDebugLogStore.i(tag, "streamOcrThenChat ocrModel=${models.ocrModel} textModel=${models.textModel} imageCount=${bitmaps.size}")
        val ocrTexts = mutableListOf<String>()
        bitmaps.forEach { bitmap ->
            val ocrText = if (models.usingLocalOcr) {
                runLocalOcr(bitmap)
            } else {
                collectTextStream(
                    provider = requireNotNull(models.ocrProvider),
                    model = models.ocrModel,
                    systemPrompt = models.assistant.ocrPrompt,
                    userPrompt = "请执行 OCR。",
                    imagesBase64 = listOf(bitmap.toBase64Jpeg()),
                    firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                    trustAllHttpsCertificates = models.trustAllHttpsCertificates,
                    onDelta = {}
                )
            }
            ocrTexts.add(ocrText)
        }
        val combinedOcrText = ocrTexts.joinToString("\n\n---\n\n")
        onOcrDelta(combinedOcrText)

        val searchOutcome = maybeSearch(models, combinedOcrText, isAutomation = false)
        val userPrompt = buildEnhancedUserPrompt(
            basePrompt = "以下是 OCR 结果，请完成任务：\n$combinedOcrText",
            searchOutcome = searchOutcome
        )
        val answer = collectTextStream(
            provider = requireNotNull(models.textProvider),
            model = models.textModel,
            systemPrompt = assistantPromptWithCopyMarker(models.assistant.textPrompt),
            userPrompt = userPrompt,
            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = models.trustAllHttpsCertificates,
            onDelta = onAnswerDelta
        )
        AppDebugLogStore.i(tag, "streamOcrThenChat success ocrLength=${combinedOcrText.length} answerLength=${answer.length}")
        return Triple(combinedOcrText, answer, searchOutcome)
    }

    suspend fun streamVisionDirect(
        models: ResolvedModels,
        bitmaps: List<Bitmap>,
        onAnswerDelta: (String) -> Unit
    ): String {
        AppDebugLogStore.i(tag, "streamVisionDirect visionModel=${models.visionModel} imageCount=${bitmaps.size}")
        val imagesBase64 = bitmaps.map { it.toBase64Jpeg() }
        val answer = collectTextStream(
            provider = requireNotNull(models.visionProvider),
            model = models.visionModel,
            systemPrompt = assistantPromptWithCopyMarker(models.assistant.visionPrompt),
            userPrompt = "请直接基于图片内容完成任务。",
            imagesBase64 = imagesBase64,
            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = models.trustAllHttpsCertificates,
            onDelta = onAnswerDelta
        )
        AppDebugLogStore.i(tag, "streamVisionDirect success answerLength=${answer.length}")
        return answer
    }

    suspend fun streamOcrThenAutomation(
        models: ResolvedModels,
        bitmaps: List<Bitmap>,
        onOcrDelta: (String) -> Unit,
        onThoughtDelta: (String) -> Unit
    ): Triple<String, AutomationResult, SearchOutcome?> {
        AppDebugLogStore.i(tag, "streamOcrThenAutomation ocrModel=${models.ocrModel} textModel=${models.textModel} imageCount=${bitmaps.size}")
        val ocrTexts = mutableListOf<String>()
        bitmaps.forEach { bitmap ->
            val ocrText = if (models.usingLocalOcr) {
                runLocalOcr(bitmap)
            } else {
                collectTextStream(
                    provider = requireNotNull(models.ocrProvider),
                    model = models.ocrModel,
                    systemPrompt = models.assistant.ocrPrompt,
                    userPrompt = "请执行 OCR。",
                    imagesBase64 = listOf(bitmap.toBase64Jpeg()),
                    firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
                    trustAllHttpsCertificates = models.trustAllHttpsCertificates,
                    onDelta = {}
                )
            }
            ocrTexts.add(ocrText)
        }
        val combinedOcrText = ocrTexts.joinToString("\n\n---\n\n")
        onOcrDelta(combinedOcrText)
        val searchOutcome = maybeSearch(models, combinedOcrText, isAutomation = true)
        val userPrompt = buildEnhancedUserPrompt(
            basePrompt = "以下是 OCR 结果，请先输出思考过程，再通过一次工具调用给出自动模式动作：\n$combinedOcrText",
            searchOutcome = searchOutcome
        )
        val streamResult = collectToolStream(
            provider = requireNotNull(models.textProvider),
            model = models.textModel,
            systemPrompt = automationSystemPrompt(models.assistant.textPrompt),
            userPrompt = userPrompt,
            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = models.trustAllHttpsCertificates,
            onThoughtDelta = onThoughtDelta
        )
        val result = buildAutomationResult(streamResult)
        AppDebugLogStore.i(tag, "streamOcrThenAutomation success ocrLength=${combinedOcrText.length} thoughtLength=${result.thought.length} action=${result.action.type}")
        return Triple(combinedOcrText, result, searchOutcome)
    }

    suspend fun streamAutomationDirect(
        models: ResolvedModels,
        bitmaps: List<Bitmap>,
        onThoughtDelta: (String) -> Unit
    ): AutomationResult {
        AppDebugLogStore.i(tag, "streamAutomationDirect visionModel=${models.visionModel} imageCount=${bitmaps.size}")
        val imagesBase64 = bitmaps.map { it.toBase64Jpeg() }
        val streamResult = collectToolStream(
            provider = requireNotNull(models.visionProvider),
            model = models.visionModel,
            systemPrompt = automationSystemPrompt(models.assistant.visionPrompt),
            userPrompt = "请根据整张屏幕截图先输出思考过程，再通过一次工具调用给出自动模式动作。",
            imagesBase64 = imagesBase64,
            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = models.trustAllHttpsCertificates,
            onThoughtDelta = onThoughtDelta
        )
        val result = buildAutomationResult(streamResult)
        AppDebugLogStore.i(tag, "streamAutomationDirect success thoughtLength=${result.thought.length} action=${result.action.type} actionText=${result.action.text}")
        return result
    }

    /**
     * 如果搜索编排器存在且设置已启用，执行联网搜索。
     * 任何失败都不阻断主流程，返回 [SearchOutcome] 供调用方决定如何使用。
     */
    private suspend fun maybeSearch(
        models: ResolvedModels,
        questionText: String,
        isAutomation: Boolean
    ): SearchOutcome? {
        val orchestrator = searchOrchestrator ?: return null
        if (!models.webSearchSettings.enabled) return null
        val provider = models.searchProvider ?: return null
        if (models.searchModel.isBlank()) return null
        return orchestrator.execute(
            SearchContext(
                questionText = questionText,
                settings = models.webSearchSettings,
                llmProvider = provider,
                llmModel = models.searchModel,
                trustAllHttps = models.trustAllHttpsCertificates,
                isAutomation = isAutomation,
                firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis
            )
        )
    }

    /**
     * 将搜索结果注入到 user prompt 前面。
     */
    private fun buildEnhancedUserPrompt(
        basePrompt: String,
        searchOutcome: SearchOutcome?
    ): String {
        if (searchOutcome?.formattedText == null) return basePrompt
        return "${searchOutcome.formattedText}\n\n$basePrompt"
    }

    /**
     * 将搜索结果转换成处理事件（用于历史记录展示）。
     */
    fun searchEvent(outcome: SearchOutcome?): ProcessingEvent? {
        if (outcome == null) return null
        return if (outcome.performed && outcome.results.isNotEmpty()) {
            ProcessingEvent(
                title = "联网搜索完成",
                detail = "关键词：${outcome.keywords}，获取 ${outcome.results.size} 条结果"
            )
        } else {
            outcome.skipReason?.let {
                ProcessingEvent(
                    title = "联网搜索已跳过",
                    detail = it.displayText
                )
            }
        }
    }

    fun buildChatResult(
        base: ProcessingResult,
        models: ResolvedModels,
        ocrText: String,
        answer: String,
        historyId: String,
        screenshotPaths: List<String>,
        searchOutcome: `fun`.kirari.hanako.network.search.SearchOutcome? = null
    ): ProcessingResult {
        val events = base.events.toMutableList()
        if (models.route == ProcessingRoute.OCR_THEN_LLM) {
            events.add(ProcessingEvent(title = "OCR 完成", detail = "已提取 ${ocrText.length} 个字符"))
        }
        searchEvent(searchOutcome)?.let { events.add(it) }
        events.add(ProcessingEvent(title = "答案完成", detail = "已生成 ${answer.length} 个字符"))
        return ProcessingResult(
            id = historyId,
            assistantName = models.assistant.name,
            route = models.route,
            status = ProcessingStatus.SUCCESS,
            modelSummary = when (models.route) {
                ProcessingRoute.OCR_THEN_LLM -> buildModelSummary(models.textModel, models.textProvider?.name)
                ProcessingRoute.MULTIMODAL_DIRECT -> buildModelSummary(models.visionModel, models.visionProvider?.name)
            },
            detail = "处理完成",
            extractedText = ocrText,
            answer = answer,
            screenshotPath = screenshotPaths.firstOrNull(),
            screenshotPaths = screenshotPaths,
            events = events,
            createdAtMillis = base.createdAtMillis
        )
    }

    fun buildAutomationResult(
        base: ProcessingResult,
        models: ResolvedModels,
        ocrText: String,
        automationResult: AutomationResult,
        historyId: String,
        screenshotPaths: List<String>,
        searchOutcome: `fun`.kirari.hanako.network.search.SearchOutcome? = null
    ): Pair<AutomationActionRecord, ProcessingResult> {
        val events = base.events.toMutableList()
        if (models.route == ProcessingRoute.OCR_THEN_LLM) {
            events.add(ProcessingEvent(title = "OCR 完成", detail = "已提取 ${ocrText.length} 个字符"))
        }
        searchEvent(searchOutcome)?.let { events.add(it) }
        events.add(
            ProcessingEvent(
                title = "工具动作完成",
                detail = "${automationResult.action.type}: ${automationResult.action.text}"
            )
        )
        val result = ProcessingResult(
            id = historyId,
            assistantName = models.assistant.name,
            route = models.route,
            status = ProcessingStatus.SUCCESS,
            modelSummary = when (models.route) {
                ProcessingRoute.OCR_THEN_LLM -> buildModelSummary(models.textModel, models.textProvider?.name)
                ProcessingRoute.MULTIMODAL_DIRECT -> buildModelSummary(models.visionModel, models.visionProvider?.name)
            },
            detail = "自动处理完成",
            extractedText = ocrText,
            answer = "",
            automationThought = automationResult.thought,
            automationAction = automationResult.action,
            screenshotPath = screenshotPaths.firstOrNull(),
            screenshotPaths = screenshotPaths,
            events = events,
            createdAtMillis = base.createdAtMillis
        )
        return automationResult.action to result
    }
}

private fun assistantPromptWithCopyMarker(systemPrompt: String): String {
    val trimmed = systemPrompt.trim()
    if (trimmed.isBlank()) return trimmed
    return """
        你可以在回答中插入如下格式的可复制文本块：
        [copy:内容]
        其中"内容"会显示为一个小标签，点击复制图标后会写入同样的文本到剪贴板。
        对于问题的答案或用户需要填写到某个表单中的内容，你必须给出一键复制的标签。

        $trimmed
    """.trimIndent()
}

private fun automationSystemPrompt(userPrompt: String): String {
    val trimmed = userPrompt.trim()
    return """
        你当前处于自动答题模式。
        你必须先输出简短、清晰的思考过程，说明你识别到了什么题型以及为什么这样判断。
        思考过程结束后，你必须且只能调用一个工具，不能在工具调用后继续输出额外文本。
        当答案适合直接填写到输入框、文本框、填空题空格时，调用 set_clipboard。
        当答案适合让用户直接在悬浮球上查看选项字母时，调用 show_bubble_letters。
        show_bubble_letters 的 text 参数可以是 1-8 个英文字母（大小写均可），或者"对""错""√""×"。不能包含空格、标点或其他解释。
        set_clipboard 的 text 参数必须是用户可以直接粘贴使用的最终答案。
        不允许调用多个工具，不允许省略工具调用。

        ${trimmed.ifBlank { "请根据截图内容判断题目类型，并选择最合适的自动动作。" }}
    """.trimIndent()
}

private fun Bitmap.toBase64Jpeg(quality: Int = 92): String {
    val output = java.io.ByteArrayOutputStream()
    compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output)
    return android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
}
