package `fun`.kirari.hanako.overlay

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import `fun`.kirari.hanako.data.ModelPurpose

internal enum class AssistantSwitchDirection {
    PREVIOUS,
    NEXT,
    PICKER
}

@Composable
internal fun OverlayPanel(
    viewModel: OverlayViewModel,
    onDismiss: () -> Unit,
    panelHeightPx: Int = 0
) {
    val uiState by viewModel.uiState.collectAsState()
    var modelPickerTarget by remember { mutableStateOf<ModelPurpose?>(null) }
    when (uiState.sheetMode) {
        OverlaySheetMode.CROP -> {
            CropOverlaySheet(
                uiState = uiState,
                onClose = onDismiss,
                onConfirm = viewModel::process,
                panelHeightPx = panelHeightPx,
                modelPickerTarget = modelPickerTarget,
                onModelPickerTargetChange = { modelPickerTarget = it },
                onSelectAssistant = viewModel::selectAssistant,
                onSelectPreviousAssistant = viewModel::selectPreviousAssistant,
                onSelectNextAssistant = viewModel::selectNextAssistant,
                onUpdateModelSelection = viewModel::updateModelSelectionWithFavorite,
                onToggleFavoriteModel = viewModel::toggleFavoriteModel,
                onToggleProcessingRoute = viewModel::toggleProcessingRoute,
                providerModelsApi = viewModel.providerModelsApi
            )
        }

        OverlaySheetMode.RESULT -> {
            ResultOverlaySheet(
                uiState = uiState,
                onClose = onDismiss,
                panelHeightPx = panelHeightPx
            )
        }
    }
}

internal fun assistantNameTransform(
    direction: AssistantSwitchDirection
): ContentTransform {
    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(
        durationMillis = 240,
        easing = FastOutSlowInEasing
    )
    val fadeSpec = tween<Float>(
        durationMillis = 180,
        easing = FastOutSlowInEasing
    )
    return when (direction) {
        AssistantSwitchDirection.PREVIOUS ->
            (slideInHorizontally(animationSpec = slideSpec) { -it / 2 } + fadeIn(fadeSpec))
                .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { it / 2 } + fadeOut(fadeSpec))

        AssistantSwitchDirection.NEXT ->
            (slideInHorizontally(animationSpec = slideSpec) { it / 2 } + fadeIn(fadeSpec))
                .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { -it / 2 } + fadeOut(fadeSpec))

        AssistantSwitchDirection.PICKER ->
            fadeIn(fadeSpec).togetherWith(fadeOut(fadeSpec))
    }
}
