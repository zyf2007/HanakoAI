package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.llm.core.LlmLogger

class NetworkClientProvider : `fun`.kirari.llm.core.NetworkClientProvider(
    object : LlmLogger {
        override fun d(tag: String, message: String) = AppDebugLogStore.d(tag, message)
        override fun i(tag: String, message: String) = AppDebugLogStore.i(tag, message)
        override fun e(tag: String, message: String, throwable: Throwable?) = AppDebugLogStore.e(tag, message, throwable)
    }
)
