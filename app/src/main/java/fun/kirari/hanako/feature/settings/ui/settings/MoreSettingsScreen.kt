package `fun`.kirari.hanako.feature.settings.ui.settings

import `fun`.kirari.hanako.feature.settings.ui.automation.AutoModeSettingsCard
import `fun`.kirari.hanako.feature.settings.ui.automation.BubbleAppearanceResetButton
import `fun`.kirari.hanako.feature.settings.ui.automation.BubbleAppearanceSettingsCard
import `fun`.kirari.hanako.feature.settings.ui.automation.BubbleMenuSettingsCard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.BuildConfig
import `fun`.kirari.hanako.core.data.AutomationSettings
import `fun`.kirari.hanako.core.data.BubbleAppearanceSettings
import `fun`.kirari.hanako.core.data.KirariSettings
import `fun`.kirari.hanako.core.data.ScreenCaptureMethod
import `fun`.kirari.hanako.feature.home.presentation.RegisterScrollToTopHandler
import kotlinx.coroutines.launch

@Composable
fun MoreSettingsScreen(
    scrollRoute: String,
    automationSettings: AutomationSettings,
    selectedMethod: ScreenCaptureMethod,
    trustAllHttpsCertificates: Boolean,
    kirariSettings: KirariSettings,
    hasKirariClientId: Boolean,
    hasNotificationPermission: Boolean,
    onToggleCompletionNotification: (Boolean) -> Unit,
    onOpenNotificationPermission: () -> Unit,
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
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    RegisterScrollToTopHandler(route = scrollRoute) {
        coroutineScope.launch {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
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
                icon = Icons.Default.DonutLarge,
                title = "扇形菜单设置",
                subtitle = "配置悬浮球双击展开的快捷菜单。"
            ) {
                BubbleMenuSettingsCard(
                    appearanceSettings = automationSettings.bubbleAppearance,
                    enabled = automationSettings.bubbleMenuEnabled,
                    onEnabledChange = { enabled ->
                        onUpdateAutomationSettings(automationSettings.copy(bubbleMenuEnabled = enabled))
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
                    hasNotificationPermission = hasNotificationPermission,
                    onTimeoutInputChange = { timeoutInput = it },
                    onToggleCompletionNotification = onToggleCompletionNotification,
                    onOpenNotificationPermission = onOpenNotificationPermission,
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
