package `fun`.kirari.hanako.overlay

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.kirari.hanako.R
import `fun`.kirari.hanako.copyToClipboardWithToast
import `fun`.kirari.hanako.data.AssistantPreset
import `fun`.kirari.hanako.data.previewPrompt
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ModelSelection
import `fun`.kirari.hanako.data.availableProviders
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.displayName
import `fun`.kirari.hanako.data.resolveModelName
import `fun`.kirari.hanako.data.resolveModelProvider
import `fun`.kirari.hanako.network.ProviderModelsApi
import `fun`.kirari.hanako.ui.components.ModelPickerListContent
import `fun`.kirari.hanako.ui.components.ModelPickerSurfaceItem
import `fun`.kirari.hanako.ui.components.rememberModelPickerState
import `fun`.kirari.hanako.ui.cropBitmap
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
internal fun CropOverlaySheet(
    uiState: OverlayUiState,
    onClose: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    panelHeightPx: Int,
    modelPickerTarget: ModelPurpose?,
    onModelPickerTargetChange: (ModelPurpose?) -> Unit,
    onSelectAssistant: (String) -> Unit,
    onSelectPreviousAssistant: () -> Unit,
    onSelectNextAssistant: () -> Unit,
    onUpdateModelSelection: (ModelPurpose, ModelSelection, Boolean) -> Unit,
    onToggleFavoriteModel: (String, String) -> Unit,
    onToggleProcessingRoute: () -> Unit,
    providerModelsApi: ProviderModelsApi
) {
    val bitmap = uiState.screenshot
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cropStart by remember { mutableStateOf(Offset.Zero) }
    var cropEnd by remember { mutableStateOf(Offset.Zero) }
    var cropPromptVisible by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val selectedAssistant = uiState.settings.assistants.firstOrNull { it.id == uiState.settings.selectedAssistantId }
    val routeText = if (uiState.settings.processingRoute == ProcessingRoute.OCR_THEN_LLM) "OCR模式" else "多模态模式"
    val ocrProvider = uiState.settings.resolveModelProvider(ModelPurpose.OCR)
    val ocrModel = uiState.settings.resolveModelName(ModelPurpose.OCR)
    val llmPurpose = if (uiState.settings.processingRoute == ProcessingRoute.OCR_THEN_LLM) ModelPurpose.TEXT else ModelPurpose.VISION
    val llmProvider = uiState.settings.resolveModelProvider(llmPurpose)
    val llmModel = uiState.settings.resolveModelName(llmPurpose)
    val selectionColor = MaterialTheme.colorScheme.primary
    val hasSelection = cropStart != cropEnd
    val panelMaxHeight = with(density) { panelHeightPx.toDp() }
    var showAssistantDialog by remember { mutableStateOf(false) }
    var assistantPickerClosing by remember { mutableStateOf(false) }
    var switchDirection by remember { mutableStateOf(AssistantSwitchDirection.PICKER) }
    var providerPickerTarget by remember { mutableStateOf<ModelPurpose?>(null) }
    var providerPickerClosing by remember { mutableStateOf(false) }
    var customModelTarget by remember { mutableStateOf<ModelPurpose?>(null) }
    var customModelDialogTitle by remember { mutableStateOf<String?>(null) }
    var customModelDialogClosing by remember { mutableStateOf(false) }
    var modelPickerClosing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        var closeRequested by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelMaxHeight)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, top = 52.dp, end = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CropHeaderRow(
                        assistantName = selectedAssistant?.name.orEmpty(),
                        hasMultipleAssistants = uiState.settings.assistants.size > 1,
                        switchDirection = switchDirection,
                        onSelectPrevious = {
                            switchDirection = AssistantSwitchDirection.PREVIOUS
                            onSelectPreviousAssistant()
                        },
                        onSelectNext = {
                            switchDirection = AssistantSwitchDirection.NEXT
                            onSelectNextAssistant()
                        },
                        onOpenPicker = {
                            assistantPickerClosing = false
                            showAssistantDialog = true
                        },
                        onClose = {
                            if (closeRequested) return@CropHeaderRow
                            closeRequested = true
                            onClose()
                        }
                    )
                    ModeModelRow(
                        route = uiState.settings.processingRoute,
                        ocrModel = ocrModel,
                        ocrProviderName = ocrProvider?.name,
                        llmModel = llmModel,
                        llmProviderName = llmProvider?.name,
                        onToggleProcessingRoute = onToggleProcessingRoute,
                        onPickModel = { purpose ->
                            providerPickerClosing = false
                            providerPickerTarget = purpose
                        }
                    )
                    uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black)
                            .onSizeChanged { size -> canvasSize = size }
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            if (cropPromptVisible) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.36f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_bubble_crop),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(52.dp)
                                        )
                                        Text(
                                            text = "滑动截取屏幕区域",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(canvasSize) {
                                        detectDragGestures(
                                            onDragStart = {
                                                cropPromptVisible = false
                                                cropStart = it
                                                cropEnd = it
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                cropEnd += dragAmount
                                            }
                                        )
                                    }
                            ) {
                                if (hasSelection) {
                                    val topLeft = Offset(minOf(cropStart.x, cropEnd.x), minOf(cropStart.y, cropEnd.y))
                                    val size = Size(
                                        width = abs(cropEnd.x - cropStart.x),
                                        height = abs(cropEnd.y - cropStart.y)
                                    )
                                    val right = topLeft.x + size.width
                                    val bottom = topLeft.y + size.height
                                    val overlayColor = Color.Black.copy(alpha = 0.42f)
                                    drawRect(overlayColor, topLeft = Offset.Zero, size = Size(this.size.width, topLeft.y.coerceAtLeast(0f)))
                                    drawRect(overlayColor, topLeft = Offset.Zero, size = Size(topLeft.x.coerceAtLeast(0f), this.size.height))
                                    drawRect(
                                        overlayColor,
                                        topLeft = Offset(right.coerceAtMost(this.size.width), 0f),
                                        size = Size((this.size.width - right).coerceAtLeast(0f), this.size.height)
                                    )
                                    drawRect(
                                        overlayColor,
                                        topLeft = Offset(0f, bottom.coerceAtMost(this.size.height)),
                                        size = Size(this.size.width, (this.size.height - bottom).coerceAtLeast(0f))
                                    )
                                    drawRect(
                                        color = selectionColor,
                                        topLeft = topLeft,
                                        size = size,
                                        style = Stroke(width = 4f)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                bitmap?.let {
                                    onConfirm(cropBitmap(it, cropStart, cropEnd, canvasSize))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = bitmap != null && hasSelection
                        ) {
                            Text("确认处理")
                        }
                    }
                }
            }
        }

        SideEffect { closeRequested }
    }

    if (showAssistantDialog) {
        AssistantPickerOverlay(
            assistants = uiState.settings.assistants,
            selectedAssistantId = uiState.settings.selectedAssistantId,
            closing = assistantPickerClosing,
            onDismiss = { assistantPickerClosing = true },
            onDismissFinished = {
                showAssistantDialog = false
                assistantPickerClosing = false
            },
            onSelect = { assistantId ->
                switchDirection = AssistantSwitchDirection.PICKER
                onSelectAssistant(assistantId)
                assistantPickerClosing = true
            }
        )
    }

    val providerPickerTitle = when (providerPickerTarget) {
        ModelPurpose.OCR -> "选择OCR提供方"
        ModelPurpose.TEXT -> "选择LLM提供方"
        ModelPurpose.VISION -> "选择多模态提供方"
        null -> null
    }
    providerPickerTitle?.let { title ->
        ProviderPickerOverlay(
            providers = uiState.settings.availableProviders(),
            closing = providerPickerClosing,
            title = title,
            onDismiss = { providerPickerClosing = true },
            onDismissFinished = {
                providerPickerTarget = null
                providerPickerClosing = false
            },
            onPick = { provider ->
                onUpdateModelSelection(
                    providerPickerTarget ?: return@ProviderPickerOverlay,
                    ModelSelection(providerId = provider.id, model = ""),
                    false
                )
                onModelPickerTargetChange(providerPickerTarget)
                providerPickerClosing = true
            }
        )
    }

    val pickerTarget = modelPickerTarget
    val pickerProvider = pickerTarget?.let { uiState.settings.resolveModelProvider(it) }
    if (pickerTarget != null && pickerProvider != null) {
        val title = when (pickerTarget) {
            ModelPurpose.TEXT -> "选择LLM模型"
            ModelPurpose.VISION -> "选择多模态模型"
            ModelPurpose.OCR -> "选择OCR模型"
        }
        ModelPickerOverlay(
            provider = pickerProvider,
            closing = modelPickerClosing,
            title = title,
            api = providerModelsApi,
            onDismiss = {
                modelPickerClosing = true
            },
            onDismissFinished = {
                onModelPickerTargetChange(null)
                modelPickerClosing = false
            },
            onPick = { model, isFavorite ->
                onUpdateModelSelection(
                    pickerTarget,
                    ModelSelection(providerId = pickerProvider.id, model = model),
                    isFavorite
                )
                modelPickerClosing = true
            },
            onToggleFavorite = { model, _ ->
                onToggleFavoriteModel(pickerProvider.id, model)
            },
            onCustomModelRequest = { dialogTitle ->
                customModelTarget = pickerTarget
                customModelDialogTitle = dialogTitle
                customModelDialogClosing = false
            }
        )
    }

    customModelDialogTitle?.let { title ->
        CustomModelOverlay(
            title = title,
            closing = customModelDialogClosing,
            initialValue = customModelTarget?.let(uiState.settings::resolveModelName).orEmpty(),
            onDismiss = {
                customModelDialogClosing = true
            },
            onDismissFinished = {
                customModelDialogTitle = null
                customModelTarget = null
                customModelDialogClosing = false
            },
            onConfirm = { model ->
                val purpose = customModelTarget
                val providerId = purpose?.let { uiState.settings.resolveModelProvider(it)?.id }
                if (purpose != null && providerId != null) {
                    onUpdateModelSelection(
                        purpose,
                        ModelSelection(providerId = providerId, model = model),
                        true
                    )
                    customModelDialogClosing = true
                }
            }
        )
    }
}
