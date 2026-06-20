package `fun`.kirari.hanako.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.data.ModelProviderConfig
import `fun`.kirari.hanako.data.displayName
import `fun`.kirari.hanako.data.parseImportedProviderConfig
import `fun`.kirari.hanako.data.requestPreviewUrl
import `fun`.kirari.llm.core.ProviderKind
import kotlinx.coroutines.delay

@Composable
fun ProviderEditor(
    provider: ModelProviderConfig,
    onChange: (ModelProviderConfig) -> Unit,
    onImportResult: (String) -> Unit = {},
    readOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "提供方配置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(
                enabled = !readOnly,
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val text = clipboard
                        ?.takeIf { it.hasPrimaryClip() }
                        ?.primaryClip
                        ?.takeIf { clip ->
                            val description = clipboard.primaryClipDescription
                            description == null ||
                                description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                                description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
                        }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    val imported = parseImportedProviderConfig(text)
                    if (imported != null) {
                        onChange(
                            provider.copy(
                                kind = imported.kind,
                                name = imported.name ?: provider.name,
                                baseUrl = imported.baseUrl,
                                apiKey = imported.apiKey
                            )
                        )
                        onImportResult("已粘贴导入配置")
                    } else {
                        onImportResult("未识别到支持的提供方配置")
                    }
                }
            ) {
                Text("粘贴")
            }
        }

        ProviderTypeSelector(
            kind = provider.kind,
            enabled = !readOnly,
            onChange = { nextKind ->
                onChange(provider.copy(kind = nextKind))
            }
        )

        EditableField(
            fieldKey = "${provider.id}:name",
            value = provider.name,
            onCommit = { onChange(provider.copy(name = it)) },
            label = "提供方名称",
            readOnly = readOnly
        )

        EditableField(
            fieldKey = "${provider.id}:baseUrl",
            value = provider.baseUrl,
            onCommit = { onChange(provider.copy(baseUrl = it)) },
            label = "Base URL",
            readOnly = readOnly
        )

        if (provider.baseUrl.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = provider.requestPreviewUrl(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        EditableField(
            fieldKey = "${provider.id}:apiKey",
            value = provider.apiKey,
            onCommit = { onChange(provider.copy(apiKey = it)) },
            label = "API Key",
            password = true,
            readOnly = readOnly
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProviderTypeSelector(
    kind: ProviderKind,
    enabled: Boolean,
    onChange: (ProviderKind) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = {
            if (enabled) {
                expanded = !expanded
            }
        }
    ) {
        OutlinedTextField(
            value = kind.displayName,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
            label = { Text("提供方类型") },
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            shape = RoundedCornerShape(16.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            ProviderKind.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.displayName) },
                    onClick = {
                        expanded = false
                        onChange(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun EditableField(
    fieldKey: String,
    value: String,
    onCommit: (String) -> Unit,
    label: String,
    password: Boolean = false,
    readOnly: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
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
        onValueChange = {
            if (!readOnly) {
                textFieldValue = it
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (!focusState.isFocused && textFieldValue.text != value) {
                    onCommit(textFieldValue.text)
                }
            },
        label = { Text(label) },
        readOnly = readOnly,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(16.dp),
        trailingIcon = if (!password) {
            null
        } else {
            {
                IconButton(onClick = { visible = !visible }, enabled = !readOnly) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "隐藏密码" else "显示密码"
                    )
                }
            }
        }
    )
}

@Composable
fun ModelButtonField(
    label: String,
    value: String,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onPick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value.ifBlank { "未选择" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
