package `fun`.kirari.llm.core

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

open class NetworkClientProvider(
    private val logger: LlmLogger = NoopLlmLogger
) {
    private val tag = "KirariLlmNetwork"

    private val safeClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val trustAllClient: OkHttpClient by lazy {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        logger.i(tag, "trust-all HTTPS client created; certificate and hostname checks are disabled")
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun client(trustAllHttpsCertificates: Boolean): OkHttpClient {
        return if (trustAllHttpsCertificates) trustAllClient else safeClient
    }

    /**
     * 返回带指定超时时间的客户端（用于非流式请求，如搜索 API）。
     * 基于 [client] 派生，复用 SSL 配置。
     */
    fun clientWithTimeout(trustAllHttpsCertificates: Boolean, timeoutMillis: Long): OkHttpClient {
        return client(trustAllHttpsCertificates).newBuilder()
            .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
    }
}
