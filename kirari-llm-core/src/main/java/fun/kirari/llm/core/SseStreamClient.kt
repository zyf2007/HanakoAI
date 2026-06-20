package `fun`.kirari.llm.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SseStreamClient(
    private val client: OkHttpClient,
    private val logger: LlmLogger = NoopLlmLogger
) {
    private val tag = "KirariLlmSse"

    data class StreamEventResult(
        val delta: String? = null,
        val done: Boolean = false
    )

    suspend fun stream(
        request: Request,
        firstDeltaTimeoutMillis: Long = 10_000L,
        onEvent: (eventSource: EventSource, type: String?, id: String?, data: String) -> StreamEventResult?,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val builder = StringBuilder()
            val finished = AtomicBoolean(false)
            val firstDeltaReceived = AtomicBoolean(false)
            var eventCount = 0
            var eventSourceRef: EventSource? = null
            var timeoutJobRef: Job? = null

            fun finish(block: () -> Unit) {
                if (finished.compareAndSet(false, true)) {
                    timeoutJobRef?.cancel()
                    eventSourceRef?.cancel()
                    block()
                }
            }

            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    logger.i(tag, "stream opened code=${response.code} url=${request.url}")
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    try {
                        eventCount += 1
                        if (eventCount <= 5 || eventCount % 25 == 0) {
                            logger.d(tag, "stream event#$eventCount type=$type id=$id dataLength=${data.length}")
                        }
                        val result = onEvent(eventSource, type, id, data)
                        val delta = result?.delta
                        if (!delta.isNullOrEmpty()) {
                            firstDeltaReceived.set(true)
                            timeoutJobRef?.cancel()
                            builder.append(delta)
                            onDelta(delta)
                        }
                        if (result?.done == true) {
                            logger.i(tag, "stream completed by protocol signal totalEvents=$eventCount outputLength=${builder.length} url=${request.url}")
                            finish { cont.resume(builder.toString()) }
                        }
                    } catch (t: Throwable) {
                        finish { cont.resumeWithException(t) }
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    logger.e(tag, "stream failure code=${response?.code} url=${request.url}", t)
                    response?.close()
                    val normalized = if (
                        !firstDeltaReceived.get() &&
                        (t is SocketTimeoutException || t?.message?.contains("timeout", ignoreCase = true) == true)
                    ) {
                        SocketTimeoutException("自动模式首字延迟超时（${firstDeltaTimeoutMillis / 1000} 秒）")
                    } else {
                        t
                    }
                    finish {
                        cont.resumeWithException(
                            normalized ?: IllegalStateException(
                                "Stream failed: ${response?.code ?: "unknown"}"
                            )
                        )
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    logger.i(tag, "stream closed totalEvents=$eventCount outputLength=${builder.length} url=${request.url}")
                    finish { cont.resume(builder.toString()) }
                }
            }

            val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
            eventSourceRef = eventSource
            val timeoutJob = CoroutineScope(cont.context).launch {
                delay(firstDeltaTimeoutMillis)
                if (!firstDeltaReceived.get()) {
                    logger.e(
                        tag,
                        "stream first delta timeout after ${firstDeltaTimeoutMillis}ms url=${request.url}",
                        null
                    )
                    finish {
                        cont.resumeWithException(
                            SocketTimeoutException("自动模式首字延迟超时（${firstDeltaTimeoutMillis / 1000} 秒）")
                        )
                    }
                }
            }
            timeoutJobRef = timeoutJob
            cont.invokeOnCancellation {
                if (finished.compareAndSet(false, true)) {
                    timeoutJob.cancel()
                    eventSource.cancel()
                }
            }
        }
    }
}
