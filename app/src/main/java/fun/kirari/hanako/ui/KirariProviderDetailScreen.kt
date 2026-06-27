package `fun`.kirari.hanako.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.llm.core.ProviderUsageSummary
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun KirariProviderDetailScreen(
    provider: ModelProviderConfig,
    providerMetaState: ProviderMetaState,
    kirariAccountState: KirariAccountState,
    connectionTestState: ConnectionTestState,
    hasKirariClientId: Boolean,
    shouldSuggestAutoSetup: Boolean,
    onApplyAutoSetup: () -> Unit,
    onViewModels: () -> Unit,
    onTestConnection: (ModelProviderConfig) -> Unit,
    onLoginKirari: () -> Unit,
    onLogoutKirari: () -> Unit
) {
    // 根背景使用纯粹的 surface，剥离所有自定义 SectionCard 的干扰
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. 无边框沉浸式 Header（身份与状态）
            item {
                AccountHeroHeader(
                    accountState = kirariAccountState,
                    hasKirariClientId = hasKirariClientId,
                    onLogin = onLoginKirari,
                    onLogout = onLogoutKirari
                )
            }

            // 2. 全页唯一高光卡片：额度仪表盘
            item {
                MasterQuotaCard(
                    state = providerMetaState,
                    shouldSuggestAutoSetup = shouldSuggestAutoSetup,
                    onApplyAutoSetup = onApplyAutoSetup
                )
            }

            // 3. 纵向规整参数表（替代原本的田字格）
            item {
                SpecsTableView(usageSummary = providerMetaState.usageSummary)
            }

            // 4. 极简操作台
            item {
                OperationsDeck(
                    provider = provider,
                    connectionTestState = connectionTestState,
                    onViewModels = onViewModels,
                    onTestConnection = onTestConnection
                )
            }

            item { Spacer(modifier = Modifier.height(60.dp)) }
        }
    }
}

// ============================================================================
// 1. 沉浸式头部 (No Card Container)
// ============================================================================
@Composable
private fun AccountHeroHeader(
    accountState: KirariAccountState,
    hasKirariClientId: Boolean,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val accountLabel = accountState.email ?: accountState.displayName ?: accountState.subject
    val isConnected = accountState.loggedIn

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 在线小绿点 / 离线小灰点
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                )
                Text(
                    text = "The Kirari Network",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = if (isConnected) "已连接 · ${accountLabel ?: "默认节点"}" else "未接入 Kirari 统一网关",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 动作按钮顺滑贴在右侧
        when {
            accountState.loading -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            !isConnected -> {
                Button(
                    onClick = onLogin,
                    enabled = hasKirariClientId,
                    shape = RoundedCornerShape(99.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("登录网关", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            else -> {
                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(99.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("断开", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ============================================================================
// 2. 额度主卡片 (Master Quota Card)
// ============================================================================
@Composable
private fun MasterQuotaCard(
    state: ProviderMetaState,
    shouldSuggestAutoSetup: Boolean,
    onApplyAutoSetup: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, // 极净单层背景
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.padding(24.dp)) {
            when {
                state.loading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }
                state.errorMessage != null -> {
                    Text(state.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                state.usageSummary != null -> {
                    val summary = state.usageSummary
                    val total = summary.dailyCredits ?: 0.0
                    val left = summary.dailyCreditsLeft ?: max(total - (summary.dailyCreditsUsed ?: 0.0), 0.0)
                    val used = summary.dailyCreditsUsed ?: max(total - left, 0.0)
                    val progress = if (total > 0.0) min((left / total).toFloat(), 1f) else 0f

                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column {
                                Text("剩余可用额度 (Credits)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = left.formatCompactNumber(),
                                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp),
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("今日周期上限", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (total > 0) total.formatCompactNumber() else "无限制",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 极简双色进度条
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("已消耗: ${used.formatCompactNumber()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("计费状态正常", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), fontWeight = FontWeight.Medium)
                        }

                        AnimatedVisibility(
                            visible = shouldSuggestAutoSetup,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            AutoSetupCard(onApplyAutoSetup = onApplyAutoSetup)
                        }
                    }
                }
                else -> {
                    Text("请登录账户以同步 API 额度数据。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AutoSetupCard(onApplyAutoSetup: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.surface.luminance() < 0.5f
    val backgroundBrush = if (isDarkTheme) {
        Brush.linearGradient(
            listOf(Color(0xFF0F172A), Color(0xFF123A5A), Color(0xFF154E63))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFE0F2FE), Color(0xFFECFEFF), Color(0xFFDCFCE7))
        )
    }
    val titleColor = if (isDarkTheme) Color(0xFFF0F9FF) else Color(0xFF0F172A)
    val bodyColor = if (isDarkTheme) Color(0xFFBAE6FD) else Color(0xFF164E63)
    val buttonContainer = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0EA5E9)
    val buttonContent = if (isDarkTheme) Color(0xFF082F49) else Color(0xFFF8FAFC)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundBrush)
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "一键自动设置模型",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = titleColor
            )
            Text(
                text = "自动配置为 The Kirari Network 推荐的模型组合。",
                style = MaterialTheme.typography.bodySmall,
                color = bodyColor
            )
            Button(
                onClick = onApplyAutoSetup,
                shape = RoundedCornerShape(99.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonContainer,
                    contentColor = buttonContent
                )
            ) {
                Text("立即自动设置", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// 3. 纵向规整表 (The Specs Table View)
// ============================================================================
@Composable
private fun SpecsTableView(usageSummary: ProviderUsageSummary?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "网关运行限制",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                SpecRowItem(
                    icon = Icons.Default.Person,
                    label = "当前调度用户组",
                    value = usageSummary?.groupName?.takeIf { it.isNotBlank() } ?: "Default Pool",
                    isLast = false
                )
                SpecRowItem(
                    icon = Icons.Default.Speed,
                    label = "每分钟请求并发 (RPM)",
                    value = usageSummary?.requestsPerMinute?.let { "$it 次/分" } ?: "无限制",
                    isLast = false
                )
                SpecRowItem(
                    icon = Icons.Default.Memory,
                    label = "单次最大上下文输入",
                    value = usageSummary?.maxInputTokens?.formatWithCommas()?.let { "$it Tokens" } ?: "标准动态上限",
                    isLast = false
                )
                SpecRowItem(
                    icon = Icons.Default.Toll,
                    label = "单次生成截断输出",
                    value = usageSummary?.maxOutputTokens?.formatWithCommas()?.let { "$it Tokens" } ?: "系统默认",
                    isLast = true
                )
            }
        }
    }
}

@Composable
private fun SpecRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    isLast: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        if (!isLast) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        }
    }
}

// ============================================================================
// 4. 操作台 (Operations Deck)
// ============================================================================
@Composable
private fun OperationsDeck(
    provider: ModelProviderConfig,
    connectionTestState: ConnectionTestState,
    onViewModels: () -> Unit,
    onTestConnection: (ModelProviderConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "诊断与支持",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                // Action 1: 测试连接
                OpActionRow(
                    label = "测试节点连通延迟",
                    state = connectionTestState,
                    onClick = { onTestConnection(provider) },
                    isLast = false
                )
                
                // Action 2: 查看可用模型
                Surface(
                    onClick = onViewModels,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("查阅此网关支持的模型清单", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun OpActionRow(
    label: String,
    state: ConnectionTestState,
    onClick: () -> Unit,
    isLast: Boolean
) {
    val isTesting = state.status == ConnectionTestStatus.TESTING
    val isSuccess = state.status == ConnectionTestStatus.SUCCESS
    val isFail = state.status == ConnectionTestStatus.FAILED

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(visible = isSuccess || isFail, enter = fadeIn(), exit = fadeOut()) {
                    val tint = if (isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                        Text(if (isSuccess) "${state.latencyMs}ms" else "失败", style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onClick,
                    enabled = !isTesting,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("发起 Ping", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (!isLast) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        }
    }
}

// ============================================================================
// Helpers
// ============================================================================
private fun Double.formatCompactNumber(): String {
    val whole = toLong()
    return if (toDouble() == whole.toDouble()) {
        whole.toString()
    } else {
        String.format(Locale.US, "%.2f", this)
    }
}

private fun Int.formatWithCommas(): String {
    return NumberFormat.getNumberInstance(Locale.US).format(this)
}
