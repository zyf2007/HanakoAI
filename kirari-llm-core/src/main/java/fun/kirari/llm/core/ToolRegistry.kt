package `fun`.kirari.llm.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ToolRegistry {

    val AUTOMATION_TOOLS = listOf(
        ToolDef(
            name = "set_clipboard",
            description = "将最终答案写入剪贴板",
            params = listOf(ToolParam("text", "string", "最终可直接粘贴的答案"))
        ),
        ToolDef(
            name = "show_bubble_letters",
            description = "在悬浮球上展示选项字母",
            params = listOf(ToolParam("text", "string", "1-8个英文字母（大小写均可），或对、错、√、×", pattern = "^[A-Za-z]{1,8}$|^(对|错|√|×)$"))
        )
    )

    fun formatForProvider(tools: List<ToolDef>, kind: ProviderKind): JsonElement = when (kind) {
        ProviderKind.OPENAI_COMPATIBLE, ProviderKind.KIRARI_NETWORK -> formatChatCompletions(tools)
        ProviderKind.OPENAI_RESPONSES -> formatResponses(tools)
        ProviderKind.ANTHROPIC -> formatAnthropic(tools)
        ProviderKind.GOOGLE -> formatGoogle(tools)
    }

    private fun formatChatCompletions(tools: List<ToolDef>): JsonArray = buildJsonArray {
        for (tool in tools) {
            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            for (p in tool.params) {
                                put(p.name, buildJsonObject {
                                    put("type", p.type)
                                    put("description", p.description)
                                    p.pattern?.let { put("pattern", it) }
                                })
                            }
                        })
                        put("required", buildJsonArray {
                            for (p in tool.params) add(JsonPrimitive(p.name))
                        })
                        put("additionalProperties", false)
                    })
                })
            })
        }
    }

    private fun formatResponses(tools: List<ToolDef>): JsonArray = buildJsonArray {
        for (tool in tools) {
            add(buildJsonObject {
                put("type", "function")
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        for (p in tool.params) {
                            put(p.name, buildJsonObject {
                                put("type", p.type)
                                put("description", p.description)
                                p.pattern?.let { put("pattern", it) }
                            })
                        }
                    })
                    put("required", buildJsonArray {
                        for (p in tool.params) add(JsonPrimitive(p.name))
                    })
                    put("additionalProperties", false)
                })
            })
        }
    }

    private fun formatAnthropic(tools: List<ToolDef>): JsonArray = buildJsonArray {
        for (tool in tools) {
            add(buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("input_schema", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        for (p in tool.params) {
                            put(p.name, buildJsonObject {
                                put("type", p.type)
                                put("description", p.description)
                                p.pattern?.let { put("pattern", it) }
                            })
                        }
                    })
                    put("required", buildJsonArray {
                        for (p in tool.params) add(JsonPrimitive(p.name))
                    })
                })
            })
        }
    }

    private fun formatGoogle(tools: List<ToolDef>): JsonObject = buildJsonObject {
        put("functionDeclarations", buildJsonArray {
            for (tool in tools) {
                add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", buildJsonObject {
                        put("type", "OBJECT")
                        put("properties", buildJsonObject {
                            for (p in tool.params) {
                                put(p.name, buildJsonObject {
                                    put("type", "STRING")
                                    put("description", p.description)
                                    p.pattern?.let { put("pattern", it) }
                                })
                            }
                        })
                        put("required", buildJsonArray {
                            for (p in tool.params) add(JsonPrimitive(p.name))
                        })
                    })
                })
            }
        })
    }
}
