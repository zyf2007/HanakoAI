package `fun`.kirari.hanako.ui.components

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.previewPrompt
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.ScreenCaptureMethod
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroSection(
    overlayEnabled: Boolean,
    hasOverlayPermission: Boolean,
    captureMethod: ScreenCaptureMethod,
    route: ProcessingRoute,
    onSelectRoute: (ProcessingRoute) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onStartAutoMode: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 头部：应用信息与精致的运行状态标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            "Hanako AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "屏幕智能助理",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }

                // 胶囊状态指示器
                val statusContainerColor = if (overlayEnabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.05f)
                }
                val statusDotColor = if (overlayEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                Surface(
                    shape = CircleShape,
                    color = statusContainerColor,
                    border = if (overlayEnabled) BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Text(
                            if (overlayEnabled) "运行中" else "已停止",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 工作模式区域 (替换掉原本丑陋的 SegmentedButtonRow)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "工作模式",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProcessingRoute.entries.forEach { item ->
                        val isSelected = route == item
                        ModeSelectorItem(
                            modifier = Modifier.weight(1f),
                            title = if (item == ProcessingRoute.OCR_THEN_LLM) "OCR 模式" else "多模态视觉",
                            subtitle = if (item == ProcessingRoute.OCR_THEN_LLM) "提取文字后分析" else "直接分析全屏幕",
                            icon = if (item == ProcessingRoute.OCR_THEN_LLM) Icons.Default.Language else Icons.Default.AutoAwesome,
                            isSelected = isSelected,
                            onClick = { onSelectRoute(item) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))

            // 底部操作按钮群
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenOverlayPermission,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("权限设置", fontWeight = FontWeight.Medium)
                    }

                    val buttonColor = if (overlayEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                    val buttonTextColor = if (overlayEnabled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primaryContainer

                    Button(
                        onClick = { },
                        modifier = Modifier.weight(1f),
                        enabled = hasOverlayPermission,
                        shape = RoundedCornerShape(16.dp),
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        onToggleOverlay(!overlayEnabled && hasOverlayPermission)
                                    },
                                    onLongClick = {
                                        if (!overlayEnabled && hasOverlayPermission) {
                                            onStartAutoMode()
                                        }
                                    }
                                ),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (overlayEnabled) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(if (overlayEnabled) "关闭" else "启动", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 提示区域
                if (!hasOverlayPermission) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "核心提示：请先开启悬浮窗权限以激活功能",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (captureMethod == ScreenCaptureMethod.MEDIA_PROJECTION &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    !overlayEnabled
                ) {
                    Text(
                        "提示：点击启动进入普通模式，长按启动进入自动模式；Android 14+ 首次会弹出截屏授权。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                } else if (captureMethod == ScreenCaptureMethod.SHIZUKU_ADB && !overlayEnabled) {
                    Text(
                        "提示：当前使用 Shizuku 路线。启动时会先申请 Shizuku 授权，后续截图走 shell screencap。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                } else if (!overlayEnabled) {
                    Text(
                        "点击启动进入普通模式，长按启动进入自动模式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * 优雅的独立分立式模式选择卡片
 */
@Composable
fun ModeSelectorItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.05f),
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        label = "contentColor"
    )
    val borderDp by animateDpAsState(
        targetValue = if (isSelected) 0.dp else 1.dp,
        label = "borderDp"
    )

    Surface(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = if (borderDp > 0.dp) BorderStroke(borderDp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)) else null
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = if (isSelected) 1f else 0.8f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.65f),
                    maxLines = 1
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}

@Composable
fun RouteSection(
    route: ProcessingRoute,
    onSelect: (ProcessingRoute) -> Unit
) {
    SectionCard(title = "工作模式") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProcessingRoute.entries.forEach { item ->
                val isSelected = route == item
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(item) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                        Text(
                            if (item == ProcessingRoute.OCR_THEN_LLM) "OCR+语言模型" else "多模态模型",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantSelector(
    assistant: AssistantPreset,
    onChange: (AssistantPreset) -> Unit,
) {
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DraftOutlinedTextField(
                fieldKey = "${assistant.id}:name",
                value = assistant.name,
                onCommit = { onChange(assistant.copy(name = it)) },
                label = "助手名称"
            )
            DraftOutlinedTextField(
                fieldKey = "${assistant.id}:ocrPrompt",
                value = assistant.ocrPrompt,
                onCommit = { onChange(assistant.copy(ocrPrompt = it)) },
                label = "OCR 模型提示词",
                minLines = 4
            )
            DraftOutlinedTextField(
                fieldKey = "${assistant.id}:textPrompt",
                value = assistant.textPrompt,
                onCommit = { onChange(assistant.copy(textPrompt = it)) },
                label = "LLM 提示词",
                minLines = 5
            )
            DraftOutlinedTextField(
                fieldKey = "${assistant.id}:visionPrompt",
                value = assistant.visionPrompt,
                onCommit = { onChange(assistant.copy(visionPrompt = it)) },
                label = "多模态模型提示词",
                minLines = 5
            )
        }
    }
}

@Composable
fun DraftOutlinedTextField(
    fieldKey: String,
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    minLines: Int = 1
) {
    var textFieldValue by rememberSaveable(fieldKey, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    var isFocused by remember(fieldKey) { mutableStateOf(false) }

    LaunchedEffect(fieldKey, value, isFocused) {
        if (!isFocused && value != textFieldValue.text) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    LaunchedEffect(fieldKey, textFieldValue.text) {
        delay(250)
        if (textFieldValue.text != value) {
            onCommit(textFieldValue.text)
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { textFieldValue = it },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (!focusState.isFocused && textFieldValue.text != value) {
                    onCommit(textFieldValue.text)
                }
            },
        minLines = minLines,
        label = { Text(label) },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                content()
            }
        }
    }
}
