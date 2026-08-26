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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.libxposed.service.XposedService

private enum class MainPage { Microphone, About }
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
    var appearanceMode by remember {
        mutableStateOf(
            AppearanceMode.entries.firstOrNull {
                it.name.equals(uiPrefs.getString("appearance_mode", "dynamic"), ignoreCase = true)
            } ?: AppearanceMode.Dynamic,
        )
    }
    var themeColor by remember {
        mutableStateOf(
            ThemeColor.entries.firstOrNull {
                it.name.equals(uiPrefs.getString("theme_color", "blue"), ignoreCase = true)
            } ?: ThemeColor.Blue,
        )
    }
    var page by remember { mutableStateOf(MainPage.Microphone) }

    val systemDark = isSystemInDarkTheme()
    val dark = when (appearanceMode) {
        AppearanceMode.Dynamic -> systemDark
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        customColorScheme(themeColor, dark)
    }
    fun tr(zh: String, en: String) = if (language == "zh") zh else en

    MaterialTheme(colorScheme = colors) {
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
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = page == MainPage.Microphone,
                        onClick = { page = MainPage.Microphone },
                        icon = { Text("◉") },
                        label = { Text(tr("麦克风", "Microphone")) },
                    )
                    NavigationBarItem(
                        selected = page == MainPage.About,
                        onClick = { page = MainPage.About },
                        icon = { Text("ⓘ") },
                        label = { Text(tr("关于", "About")) },
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
                        uiPrefs.edit().putString("theme_color", it.name.lowercase()).apply()
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
            StatusCard(
                connected = service != null,
                supported = supported,
                language = language,
            )
        }
        item {
            Text(
                tr("选择全局麦克风", "Choose the global microphone"),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                tr(
                    "选择后会通过 system_server 应用于标准录音来源，无需逐个选择应用。",
                    "The choice is applied to standard capture sources through system_server, without selecting individual apps.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun StatusCard(connected: Boolean, supported: Boolean, language: String) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    val ready = connected && supported
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            contentColor = if (ready) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (ready) tr("全局路由已就绪", "Global routing is ready") else tr("暂时无法选择", "Selection unavailable"),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !supported -> tr("全局麦克风路由需要 Android 11 或更高版本。", "Global microphone routing requires Android 11 or newer.")
                    !connected -> tr("请确认模块已在 LSPosed 中启用并重新打开 MicRouter。", "Enable the module in LSPosed, then reopen MicRouter.")
                    else -> tr("选择会写入唯一的系统级麦克风设置。", "Your choice is stored as the single system-wide microphone setting.")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
    themeColor: ThemeColor,
    onLanguageChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onThemeColorChange: (ThemeColor) -> Unit,
) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    var languageMenu by remember { mutableStateOf(false) }
    var themeMenu by remember { mutableStateOf(false) }
    val connected = service != null

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("MicRouter", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(tr("基于 LSPosed/libxposed 的系统级麦克风路由模块。", "System-wide microphone routing based on LSPosed/libxposed."))
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(tr("LSPosed 连接状态", "LSPosed connection"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(if (connected) tr("已连接", "Connected") else tr("未连接", "Disconnected"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (connected) {
                            tr("libxposed 服务可用，系统设置可以正常读写。", "The libxposed service is available and system settings can be read and written.")
                        } else {
                            tr("请确认模块已在 LSPosed 中启用并重新打开 MicRouter。", "Enable the module in LSPosed, then reopen MicRouter.")
                        },
                        style = MaterialTheme.typography.bodySmall,
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
                                DropdownMenuItem(text = { Text("简体中文") }, onClick = { onLanguageChange("zh"); languageMenu = false })
                                DropdownMenuItem(text = { Text("English") }, onClick = { onLanguageChange("en"); languageMenu = false })
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(tr("深色模式", "Appearance"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("动态模式会跟随系统浅色或深色设置。", "Dynamic follows the system light or dark appearance."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        },
                                    )
                                },
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("动态取色", "Dynamic color"), style = MaterialTheme.typography.titleMedium)
                            Text(tr("Android 12+ 从系统壁纸取色", "Use system wallpaper colors on Android 12+"), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                    }

                    if (!dynamicColor) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("主题颜色", "Theme color"), style = MaterialTheme.typography.titleMedium)
                                Text(themeColorName(themeColor, language), style = MaterialTheme.typography.bodySmall)
                            }
                            Box {
                                FilledTonalButton(onClick = { themeMenu = true }) {
                                    Surface(modifier = Modifier.size(18.dp), shape = CircleShape, color = themePreviewColor(themeColor)) {}
                                    Spacer(Modifier.width(8.dp))
                                    Text(themeColorName(themeColor, language))
                                }
                                DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                                    ThemeColor.entries.forEach { color ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(modifier = Modifier.size(18.dp), shape = CircleShape, color = themePreviewColor(color)) {}
                                                    Spacer(Modifier.width(10.dp))
                                                    Text(themeColorName(color, language))
                                                }
                                            },
                                            onClick = { onThemeColorChange(color); themeMenu = false },
                                        )
                                    }
                                }
                            }
                        }
                    }
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

private fun customColorScheme(color: ThemeColor, dark: Boolean): ColorScheme {
    val primary = themePreviewColor(color)
    return if (dark) {
        darkColorScheme(
            primary = when (color) {
                ThemeColor.Blue -> Color(0xFFA8C7FA)
                ThemeColor.Purple -> Color(0xFFD0BCFF)
                ThemeColor.Green -> Color(0xFF6FDBAF)
                ThemeColor.Orange -> Color(0xFFFFB870)
                ThemeColor.Rose -> Color(0xFFFFB1C5)
            },
            primaryContainer = primary,
            background = Color(0xFF10141C),
            surface = Color(0xFF151A24),
            surfaceVariant = Color(0xFF222A38),
        )
    } else {
        lightColorScheme(
            primary = primary,
            primaryContainer = when (color) {
                ThemeColor.Blue -> Color(0xFFD3E3FD)
                ThemeColor.Purple -> Color(0xFFEADDFF)
                ThemeColor.Green -> Color(0xFF8BF8C8)
                ThemeColor.Orange -> Color(0xFFFFDCC0)
                ThemeColor.Rose -> Color(0xFFFFD9E1)
            },
            background = Color(0xFFF5F8FF),
            surface = Color(0xFFEEF3FE),
            surfaceVariant = Color(0xFFE1EAF8),
        )
    }
}
