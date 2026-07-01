package `fun`.kirari.hanako.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.ScreenCaptureMethod
import `fun`.kirari.hanako.data.description
import `fun`.kirari.hanako.data.displayName

@Composable
internal fun TrustAllHttpsSwitch(
    trustAllHttpsCertificates: Boolean,
    onToggleTrustAllHttpsCertificates: (Boolean) -> Unit
) {
    SwitchSettingRow(
        title = "信任所有 HTTPS 证书",
        subtitle = "允许连接自签名、过期或域名不匹配的 HTTPS 服务。存在安全风险，仅建议用于个人测试接口。HTTP 明文访问已允许。",
        checked = trustAllHttpsCertificates,
        onCheckedChange = onToggleTrustAllHttpsCertificates
    )
}

@Composable
internal fun ScreenCaptureMethodList(
    selectedMethod: ScreenCaptureMethod,
    onSelectMethod: (ScreenCaptureMethod) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenCaptureMethod.entries.forEach { method ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectMethod(method) },
                shape = RoundedCornerShape(20.dp),
                color = if (selectedMethod == method) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = method.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = method.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RadioButton(
                        selected = selectedMethod == method,
                        onClick = { onSelectMethod(method) }
                    )
                }
            }
        }
    }
}
