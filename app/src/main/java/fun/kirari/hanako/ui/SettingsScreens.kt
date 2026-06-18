@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package `fun`.kirari.hanako.ui

import android.app.Service
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.AppSettings
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.LOCAL_OCR_PROVIDER_ID
import `fun`.kirari.hanako.data.previewPrompt
import `fun`.kirari.hanako.data.AutomationSettings
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ScreenCaptureMethod
import `fun`.kirari.hanako.data.description
import `fun`.kirari.hanako.data.displayName
import `fun`.kirari.hanako.data.resolveModelName
import `fun`.kirari.hanako.data.resolveModelProvider
import `fun`.kirari.hanako.ui.components.AssistantSelector
import `fun`.kirari.hanako.ui.components.ModelButtonField
import `fun`.kirari.hanako.ui.components.ProviderEditor
import `fun`.kirari.hanako.ui.components.SectionCard

@Composable
fun SettingsMenuScreen(
    onNavigateProvider: () -> Unit,
    onNavigateModel: () -> Unit,
    onNavigateAssistant: () -> Unit,
    onNavigateMore: () -> Unit,
    onNavigateDebugLogs: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsEntryCard(
                title = "模型提供方",
                subtitle = "配置 API 地址与模型",
                icon = Icons.Default.Build,
                onClick = onNavigateProvider
            )
        }
        item {
            SettingsEntryCard(
                title = "模型设置",
                subtitle = "为 OCR、文本、多模态分别指定提供方和模型",
                icon = Icons.Default.Memory,
                onClick = onNavigateModel
            )
        }
        item {
            SettingsEntryCard(
                title = "助手配置",
                subtitle = "管理助手名称与提示词",
                icon = Icons.Default.Person,
                onClick = onNavigateAssistant
            )
        }
        item {
            SettingsEntryCard(
                title = "更多",
                subtitle = "自动模式、屏幕录制方式、网络兼容",
                icon = Icons.Default.SmartToy,
                onClick = onNavigateMore
            )
        }
    }
}

@Composable
fun MoreSettingsScreen(
    automationSettings: AutomationSettings,
    selectedMethod: ScreenCaptureMethod,
    trustAllHttpsCertificates: Boolean,
    onToggleCompletionNotification: (Boolean) -> Unit,
    onToggleStaticMode: (Boolean) -> Unit,
    onNavigateStaticVibrationSettings: () -> Unit,
    onUpdateTimeoutSeconds: (Int) -> Unit,
    onSelectMethod: (ScreenCaptureMethod) -> Unit,
    onToggleTrustAllHttpsCertificates: (Boolean) -> Unit
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
                icon = Icons.Default.SmartToy,
                title = "自动模式",
                subtitle = "主页长按启动进入自动模式。"
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "完成后发送通知",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "处理完成后发送系统通知。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = automationSettings.completionNotificationEnabled,
                            onCheckedChange = onToggleCompletionNotification
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onNavigateStaticVibrationSettings),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "静态模式",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "隐藏悬浮球动画，使用振动表示识别结果",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = automationSettings.staticModeEnabled,
                            onCheckedChange = onToggleStaticMode
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "自动模式超时时间",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "首字延迟超时，当前用于流式请求在收到第一段输出前的等待时间。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = timeoutInput,
                        onValueChange = { value ->
                            val digits = value.filter(Char::isDigit)
                            timeoutInput = digits
                            digits.toIntOrNull()?.takeIf { it > 0 }?.let(onUpdateTimeoutSeconds)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("秒") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        "默认 30 秒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            MoreSettingCard(
                icon = Icons.Default.Security,
                title = "网络兼容",
                subtitle = "HTTP 与自签 HTTPS 测试接口。"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "信任所有 HTTPS 证书",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "允许连接自签名、过期或域名不匹配的 HTTPS 服务。存在安全风险，仅建议用于个人测试接口。HTTP 明文访问已允许。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = trustAllHttpsCertificates,
                        onCheckedChange = onToggleTrustAllHttpsCertificates
                    )
                }
            }
        }

        item {
            MoreSettingCard(
                icon = Icons.Default.PhoneAndroid,
                title = "屏幕录制方式",
                subtitle = "管理当前激活的截图实现。"
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
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun StaticVibrationSettingsScreen(
    automationSettings: AutomationSettings,
    onUpdateSettings: ((AutomationSettings) -> AutomationSettings) -> Unit
) {
    val context = LocalContext.current
    var intraLetterGapInput by remember(automationSettings.staticIntraLetterGapMs) {
        mutableStateOf(automationSettings.staticIntraLetterGapMs.toString())
    }
    var interLetterGapInput by remember(automationSettings.staticInterLetterGapMs) {
        mutableStateOf(automationSettings.staticInterLetterGapMs.toString())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "静态模式振动") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "使用振动次数表示答案对应的选项。隐藏悬浮球动画和弹窗。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VibrationDurationField(
                        label = "单个字母内部间隔",
                        value = intraLetterGapInput,
                        onValueChange = { value ->
                            val digits = value.filter(Char::isDigit)
                            intraLetterGapInput = digits
                            digits.toIntOrNull()?.takeIf { it >= 0 }?.let { gap ->
                                onUpdateSettings { current ->
                                    current.copy(staticIntraLetterGapMs = gap)
                                }
                            }
                        }
                    )
                    VibrationDurationField(
                        label = "字母之间间隔",
                        value = interLetterGapInput,
                        onValueChange = { value ->
                            val digits = value.filter(Char::isDigit)
                            interLetterGapInput = digits
                            digits.toIntOrNull()?.takeIf { it >= 0 }?.let { gap ->
                                onUpdateSettings { current ->
                                    current.copy(staticInterLetterGapMs = gap)
                                }
                            }
                        }
                    )
                    OutlinedButton(
                        onClick = {
                            playPreviewVibration(context, automationSettings, "ABCD")
                            Toast.makeText(context, "正在试听 ABCD", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("试听 ABCD")
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderSettingsScreen(
    settings: AppSettings,
    onAddProvider: () -> Unit,
    onDeleteProvider: (String) -> Unit,
    onOpenProvider: (String) -> Unit
) {
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "模型提供方",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onAddProvider) {
                    Text("新增")
                }
            }
        }
        items(settings.providers, key = { it.id }) { provider ->
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = { onOpenProvider(provider.id) },
                    onLongClick = { deleteTargetId = provider.id }
                ),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            provider.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            provider.kind.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    val deleteTarget = settings.providers.firstOrNull { it.id == deleteTargetId }
    if (deleteTarget != null) {
        DeleteConfirmDialog(
            title = "删除提供方",
            message = "确认删除 ${deleteTarget.name}？",
            onDismiss = { deleteTargetId = null },
            onConfirm = {
                onDeleteProvider(deleteTarget.id)
                deleteTargetId = null
            }
        )
    }
}

@Composable
fun ProviderDetailScreen(
    provider: ModelProviderConfig,
    connectionTestState: ConnectionTestState,
    onUpdateProvider: (ModelProviderConfig) -> Unit,
    onViewModels: () -> Unit,
    onTestConnection: (ModelProviderConfig) -> Unit,
    onClearConnectionTest: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(provider.id) {
        onDispose { onClearConnectionTest() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderEditor(
                        provider = provider,
                        onChange = onUpdateProvider,
                        onImportResult = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    )
                    OutlinedButton(
                        onClick = onViewModels,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("查看可用模型")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onTestConnection(provider) },
                            enabled = connectionTestState.status != ConnectionTestStatus.TESTING
                        ) {
                            if (connectionTestState.status == ConnectionTestStatus.TESTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("测试中...")
                            } else {
                                Text("测试连接")
                            }
                        }
                        ConnectionTestInlineResult(
                            state = connectionTestState,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun ModelSettingsScreen(
    settings: AppSettings,
    onPickModel: (ModelPurpose) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "模型设置") {
                ModelPurpose.entries.forEach { purpose ->
                    val provider = settings.resolveModelProvider(purpose)
                    val model = settings.resolveModelName(purpose)
                    ModelButtonField(
                        label = "${purpose.displayName} 模型",
                        value = buildString {
                            append(
                                when {
                                    purpose == ModelPurpose.OCR && settings.ocrModelSelection.providerId == LOCAL_OCR_PROVIDER_ID ->
                                        "本地ML Kit"
                                    provider != null -> provider.name
                                    else -> "未选择提供方"
                                }
                            )
                            if (model.isNotBlank()) {
                                append(" / ")
                                append(model)
                            }
                        },
                        onPick = { onPickModel(purpose) }
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun AssistantSettingsScreen(
    settings: AppSettings,
    onAddAssistant: () -> Unit,
    onDeleteAssistant: (String) -> Unit,
    onOpenAssistant: (String) -> Unit
) {
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "助手配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onAddAssistant) {
                    Text("新增")
                }
            }
        }
        items(settings.assistants, key = { it.id }) { assistant ->
            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = { onOpenAssistant(assistant.id) },
                    onLongClick = { deleteTargetId = assistant.id }
                ),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            assistant.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            assistant.previewPrompt().replace('\n', ' '),
                            maxLines = 2,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    val deleteTarget = settings.assistants.firstOrNull { it.id == deleteTargetId }
    if (deleteTarget != null) {
        DeleteConfirmDialog(
            title = "删除助手",
            message = "确认删除 ${deleteTarget.name}？",
            onDismiss = { deleteTargetId = null },
            onConfirm = {
                onDeleteAssistant(deleteTarget.id)
                deleteTargetId = null
            }
        )
    }
}

@Composable
fun AssistantDetailScreen(
    assistant: AssistantPreset,
    onUpdateAssistant: (AssistantPreset) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "助手配置") {
                AssistantSelector(
                    assistant = assistant,
                    onChange = onUpdateAssistant
                )
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun SettingsEntryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MoreSettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun VibrationDurationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("毫秒") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

private fun playPreviewVibration(
    context: android.content.Context,
    settings: AutomationSettings,
    text: String
) {
    val pattern = mutableListOf<Long>()
    val amplitudes = mutableListOf<Int>()
    pattern += 0L
    amplitudes += 0

    text.uppercase().forEachIndexed { index, ch ->
        val count = ch.code - 'A'.code + 1
        if (count <= 0) return@forEachIndexed
        repeat(count) { pulseIndex ->
            pattern += 30L
            amplitudes += VibrationEffect.DEFAULT_AMPLITUDE
            if (pulseIndex != count - 1) {
                pattern += settings.staticIntraLetterGapMs.toLong()
                amplitudes += 0
            }
        }
        if (index != text.lastIndex) {
            pattern += settings.staticInterLetterGapMs.toLong()
            amplitudes += 0
        }
    }

    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern.toLongArray(), amplitudes.toIntArray(), -1))
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Service.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern.toLongArray(), amplitudes.toIntArray(), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern.toLongArray(), -1)
                }
            }
        }
    }
}

@Composable
private fun ConnectionTestInlineResult(
    state: ConnectionTestState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.status == ConnectionTestStatus.SUCCESS || state.status == ConnectionTestStatus.FAILED,
        modifier = modifier,
        enter = expandHorizontally(expandFrom = Alignment.Start) +
            slideInHorizontally { -it / 3 } +
            fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
    ) {
        val success = state.status == ConnectionTestStatus.SUCCESS
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (success) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    if (success) "连接成功 ${state.latencyMs}ms" else "连接失败 ${state.errorMessage}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (success) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        icon = {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }
    )
}
