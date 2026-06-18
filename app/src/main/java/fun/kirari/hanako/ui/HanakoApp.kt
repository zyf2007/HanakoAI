package `fun`.kirari.hanako.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.widget.Toast
import `fun`.kirari.hanako.BuildConfig
import `fun`.kirari.hanako.capture.ScreenCaptureManager
import `fun`.kirari.hanako.capture.ScreenCaptureStartResult
import `fun`.kirari.hanako.copyToClipboardWithToast
import `fun`.kirari.hanako.data.LOCAL_OCR_MODEL_ID
import `fun`.kirari.hanako.data.LOCAL_OCR_PROVIDER_ID
import `fun`.kirari.hanako.data.ModelPurpose
import `fun`.kirari.hanako.data.ModelSelection
import `fun`.kirari.hanako.data.ProcessingRoute
import `fun`.kirari.hanako.data.displayName
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.formatDebugTime
import `fun`.kirari.hanako.overlay.OverlayLaunchMode
import `fun`.kirari.hanako.overlay.OverlayRuntimeState
import `fun`.kirari.hanako.overlay.OverlayService
import `fun`.kirari.hanako.ui.components.CustomModelDialog
import `fun`.kirari.hanako.ui.components.HeroSection
import `fun`.kirari.hanako.ui.components.ModelPickerDialog

enum class Screen(val title: String, val icon: ImageVector) {
    Hanako("Hanako", Icons.Default.Home),
    Settings("设置", Icons.Default.Settings)
}

private const val ROUTE_HOME_SHELL = "home_shell"
private const val ROUTE_HANAKO_HOME = "hanako_home"
private const val ROUTE_HANAKO_HISTORY = "hanako_history"
private const val ROUTE_HANAKO_HISTORY_DETAIL = "hanako_history_detail"
private const val ROUTE_SETTINGS_PROVIDER = "settings_provider"
private const val ROUTE_SETTINGS_PROVIDER_DETAIL = "settings_provider_detail"
private const val ROUTE_SETTINGS_MODEL = "settings_model"
private const val ROUTE_SETTINGS_ASSISTANT = "settings_assistant"
private const val ROUTE_SETTINGS_ASSISTANT_DETAIL = "settings_assistant_detail"
private const val ROUTE_SETTINGS_MORE = "settings_more"
private const val ROUTE_SETTINGS_STATIC_VIBRATION = "settings_static_vibration"
private const val ROUTE_SETTINGS_DEBUG_LOGS = "settings_debug_logs"
private const val ARG_PROVIDER_ID = "providerId"
private const val ARG_ASSISTANT_ID = "assistantId"
private const val ARG_HISTORY_ID = "historyId"

private fun providerDetailRoute(providerId: String): String = "$ROUTE_SETTINGS_PROVIDER_DETAIL/$providerId"
private fun assistantDetailRoute(assistantId: String): String = "$ROUTE_SETTINGS_ASSISTANT_DETAIL/$assistantId"
private fun historyDetailRoute(historyId: String): String = "$ROUTE_HANAKO_HISTORY_DETAIL/$historyId"

private fun appTitle(route: String?, currentScreen: Screen): String = when (route) {
    ROUTE_HOME_SHELL -> currentScreen.title
    ROUTE_HANAKO_HOME -> Screen.Hanako.title
    ROUTE_HANAKO_HISTORY -> "历史记录"
    ROUTE_SETTINGS_PROVIDER -> "模型提供方"
    ROUTE_SETTINGS_MODEL -> "模型设置"
    ROUTE_SETTINGS_ASSISTANT -> "助手配置"
    ROUTE_SETTINGS_MORE -> "更多"
    ROUTE_SETTINGS_STATIC_VIBRATION -> "静态模式振动"
    ROUTE_SETTINGS_DEBUG_LOGS -> "调试日志"
    null -> currentScreen.title
    else -> when {
        route.startsWith("$ROUTE_HANAKO_HISTORY_DETAIL/") -> "历史详情"
        route.startsWith("$ROUTE_SETTINGS_PROVIDER_DETAIL/") -> "编辑提供方"
        route.startsWith("$ROUTE_SETTINGS_ASSISTANT_DETAIL/") -> "编辑助手"
        else -> currentScreen.title
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HanakoApp(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val debugEntries by AppDebugLogStore.entries.collectAsState()
    val context = LocalContext.current
    val overlayEnabled by OverlayRuntimeState.running.collectAsState()
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var providerModelsPreviewId by remember { mutableStateOf<String?>(null) }
    var modelPickerTarget by remember { mutableStateOf<ModelPurpose?>(null) }
    var customModelTarget by remember { mutableStateOf<ModelPurpose?>(null) }
    var customModelDialogTitle by remember { mutableStateOf<String?>(null) }
    var providerPickerTarget by remember { mutableStateOf<ModelPurpose?>(null) }
    var modelPickerProviderId by remember { mutableStateOf<String?>(null) }
    var showLocalOcrDialog by remember { mutableStateOf(false) }
    var showLiteDownloadDialog by remember { mutableStateOf(false) }

    var currentScreen by rememberSaveable { mutableStateOf(Screen.Hanako) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = currentRoute == ROUTE_HOME_SHELL && currentScreen == Screen.Settings) {
        currentScreen = Screen.Hanako
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            appTitle(currentRoute, currentScreen),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        if (currentRoute != null && currentRoute != ROUTE_HOME_SHELL) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = currentRoute == ROUTE_HOME_SHELL,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    NavigationBar {
                        Screen.entries.forEach { screen ->
                            NavigationBarItem(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) }
                            )
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME_SHELL,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(padding),
                enterTransition = {
                    slideInHorizontally { it } + fadeIn()
                },
                exitTransition = {
                    slideOutHorizontally { -it / 2 } + fadeOut()
                },
                popEnterTransition = {
                    slideInHorizontally { -it / 2 } + fadeIn()
                },
                popExitTransition = {
                    slideOutHorizontally { it }
                }
            ) {
                composable(ROUTE_HOME_SHELL) {
                    MainShellScreen(
                        currentScreen = currentScreen,
                        onScreenChange = { currentScreen = it },
                        hanakoContent = {
                            HanakoHomeScreen(
                                settings = settings,
                                overlayEnabled = overlayEnabled,
                                hasOverlayPermission = hasOverlayPermission,
                                onOpenOverlayPermission = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                },
                                onToggleOverlay = { enabled ->
                                    if (enabled) {
                                        when (
                                            val result = ScreenCaptureManager.requestStart(
                                                context = context,
                                                method = settings.screenCaptureMethod,
                                                launchMode = OverlayLaunchMode.NORMAL
                                            )
                                        ) {
                                            ScreenCaptureStartResult.Started -> Unit
                                            is ScreenCaptureStartResult.UserActionRequired -> {
                                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                            }
                                            is ScreenCaptureStartResult.Failed -> {
                                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        context.stopService(Intent(context, OverlayService::class.java))
                                        ScreenCaptureManager.stop(context, settings.screenCaptureMethod)
                                    }
                                },
                                onSelectRoute = viewModel::setRoute,
                                onOpenHistory = { navController.navigate(ROUTE_HANAKO_HISTORY) }
                            )
                        },
                        settingsContent = {
                            SettingsMenuScreen(
                                onNavigateProvider = { navController.navigate(ROUTE_SETTINGS_PROVIDER) },
                                onNavigateModel = { navController.navigate(ROUTE_SETTINGS_MODEL) },
                                onNavigateAssistant = { navController.navigate(ROUTE_SETTINGS_ASSISTANT) },
                                onNavigateMore = { navController.navigate(ROUTE_SETTINGS_MORE) },
                                onNavigateDebugLogs = { navController.navigate(ROUTE_SETTINGS_DEBUG_LOGS) }
                            )
                        }
                    )
                }
                composable(ROUTE_HANAKO_HISTORY) {
                    HistorySubScreen(
                        settings = settings,
                        onClearHistory = viewModel::clearHistory,
                        onDeleteHistoryItem = viewModel::deleteHistoryItem,
                        onOpenHistoryDetail = { resultId ->
                            navController.navigate(historyDetailRoute(resultId))
                        }
                    )
                }
                composable("$ROUTE_HANAKO_HISTORY_DETAIL/{$ARG_HISTORY_ID}") { entry ->
                    val resultId = entry.arguments?.getString(ARG_HISTORY_ID)
                    val result = settings.history.firstOrNull { it.id == resultId }
                    HistoryDetailScreen(result = result)
                }
                composable(ROUTE_SETTINGS_PROVIDER) {
                    ProviderSettingsScreen(
                        settings = settings,
                        onAddProvider = viewModel::addProvider,
                        onDeleteProvider = viewModel::deleteProvider,
                        onOpenProvider = { providerId ->
                            viewModel.selectProvider(providerId)
                            navController.navigate(providerDetailRoute(providerId))
                        }
                    )
                }
                composable("$ROUTE_SETTINGS_PROVIDER_DETAIL/{$ARG_PROVIDER_ID}") { entry ->
                    val providerId = entry.arguments?.getString(ARG_PROVIDER_ID)
                    val provider = settings.providers.firstOrNull { it.id == providerId }
                    if (provider != null) {
                        val connectionTestState by viewModel.connectionTestState.collectAsState()
                        ProviderDetailScreen(
                            provider = provider,
                            connectionTestState = connectionTestState,
                            onUpdateProvider = viewModel::updateProvider,
                            onViewModels = { providerModelsPreviewId = provider.id },
                            onTestConnection = viewModel::testProviderConnection,
                            onClearConnectionTest = viewModel::resetConnectionTest
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }
                composable(ROUTE_SETTINGS_MODEL) {
                    ModelSettingsScreen(
                        settings = settings,
                        onPickModel = { providerPickerTarget = it }
                    )
                }
                composable(ROUTE_SETTINGS_ASSISTANT) {
                    AssistantSettingsScreen(
                        settings = settings,
                        onAddAssistant = viewModel::addAssistant,
                        onDeleteAssistant = viewModel::deleteAssistant,
                        onOpenAssistant = { assistantId ->
                            viewModel.selectAssistant(assistantId)
                            navController.navigate(assistantDetailRoute(assistantId))
                        }
                    )
                }
                composable("$ROUTE_SETTINGS_ASSISTANT_DETAIL/{$ARG_ASSISTANT_ID}") { entry ->
                    val assistantId = entry.arguments?.getString(ARG_ASSISTANT_ID)
                    val assistant = settings.assistants.firstOrNull { it.id == assistantId }
                    if (assistant != null) {
                        AssistantDetailScreen(
                            assistant = assistant,
                            onUpdateAssistant = viewModel::updateAssistant
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }
                composable(ROUTE_SETTINGS_MORE) {
                    MoreSettingsScreen(
                        automationSettings = settings.automation,
                        selectedMethod = settings.screenCaptureMethod,
                        trustAllHttpsCertificates = settings.trustAllHttpsCertificates,
                        onToggleCompletionNotification = { enabled ->
                            viewModel.updateAutomationSettings {
                                it.copy(completionNotificationEnabled = enabled)
                            }
                        },
                        onToggleStaticMode = { enabled ->
                            viewModel.updateAutomationSettings {
                                it.copy(staticModeEnabled = enabled)
                            }
                        },
                        onNavigateStaticVibrationSettings = { navController.navigate(ROUTE_SETTINGS_STATIC_VIBRATION) },
                        onSelectMethod = viewModel::setScreenCaptureMethod,
                        onUpdateTimeoutSeconds = { seconds ->
                            viewModel.updateAutomationSettings {
                                it.copy(autoModeTimeoutSeconds = seconds)
                            }
                        },
                        onToggleTrustAllHttpsCertificates = viewModel::setTrustAllHttpsCertificates
                    )
                }
                composable(ROUTE_SETTINGS_STATIC_VIBRATION) {
                    StaticVibrationSettingsScreen(
                        automationSettings = settings.automation,
                        onUpdateSettings = { transform ->
                            viewModel.updateAutomationSettings(transform)
                        }
                    )
                }
                composable(ROUTE_SETTINGS_DEBUG_LOGS) {
                    DebugLogScreen(
                        onClearLogs = viewModel::clearDebugLogs
                    )
                }
            }
        }
    }

    val pickerProvider = settings.providers.firstOrNull { it.id == modelPickerProviderId }
    if (providerPickerTarget != null) {
        ProviderSelectDialog(
            providers = settings.providers,
            includeLocalOcr = providerPickerTarget == ModelPurpose.OCR,
            localOcrInstalled = settings.localOcr.installed,
            title = "选择${providerPickerTarget?.displayName}提供方",
            onDismiss = { providerPickerTarget = null },
            onPick = { providerId ->
                if (providerId == LOCAL_OCR_PROVIDER_ID) {
                    providerPickerTarget = null
                    if (!BuildConfig.HAS_MLKIT) {
                        showLiteDownloadDialog = true
                    } else if (settings.localOcr.installed) {
                        viewModel.updateModelSelection(
                            ModelPurpose.OCR,
                            ModelSelection(providerId = LOCAL_OCR_PROVIDER_ID, model = LOCAL_OCR_MODEL_ID)
                        )
                    } else {
                        showLocalOcrDialog = true
                    }
                } else {
                    modelPickerProviderId = providerId
                    modelPickerTarget = providerPickerTarget
                    providerPickerTarget = null
                }
            }
        )
    }

    val pickerTarget = modelPickerTarget
    if (pickerProvider != null && pickerTarget != null) {
        val title = when (pickerTarget) {
            ModelPurpose.TEXT -> "选择文本模型"
            ModelPurpose.VISION -> "选择多模态模型"
            ModelPurpose.OCR -> "选择 OCR 模型"
        }
        ModelPickerDialog(
            provider = pickerProvider,
            title = title,
            onDismiss = {
                modelPickerTarget = null
                modelPickerProviderId = null
            },
            onPick = { model, isFavorite ->
                viewModel.updateModelSelectionWithFavorite(
                    pickerTarget,
                    ModelSelection(providerId = pickerProvider.id, model = model),
                    favoriteModel = isFavorite
                )
                modelPickerTarget = null
                modelPickerProviderId = null
            },
            onToggleFavorite = { model, _ ->
                viewModel.toggleFavoriteModel(pickerProvider.id, model)
            },
            onCustomModelRequest = { dialogTitle ->
                customModelTarget = pickerTarget
                customModelDialogTitle = dialogTitle
            },
            trustAllHttpsCertificates = settings.trustAllHttpsCertificates
        )
    }

    customModelDialogTitle?.let { title ->
        CustomModelDialog(
            title = title,
            onDismiss = {
                customModelDialogTitle = null
                customModelTarget = null
                modelPickerProviderId = null
            },
            onConfirm = { model ->
                val purpose = customModelTarget ?: ModelPurpose.TEXT
                val providerId = modelPickerProviderId ?: return@CustomModelDialog
                viewModel.updateModelSelectionWithFavorite(
                    purpose,
                    ModelSelection(providerId = providerId, model = model),
                    favoriteModel = true
                )
                customModelDialogTitle = null
                customModelTarget = null
                modelPickerTarget = null
                modelPickerProviderId = null
            }
        )
    }

    val previewProvider = settings.providers.firstOrNull { it.id == providerModelsPreviewId }
    if (previewProvider != null) {
        ModelPickerDialog(
            provider = previewProvider,
            title = "查看可用模型",
            onDismiss = { providerModelsPreviewId = null },
            onPick = { _, _ -> providerModelsPreviewId = null },
            onToggleFavorite = { model, _ ->
                viewModel.toggleFavoriteModel(previewProvider.id, model)
            },
            onCustomModelRequest = { },
            trustAllHttpsCertificates = settings.trustAllHttpsCertificates
        )
    }

    if (showLocalOcrDialog) {
        val localOcrLogText = remember(debugEntries) {
            debugEntries
                .filter { it.tag == "LocalOcr" || it.tag == "LocalOcrUi" }
                .takeLast(12)
                .joinToString("\n\n") { entry ->
                    "${formatDebugTime(entry.timestamp)} ${entry.level}/${entry.tag}\n${entry.message}"
                }
        }
        AlertDialog(
            onDismissRequest = { showLocalOcrDialog = false },
            title = { Text("本地ML Kit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        buildString {
                            append("不支持latex公式等识别，正式搜题建议使用qwen-vl-ocr等在线ocr模型。")
                            settings.localOcr.lastMessage?.takeIf { it.isNotBlank() }?.let {
                                append("\n\n")
                                append(it)
                            }
                            append("\n\n点击“立即使用”后会直接切换到本地 OCR。")
                        }
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            copyToClipboardWithToast(
                                context = context,
                                label = "Hanako Local OCR Logs",
                                text = if (localOcrLogText.isNotBlank()) localOcrLogText else "暂无 Local OCR 日志",
                                toastText = "本地 OCR 日志已复制"
                            )
                        }
                    ) {
                        Text("一键复制日志")
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            text = if (localOcrLogText.isBlank()) "暂无 Local OCR 日志" else localOcrLogText,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.widthIn(min = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            if (settings.localOcr.installed) {
                                viewModel.updateModelSelection(
                                    ModelPurpose.OCR,
                                    ModelSelection(providerId = LOCAL_OCR_PROVIDER_ID, model = LOCAL_OCR_MODEL_ID)
                                )
                                showLocalOcrDialog = false
                            }
                        }
                    ) {
                        Text("立即使用")
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = viewModel::syncLocalOcrInstallation
                    ) {
                        Text("刷新状态")
                    }
                    TextButton(onClick = { showLocalOcrDialog = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }

    if (showLiteDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showLiteDownloadDialog = false },
            title = { Text("本地ML Kit不可用") },
            text = {
                Text("当前 Lite 版本不包含本地 ML Kit OCR 功能，建议使用 OCR 大模型 API。如需使用此功能，请下载包含 ML Kit 的 Full 版本。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLiteDownloadDialog = false
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/zyf2007/HanakoAI")
                            )
                        )
                    }
                ) {
                    Text("前往下载")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLiteDownloadDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun MainShellScreen(
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit,
    hanakoContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = currentScreen.ordinal,
        pageCount = { Screen.entries.size }
    )

    var isAnimating by remember { mutableStateOf(false) }

    // 用户点击tab时触发动画
    LaunchedEffect(currentScreen) {
        val target = currentScreen.ordinal
        if (target != pagerState.currentPage) {
            isAnimating = true
            try {
                pagerState.animateScrollToPage(target)
            } finally {
                isAnimating = false
            }
        }
    }

    // 手势滑动完成时更新状态，但不触发动画
    LaunchedEffect(pagerState.settledPage) {
        if (isAnimating) return@LaunchedEffect
        val settledScreen = Screen.entries[pagerState.settledPage]
        if (currentScreen != settledScreen) {
            onScreenChange(settledScreen)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = true
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (Screen.entries[page]) {
                Screen.Hanako -> hanakoContent()
                Screen.Settings -> settingsContent()
            }
        }
    }
}

@Composable
private fun HanakoHomeScreen(
    settings: `fun`.kirari.hanako.data.AppSettings,
    overlayEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onOpenOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onSelectRoute: (`fun`.kirari.hanako.data.ProcessingRoute) -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroSection(
                overlayEnabled = overlayEnabled,
                hasOverlayPermission = hasOverlayPermission,
                captureMethod = settings.screenCaptureMethod,
                staticModeEnabled = settings.automation.staticModeEnabled,
                route = settings.processingRoute,
                onSelectRoute = onSelectRoute,
                onOpenOverlayPermission = onOpenOverlayPermission,
                onToggleOverlay = onToggleOverlay,
                onStartAutoMode = {
                    if (!hasOverlayPermission) return@HeroSection
                    when (
                        val result = ScreenCaptureManager.requestStart(
                            context = context,
                            method = settings.screenCaptureMethod,
                            launchMode = OverlayLaunchMode.AUTO
                        )
                    ) {
                        ScreenCaptureStartResult.Started -> Unit
                        is ScreenCaptureStartResult.UserActionRequired -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                        is ScreenCaptureStartResult.Failed -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenHistory),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "历史记录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "查看悬浮窗处理过的历史记录",
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
}

@Composable
private fun ProviderSelectDialog(
    providers: List<`fun`.kirari.hanako.data.ModelProviderConfig>,
    includeLocalOcr: Boolean,
    localOcrInstalled: Boolean,
    title: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (includeLocalOcr) {
                        item(LOCAL_OCR_PROVIDER_ID) {
                            OutlinedButton(
                                onClick = { onPick(LOCAL_OCR_PROVIDER_ID) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("本地ML Kit")
                                    Text(
                                        if (localOcrInstalled) {
                                            "不支持latex公式等识别且准确率较低，正式搜题建议使用在线 OCR 例如 qwen-vl-ocr"
                                        } else {
                                            "点击查看本地 ML Kit 说明"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    items(providers, key = { it.id }) { provider ->
                        OutlinedButton(
                            onClick = { onPick(provider.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(provider.name)
                                Text(
                                    provider.kind.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("取消")
                }
            }
        }
    }
}
