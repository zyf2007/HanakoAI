package `fun`.kirari.hanako.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.BuildConfig
import `fun`.kirari.hanako.data.KirariSettings
import `fun`.kirari.hanako.ui.components.DraftOutlinedTextField

@Composable
internal fun KirariSettingsCard(
    kirariSettings: KirariSettings,
    hasKirariClientId: Boolean,
    onServerUrlCommit: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val expiresAt = kirariSettings.auth.accessTokenExpiresAtMillis
    val loggedIn = kirariSettings.auth.accessToken.isNotBlank() &&
        expiresAt > System.currentTimeMillis()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "模型提供方固定为 The Kirari Network。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        KirariServerUrlField(
            serverUrl = kirariSettings.serverUrl,
            onServerUrlCommit = onServerUrlCommit
        )
        Text(
            text = when {
                !hasKirariClientId -> "当前构建未注入 OIDC Client ID，无法登录。"
                loggedIn -> "已登录，Token 有效。"
                kirariSettings.auth.refreshToken.isNotBlank() -> "访问令牌已过期，请重新登录或等待自动刷新。"
                else -> "未登录。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onLogin,
                enabled = hasKirariClientId,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (loggedIn) "重新登录" else "登录")
            }
            OutlinedButton(
                onClick = onLogout,
                enabled = kirariSettings.auth.accessToken.isNotBlank() || kirariSettings.auth.refreshToken.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("退出登录")
            }
        }
    }
}

@Composable
private fun KirariServerUrlField(
    serverUrl: String,
    onServerUrlCommit: (String) -> Unit
) {
    if (BuildConfig.KIRARI_SERVER_URL_EDITABLE) {
        DraftOutlinedTextField(
            fieldKey = "kirari_server_url",
            value = serverUrl,
            onCommit = onServerUrlCommit,
            modifier = Modifier.fillMaxWidth(),
            label = "Kirari 服务器地址"
        )
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                text = serverUrl.ifBlank { "未配置" },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
