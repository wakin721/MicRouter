package dev.wakin.microuter

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
private data class DeviceChoice(val type: Int, val address: String, val name: String)

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
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else lightColorScheme()
    val apps = remember { installedApps(context) }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AppItem?>(null) }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text("MicRouter", style = MaterialTheme.typography.displaySmall)
                    Text(
                        if (service == null) "LSPosed service unavailable" else "Per-app microphone routing",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Routing scope", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Choose an app, select an input device, then request LSPosed scope for configured apps.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { requestConfiguredScope(context, service) }, enabled = service != null) {
                            Text("Apply LSPosed scope")
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search apps or package names") },
                    shape = RoundedCornerShape(22.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps.filter { search.isBlank() || it.label.contains(search, true) || it.packageName.contains(search, true) }) { app ->
                        ElevatedCard(
                            onClick = { if (service != null) editing = app else Toast.makeText(context, "Enable the module in LSPosed first", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text(app.label, style = MaterialTheme.typography.titleMedium)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        editing?.let { app ->
            RouteDialog(context, service!!, app, onDismiss = { editing = null })
        }
    }
}

@Composable
private fun RouteDialog(context: Context, service: XposedService, app: AppItem, onDismiss: () -> Unit) {
    val prefs = remember(app.packageName) { service.getRemotePreferences(RouteStore.PREFS) }
    var rule by remember(app.packageName) { mutableStateOf(RouteStore.read(prefs, app.packageName)) }
    val devices = remember { currentInputs(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                Text("Input device", style = MaterialTheme.typography.titleMedium)
                devices.forEach { d ->
                    FilterChip(
                        selected = rule.deviceType == d.type && (d.type == -1 || rule.deviceAddress == d.address),
                        onClick = { rule = rule.copy(deviceType = d.type, deviceAddress = d.address, deviceName = d.name) },
                        label = { Text(d.name) }
                    )
                }
                Text("Software input gain: ${"%.1f".format(rule.gainDb)} dB", style = MaterialTheme.typography.titleMedium)
                Slider(value = rule.gainDb, onValueChange = { rule = rule.copy(gainDb = it) }, valueRange = -12f..24f, steps = 35)
                Text("Gain is applied to Java AudioRecord PCM reads only. MediaRecorder and native AAudio paths may bypass it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { RouteStore.write(prefs, rule); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Suppress("DEPRECATION")
private fun installedApps(context: Context): List<AppItem> = context.packageManager.getInstalledApplications(0)
    .asSequence()
    .filter { it.packageName != context.packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.packageName.startsWith("com.android.camera")) }
    .map { AppItem(context.packageManager.getApplicationLabel(it).toString(), it.packageName) }
    .sortedBy { it.label.lowercase() }
    .toList()

private fun currentInputs(context: Context): List<DeviceChoice> {
    val am = context.getSystemService(AudioManager::class.java)
    val list = mutableListOf(DeviceChoice(-1, "", "System default"))
    am.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.isSource }.forEach {
        val label = "${it.productName} · ${deviceTypeName(it.type)}"
        list += DeviceChoice(it.type, it.address.orEmpty(), label)
    }
    return list.distinctBy { Triple(it.type, it.address, it.name) }
}

private fun deviceTypeName(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
    AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
    else -> "Type $type"
}

private fun requestConfiguredScope(context: Context, service: XposedService?) {
    if (service == null) return
    val prefs = service.getRemotePreferences(RouteStore.PREFS)
    val packages = (RouteStore.configuredPackages(prefs) + listOf("com.tencent.mm", "com.discord")).distinct()
    service.requestScope(packages, object : OnScopeEventListener {
        override fun onScopeRequestApproved(approved: List<String>) {
            Toast.makeText(context, "Scope approved for ${approved.size} apps", Toast.LENGTH_SHORT).show()
        }
        override fun onScopeRequestFailed(message: String) {
            Toast.makeText(context, "Scope request failed: $message", Toast.LENGTH_LONG).show()
        }
    })
}
