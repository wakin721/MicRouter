package dev.wakin.microuter

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MicrophoneInfo
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedService.OnScopeEventListener

private data class AppItem(val label: String, val packageName: String)
private data class DeviceChoice(
    val type: Int,
    val address: String,
    val id: Int,
    val name: String,
    val microphoneDescription: String = "",
    val microphoneGroup: Int = -1,
    val microphoneIndex: Int = -1,
)

private enum class MainPage { Apps, About }
private enum class AppearanceMode { Dynamic, Light, Dark }
private enum class ThemeColor { Blue, Purple, Green, Orange, Rose }

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
    var autoApplyScope by remember { mutableStateOf(uiPrefs.getBoolean("auto_apply_scope", false)) }
    var appearanceMode by remember {
        mutableStateOf(
            AppearanceMode.entries.firstOrNull {
                it.name.equals(uiPrefs.getString("appearance_mode", "dynamic"), ignoreCase = true)
            } ?: AppearanceMode.Dynamic
        )
    }
    var themeColor by remember {
        mutableStateOf(
            ThemeColor.entries.firstOrNull {
                it.name.equals(uiPrefs.getString("theme_color", "blue"), ignoreCase = true)
            } ?: ThemeColor.Blue
        )
    }
    var page by remember { mutableStateOf(MainPage.Apps) }

    val systemDark = isSystemInDarkTheme()
    val dark = when (appearanceMode) {
        AppearanceMode.Dynamic -> systemDark
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        customColorScheme(themeColor, dark)
    }
    val apps = remember { installedApps(context) }

    fun tr(zh: String, en: String) = if (language == "zh") zh else en

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column(
                    Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 44.dp,
                        bottom = 18.dp
                    )
                ) {
                    Text("MicRouter", style = MaterialTheme.typography.displaySmall)
                    Text(
                        when (page) {
                            MainPage.Apps -> tr("软件管理", "App management")
                            MainPage.About -> tr("关于", "About")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = page == MainPage.Apps,
                        onClick = { page = MainPage.Apps },
                        icon = { Text("▦") },
                        label = { Text(tr("软件管理", "Apps")) }
                    )
                    NavigationBarItem(
                        selected = page == MainPage.About,
                        onClick = { page = MainPage.About },
                        icon = { Text("ⓘ") },
                        label = { Text(tr("关于", "About")) }
                    )
                }
            }
        ) { padding ->
            when (page) {
                MainPage.Apps -> AppsPage(
                    modifier = Modifier.padding(padding),
                    context = context,
                    service = service,
                    apps = apps,
                    language = language,
                    autoApplyScope = autoApplyScope,
                )
                MainPage.About -> AboutPage(
                    modifier = Modifier.padding(padding),
                    service = service,
                    language = language,
                    dynamicColor = dynamicColor,
                    appearanceMode = appearanceMode,
                    themeColor = themeColor,
                    autoApplyScope = autoApplyScope,
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
                        uiPrefs.edit().putString("theme_color", it.name.lowercase()).apply()
                    },
                    onAutoApplyScopeChange = {
                        autoApplyScope = it
                        uiPrefs.edit().putBoolean("auto_apply_scope", it).apply()
                    },
                )
            }
        }
    }
}

@Composable
private fun AppsPage(
    modifier: Modifier,
    context: Context,
    service: XposedService?,
    apps: List<AppItem>,
    language: String,
    autoApplyScope: Boolean,
) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppItem?>(null) }
    var editGlobal by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val prefs = remember(service) { service?.getRemotePreferences(RouteStore.PREFS) }
    val globalRule = prefs?.let { RouteStore.readGlobal(it) }

    val visibleApps = remember(apps, search, prefs, revision) {
        apps.asSequence()
            .filter { search.isBlank() || it.label.contains(search, true) || it.packageName.contains(search, true) }
            .sortedWith(
                compareByDescending<AppItem> { app -> prefs?.let { RouteStore.hasRule(it, app.packageName) } == true }
                    .thenBy { it.label.lowercase() }
                    .thenBy { it.packageName }
            )
            .toList()
    }

    Column(modifier.padding(horizontal = 16.dp)) {
        Card(
            onClick = {
                if (service != null) editGlobal = true
                else Toast.makeText(context, tr("请先在 LSPosed 中启用模块", "Enable the module in LSPosed first"), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(tr("全局设置", "Global settings"), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        globalRule == null -> tr("LSPosed 服务不可用", "LSPosed service unavailable")
                        !globalRule.enabled -> tr("未启用；仅使用各软件独立规则", "Disabled; per-app rules only")
                        else -> tr("默认输入：${globalRule.deviceName}", "Default input: ${globalRule.deviceName}")
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("未单独配置的软件会继承全局规则。", "Apps without their own rule inherit this default."),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("搜索软件或包名", "Search apps or package names")) },
            shape = RoundedCornerShape(22.dp),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleApps, key = { it.packageName }) { app ->
                val configured = prefs?.let { RouteStore.hasRule(it, app.packageName) } == true
                val summary = prefs?.let { RouteStore.readEffective(it, app.packageName) }
                ElevatedCard(
                    onClick = {
                        if (service != null) editing = app
                        else Toast.makeText(context, tr("请先在 LSPosed 中启用模块", "Enable the module in LSPosed first"), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(context, app)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (summary != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (summary.enabled) summary.deviceName else tr("不干预", "No override"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (configured) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(onClick = {}, label = { Text(tr("已设置", "Configured")) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (editGlobal && service != null) {
        RouteDialog(
            context = context,
            title = tr("全局设置", "Global settings"),
            subtitle = tr("作为未单独配置软件的默认规则", "Default rule for apps without an override"),
            initialRule = RouteStore.readGlobal(service.getRemotePreferences(RouteStore.PREFS)),
            language = language,
            onSave = { rule ->
                RouteStore.writeGlobal(service.getRemotePreferences(RouteStore.PREFS), rule)
                revision++
                editGlobal = false
            },
            onDismiss = { editGlobal = false }
        )
    }

    editing?.let { app ->
        if (service != null) {
            val remote = service.getRemotePreferences(RouteStore.PREFS)
            RouteDialog(
                context = context,
                title = app.label,
                subtitle = app.packageName,
                initialRule = RouteStore.read(remote, app.packageName),
                language = language,
                onSave = { rule ->
                    RouteStore.write(remote, rule.copy(packageName = app.packageName))
                    revision++
                    if (autoApplyScope) requestAppScope(context, service, app.packageName, language)
                    editing = null
                },
                onDismiss = { editing = null }
            )
        }
    }
}

@Composable
private fun AppIcon(context: Context, app: AppItem) {
    val bitmap = remember(app.packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(app.packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier.size(48.dp)
        )
    } else {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(app.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AboutPage(
    modifier: Modifier,
    service: XposedService?,
    language: String,
    dynamicColor: Boolean,
    appearanceMode: AppearanceMode,
    themeColor: ThemeColor,
    autoApplyScope: Boolean,
    onLanguageChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onThemeColorChange: (ThemeColor) -> Unit,
    onAutoApplyScopeChange: (Boolean) -> Unit,
) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    var languageMenu by remember { mutableStateOf(false) }
    var themeMenu by remember { mutableStateOf(false) }
    val connected = service != null

    LazyColumn(
        modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("MicRouter", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(tr("基于 LSPosed/libxposed 的按软件麦克风路由模块。", "Per-app microphone routing based on LSPosed/libxposed."))
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(tr("LSPosed 连接状态", "LSPosed connection"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (connected) tr("已连接", "Connected") else tr("未连接", "Disconnected"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (connected) {
                            tr("libxposed 服务可用，软件设置可以正常读写。", "libxposed service is available and app settings can be read and written.")
                        } else {
                            tr("请确认模块已在 LSPosed 中启用并重新打开 MicRouter。", "Confirm the module is enabled in LSPosed, then reopen MicRouter.")
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(tr("设置", "Settings"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("语言", "Language"), style = MaterialTheme.typography.titleMedium)
                            Text(if (language == "zh") "简体中文" else "English", style = MaterialTheme.typography.bodySmall)
                        }
                        Box {
                            FilledTonalButton(onClick = { languageMenu = true }) {
                                Text(if (language == "zh") "中文" else "English")
                            }
                            DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("简体中文") },
                                    onClick = { onLanguageChange("zh"); languageMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("English") },
                                    onClick = { onLanguageChange("en"); languageMenu = false }
                                )
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(tr("深色模式", "Appearance"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("动态模式会跟随系统浅色或深色设置。", "Dynamic follows the system light or dark appearance."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppearanceMode.entries.forEach { mode ->
                            FilterChip(
                                selected = appearanceMode == mode,
                                onClick = { onAppearanceModeChange(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            AppearanceMode.Dynamic -> tr("动态", "Dynamic")
                                            AppearanceMode.Light -> tr("浅色", "Light")
                                            AppearanceMode.Dark -> tr("深色", "Dark")
                                        }
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("动态取色", "Dynamic color"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                tr("Android 12+ 从系统壁纸取色", "Use system wallpaper colors on Android 12+"),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                    }

                    if (!dynamicColor) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("主题颜色", "Theme color"), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    themeColorName(themeColor, language),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box {
                                FilledTonalButton(onClick = { themeMenu = true }) {
                                    Surface(
                                        modifier = Modifier.size(18.dp),
                                        shape = CircleShape,
                                        color = themePreviewColor(themeColor)
                                    ) {}
                                    Spacer(Modifier.width(8.dp))
                                    Text(themeColorName(themeColor, language))
                                }
                                DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                                    ThemeColor.entries.forEach { color ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        modifier = Modifier.size(18.dp),
                                                        shape = CircleShape,
                                                        color = themePreviewColor(color)
                                                    ) {}
                                                    Spacer(Modifier.width(10.dp))
                                                    Text(themeColorName(color, language))
                                                }
                                            },
                                            onClick = {
                                                onThemeColorChange(color)
                                                themeMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("保存后申请 LSPosed 作用域", "Apply LSPosed scope after saving"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                tr("保存单个软件设置时自动申请该软件作用域。", "Automatically request scope for the app when saving its settings."),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = autoApplyScope, onCheckedChange = onAutoApplyScopeChange)
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(tr("说明", "Notes"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(tr("麦克风增益仅作用于 Java AudioRecord PCM 读取；MediaRecorder、AAudio/Oboe 或 WebRTC 原生路径可能绕过软件增益。", "Microphone gain only affects Java AudioRecord PCM reads. MediaRecorder, AAudio/Oboe, or native WebRTC paths may bypass software gain."))
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun RouteDialog(
    context: Context,
    title: String,
    subtitle: String,
    initialRule: RouteRule,
    language: String,
    onSave: (RouteRule) -> Unit,
    onDismiss: () -> Unit,
) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    var rule by remember(title, initialRule.toJson()) { mutableStateOf(initialRule) }
    val devices = remember(language) { currentInputs(context, language) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("启用路由", "Enable routing"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = rule.enabled, onCheckedChange = { rule = rule.copy(enabled = it) })
                }
                Text(tr("输入设备", "Input device"), style = MaterialTheme.typography.titleMedium)
                devices.forEach { d ->
                    FilterChip(
                        selected = rule.deviceType == d.type && (
                            d.type == -1 ||
                                (rule.deviceAddress.isNotBlank() && rule.deviceAddress == d.address) ||
                                (rule.microphoneGroup >= 0 && rule.microphoneGroup == d.microphoneGroup && rule.microphoneIndex == d.microphoneIndex) ||
                                (rule.deviceIdHint >= 0 && rule.deviceIdHint == d.id)
                            ),
                        onClick = {
                            rule = rule.copy(
                                deviceType = d.type,
                                deviceAddress = d.address,
                                deviceIdHint = d.id,
                                microphoneDescription = d.microphoneDescription,
                                microphoneGroup = d.microphoneGroup,
                                microphoneIndex = d.microphoneIndex,
                                deviceName = d.name
                            )
                        },
                        label = { Text(d.name) }
                    )
                }
                Text(tr("软件输入增益：${"%.1f".format(rule.gainDb)} dB", "Software input gain: ${"%.1f".format(rule.gainDb)} dB"), style = MaterialTheme.typography.titleMedium)
                Slider(value = rule.gainDb, onValueChange = { rule = rule.copy(gainDb = it) }, valueRange = -12f..24f, steps = 35)
                Text(
                    tr("仅处理 Java AudioRecord PCM 数据。", "Only Java AudioRecord PCM data is processed."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(rule) }) { Text(tr("保存", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel")) } }
    )
}

@Suppress("DEPRECATION")
private fun installedApps(context: Context): List<AppItem> = context.packageManager.getInstalledApplications(0)
    .asSequence()
    .filter { it.packageName != context.packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName.startsWith("com.android.camera")) }
    .map { AppItem(context.packageManager.getApplicationLabel(it).toString(), it.packageName) }
    .sortedBy { it.label.lowercase() }
    .toList()

private fun currentInputs(context: Context, language: String): List<DeviceChoice> {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    val am = context.getSystemService(AudioManager::class.java)
    val microphones = runCatching { am.microphones }.getOrDefault(emptyList())
    val list = mutableListOf(DeviceChoice(-1, "", -1, tr("系统默认", "System default")))

    am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        .filter { it.isSource && isPhysicalMicType(it.type) }
        .forEach { device ->
            val mic = microphones.firstOrNull { it.id == device.id }
                ?: microphones.firstOrNull { it.type == device.type && it.address == device.address }
            val typeName = deviceTypeName(device.type, language)
            val identity = microphoneIdentity(mic, device.id, language)
            val product = device.productName?.toString().orEmpty().trim()
            val label = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "$typeName · $identity"
                else -> listOf(product, typeName, identity).filter { it.isNotBlank() }.distinct().joinToString(" · ")
            }
            list += DeviceChoice(
                type = device.type,
                address = device.address.orEmpty(),
                id = device.id,
                name = label,
                microphoneDescription = mic?.description.orEmpty(),
                microphoneGroup = mic?.group ?: MicrophoneInfo.GROUP_UNKNOWN,
                microphoneIndex = mic?.indexInTheGroup ?: MicrophoneInfo.INDEX_IN_THE_GROUP_UNKNOWN,
            )
        }
    return list.distinctBy { listOf(it.type, it.address, it.microphoneGroup, it.microphoneIndex, it.id) }
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

private fun microphoneIdentity(mic: MicrophoneInfo?, deviceId: Int, language: String): String {
    val idText = "id $deviceId"
    if (mic == null) return idText
    val group = if (mic.group != MicrophoneInfo.GROUP_UNKNOWN && mic.indexInTheGroup != MicrophoneInfo.INDEX_IN_THE_GROUP_UNKNOWN) {
        "g${mic.group}#${mic.indexInTheGroup}"
    } else ""
    val location = when (mic.location) {
        MicrophoneInfo.LOCATION_MAINBODY -> if (language == "zh") "机身" else "main body"
        MicrophoneInfo.LOCATION_MAINBODY_MOVABLE -> if (language == "zh") "可动机身" else "movable"
        MicrophoneInfo.LOCATION_PERIPHERAL -> if (language == "zh") "外设" else "peripheral"
        else -> ""
    }
    val desc = mic.description.orEmpty().trim()
    return listOf(desc, location, group, idText).filter { it.isNotBlank() }.distinct().joinToString(" · ")
}

private fun deviceTypeName(type: Int, language: String): String {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> tr("内置麦克风", "Built-in microphone")
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> tr("USB 麦克风", "USB microphone")
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> tr("有线耳机麦克风", "Wired headset microphone")
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> tr("蓝牙 SCO 麦克风", "Bluetooth SCO microphone")
        AudioDeviceInfo.TYPE_BLE_HEADSET -> tr("蓝牙 LE 麦克风", "Bluetooth LE microphone")
        else -> tr("音频输入 $type", "Audio input $type")
    }
}

private fun themeColorName(color: ThemeColor, language: String): String = when (color) {
    ThemeColor.Blue -> if (language == "zh") "蓝色" else "Blue"
    ThemeColor.Purple -> if (language == "zh") "紫色" else "Purple"
    ThemeColor.Green -> if (language == "zh") "绿色" else "Green"
    ThemeColor.Orange -> if (language == "zh") "橙色" else "Orange"
    ThemeColor.Rose -> if (language == "zh") "玫红" else "Rose"
}

private fun themePreviewColor(color: ThemeColor): Color = when (color) {
    ThemeColor.Blue -> Color(0xFF0B57D0)
    ThemeColor.Purple -> Color(0xFF6750A4)
    ThemeColor.Green -> Color(0xFF006C4C)
    ThemeColor.Orange -> Color(0xFF8D4E00)
    ThemeColor.Rose -> Color(0xFF9A405C)
}

private fun customColorScheme(color: ThemeColor, dark: Boolean): ColorScheme = when (color) {
    ThemeColor.Blue -> if (dark) {
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
    ThemeColor.Purple -> if (dark) {
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
    ThemeColor.Green -> if (dark) {
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
    ThemeColor.Orange -> if (dark) {
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
    ThemeColor.Rose -> if (dark) {
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
}

private fun requestAppScope(context: Context, service: XposedService, packageName: String, language: String) {
    requestScope(context, service, listOf(packageName), language)
}

private fun requestScope(context: Context, service: XposedService, packages: List<String>, language: String) {
    service.requestScope(packages, object : OnScopeEventListener {
        override fun onScopeRequestApproved(approved: List<String>) {
            val text = if (language == "zh") "已批准 ${approved.size} 个软件" else "Scope approved for ${approved.size} apps"
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }

        override fun onScopeRequestFailed(message: String) {
            val text = if (language == "zh") "作用域请求失败：$message" else "Scope request failed: $message"
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    })
}
