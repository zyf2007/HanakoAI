package `fun`.kirari.hanako.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.kirari.hanako.data.BubbleAppearanceSettings
import `fun`.kirari.hanako.data.MAX_BUBBLE_DIAMETER_DP
import `fun`.kirari.hanako.data.MAX_BUBBLE_LETTER_TEXT_SIZE_DP
import `fun`.kirari.hanako.data.MAX_SPINNER_DIAMETER_DP
import `fun`.kirari.hanako.data.MIN_BUBBLE_DIAMETER_DP
import `fun`.kirari.hanako.data.MIN_BUBBLE_LETTER_TEXT_SIZE_DP
import `fun`.kirari.hanako.data.MIN_SPINNER_DIAMETER_DP
import kotlinx.coroutines.delay

@Composable
internal fun BubbleAppearanceResetButton(
    onReset: () -> Unit
) {
    OutlinedButton(onClick = onReset) {
        Text("重置")
    }
}

@Composable
internal fun BubbleAppearanceSettingsCard(
    settings: BubbleAppearanceSettings,
    onChange: (BubbleAppearanceSettings) -> Unit
) {
    var draftSettings by remember(settings) { mutableStateOf(settings) }

    LaunchedEffect(draftSettings) {
        if (draftSettings != settings) {
            delay(180)
            onChange(draftSettings)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BubblePreview(settings = draftSettings)
        SliderSettingRow(
            title = "悬浮球直径",
            value = draftSettings.bubbleDiameterDp,
            range = MIN_BUBBLE_DIAMETER_DP..MAX_BUBBLE_DIAMETER_DP,
            valueText = draftSettings.bubbleDiameterDp.toInt().toString(),
            onValueChange = { draftSettings = draftSettings.copy(bubbleDiameterDp = it) },
            onValueChangeFinished = { onChange(draftSettings) }
        )
        SliderSettingRow(
            title = "加载动画直径",
            value = draftSettings.spinnerDiameterDp,
            range = MIN_SPINNER_DIAMETER_DP..MAX_SPINNER_DIAMETER_DP,
            valueText = draftSettings.spinnerDiameterDp.toInt().toString(),
            onValueChange = { draftSettings = draftSettings.copy(spinnerDiameterDp = it) },
            onValueChangeFinished = { onChange(draftSettings) }
        )
        SliderSettingRow(
            title = "字母大小",
            value = draftSettings.letterTextSizeDp,
            range = MIN_BUBBLE_LETTER_TEXT_SIZE_DP..MAX_BUBBLE_LETTER_TEXT_SIZE_DP,
            valueText = draftSettings.letterTextSizeDp.toInt().toString(),
            onValueChange = { draftSettings = draftSettings.copy(letterTextSizeDp = it) },
            onValueChangeFinished = { onChange(draftSettings) }
        )
        SliderSettingRow(
            title = "字母透明度",
            value = draftSettings.letterOpacity,
            range = 0f..100f,
            valueText = draftSettings.letterOpacity.toInt().toString(),
            onValueChange = { draftSettings = draftSettings.copy(letterOpacity = it) },
            onValueChangeFinished = { onChange(draftSettings) }
        )
        SliderSettingRow(
            title = "悬浮球透明度",
            value = draftSettings.overallOpacity,
            range = 0f..100f,
            valueText = draftSettings.overallOpacity.toInt().toString(),
            onValueChange = { draftSettings = draftSettings.copy(overallOpacity = it) },
            onValueChangeFinished = { onChange(draftSettings) }
        )
    }
}

@Composable
private fun BubblePreview(settings: BubbleAppearanceSettings) {
    var previewState by remember { mutableStateOf(BubblePreviewState.Idle) }
    val bubbleDiameter = settings.bubbleDiameterDp.dp
    val spinnerDiameter = settings.spinnerDiameterDp.dp
    val overallAlpha = (settings.overallOpacity / 100f).coerceIn(0f, 1f)
    val letterAlpha = (settings.letterOpacity / 100f).coerceIn(0f, 1f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "预览",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BubblePreviewCanvas(
                    modifier = Modifier.weight(1f),
                    settings = settings,
                    previewState = previewState,
                    bubbleDiameter = bubbleDiameter,
                    spinnerDiameter = spinnerDiameter,
                    overallAlpha = overallAlpha,
                    letterAlpha = letterAlpha
                )
                BubblePreviewStateSelector(
                    selectedState = previewState,
                    onSelectState = { previewState = it }
                )
            }
        }
    }
}

@Composable
private fun BubblePreviewCanvas(
    modifier: Modifier,
    settings: BubbleAppearanceSettings,
    previewState: BubblePreviewState,
    bubbleDiameter: Dp,
    spinnerDiameter: Dp,
    overallAlpha: Float,
    letterAlpha: Float
) {
    Box(
        modifier = modifier
            .height(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (previewState == BubblePreviewState.Loading) {
            SpinnerRing(
                size = spinnerDiameter,
                color = Color(0xFF6750A4),
                alpha = overallAlpha
            )
        }
        BubbleCircle(
            size = bubbleDiameter,
            alpha = overallAlpha,
            shape = RoundedCornerShape(percent = 50)
        ) {
            if (previewState == BubblePreviewState.Idle) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF4F378B),
                    modifier = Modifier.size((settings.bubbleDiameterDp * 0.45f).dp)
                )
            }
        }
        if (previewState == BubblePreviewState.Letters) {
            Text(
                text = "AB",
                modifier = Modifier.alpha(letterAlpha),
                color = Color(0xFF0B57D0),
                fontSize = settings.letterTextSizeDp.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = settings.letterTextSizeDp.sp
            )
        }
    }
}

@Composable
private fun BubblePreviewStateSelector(
    selectedState: BubblePreviewState,
    onSelectState: (BubblePreviewState) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(118.dp)
            .height(132.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "状态切换",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            BubblePreviewState.entries.forEach { state ->
                BubblePreviewStateItem(
                    state = state,
                    selected = selectedState == state,
                    onClick = { onSelectState(state) }
                )
            }
        }
    }
}

@Composable
private fun BubblePreviewStateItem(
    state: BubblePreviewState,
    selected: Boolean,
    onClick: () -> Unit
) {
    val itemShape = RoundedCornerShape(10.dp)
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 26.dp)
            .clip(itemShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
        )
        Text(
            text = state.label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1
        )
    }
}

private enum class BubblePreviewState(val label: String) {
    Idle("闲置状态"),
    Loading("加载状态"),
    Letters("字母展示")
}

@Composable
private fun SpinnerRing(
    size: Dp,
    color: Color,
    alpha: Float
) {
    val transition = rememberInfiniteTransition(label = "bubble_preview_spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubble_preview_spinner_rotation"
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation - 90f }
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            progress = { 0.72f },
            color = color,
            strokeWidth = 3.dp,
            trackColor = Color.Transparent
        )
    }
}

@Composable
private fun BubbleCircle(
    size: Dp,
    alpha: Float,
    shape: Shape,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .size(size)
            .alpha(alpha),
        shape = shape,
        color = Color(0xFFEADDFF)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
