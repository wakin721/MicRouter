package dev.wakin.microuter

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MicrophoneInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import java.lang.reflect.Method

class SystemMicrophoneRouter(
    private val logger: (priority: Int, message: String) -> Unit,
) {
    private var started = false
    private var audioManager: AudioManager? = null
    private var preferences: SharedPreferences? = null
    private var workerThread: HandlerThread? = null
    private var handler: Handler? = null

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == RouteStore.GLOBAL_KEY) scheduleApply()
        }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = scheduleApply()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = scheduleApply()
    }

    @Synchronized
    fun start(context: Context, routePreferences: SharedPreferences) {
        if (started) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            logSafely(Log.WARN, "Global microphone routing requires Android 11 or newer")
            return
        }

        var newThread: HandlerThread? = null
        runCatching {
            val manager = context.getSystemService(AudioManager::class.java)
                ?: error("AudioManager unavailable")
            newThread = HandlerThread(
                "MicRouterRouting",
                Process.THREAD_PRIORITY_BACKGROUND,
            ).apply { start() }
            val workerHandler = Handler(checkNotNull(newThread).looper)

            audioManager = manager
            preferences = routePreferences
            workerThread = newThread
            handler = workerHandler
            routePreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
            manager.registerAudioDeviceCallback(deviceCallback, workerHandler)
            started = true
            scheduleApply()
            logSafely(Log.INFO, "Global microphone routing started in system_server")
        }.onFailure { failure ->
            runCatching { routePreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener) }
            runCatching { audioManager?.unregisterAudioDeviceCallback(deviceCallback) }
            runCatching { newThread?.quitSafely() }
            handler = null
            workerThread = null
            preferences = null
            audioManager = null
            logSafely(Log.ERROR, "Global microphone routing startup failed: $failure")
        }
    }

    private fun scheduleApply() {
        val workerHandler = handler ?: return
        runCatching {
            workerHandler.removeCallbacks(applyRoute)
            workerHandler.post(applyRoute)
        }.onFailure { logSafely(Log.ERROR, "Could not schedule microphone routing: $it") }
    }

    private val applyRoute = ExceptionIsolatingTask(
        action = ::applySavedRoute,
        onFailure = { logSafely(Log.ERROR, "Global microphone routing failed safely: $it") },
    )

    private fun applySavedRoute() {
        val manager = audioManager ?: return
        val routePreferences = preferences ?: return
        val route = RouteStore.readSystemRoute(routePreferences)
        val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.isSource }
        val microphones = runCatching { manager.microphones }.getOrDefault(emptyList())
        val identities = inputs.associateWith { it.toIdentity(microphones) }
        val resolvedIdentity = InputDeviceResolver.resolve(route, identities.values.toList())
        val resolvedDevice = identities.entries.firstOrNull { it.value == resolvedIdentity }?.key

        if (route.enabled && resolvedDevice == null) {
            logSafely(Log.WARN, "Selected microphone is unavailable; using system default until it returns")
        }

        val backend = runCatching { ReflectiveCapturePresetBackend(manager) }
            .onFailure { logSafely(Log.ERROR, "Capture routing API unavailable: $it") }
            .getOrNull()
            ?: return

        val attributes = resolvedDevice?.let { device ->
            runCatching { backend.attributesFor(device) }
                .onFailure { logSafely(Log.ERROR, "Could not describe selected microphone: $it") }
                .getOrNull()
        }
        val report = CapturePresetCoordinator(backend).apply(attributes)
        if (report.failedPresets.isEmpty()) {
            val label = resolvedIdentity?.name ?: "System default"
            logSafely(Log.INFO, "Capture presets routed to $label")
        } else {
            logSafely(Log.WARN, "Capture preset routing failed for ${report.failedPresets.joinToString()}")
        }
    }

    private fun logSafely(priority: Int, message: String) {
        runCatching { logger(priority, message) }
    }

    private fun AudioDeviceInfo.toIdentity(microphones: List<MicrophoneInfo>): InputDeviceIdentity {
        val microphone = microphones.firstOrNull { it.id == id }
            ?: microphones.firstOrNull { it.type == type && it.address == address }
        return InputDeviceIdentity(
            type = type,
            address = address,
            id = id,
            microphoneDescription = microphone?.description?.toString().orEmpty(),
            microphoneGroup = microphone?.group ?: -1,
            microphoneIndex = microphone?.indexInTheGroup ?: -1,
            name = productName?.toString().orEmpty().ifBlank { "Input $id" },
        )
    }
}

private class ReflectiveCapturePresetBackend(
    private val audioManager: AudioManager,
) : CapturePresetBackend<Any> {
    private val attributesClass = Class.forName("android.media.AudioDeviceAttributes")
    private val attributesConstructor = attributesClass.getDeclaredConstructor(AudioDeviceInfo::class.java)
        .also { it.isAccessible = true }
    private val preferMethod: Method = AudioManager::class.java.getDeclaredMethod(
        "setPreferredDeviceForCapturePreset",
        Integer.TYPE,
        attributesClass,
    ).also { it.isAccessible = true }
    private val clearMethod: Method = AudioManager::class.java.getDeclaredMethod(
        "clearPreferredDevicesForCapturePreset",
        Integer.TYPE,
    ).also { it.isAccessible = true }

    fun attributesFor(device: AudioDeviceInfo): Any = attributesConstructor.newInstance(device)

    override fun prefer(preset: Int, device: Any): Boolean =
        preferMethod.invoke(audioManager, preset, device) as? Boolean ?: false

    override fun clear(preset: Int): Boolean =
        clearMethod.invoke(audioManager, preset) as? Boolean ?: false
}
