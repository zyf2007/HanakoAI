package `fun`.kirari.hanako.core.data

import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModelsTest {
    @Test
    fun settings_removingScreenshotTogglePreservesLegacySettings() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        listOf(false, true).forEach { legacyEnabled ->
            val settings = json.decodeFromString<AppSettings>(
                """{"automation":{"skipScreenshotEnabled":$legacyEnabled,"autoModeTimeoutSeconds":45,"staticModeEnabled":true,"bubbleAppearance":{"bubbleDiameterDp":24}}}"""
            )

            assertEquals(45, settings.automation.autoModeTimeoutSeconds)
            assertTrue(settings.automation.staticModeEnabled)
            assertEquals(24f, settings.automation.bubbleAppearance.bubbleDiameterDp, 0f)
            val savedAutomation = json.encodeToJsonElement(AppSettings.serializer(), settings)
                .jsonObject.getValue("automation").jsonObject
            assertFalse(savedAutomation.containsKey("skipScreenshotEnabled"))
        }
    }

    @Test
    fun normalize_advancesHistoryDataSchemaForQuoteFields() {
        assertEquals(StorageSchema.CURRENT_APP_DATA_VERSION, AppSettings(schemaVersion = 2).normalize().schemaVersion)
        assertTrue(StorageSchema.ANSWER_VERSION_IDS_MIGRATION in StorageSchema.completedMigrations)
        assertTrue(StorageSchema.QUOTED_FRAGMENTS_MIGRATION in StorageSchema.completedMigrations)
    }

    @Test
    fun defaultAssistants_onlyKeepsProblemSolvingAssistant() {
        val assistants = defaultAssistants()

        assertEquals(1, assistants.size)
        assertEquals("题目解答助手", assistants.single().name)
    }

    @Test
    fun normalize_migratesLegacyDefaultAssistantsToSingleProblemSolvingAssistant() {
        val chatSummary = AssistantPreset(
            id = "legacy-chat",
            name = "聊天记录总结助手",
            ocrPrompt = "请准确提取图片中的全部文字，按原有结构输出，不要解释。",
            textPrompt = "你是聊天记录总结助手。请提炼重点、待办、情绪倾向，并用简洁中文输出。",
            visionPrompt = "你是聊天记录总结助手。请直接阅读图片内容，提炼重点、待办、情绪倾向，并用简洁中文输出。"
        )
        val problemSolving = AssistantPreset(
            id = "legacy-problem",
            name = "题目解答助手",
            ocrPrompt = "请准确提取图片中的题目、选项、公式和注释，尽量保持原有结构，不要解释。",
            textPrompt = "你是题目解答助手。请先识别题目内容，再给出解题思路、关键知识点和答案。",
            visionPrompt = "你是题目解答助手。请直接阅读图片中的题目内容，给出解题思路、关键知识点和答案。"
        )

        val normalized = AppSettings(
            assistants = listOf(chatSummary, problemSolving),
            selectedAssistantId = problemSolving.id
        ).normalize()

        assertEquals(1, normalized.assistants.size)
        assertEquals("题目解答助手", normalized.assistants.single().name)
        assertEquals(normalized.assistants.single().id, normalized.selectedAssistantId)
    }

    @Test
    fun normalize_preservesCustomizedAssistants() {
        val customized = AssistantPreset(
            id = "customized",
            name = "聊天记录总结助手 Plus",
            ocrPrompt = "请准确提取图片中的全部文字，按原有结构输出，不要解释。",
            textPrompt = "你是聊天记录总结助手。请提炼重点、待办、情绪倾向，并用简洁中文输出。",
            visionPrompt = "你是聊天记录总结助手。请直接阅读图片内容，提炼重点、待办、情绪倾向，并用简洁中文输出。"
        )
        val problemSolving = AssistantPreset(
            id = "problem",
            name = "题目解答助手",
            ocrPrompt = "请准确提取图片中的题目、选项、公式和注释，尽量保持原有结构，不要解释。",
            textPrompt = "你是题目解答助手。请先识别题目内容，再给出解题思路、关键知识点和答案。",
            visionPrompt = "你是题目解答助手。请直接阅读图片中的题目内容，给出解题思路、关键知识点和答案。"
        )

        val normalized = AppSettings(
            assistants = listOf(customized, problemSolving),
            selectedAssistantId = problemSolving.id
        ).normalize()

        assertEquals(2, normalized.assistants.size)
        assertEquals(problemSolving.id, normalized.selectedAssistantId)
        assertNotNull(normalized.assistants.firstOrNull { it.id == customized.id })
    }

    @Test
    fun normalize_clampsAutomationValuesAndPrependsKirariScheme() {
        val normalized = AppSettings(
            automation = AutomationSettings(
                autoModeTimeoutSeconds = 0,
                bubbleAppearance = BubbleAppearanceSettings(
                    bubbleDiameterDp = -1f,
                    spinnerDiameterDp = 100f,
                    letterTextSizeDp = 200f,
                    letterOpacity = -5f,
                    overallOpacity = 120f
                )
            ),
            kirari = KirariSettings(serverUrl = "example.com")
        ).normalize()

        assertEquals(1, normalized.automation.autoModeTimeoutSeconds)
        assertEquals(MIN_BUBBLE_DIAMETER_DP, normalized.automation.bubbleAppearance.bubbleDiameterDp, 0f)
        assertEquals(MAX_SPINNER_DIAMETER_DP, normalized.automation.bubbleAppearance.spinnerDiameterDp, 0f)
        assertEquals(MAX_BUBBLE_LETTER_TEXT_SIZE_DP, normalized.automation.bubbleAppearance.letterTextSizeDp, 0f)
        assertEquals(0f, normalized.automation.bubbleAppearance.letterOpacity, 0f)
        assertEquals(100f, normalized.automation.bubbleAppearance.overallOpacity, 0f)
        assertEquals("http://example.com", normalized.kirari.serverUrl)
    }

    @Test
    fun normalize_repairsBlankSelectionsForExistingProvider() {
        val provider = ModelProviderConfig(
            id = "provider-1",
            name = "P1",
            chatModel = "chat-1",
            visionModel = "vision-1",
            ocrModel = "ocr-1"
        )

        val normalized = AppSettings(
            providers = listOf(provider),
            selectedProviderId = provider.id,
            textModelSelection = ModelSelection(providerId = provider.id, model = ""),
            visionModelSelection = ModelSelection(providerId = provider.id, model = ""),
            ocrModelSelection = ModelSelection(providerId = provider.id, model = "")
        ).normalize()

        assertEquals(provider.id, normalized.selectedProviderId)
        assertEquals(ModelSelection(provider.id, "chat-1"), normalized.textModelSelection)
        assertEquals(ModelSelection(provider.id, "vision-1"), normalized.visionModelSelection)
        assertEquals(ModelSelection(provider.id, "ocr-1"), normalized.ocrModelSelection)
    }

    @Test
    fun normalize_invalidSelectedProviderFallsBackToFirstAvailableProvider() {
        val provider = ModelProviderConfig(id = "provider-1", name = "P1")

        val normalized = AppSettings(
            providers = listOf(provider),
            selectedProviderId = "missing"
        ).normalize()

        assertEquals(KIRARI_PROVIDER_ID, normalized.selectedProviderId)
    }

    @Test
    fun normalize_preservesLocalOcrSelectionAndAssignsDefaultModelId() {
        val normalized = AppSettings(
            ocrModelSelection = ModelSelection(providerId = LOCAL_OCR_PROVIDER_ID, model = "")
        ).normalize()

        assertEquals(LOCAL_OCR_PROVIDER_ID, normalized.ocrModelSelection.providerId)
        assertEquals(LOCAL_OCR_MODEL_ID, normalized.ocrModelSelection.model)
    }

    @Test
    fun availableProviders_includesKirariProviderWhenEnabled() {
        val providers = AppSettings().availableProviders()

        assertTrue(providers.isNotEmpty())
        assertEquals(KIRARI_PROVIDER_ID, providers.first().id)
    }

    @Test
    fun processingResult_allScreenshotPaths_prefersListButFallsBackToSinglePath() {
        val withSingle = ProcessingResult(
            assistantName = "助手",
            route = ProcessingRoute.OCR_THEN_LLM,
            screenshotPath = "/tmp/a.jpg"
        )
        val withList = ProcessingResult(
            assistantName = "助手",
            route = ProcessingRoute.OCR_THEN_LLM,
            screenshotPath = "/tmp/a.jpg",
            screenshotPaths = listOf("/tmp/b.jpg", "/tmp/c.jpg")
        )

        assertEquals(listOf("/tmp/a.jpg"), withSingle.allScreenshotPaths)
        assertEquals(listOf("/tmp/b.jpg", "/tmp/c.jpg"), withList.allScreenshotPaths)
    }

    @Test
    fun assistantPreviewPrompt_prefersTextThenVisionThenOcr() {
        val withText = AssistantPreset(
            name = "A",
            ocrPrompt = "ocr",
            textPrompt = "text",
            visionPrompt = "vision"
        )
        val withVision = AssistantPreset(
            name = "B",
            ocrPrompt = "ocr",
            textPrompt = "",
            visionPrompt = "vision"
        )
        val withOcr = AssistantPreset(
            name = "C",
            ocrPrompt = "ocr",
            textPrompt = "",
            visionPrompt = ""
        )

        assertEquals("text", withText.previewPrompt())
        assertEquals("vision", withVision.previewPrompt())
        assertEquals("ocr", withOcr.previewPrompt())
    }

    @Test
    fun requestPreviewUrl_usesProviderSpecificSuffixAndTrimsTrailingSlash() {
        val openAi = ModelProviderConfig(
            kind = `fun`.kirari.llm.core.ProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://example.com/v1/"
        )
        val google = ModelProviderConfig(
            kind = `fun`.kirari.llm.core.ProviderKind.GOOGLE,
            baseUrl = "https://googleapis.com/v1beta/"
        )

        assertEquals("https://example.com/v1/chat/completions", openAi.requestPreviewUrl())
        assertEquals("https://googleapis.com/v1beta/models", google.requestPreviewUrl())
    }

    @Test
    fun modelSelectionHelpers_coverLocalOcrAndRemoteProviders() {
        val provider = ModelProviderConfig(id = "provider-1", name = "P1")
        val remoteSettings = AppSettings(
            providers = listOf(provider),
            ocrModelSelection = ModelSelection(providerId = provider.id, model = "ocr-remote")
        )
        val localSettings = AppSettings(
            providers = listOf(provider),
            localOcr = LocalOcrSettings(displayName = "本地 OCR"),
            ocrModelSelection = ModelSelection(providerId = LOCAL_OCR_PROVIDER_ID, model = "")
        )

        assertEquals(false, remoteSettings.ocrModelSelection.isLocalOcrSelection())
        assertEquals(provider.id, remoteSettings.resolveModelProvider(ModelPurpose.OCR)?.id)
        assertEquals("ocr-remote", remoteSettings.resolveModelName(ModelPurpose.OCR))

        assertEquals(true, localSettings.ocrModelSelection.isLocalOcrSelection())
        assertEquals(null, localSettings.resolveModelProvider(ModelPurpose.OCR))
        assertEquals("本地 OCR", localSettings.resolveModelName(ModelPurpose.OCR))
    }
}
