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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    var page by remember { mutableStateOf(MainPage.Apps) }
    val dark = isSystemInDarkTheme()
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    val apps = remember { installedApps(context) }

    fun tr(zh: String, en: String) = if (language == "zh") zh else en

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
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
                NavigationBar {
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
                )
                MainPage.About -> AboutPage(
                    modifier = Modifier.padding(padding),
                    service = service,
                    language = language,
                    dynamicColor = dynamicColor,
                    onLanguageChange = {
                        language = it
                        uiPrefs.edit().putString("language", it).apply()
                    },
                    onDynamicColorChange = {
                        dynamicColor = it
                        uiPrefs.edit().putBoolean("dynamic_color", it).apply()
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

        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(tr("LSPosed 作用域", "LSPosed scope"), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (service == null) tr("服务不可用", "Service unavailable")
                    else tr("仅申请你已经配置的软件；不再包含推荐软件。", "Only request apps you configured; no recommended apps are added.")
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { requestConfiguredScope(context, service, language) },
                    enabled = service != null
                ) { Text(tr("应用已配置软件作用域", "Apply configured app scope")) }
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
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (configured) {
                                AssistChip(onClick = {}, label = { Text(tr("已设置", "Configured")) })
                            }
                        }
                        if (summary != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (summary.enabled) summary.deviceName else tr("不干预", "No override"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
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
            allowScopeRequest = false,
            onSave = { rule, _ ->
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
                allowScopeRequest = true,
                onSave = { rule, applyScope ->
                    RouteStore.write(remote, rule.copy(packageName = app.packageName))
                    revision++
                    if (applyScope) requestAppScope(context, service, app.packageName, language)
                    editing = null
                },
                onDismiss = { editing = null }
            )
        }
    }
}

@Composable
private fun AboutPage(
    modifier: Modifier,
    service: XposedService?,
    language: String,
    dynamicColor: Boolean,
    onLanguageChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    var languageMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("MicRouter", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(tr("基于 LSPosed/libxposed 的按软件麦克风路由模块。", "Per-app microphone routing based on LSPosed/libxposed."))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (service == null) tr("LSPosed 服务：未连接", "LSPosed service: disconnected")
                        else tr("LSPosed 服务：已连接", "LSPosed service: connected"),
                        color = if (service == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
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
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
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
    allowScopeRequest: Boolean,
    onSave: (RouteRule, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    fun tr(zh: String, en: String) = if (language == "zh") zh else en
    var rule by remember(title, initialRule.toJson()) { mutableStateOf(initialRule) }
    var applyScope by remember(title) { mutableStateOf(false) }
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
                if (allowScopeRequest) {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("保存后申请 LSPosed 作用域", "Apply LSPosed scope after saving"), style = MaterialTheme.typography.titleMedium)
                            Text(tr("仅申请当前软件，不添加推荐软件。", "Request only this app; no recommended apps are added."), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = applyScope, onCheckedChange = { applyScope = it })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(rule, applyScope) }) { Text(tr("保存", "Save")) } },
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

private fun requestConfiguredScope(context: Context, service: XposedService?, language: String) {
    if (service == null) return
    val prefs = service.getRemotePreferences(RouteStore.PREFS)
    val packages = RouteStore.configuredPackages(prefs).distinct()
    if (packages.isEmpty()) {
        Toast.makeText(context, if (language == "zh") "还没有已配置的软件" else "No configured apps yet", Toast.LENGTH_SHORT).show()
        return
    }
    requestScope(context, service, packages, language)
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
