package `fun`.kirari.hanako.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.BuildConfig
import `fun`.kirari.hanako.data.AutomationSettings
import `fun`.kirari.hanako.data.BubbleAppearanceSettings
import `fun`.kirari.hanako.data.KirariSettings
import `fun`.kirari.hanako.data.ScreenCaptureMethod

@Composable
fun MoreSettingsScreen(
    automationSettings: AutomationSettings,
    selectedMethod: ScreenCaptureMethod,
    trustAllHttpsCertificates: Boolean,
    kirariSettings: KirariSettings,
    hasKirariClientId: Boolean,
    onToggleCompletionNotification: (Boolean) -> Unit,
    onToggleStaticMode: (Boolean) -> Unit,
    onNavigateStaticVibrationSettings: () -> Unit,
    onUpdateAutomationSettings: (AutomationSettings) -> Unit,
    onUpdateTimeoutSeconds: (Int) -> Unit,
    onSelectMethod: (ScreenCaptureMethod) -> Unit,
    onToggleTrustAllHttpsCertificates: (Boolean) -> Unit,
    onUpdateKirariServerUrl: (String) -> Unit,
    onLoginKirari: () -> Unit,
    onLogoutKirari: () -> Unit
) {
    var timeoutInput by remember(automationSettings.autoModeTimeoutSeconds) {
        mutableStateOf(automationSettings.autoModeTimeoutSeconds.toString())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MoreSettingCard(
                icon = Icons.Default.Adjust,
                title = "悬浮球外观",
                subtitle = "调整悬浮球的尺寸与透明度。",
                trailing = {
                    BubbleAppearanceResetButton(
                        onReset = {
                            onUpdateAutomationSettings(
                                automationSettings.copy(bubbleAppearance = BubbleAppearanceSettings())
                            )
                        }
                    )
                }
            ) {
                BubbleAppearanceSettingsCard(
                    settings = automationSettings.bubbleAppearance,
                    onChange = { bubbleAppearance ->
                        onUpdateAutomationSettings(automationSettings.copy(bubbleAppearance = bubbleAppearance))
                    }
                )
            }
        }
        item {
            MoreSettingCard(
                icon = Icons.Default.SmartToy,
                title = "自动模式",
                subtitle = "主页长按启动进入自动模式。"
            ) {
                AutoModeSettingsCard(
                    automationSettings = automationSettings,
                    timeoutInput = timeoutInput,
                    onTimeoutInputChange = { timeoutInput = it },
                    onToggleCompletionNotification = onToggleCompletionNotification,
                    onToggleStaticMode = onToggleStaticMode,
                    onNavigateStaticVibrationSettings = onNavigateStaticVibrationSettings,
                    onUpdateTimeoutSeconds = onUpdateTimeoutSeconds
                )
            }
        }
        item {
            MoreSettingCard(
                icon = Icons.Default.Security,
                title = "网络兼容",
                subtitle = "HTTP 与自签 HTTPS 测试接口。"
            ) {
                TrustAllHttpsSwitch(
                    trustAllHttpsCertificates = trustAllHttpsCertificates,
                    onToggleTrustAllHttpsCertificates = onToggleTrustAllHttpsCertificates
                )
            }
        }
        item {
            MoreSettingCard(
                icon = Icons.Default.PhoneAndroid,
                title = "屏幕录制方式",
                subtitle = "管理当前激活的截图实现。"
            ) {
                ScreenCaptureMethodList(
                    selectedMethod = selectedMethod,
                    onSelectMethod = onSelectMethod
                )
            }
        }
        item {
            if (BuildConfig.SHOW_KIRARI_ENTRY) {
                MoreSettingCard(
                    icon = Icons.Default.Cloud,
                    title = "The Kirari Network",
                    subtitle = "标准 OIDC 登录与 Kirari LLM 网关。"
                ) {
                    KirariSettingsCard(
                        kirariSettings = kirariSettings,
                        hasKirariClientId = hasKirariClientId,
                        onServerUrlCommit = onUpdateKirariServerUrl,
                        onLogin = onLoginKirari,
                        onLogout = onLogoutKirari
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
