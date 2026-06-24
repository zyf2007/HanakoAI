package `fun`.kirari.hanako.automation

import `fun`.kirari.hanako.data.AutomationActionRecord

internal fun validateAutomationAction(name: String, text: String): AutomationActionRecord {
    val normalized = text.trim()
    require(normalized.isNotBlank()) { "自动模式工具参数不能为空" }
    return when (name) {
        "set_clipboard" -> clipboardAction(normalized)
        "show_bubble_letters" -> {
            require(Regex("^[A-Za-z]{1,8}$|^(对|错|√|×)$").matches(normalized)) {
                "悬浮球字母必须是 1-8 个英文字母（大小写均可），或'对'、'错'、'√'、'×'"
            }
            bubbleLettersAction(normalized)
        }

        else -> error("未知自动模式工具：$name")
    }
}
