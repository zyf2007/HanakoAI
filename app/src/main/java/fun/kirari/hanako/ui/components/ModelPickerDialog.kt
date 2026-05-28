package `fun`.kirari.hanako.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.network.ProviderModelsApi
import `fun`.kirari.hanako.network.RemoteModelOption
import kotlinx.coroutines.CancellationException

data class ModelPickerEntry(
    val id: String,
    val displayName: String = id,
    val isFavorite: Boolean = false,
    val isLocalOnly: Boolean = false
)

@Composable
fun rememberModelPickerState(
    provider: ModelProviderConfig,
    trustAllHttpsCertificates: Boolean = false,
    api: ProviderModelsApi = ProviderModelsApi()
): ModelPickerState {
    val sessionFavoriteOrder = remember(provider.id) { provider.favoriteModels.normalizedModelNames() }
    val localFavoriteModels = remember(provider.favoriteModels) {
        provider.favoriteModels.normalizedModelNames()
    }
    val locallyRemovedFavorites = remember(provider.id) { mutableStateListOf<String>() }
    var requestCancelled by remember(provider.id) { mutableStateOf(false) }

    val networkModels by produceState<List<RemoteModelOption>?>(initialValue = null, provider.id, requestCancelled, trustAllHttpsCertificates) {
        if (!requestCancelled) {
            try {
                value = api.listModels(provider, trustAllHttpsCertificates)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                value = emptyList()
            }
        } else {
            value = null
        }
    }

    val visibleFavoriteModels = remember(localFavoriteModels, locallyRemovedFavorites.size) {
        localFavoriteModels.filterNot { favorite ->
            locallyRemovedFavorites.any { it.equals(favorite, ignoreCase = true) }
        }
    }

    val entries = remember(sessionFavoriteOrder, visibleFavoriteModels, networkModels, requestCancelled) {
        mergeModelEntries(
            sessionFavoriteOrder = sessionFavoriteOrder,
            visibleFavoriteModels = visibleFavoriteModels,
            networkModels = networkModels
        )
    }

    val hasNetworkResponse = networkModels != null || requestCancelled

    return remember(
        provider.id,
        entries,
        hasNetworkResponse,
        requestCancelled,
        localFavoriteModels,
        locallyRemovedFavorites.size
    ) {
        ModelPickerState(
            entries = entries,
            hasNetworkResponse = hasNetworkResponse,
            cancelPendingRequest = { requestCancelled = true },
            markFavoriteRemovedForSession = { modelId ->
                if (localFavoriteModels.any { it.equals(modelId, ignoreCase = true) } &&
                    locallyRemovedFavorites.none { it.equals(modelId, ignoreCase = true) }
                ) {
                    locallyRemovedFavorites += modelId
                }
            },
            clearFavoriteRemovedForSession = { modelId ->
                locallyRemovedFavorites.removeAll { it.equals(modelId, ignoreCase = true) }
            }
        )
    }
}

class ModelPickerState(
    val entries: List<ModelPickerEntry>,
    val hasNetworkResponse: Boolean,
    private val cancelPendingRequest: () -> Unit,
    private val markFavoriteRemovedForSession: (String) -> Unit,
    private val clearFavoriteRemovedForSession: (String) -> Unit
) {
    fun cancelIfWaitingForNetwork() {
        if (!hasNetworkResponse) {
            cancelPendingRequest()
        }
    }

    fun removeFavoriteFromCurrentSession(modelId: String) {
        markFavoriteRemovedForSession(modelId)
    }

    fun restoreFavoriteInCurrentSession(modelId: String) {
        clearFavoriteRemovedForSession(modelId)
    }
}

@Composable
fun ModelPickerDialog(
    provider: ModelProviderConfig,
    title: String,
    onDismiss: () -> Unit,
    onPick: (String, Boolean) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onCustomModelRequest: (String) -> Unit,
    trustAllHttpsCertificates: Boolean = false,
    api: ProviderModelsApi = ProviderModelsApi()
) {
    val pickerState = rememberModelPickerState(
        provider = provider,
        trustAllHttpsCertificates = trustAllHttpsCertificates,
        api = api
    )
    var query by remember { mutableStateOf("") }
    val filteredModels = remember(pickerState.entries, query) {
        filterModelEntries(pickerState.entries, query)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = title)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索模型") },
                    placeholder = { Text("按名称或 ID 搜索") },
                    singleLine = true
                )

                ModelPickerListContent(
                    models = filteredModels,
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
                                } else {
                                    pickerState.restoreFavoriteInCurrentSession(item.id)
                                }
                                onToggleFavorite(item.id, item.isFavorite)
                            }
                        )
                    }
                )

                OutlinedButton(
                    onClick = { onCustomModelRequest(title) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("使用自定义模型")
                }

                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
fun ModelPickerListContent(
    models: List<ModelPickerEntry>,
    hasNetworkResponse: Boolean,
    onPick: (String, Boolean) -> Unit,
    buttonStyle: @Composable (ModelPickerEntry, () -> Unit, @Composable () -> Unit) -> Unit
) {
    if (models.isEmpty() && !hasNetworkResponse) {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            CircularProgressIndicator()
        }
        return
    }

    if (models.isEmpty()) {
        Text("没有匹配的模型")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(models, key = { it.id }) { model ->
            buttonStyle(model, { onPick(model.id, model.isFavorite) }) {
                ModelPickerEntryContent(model = model)
            }
        }
        if (!hasNetworkResponse) {
            item("loading-indicator") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun ModelPickerSurfaceItem(
    model: ModelPickerEntry,
    onPick: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        modifier = Modifier.combinedClickable(
            onClick = onPick,
            onLongClick = onLongPress
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        ModelPickerEntryContent(
            model = model,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ModelPickerEntryContent(
    model: ModelPickerEntry,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = model.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (model.displayName != model.id) {
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (model.isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "已收藏",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun filterModelEntries(models: List<ModelPickerEntry>, query: String): List<ModelPickerEntry> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        return models
    }
    return models.filter { model ->
        model.displayName.contains(normalizedQuery, ignoreCase = true) ||
            model.id.contains(normalizedQuery, ignoreCase = true)
    }
}

private fun mergeModelEntries(
    sessionFavoriteOrder: List<String>,
    visibleFavoriteModels: List<String>,
    networkModels: List<RemoteModelOption>?
): List<ModelPickerEntry> {
    val visibleFavoriteSet = visibleFavoriteModels.map { it.lowercase() }.toSet()
    val orderedFavorites = sessionFavoriteOrder.filter { favorite ->
        visibleFavoriteSet.contains(favorite.lowercase())
    }
    val result = mutableListOf<ModelPickerEntry>()
    val seen = linkedSetOf<String>()

    orderedFavorites.forEach { favorite ->
        val key = favorite.lowercase()
        if (seen.add(key)) {
            result += ModelPickerEntry(
                id = favorite,
                displayName = favorite,
                isFavorite = true,
                isLocalOnly = true
            )
        }
    }

    networkModels.orEmpty().forEach { model ->
        val key = model.id.lowercase()
        if (seen.add(key)) {
            result += ModelPickerEntry(
                id = model.id,
                displayName = model.displayName,
                isFavorite = visibleFavoriteSet.contains(key),
                isLocalOnly = false
            )
        } else if (visibleFavoriteSet.contains(key)) {
            val index = result.indexOfFirst { it.id.equals(model.id, ignoreCase = true) }
            if (index >= 0) {
                result[index] = result[index].copy(
                    displayName = model.displayName,
                    isFavorite = true
                )
            }
        }
    }

    return result
}

private fun List<String>.normalizedModelNames(): List<String> {
    return map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
}
