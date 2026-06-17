package `fun`.kirari.hanako.network

import `fun`.kirari.hanako.data.ModelProviderConfig

interface ConnectionTester {
    suspend fun testConnection(
        provider: ModelProviderConfig,
        trustAllHttpsCertificates: Boolean = false
    ): ConnectionTestResult
}
