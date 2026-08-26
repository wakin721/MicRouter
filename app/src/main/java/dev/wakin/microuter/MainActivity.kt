package dev.wakin.microuter

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MicrophoneInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MainPage { Microphone, About }
private enum class AppearanceMode { Dynamic, Light, Dark }

class MainActivity : ComponentActivity() {
    private var xposedService by mutableStateOf<XposedService?>(null)
    private val serviceListener: (XposedService?) -> Unit = { xposedService = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MicRouterUi(xposedService) }
    }

    override fun onStart() {
        super.onStart()
        MicRouterApp.addServiceListener(serviceListener)
    }

    override fun onStop() {
        MicRouterApp.removeServiceListener(serviceListener)
        super.onStop()
    }
}

@Composable
private fun MicRouterUi(service: XposedService?) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE) }
    var language by remember { mutableStateOf(uiPrefs.getString("language", "zh") ?: "zh") }
    var dynamicColor by remember { mutableStateOf(uiPrefs.getBoolean("dynamic_color", true)) }
    var appearanceMode by remember {
        mutableStateOf(
            AppearanceMode.entries.firstOrNull {
                it.name.equals(uiPrefs.getString("appearance_mode", "dynamic"), ignoreCase = true)
            } ?: AppearanceMode.Dynamic,
        )
    }
    var themeColor by remember {
        mutableStateOf(ThemePalette.fromStoredValue(uiPrefs.getString("theme_color", "blue")))
    }
    var page by remember { mutableStateOf(MainPage.Microphone) }

    val systemDark = isSystemInDarkTheme()
    val dark = when (appearanceMode) {
        AppearanceMode.Dynamic -> systemDark
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }
    val colors = if (ThemeSettingsPolicy.dynamicColorActive(dynamicColor, Build.VERSION.SDK_INT)) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        customColorScheme(themeColor, dark)
    }
    fun tr(zh: String, en: String) = if (language == "zh") zh else en

    MaterialTheme(colorScheme = colors) {
        val navigationItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 44.dp, bottom = 18.dp),
                ) {
                    Text("MicRouter", style = MaterialTheme.typography.displaySmall)
                    Text(
                        when (page) {
                            MainPage.Microphone -> tr("麦克风选择", "Microphone selection")
                            MainPage.About -> tr("关于", "About")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 6.dp,
                ) {
                    NavigationBarItem(
                        selected = page == MainPage.Microphone,
                        onClick = { page = MainPage.Microphone },
                        icon = {
                            Text(
                                "◉",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                            )
                        },
                        label = { Text(tr("麦克风", "Microphone")) },
                        colors = navigationItemColors,
                    )
                    NavigationBarItem(
                        selected = page == MainPage.About,
                        onClick = { page = MainPage.About },
                        icon = {
                            Text(
                                "ⓘ",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                            )
                        },
                        label = { Text(tr("关于", "About")) },
                        colors = navigationItemColors,
                    )
                }
            },
        ) { padding ->
            when (page) {
                MainPage.Microphone -> MicrophonePage(
                    modifier = Modifier.padding(padding),
                    context = context,
                    service = service,
                    language = language,
                )
                MainPage.About -> AboutPage(
                    modifier = Modifier.padding(padding),
                    service = service,
                    language = language,
                    dynamicColor = dynamicColor,
                    appearanceMode = appearanceMode,
                    themeColor = themeColor,
                    onLanguageChange = {
                        language = it
                        uiPrefs.edit().putString("language", it).apply()
                    },
                    onDynamicColorChange = {
                        dynamicColor = it
                        uiPrefs.edit().putBoolean("dynamic_color", it).apply()
                    },
                    onAppearanceModeChange = {
                        appearanceMode = it
                        uiPrefs.edit().putString("appearance_mode", it.name.lowercase()).apply()
                    },
                    onThemeColorChange = {
                        themeColor = it
                        uiPrefs.edit().putString("theme_color", it.storageKey).apply()
                    },
                )
            }
        }
    }
}

@Composable
private fun MicrophonePage(
    modifier: Modifier,
    context: Context,
    service: XposedService?,
    language: String,
) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    var deviceRevision by remember { mutableIntStateOf(0) }
    var routeRevision by remember(service) { mutableIntStateOf(0) }
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                deviceRevision++
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                deviceRevision++
            }
        }
        audioManager?.registerAudioDeviceCallback(callback, mainHandler)
        onDispose { audioManager?.unregisterAudioDeviceCallback(callback) }
    }

    val inputs = remember(language, deviceRevision) { currentInputs(context, language) }
    val preferences = remember(service) { service?.getRemotePreferences(RouteStore.PREFS) }
    val route = remember(preferences, routeRevision) {
        preferences?.let(RouteStore::readSystemRoute) ?: SystemRoute.systemDefault()
    }
    val resolved = InputDeviceResolver.resolve(route, inputs)
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val canSelect = service != null && supported

    fun saveRoute(newRoute: SystemRoute) {
        val prefs = preferences ?: return
        RouteStore.writeSystemRoute(prefs, newRoute)
        routeRevision++
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MicrophoneChoiceCard(
                name = tr("系统默认", "System default"),
                details = tr("清除 MicRouter 的全局设备偏好", "Clear MicRouter's global device preference"),
                selected = !route.enabled,
                enabled = canSelect,
                onClick = { saveRoute(SystemRoute.systemDefault()) },
            )
        }
        items(
            items = inputs,
            key = { "${it.type}:${it.address}:${it.microphoneGroup}:${it.microphoneIndex}:${it.id}" },
        ) { input ->
            MicrophoneChoiceCard(
                name = input.name,
                details = deviceDetails(input, language),
                selected = route.enabled && resolved == input,
                enabled = canSelect,
                onClick = { saveRoute(SystemRoute.fromDevice(input)) },
            )
        }
        if (route.enabled && resolved == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        tr(
                            "已保存的麦克风当前不可用；连接恢复前会暂时使用系统默认。",
                            "The saved microphone is unavailable; system default is used until it returns.",
                        ),
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MicrophoneChoiceCard(
    name: String,
    details: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        ListItem(
            headlineContent = { Text(name) },
            supportingContent = { Text(details) },
            trailingContent = {
                RadioButton(selected = selected, onClick = null, enabled = enabled)
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun AboutPage(
    modifier: Modifier,
    service: XposedService?,
    language: String,
    dynamicColor: Boolean,
    appearanceMode: AppearanceMode,
    themeColor: ThemePalette,
    onLanguageChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onThemeColorChange: (ThemePalette) -> Unit,
) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    var languageMenu by remember { mutableStateOf(false) }
    val dynamicActive = ThemeSettingsPolicy.dynamicColorActive(dynamicColor, Build.VERSION.SDK_INT)
    val paletteSelectionEnabled = ThemeSettingsPolicy.paletteSelectionEnabled(dynamicColor, Build.VERSION.SDK_INT)

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            FrameworkStatusCard(service = service, language = language)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("语言", "Language"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (language == "zh") "简体中文" else "English",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        FilledTonalButton(onClick = { languageMenu = true }) {
                            Text(if (language == "zh") "中文" else "English")
                        }
                        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                            DropdownMenuItem(text = { Text("简体中文") }, onClick = { onLanguageChange("zh"); languageMenu = false })
                            DropdownMenuItem(text = { Text("English") }, onClick = { onLanguageChange("en"); languageMenu = false })
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    tr("主题", "Theme"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(AppearanceMode.Light, AppearanceMode.Dark, AppearanceMode.Dynamic).forEach { mode ->
                        AppearancePreviewTile(
                            modifier = Modifier.weight(1f),
                            mode = mode,
                            selected = appearanceMode == mode,
                            language = language,
                            onClick = { onAppearanceModeChange(mode) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("动态色彩", "Dynamic color"), style = MaterialTheme.typography.titleLarge)
                        Text(
                            tr("使用系统强调色", "Use the system accent colors"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = dynamicActive,
                        onCheckedChange = onDynamicColorChange,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        tr("调色板", "Palette"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        tr("当前选择：${themeColorName(themeColor, language)}", "Selected: ${themeColorName(themeColor, language)}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        Text(
                            tr("动态色彩需要 Android 12 或更高版本", "Dynamic color requires Android 12 or newer"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (dynamicActive) {
                        Text(
                            tr("动态色彩开启时不可选择手动色盘", "Manual palettes are unavailable while dynamic color is enabled"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ThemePaletteGrid(
                        selected = themeColor,
                        enabled = paletteSelectionEnabled,
                        language = language,
                        onSelect = onThemeColorChange,
                    )
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(tr("启用说明", "Activation"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(tr("模块使用固定的 system 作用域。安装或更新后，请在 LSPosed 中启用模块并重启设备。", "The module uses the fixed system scope. After installing or updating, enable it in LSPosed and reboot the device."))
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Android 11+ 支持全局捕获预设；少数厂商录音链路可能忽略标准音频策略。", "Android 11+ supports global capture presets. Some vendor capture paths may ignore the standard audio policy."), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private data class FrameworkInfo(
    val name: String,
    val version: String,
    val apiVersion: Int,
)

private sealed interface FrameworkInfoState {
    data object Disconnected : FrameworkInfoState
    data object Loading : FrameworkInfoState
    data object Unavailable : FrameworkInfoState
    data class Available(val info: FrameworkInfo) : FrameworkInfoState
}

@Composable
private fun FrameworkStatusCard(service: XposedService?, language: String) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    val connected = service != null
    val infoState by produceState<FrameworkInfoState>(
        initialValue = if (service == null) FrameworkInfoState.Disconnected else FrameworkInfoState.Loading,
        key1 = service,
    ) {
        value = if (service == null) {
            FrameworkInfoState.Disconnected
        } else {
            value = FrameworkInfoState.Loading
            withContext(Dispatchers.IO) {
                runCatching<FrameworkInfoState> {
                    FrameworkInfoState.Available(
                        FrameworkInfo(
                            name = service.frameworkName,
                            version = service.frameworkVersion,
                            apiVersion = service.apiVersion,
                        ),
                    )
                }.getOrElse { FrameworkInfoState.Unavailable }
            }
        }
    }
    val container = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val iconContainer = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val iconContent = if (connected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(66.dp),
                shape = CircleShape,
                color = iconContainer,
                contentColor = iconContent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (connected) "✓" else "×",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (connected) tr("模块服务已连接", "Module service connected") else tr("模块服务未连接", "Module service disconnected"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when (val state = infoState) {
                        FrameworkInfoState.Disconnected -> tr(
                            "请在 LSPosed 中启用模块并重新打开 MicRouter",
                            "Enable the module in LSPosed, then reopen MicRouter",
                        )
                        FrameworkInfoState.Loading -> tr("正在读取框架信息…", "Reading framework information…")
                        is FrameworkInfoState.Available -> {
                            val framework = listOf(state.info.name, state.info.version)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { "LSPosed" }
                            tr(
                                "已连接 $framework · API ${state.info.apiVersion}",
                                "Connected to $framework · API ${state.info.apiVersion}",
                            )
                        }
                        FrameworkInfoState.Unavailable -> tr("已连接 LSPosed", "Connected to LSPosed")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AppearancePreviewTile(
    modifier: Modifier,
    mode: AppearanceMode,
    selected: Boolean,
    language: String,
    onClick: () -> Unit,
) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    val label = when (mode) {
        AppearanceMode.Light -> tr("浅色", "Light")
        AppearanceMode.Dark -> tr("深色", "Dark")
        AppearanceMode.Dynamic -> tr("自动", "Auto")
    }
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.70f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            AppearanceMiniature(mode)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun AppearanceMiniature(mode: AppearanceMode) {
    val lightBackground = Color(0xFFFFF8F7)
    val lightSurface = Color(0xFFFFE7E4)
    val darkBackground = Color(0xFF21191A)
    val darkSurface = Color(0xFF342729)
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))) {
        when (mode) {
            AppearanceMode.Light -> Box(Modifier.fillMaxSize().background(lightBackground)) {}
            AppearanceMode.Dark -> Box(Modifier.fillMaxSize().background(darkBackground)) {}
            AppearanceMode.Dynamic -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().background(lightBackground)) {}
                Box(Modifier.weight(1f).fillMaxHeight().background(darkBackground)) {}
            }
        }
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Box(
                Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(9.dp)).background(
                    if (mode == AppearanceMode.Dark) darkSurface else lightSurface,
                ),
            ) {}
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth(0.72f).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))) {}
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth(0.52f).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))) {}
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth(0.84f).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {}
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(9.dp)).background(
                    if (mode == AppearanceMode.Dark) darkSurface else lightSurface,
                ).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(modifier = Modifier.size(18.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
                Spacer(Modifier.width(5.dp))
                Box(Modifier.weight(1f).height(13.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)) {}
            }
        }
    }
}

@Composable
private fun ThemePaletteGrid(
    selected: ThemePalette,
    enabled: Boolean,
    language: String,
    onSelect: (ThemePalette) -> Unit,
) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemePalette.entries.chunked(4).forEach { rowPalettes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowPalettes.forEach { palette ->
                    ThemePaletteTile(
                        modifier = Modifier.weight(1f),
                        palette = palette,
                        selected = selected == palette,
                        enabled = enabled,
                        label = themeColorName(palette, language),
                        onClick = { onSelect(palette) },
                    )
                }
                repeat(4 - rowPalettes.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ThemePaletteTile(
    modifier: Modifier,
    palette: ThemePalette,
    selected: Boolean,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).alpha(if (enabled) 1f else 0.38f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                PaletteSwatch(palette)
                if (selected) {
                    Surface(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            minLines = 2,
            maxLines = 2,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.55f),
        )
    }
}

@Composable
private fun PaletteSwatch(palette: ThemePalette) {
    val colors = themeSwatchColors(palette)
    Column(Modifier.size(58.dp).clip(CircleShape)) {
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(colors[0])) {}
            Box(Modifier.weight(1f).fillMaxHeight().background(colors[1])) {}
        }
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(colors[2])) {}
            Box(Modifier.weight(1f).fillMaxHeight().background(colors[3])) {}
        }
    }
}

private fun currentInputs(context: Context, language: String): List<InputDeviceIdentity> {
    val audioManager = context.getSystemService(AudioManager::class.java) ?: return emptyList()
    val microphones = runCatching { audioManager.microphones }.getOrDefault(emptyList())
    return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        .asSequence()
        .filter { it.isSource && isPhysicalMicType(it.type) }
        .map { device ->
            val microphone = microphones.firstOrNull { it.id == device.id }
                ?: microphones.firstOrNull { it.type == device.type && it.address == device.address }
            val typeName = deviceTypeName(device.type, language)
            val identity = microphoneIdentity(microphone, device.id, language)
            val product = device.productName?.toString().orEmpty().trim()
            val label = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "$typeName · $identity"
                else -> listOf(product, typeName, identity).filter(String::isNotBlank).distinct().joinToString(" · ")
            }
            InputDeviceIdentity(
                type = device.type,
                address = device.address.orEmpty(),
                id = device.id,
                microphoneDescription = microphone?.description?.toString().orEmpty(),
                microphoneGroup = microphone?.group ?: MicrophoneInfo.GROUP_UNKNOWN,
                microphoneIndex = microphone?.indexInTheGroup ?: MicrophoneInfo.INDEX_IN_THE_GROUP_UNKNOWN,
                name = label,
            )
        }
        .distinctBy { listOf(it.type, it.address, it.microphoneGroup, it.microphoneIndex, it.id) }
        .toList()
}

private fun deviceDetails(device: InputDeviceIdentity, language: String): String {
    val address = device.address.ifBlank { if (language == "zh") "无地址" else "no address" }
    return "${deviceTypeName(device.type, language)} · id ${device.id} · $address"
}

private fun isPhysicalMicType(type: Int): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_MIC,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET -> true
    else -> false
}

private fun microphoneIdentity(microphone: MicrophoneInfo?, deviceId: Int, language: String): String {
    val idText = "id $deviceId"
    if (microphone == null) return idText
    val group = if (
        microphone.group != MicrophoneInfo.GROUP_UNKNOWN &&
        microphone.indexInTheGroup != MicrophoneInfo.INDEX_IN_THE_GROUP_UNKNOWN
    ) {
        "g${microphone.group}#${microphone.indexInTheGroup}"
    } else {
        ""
    }
    val location = when (microphone.location) {
        MicrophoneInfo.LOCATION_MAINBODY -> if (language == "zh") "机身" else "main body"
        MicrophoneInfo.LOCATION_MAINBODY_MOVABLE -> if (language == "zh") "可动机身" else "movable"
        MicrophoneInfo.LOCATION_PERIPHERAL -> if (language == "zh") "外设" else "peripheral"
        else -> ""
    }
    val description = microphone.description?.toString().orEmpty().trim()
    return listOf(description, location, group, idText).filter(String::isNotBlank).distinct().joinToString(" · ")
}

private fun deviceTypeName(type: Int, language: String): String {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> tr("内置麦克风", "Built-in microphone")
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> tr("USB 麦克风", "USB microphone")
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> tr("有线耳机麦克风", "Wired headset microphone")
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> tr("蓝牙 SCO 麦克风", "Bluetooth SCO microphone")
        AudioDeviceInfo.TYPE_BLE_HEADSET -> tr("蓝牙 LE 麦克风", "Bluetooth LE microphone")
        else -> tr("音频输入 $type", "Audio input $type")
    }
}

private fun themeColorName(color: ThemePalette, language: String): String = when (color) {
    ThemePalette.Green -> if (language == "zh") "绿色" else "Green"
    ThemePalette.Teal -> if (language == "zh") "青绿" else "Teal"
    ThemePalette.Cyan -> if (language == "zh") "青色" else "Cyan"
    ThemePalette.Blue -> if (language == "zh") "蓝色" else "Blue"
    ThemePalette.Indigo -> if (language == "zh") "靛蓝" else "Indigo"
    ThemePalette.Purple -> if (language == "zh") "紫色" else "Purple"
    ThemePalette.Lavender -> if (language == "zh") "薰衣草" else "Lavender"
    ThemePalette.Rose -> if (language == "zh") "玫红" else "Rose"
    ThemePalette.Orange -> if (language == "zh") "橙色" else "Orange"
    ThemePalette.Sand -> if (language == "zh") "沙金" else "Sand"
    ThemePalette.Lime -> if (language == "zh") "青柠" else "Lime"
}

private fun themeSwatchColors(color: ThemePalette): List<Color> {
    val scheme = customColorScheme(color, dark = false)
    return listOf(
        scheme.primaryContainer,
        scheme.tertiaryContainer,
        scheme.secondary,
        scheme.surfaceVariant,
    )
}

private fun customColorScheme(color: ThemePalette, dark: Boolean): ColorScheme = when (color) {
    ThemePalette.Blue -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFA8C7FA),
            primaryContainer = Color(0xFF0842A0),
            secondary = Color(0xFFB9C6E4),
            secondaryContainer = Color(0xFF33466A),
            tertiary = Color(0xFFE1BBDD),
            tertiaryContainer = Color(0xFF593F5B),
            background = Color(0xFF10141C),
            surface = Color(0xFF151A24),
            surfaceVariant = Color(0xFF222A38),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0B57D0),
            primaryContainer = Color(0xFFD3E3FD),
            secondary = Color(0xFF475D92),
            secondaryContainer = Color(0xFFD9E2FF),
            tertiary = Color(0xFF745470),
            tertiaryContainer = Color(0xFFFFD7F0),
            background = Color(0xFFF5F8FF),
            surface = Color(0xFFEEF3FE),
            surfaceVariant = Color(0xFFE1EAF8),
        )
    }
    ThemePalette.Purple -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFD0BCFF),
            primaryContainer = Color(0xFF4F378B),
            secondary = Color(0xFFCCC2DC),
            secondaryContainer = Color(0xFF4A4458),
            tertiary = Color(0xFFEFB8C8),
            tertiaryContainer = Color(0xFF633B48),
            background = Color(0xFF17131D),
            surface = Color(0xFF1D1824),
            surfaceVariant = Color(0xFF2B2435),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6750A4),
            primaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFF625B71),
            secondaryContainer = Color(0xFFE8DEF8),
            tertiary = Color(0xFF7D5260),
            tertiaryContainer = Color(0xFFFFD8E4),
            background = Color(0xFFFBF7FF),
            surface = Color(0xFFF5F0FC),
            surfaceVariant = Color(0xFFECE3F5),
        )
    }
    ThemePalette.Green -> if (dark) {
        darkColorScheme(
            primary = Color(0xFF6FDBAF),
            primaryContainer = Color(0xFF005138),
            secondary = Color(0xFFB4CCBD),
            secondaryContainer = Color(0xFF354B3F),
            tertiary = Color(0xFFA5CDDF),
            tertiaryContainer = Color(0xFF244C5B),
            background = Color(0xFF0D1713),
            surface = Color(0xFF121D18),
            surfaceVariant = Color(0xFF203029),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF006C4C),
            primaryContainer = Color(0xFF8BF8C8),
            secondary = Color(0xFF4D6357),
            secondaryContainer = Color(0xFFCFE9D9),
            tertiary = Color(0xFF3D6373),
            tertiaryContainer = Color(0xFFC1E8FB),
            background = Color(0xFFF3FBF7),
            surface = Color(0xFFECF6F0),
            surfaceVariant = Color(0xFFDDECE4),
        )
    }
    ThemePalette.Orange -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFFFB870),
            primaryContainer = Color(0xFF6B3900),
            secondary = Color(0xFFE1C1A3),
            secondaryContainer = Color(0xFF59422D),
            tertiary = Color(0xFFC3CA9E),
            tertiaryContainer = Color(0xFF42492B),
            background = Color(0xFF1B140E),
            surface = Color(0xFF211A13),
            surfaceVariant = Color(0xFF33271B),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF8D4E00),
            primaryContainer = Color(0xFFFFDCC0),
            secondary = Color(0xFF725A42),
            secondaryContainer = Color(0xFFFFDCC0),
            tertiary = Color(0xFF5A6146),
            tertiaryContainer = Color(0xFFDEE6BF),
            background = Color(0xFFFFF8F2),
            surface = Color(0xFFFFF1E5),
            surfaceVariant = Color(0xFFF5E3D3),
        )
    }
    ThemePalette.Rose -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFFFB1C5),
            primaryContainer = Color(0xFF7C2944),
            secondary = Color(0xFFE4BDC7),
            secondaryContainer = Color(0xFF594047),
            tertiary = Color(0xFFF0BE95),
            tertiaryContainer = Color(0xFF5D421E),
            background = Color(0xFF1C1216),
            surface = Color(0xFF24171C),
            surfaceVariant = Color(0xFF35232A),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF9A405C),
            primaryContainer = Color(0xFFFFD9E1),
            secondary = Color(0xFF74565E),
            secondaryContainer = Color(0xFFFFD9E1),
            tertiary = Color(0xFF7C5735),
            tertiaryContainer = Color(0xFFFFDCBB),
            background = Color(0xFFFFF7F9),
            surface = Color(0xFFFFEEF2),
            surfaceVariant = Color(0xFFF6E0E6),
        )
    }
    ThemePalette.Teal -> if (dark) {
        darkColorScheme(
            primary = Color(0xFF7ADBCB),
            primaryContainer = Color(0xFF005047),
            secondary = Color(0xFFB1CCC5),
            secondaryContainer = Color(0xFF334B46),
            tertiary = Color(0xFFA5CDD7),
            tertiaryContainer = Color(0xFF244C55),
            background = Color(0xFF0D1716),
            surface = Color(0xFF121D1B),
            surfaceVariant = Color(0xFF20302D),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF006B5F),
            primaryContainer = Color(0xFF9EF2E2),
            secondary = Color(0xFF4A635D),
            secondaryContainer = Color(0xFFCCE8E0),
            tertiary = Color(0xFF3F6370),
            tertiaryContainer = Color(0xFFC3E8F7),
            background = Color(0xFFF3FBF8),
            surface = Color(0xFFECF7F3),
            surfaceVariant = Color(0xFFDDECE7),
        )
    }
    ThemePalette.Cyan -> if (dark) {
        darkColorScheme(
            primary = Color(0xFF53D7F2),
            primaryContainer = Color(0xFF004E5D),
            secondary = Color(0xFFB1CBD1),
            secondaryContainer = Color(0xFF334A50),
            tertiary = Color(0xFFC1C4EB),
            tertiaryContainer = Color(0xFF404563),
            background = Color(0xFF0D171A),
            surface = Color(0xFF121D20),
            surfaceVariant = Color(0xFF203034),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF00677A),
            primaryContainer = Color(0xFFA9EDFF),
            secondary = Color(0xFF4A6268),
            secondaryContainer = Color(0xFFCDE7ED),
            tertiary = Color(0xFF5A5D7A),
            tertiaryContainer = Color(0xFFE0E0FF),
            background = Color(0xFFF2FAFD),
            surface = Color(0xFFEBF6F9),
            surfaceVariant = Color(0xFFDDEBF0),
        )
    }
    ThemePalette.Indigo -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFB9C3FF),
            primaryContainer = Color(0xFF273989),
            secondary = Color(0xFFC3C6DD),
            secondaryContainer = Color(0xFF44475B),
            tertiary = Color(0xFFE5BAD7),
            tertiaryContainer = Color(0xFF5B3F54),
            background = Color(0xFF12131D),
            surface = Color(0xFF181923),
            surfaceVariant = Color(0xFF272936),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3F51A2),
            primaryContainer = Color(0xFFDDE1FF),
            secondary = Color(0xFF595E72),
            secondaryContainer = Color(0xFFDDE1F9),
            tertiary = Color(0xFF74546E),
            tertiaryContainer = Color(0xFFFFD7F4),
            background = Color(0xFFF8F7FF),
            surface = Color(0xFFF1F0FA),
            surfaceVariant = Color(0xFFE5E5F1),
        )
    }
    ThemePalette.Lavender -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFDDB8F7),
            primaryContainer = Color(0xFF573A75),
            secondary = Color(0xFFD0C1D4),
            secondaryContainer = Color(0xFF4B4350),
            tertiary = Color(0xFFF3B7C0),
            tertiaryContainer = Color(0xFF623D45),
            background = Color(0xFF19131D),
            surface = Color(0xFF201824),
            surfaceVariant = Color(0xFF302638),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF71548F),
            primaryContainer = Color(0xFFF0DBFF),
            secondary = Color(0xFF655A6B),
            secondaryContainer = Color(0xFFEBDFF0),
            tertiary = Color(0xFF805158),
            tertiaryContainer = Color(0xFFFFD9DD),
            background = Color(0xFFFFF7FF),
            surface = Color(0xFFF8EFFB),
            surfaceVariant = Color(0xFFEEE2F1),
        )
    }
    ThemePalette.Sand -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFEBC16C),
            primaryContainer = Color(0xFF5B4307),
            secondary = Color(0xFFD6C6A1),
            secondaryContainer = Color(0xFF4C4632),
            tertiary = Color(0xFFA8CFA5),
            tertiaryContainer = Color(0xFF304F31),
            background = Color(0xFF1A160D),
            surface = Color(0xFF211C12),
            surfaceVariant = Color(0xFF332C1D),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF785A1E),
            primaryContainer = Color(0xFFFFDEA0),
            secondary = Color(0xFF6B5D3F),
            secondaryContainer = Color(0xFFF4E1BB),
            tertiary = Color(0xFF4A6545),
            tertiaryContainer = Color(0xFFCCEBC3),
            background = Color(0xFFFFF9EF),
            surface = Color(0xFFFFF2DD),
            surfaceVariant = Color(0xFFF0E4CE),
        )
    }
    ThemePalette.Lime -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFBEDC68),
            primaryContainer = Color(0xFF414D00),
            secondary = Color(0xFFC7CAA8),
            secondaryContainer = Color(0xFF474A31),
            tertiary = Color(0xFFA1D0C1),
            tertiaryContainer = Color(0xFF284F46),
            background = Color(0xFF15170D),
            surface = Color(0xFF1B1D12),
            surfaceVariant = Color(0xFF2A2F1D),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF566500),
            primaryContainer = Color(0xFFD8EF7D),
            secondary = Color(0xFF5E6145),
            secondaryContainer = Color(0xFFE4E7C3),
            tertiary = Color(0xFF3A665B),
            tertiaryContainer = Color(0xFFBCEBDD),
            background = Color(0xFFF9FBEF),
            surface = Color(0xFFF2F5E7),
            surfaceVariant = Color(0xFFE6EAD4),
        )
    }
}
