package `fun`.kirari.hanako.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppModelsTest {

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
}
