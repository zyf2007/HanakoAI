package `fun`.kirari.llm.core

fun String.visibleWhitespaceForLog(maxLength: Int = 160): String {
    val escaped = buildString {
        this@visibleWhitespaceForLog.take(maxLength).forEach { ch ->
            when (ch) {
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                ' ' -> append("·")
                else -> append(ch)
            }
        }
        if (this@visibleWhitespaceForLog.length > maxLength) {
            append("...")
        }
    }
    return escaped
}
