package `fun`.kirari.auth.core

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class OidcClientConfig(
    val issuerBaseUrl: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String
)

data class AuthorizationRequest(
    val authorizationUrl: String,
    val state: String,
    val codeVerifier: String,
    val redirectUri: String
)

@Serializable
data class AuthorizationSession(
    val state: String,
    val codeVerifier: String,
    val redirectUri: String
)

@Serializable
data class OidcDiscoveryDocument(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("userinfo_endpoint") val userInfoEndpoint: String? = null,
    @SerialName("jwks_uri") val jwksUri: String? = null,
    @SerialName("end_session_endpoint") val endSessionEndpoint: String? = null
)

@Serializable
data class OidcTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("id_token") val idToken: String = "",
    @SerialName("token_type") val tokenType: String = "Bearer",
    val scope: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0L
)

@Serializable
data class OidcUserInfo(
    val sub: String = "",
    val email: String? = null,
    val name: String? = null,
    @SerialName("preferred_username") val preferredUsername: String? = null,
    val nickname: String? = null
)

class OidcPkceClient(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val secureRandom: SecureRandom = SecureRandom()
) {
    suspend fun fetchDiscovery(
        httpClient: OkHttpClient,
        issuerBaseUrl: String
    ): OidcDiscoveryDocument = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${issuerBaseUrl.trim().trimEnd('/')}/.well-known/openid-configuration")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("OIDC discovery failed: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            json.decodeFromString(OidcDiscoveryDocument.serializer(), body)
        }
    }

    suspend fun buildAuthorizationRequest(
        httpClient: OkHttpClient,
        config: OidcClientConfig
    ): AuthorizationRequest {
        require(config.issuerBaseUrl.trim().isNotBlank()) { "OIDC issuer base URL is required" }
        require(config.clientId.trim().isNotBlank()) { "OIDC client ID is required" }
        require(config.redirectUri.trim().isNotBlank()) { "OIDC redirect URI is required" }
        require(config.scope.trim().isNotBlank()) { "OIDC scope is required" }

        val discovery = fetchDiscovery(httpClient, config.issuerBaseUrl)
        val state = randomUrlSafeString(24)
        val verifier = randomUrlSafeString(48)
        val challenge = pkceChallenge(verifier)
        val authUri = Uri.parse(discovery.authorizationEndpoint).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("scope", config.scope)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
        return AuthorizationRequest(
            authorizationUrl = authUri.toString(),
            state = state,
            codeVerifier = verifier,
            redirectUri = config.redirectUri
        )
    }

    suspend fun exchangeAuthorizationCode(
        httpClient: OkHttpClient,
        config: OidcClientConfig,
        session: AuthorizationSession,
        code: String
    ): OidcTokenResponse {
        val discovery = fetchDiscovery(httpClient, config.issuerBaseUrl)
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", config.clientId)
            .add("redirect_uri", session.redirectUri)
            .add("code", code)
            .add("code_verifier", session.codeVerifier)
            .build()
        val request = Request.Builder()
            .url(discovery.tokenEndpoint)
            .post(formBody)
            .header("Accept", "application/json")
            .build()
        return executeTokenRequest(httpClient, request)
    }

    suspend fun refreshAccessToken(
        httpClient: OkHttpClient,
        config: OidcClientConfig,
        refreshToken: String
    ): OidcTokenResponse {
        require(refreshToken.isNotBlank()) { "OIDC refresh token is required" }
        val discovery = fetchDiscovery(httpClient, config.issuerBaseUrl)
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", config.clientId)
            .add("refresh_token", refreshToken)
            .build()
        val request = Request.Builder()
            .url(discovery.tokenEndpoint)
            .post(formBody)
            .header("Accept", "application/json")
            .build()
        return executeTokenRequest(httpClient, request)
    }

    suspend fun fetchUserInfo(
        httpClient: OkHttpClient,
        config: OidcClientConfig,
        accessToken: String
    ): OidcUserInfo {
        require(accessToken.isNotBlank()) { "OIDC access token is required" }
        val discovery = fetchDiscovery(httpClient, config.issuerBaseUrl)
        val endpoint = discovery.userInfoEndpoint ?: error("OIDC userinfo endpoint is not available")
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .build()
        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("OIDC userinfo failed: HTTP ${response.code} $body")
                }
                json.decodeFromString(OidcUserInfo.serializer(), body)
            }
        }
    }

    private suspend fun executeTokenRequest(
        httpClient: OkHttpClient,
        request: Request
    ): OidcTokenResponse = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("OIDC token exchange failed: HTTP ${response.code} $body")
            }
            json.decodeFromString(OidcTokenResponse.serializer(), body)
        }
    }

    private fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun randomUrlSafeString(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
