package `fun`.kirari.hanako.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.displayName
import `fun`.kirari.hanako.network.ProviderModelsApi
import `fun`.kirari.hanako.ui.components.ModelPickerListContent
import `fun`.kirari.hanako.ui.components.ModelPickerSurfaceItem
import `fun`.kirari.hanako.ui.components.rememberModelPickerState
import kotlinx.coroutines.delay

@Composable
internal fun ProviderPickerOverlay(
    providers: List<ModelProviderConfig>,
    closing: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onPick: (ModelProviderConfig) -> Unit
) {
    val density = LocalDensity.current.density
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            visible = false
            delay(220)
            onDismissFinished()
        }
    }
    val overlayAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "providerPickerOverlayAlpha"
    )
    val cardAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "providerPickerCardAlpha"
    )
    val cardTranslationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else if (closing) -20f else 20f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "providerPickerCardTranslationY"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f * overlayAlpha))
            .clickable(onClick = {
                if (!closing) onDismiss()
            }),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(300.dp)
                .heightIn(max = 520.dp)
                .graphicsLayer {
                    alpha = cardAlpha
                    translationY = cardTranslationY * density
                }
                .clickable(enabled = false, onClick = { })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(providers, key = { it.id }) { provider ->
                        ProviderPickerItem(provider = provider, onPick = onPick)
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (!closing) onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
internal fun ModelPickerOverlay(
    provider: ModelProviderConfig,
    closing: Boolean,
    title: String,
    api: ProviderModelsApi,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onPick: (String, Boolean) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onCustomModelRequest: (String) -> Unit
) {
    val pickerState = rememberModelPickerState(provider = provider, api = api)
    OverlaySelectionShell(
        title = title,
        closing = closing,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
        footer = {
            OutlinedButton(
                onClick = { onCustomModelRequest(title) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("使用自定义模型")
            }
        }
    ) {
        ModelPickerListContent(
            models = pickerState.entries,
            hasNetworkResponse = pickerState.hasNetworkResponse,
            onPick = { modelId, isFavorite ->
                if (isFavorite && !pickerState.hasNetworkResponse) {
                    pickerState.cancelIfWaitingForNetwork()
                }
                onPick(modelId, isFavorite)
            },
            buttonStyle = { item, onClick, _ ->
                ModelPickerSurfaceItem(
                    model = item,
                    onPick = onClick,
                    onLongPress = {
                        if (item.isFavorite) {
                            pickerState.removeFavoriteFromCurrentSession(item.id)
                        }
                        onToggleFavorite(item.id, item.isFavorite)
                    }
                )
            }
        )
    }
}

@Composable
internal fun CustomModelOverlay(
    title: String,
    closing: Boolean,
    initialValue: String,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val trimmedValue = value.trim()
    OverlaySelectionShell(
        title = title,
        closing = closing,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
        footer = {
            Button(
                onClick = { onConfirm(trimmedValue) },
                enabled = trimmedValue.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("确认")
            }
        }
    ) {
        Text(
            "输入要使用的模型名称，保存后将直接用于当前提供方。",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("自定义模型") },
            placeholder = { Text("例如：gpt-4.1-mini") },
            singleLine = true
        )
    }
}

@Composable
private fun OverlaySelectionShell(
    title: String,
    closing: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current.density
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            visible = false
            delay(220)
            onDismissFinished()
        }
    }
    val overlayAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "$title-overlayAlpha"
    )
    val cardAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "$title-cardAlpha"
    )
    val cardTranslationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else if (closing) -20f else 20f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "$title-cardTranslationY"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f * overlayAlpha))
            .clickable(onClick = {
                if (!closing) onDismiss()
            }),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(300.dp)
                .heightIn(max = 460.dp)
                .graphicsLayer {
                    alpha = cardAlpha
                    translationY = cardTranslationY * density
                }
                .clickable(enabled = false, onClick = { })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
                footer?.invoke()
                OutlinedButton(
                    onClick = {
                        if (!closing) onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun ProviderPickerItem(
    provider: ModelProviderConfig,
    onPick: (ModelProviderConfig) -> Unit
) {
    Surface(
        onClick = { onPick(provider) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(provider.name)
            Text(
                provider.kind.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
