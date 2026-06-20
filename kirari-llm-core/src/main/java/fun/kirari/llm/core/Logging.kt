package `fun`.kirari.llm.core

interface LlmLogger {
    fun d(tag: String, message: String) {}
    fun i(tag: String, message: String) {}
    fun e(tag: String, message: String, throwable: Throwable? = null) {}
}

object NoopLlmLogger : LlmLogger
