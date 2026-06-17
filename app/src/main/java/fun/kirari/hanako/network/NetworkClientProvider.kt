package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.debug.AppDebugLogStore
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NetworkClientProvider {
    private val tag = "HanakoNetworkClient"

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
        AppDebugLogStore.i(tag, "trust-all HTTPS client created; certificate and hostname checks are disabled")
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun client(trustAllHttpsCertificates: Boolean, timeoutMillis: Long = 0): OkHttpClient {
        val base = if (trustAllHttpsCertificates) trustAllClient else safeClient
        return if (timeoutMillis > 0) base.newBuilder()
            .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        else base
    }
}
