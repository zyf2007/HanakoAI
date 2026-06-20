package `fun`.kirari.hanako.network

internal typealias ToolParam = `fun`.kirari.llm.core.ToolParam
internal typealias ToolDef = `fun`.kirari.llm.core.ToolDef

internal object ToolRegistry {
    val AUTOMATION_TOOLS: List<ToolDef> = `fun`.kirari.llm.core.ToolRegistry.AUTOMATION_TOOLS
}
