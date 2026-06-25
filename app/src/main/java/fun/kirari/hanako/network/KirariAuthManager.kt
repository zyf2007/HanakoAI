package `fun`.kirari.hanako.network

import android.net.Uri
import `fun`.kirari.auth.core.AuthorizationSession
import `fun`.kirari.auth.core.OidcClientConfig
import `fun`.kirari.auth.core.OidcPkceClient
import `fun`.kirari.auth.core.OidcTokenResponse
import `fun`.kirari.auth.core.OidcUserInfo
import `fun`.kirari.hanako.BuildConfig
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.KirariAuthState
import `fun`.kirari.hanako.data.KirariSettings
import `fun`.kirari.hanako.data.KirariUserProfile
import `fun`.kirari.hanako.data.SettingsStore
import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class KirariAuthorizationRequest(
    val authorizationUrl: String,
    val redirectUri: String
)

internal data class KirariAuthHandleResult(
    val success: Boolean,
    val message: String
)

internal data class KirariSessionRefreshResult(
    val authenticated: Boolean,
    val errorMessage: String? = null
)

internal class KirariAuthManager(
    private val settingsStore: SettingsStore,
    private val clientProvider: NetworkClientProvider,
    private val oidcClient: OidcPkceClient = OidcPkceClient()
) {
    private val tag = "KirariAuth"
    private val refreshMutex = Mutex()
    @Volatile
    private var pendingSession: AuthorizationSession? = null

    suspend fun buildAuthorizationRequest(
        serverUrl: String,
        trustAllHttpsCertificates: Boolean
    ): KirariAuthorizationRequest = withContext(Dispatchers.IO) {
        val clientId = BuildConfig.KIRARI_OIDC_CLIENT_ID.trim()
        require(clientId.isNotBlank()) { "当前构建未注入 Kirari OIDC Client ID" }
        val config = oidcConfig(serverUrl)
        val request = oidcClient.buildAuthorizationRequest(
            httpClient = clientProvider.client(trustAllHttpsCertificates),
            config = config.copy(clientId = clientId)
        )
        pendingSession = AuthorizationSession(
            state = request.state,
            codeVerifier = request.codeVerifier,
            redirectUri = request.redirectUri
        )
        settingsStore.savePendingKirariAuthorizationSession(requireNotNull(pendingSession))
        AppDebugLogStore.i(tag, "authorization request prepared redirectUri=${request.redirectUri}")
        KirariAuthorizationRequest(
            authorizationUrl = request.authorizationUrl,
            redirectUri = request.redirectUri
        )
    }

    suspend fun handleRedirect(
        redirectUri: Uri,
        settings: AppSettings,
        trustAllHttpsCertificates: Boolean
    ): KirariAuthHandleResult = withContext(Dispatchers.IO) {
        val inMemorySession = pendingSession
        val session = inMemorySession ?: settingsStore.readPendingKirariAuthorizationSession()
            ?: return@withContext KirariAuthHandleResult(false, "未找到待完成的登录会话")
        pendingSession = session
        AppDebugLogStore.i(
            tag,
            "handleRedirect sessionSource=${if (inMemorySession != null) "memory" else "store"} state=${session.state.take(8)}"
        )
        val error = redirectUri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            clearPendingAuthorizationSession()
            val description = redirectUri.getQueryParameter("error_description").orEmpty()
            return@withContext KirariAuthHandleResult(false, listOf(error, description).filter(String::isNotBlank).joinToString(": "))
        }
        val state = redirectUri.getQueryParameter("state").orEmpty()
        if (state != session.state) {
            clearPendingAuthorizationSession()
            return@withContext KirariAuthHandleResult(false, "OIDC state 校验失败")
        }
        val code = redirectUri.getQueryParameter("code").orEmpty()
        if (code.isBlank()) {
            clearPendingAuthorizationSession()
            return@withContext KirariAuthHandleResult(false, "OIDC 未返回 authorization code")
        }
        val tokenResponse = oidcClient.exchangeAuthorizationCode(
            httpClient = clientProvider.client(trustAllHttpsCertificates),
            config = oidcConfig(settings.kirari.serverUrl),
            session = session,
            code = code
        )
        clearPendingAuthorizationSession()
        persistSession(
            tokenResponse = tokenResponse,
            serverUrl = settings.kirari.serverUrl,
            trustAllHttpsCertificates = trustAllHttpsCertificates,
            fallbackRefreshToken = settings.kirari.auth.refreshToken
        )
        AppDebugLogStore.i(tag, "authorization code exchanged")
        KirariAuthHandleResult(true, "Kirari 登录成功")
    }

    suspend fun hasPendingAuthorizationSession(): Boolean = withContext(Dispatchers.IO) {
        pendingSession != null || settingsStore.readPendingKirariAuthorizationSession() != null
    }

    suspend fun ensureValidAccessToken(
        settings: AppSettings,
        trustAllHttpsCertificates: Boolean
    ): String {
        val auth = settings.kirari.auth
        if (auth.accessToken.isNotBlank() && auth.accessTokenExpiresAtMillis > System.currentTimeMillis() + 30_000L) {
            return auth.accessToken
        }
        if (auth.refreshToken.isBlank()) {
            return ""
        }
        return refreshMutex.withLock {
            val latest = settingsStore.read().kirari.auth
            if (latest.accessToken.isNotBlank() && latest.accessTokenExpiresAtMillis > System.currentTimeMillis() + 30_000L) {
                return@withLock latest.accessToken
            }
            val refreshed = oidcClient.refreshAccessToken(
                httpClient = clientProvider.client(trustAllHttpsCertificates),
                config = oidcConfig(settings.kirari.serverUrl),
                refreshToken = latest.refreshToken
            )
            persistSession(
                tokenResponse = refreshed,
                serverUrl = settings.kirari.serverUrl,
                trustAllHttpsCertificates = trustAllHttpsCertificates,
                fallbackRefreshToken = latest.refreshToken
            )
            AppDebugLogStore.i(tag, "access token refreshed")
            refreshed.accessToken
        }
    }

    suspend fun refreshSessionStatus(
        settings: AppSettings,
        trustAllHttpsCertificates: Boolean
    ): KirariSessionRefreshResult = withContext(Dispatchers.IO) {
        val auth = settings.kirari.auth
        if (auth.accessToken.isBlank() && auth.refreshToken.isBlank()) {
            clearAuth()
            return@withContext KirariSessionRefreshResult(authenticated = false)
        }
        return@withContext runCatching {
            val accessToken = ensureValidAccessToken(settingsStore.read(), trustAllHttpsCertificates)
            if (accessToken.isBlank()) {
                clearAuth()
                KirariSessionRefreshResult(
                    authenticated = false,
                    errorMessage = "登录已失效，请重新登录"
                )
            } else {
                val latest = settingsStore.read()
                val profile = fetchUserProfile(
                    serverUrl = latest.kirari.serverUrl,
                    accessToken = accessToken,
                    trustAllHttpsCertificates = trustAllHttpsCertificates
                )
                settingsStore.update { current ->
                    current.copy(
                        kirari = current.kirari.copy(profile = profile)
                    )
                }
                KirariSessionRefreshResult(authenticated = true)
            }
        }.getOrElse { error ->
            AppDebugLogStore.e(tag, "refresh session status failed", error)
            val message = error.message ?: "刷新登录状态失败"
            if (message.contains("401") || message.contains("400") || message.contains("invalid", ignoreCase = true)) {
                clearAuth()
                KirariSessionRefreshResult(
                    authenticated = false,
                    errorMessage = "登录已失效，请重新登录"
                )
            } else {
                KirariSessionRefreshResult(
                    authenticated = false,
                    errorMessage = message
                )
            }
        }
    }

    suspend fun clearAuth() {
        clearPendingAuthorizationSession()
        settingsStore.update { current ->
            current.copy(
                kirari = current.kirari.copy(
                    auth = KirariAuthState(),
                    profile = KirariUserProfile()
                )
            )
        }
    }

    fun matchesRedirect(uri: Uri?): Boolean {
        if (uri == null) return false
        val expected = redirectUri()
        return uri.scheme == Uri.parse(expected).scheme &&
            uri.path == Uri.parse(expected).path
    }

    private fun oidcConfig(serverUrl: String): OidcClientConfig {
        val trimmed = serverUrl.trim().trimEnd('/')
        require(trimmed.isNotBlank()) { "请先填写 Kirari 服务器地址" }
        return OidcClientConfig(
            issuerBaseUrl = trimmed,
            clientId = BuildConfig.KIRARI_OIDC_CLIENT_ID.trim(),
            redirectUri = redirectUri(),
            scope = "openid profile email offline_access llm:read llm:stream"
        )
    }

    private suspend fun persistSession(
        tokenResponse: OidcTokenResponse,
        serverUrl: String,
        trustAllHttpsCertificates: Boolean,
        fallbackRefreshToken: String
    ) {
        val auth = tokenResponse.toAuthState(fallbackRefreshToken)
        val profile = fetchUserProfile(
            serverUrl = serverUrl,
            accessToken = auth.accessToken,
            trustAllHttpsCertificates = trustAllHttpsCertificates
        )
        settingsStore.update { current ->
            current.copy(
                kirari = current.kirari.copy(
                    auth = auth,
                    profile = profile
                )
            )
        }
    }

    private fun OidcTokenResponse.toAuthState(fallbackRefreshToken: String): KirariAuthState {
        val expiresAt = System.currentTimeMillis() + (expiresIn.coerceAtLeast(1L) * 1000L)
        return KirariAuthState(
            accessToken = accessToken,
            refreshToken = refreshToken.ifBlank { fallbackRefreshToken },
            idToken = idToken,
            tokenType = tokenType.ifBlank { "Bearer" },
            scope = scope,
            accessTokenExpiresAtMillis = expiresAt
        )
    }

    private suspend fun fetchUserProfile(
        serverUrl: String,
        accessToken: String,
        trustAllHttpsCertificates: Boolean
    ): KirariUserProfile {
        val userInfo = oidcClient.fetchUserInfo(
            httpClient = clientProvider.client(trustAllHttpsCertificates),
            config = oidcConfig(serverUrl),
            accessToken = accessToken
        )
        return userInfo.toProfile()
    }

    private fun OidcUserInfo.toProfile(): KirariUserProfile = KirariUserProfile(
        subject = sub,
        email = email.orEmpty(),
        name = name.orEmpty(),
        preferredUsername = preferredUsername.orEmpty(),
        nickname = nickname.orEmpty(),
        lastSyncedAtMillis = System.currentTimeMillis()
    )

    private suspend fun clearPendingAuthorizationSession() {
        pendingSession = null
        settingsStore.clearPendingKirariAuthorizationSession()
    }

    private fun redirectUri(): String = "${BuildConfig.APPLICATION_ID}:/oauth2redirect/kirari"
}
