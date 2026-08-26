package dev.wakin.microuter

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MicrophoneInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method

class SystemMicrophoneRouter(
    private val logger: (priority: Int, message: String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var audioManager: AudioManager? = null
    private var preferences: SharedPreferences? = null

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
            logger(Log.WARN, "Global microphone routing requires Android 11 or newer")
            return
        }

        val manager = context.getSystemService(AudioManager::class.java)
        if (manager == null) {
            logger(Log.ERROR, "AudioManager unavailable; global routing was not started")
            return
        }

        audioManager = manager
        preferences = routePreferences
        routePreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        manager.registerAudioDeviceCallback(deviceCallback, handler)
        started = true
        scheduleApply()
        logger(Log.INFO, "Global microphone routing started in system_server")
    }

    private fun scheduleApply() {
        handler.removeCallbacks(applyRoute)
        handler.post(applyRoute)
    }

    private val applyRoute = Runnable {
        val manager = audioManager ?: return@Runnable
        val routePreferences = preferences ?: return@Runnable
        val route = RouteStore.readSystemRoute(routePreferences)
        val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.isSource }
        val microphones = runCatching { manager.microphones }.getOrDefault(emptyList())
        val identities = inputs.associateWith { it.toIdentity(microphones) }
        val resolvedIdentity = InputDeviceResolver.resolve(route, identities.values)
        val resolvedDevice = identities.entries.firstOrNull { it.value == resolvedIdentity }?.key

        if (route.enabled && resolvedDevice == null) {
            logger(Log.WARN, "Selected microphone is unavailable; using system default until it returns")
        }

        val backend = runCatching { ReflectiveCapturePresetBackend(manager) }
            .onFailure { logger(Log.ERROR, "Capture routing API unavailable: $it") }
            .getOrNull()
            ?: return@Runnable

        val attributes = resolvedDevice?.let { device ->
            runCatching { backend.attributesFor(device) }
                .onFailure { logger(Log.ERROR, "Could not describe selected microphone: $it") }
                .getOrNull()
        }
        val report = CapturePresetCoordinator(backend).apply(attributes)
        if (report.failedPresets.isEmpty()) {
            val label = resolvedIdentity?.name ?: "System default"
            logger(Log.INFO, "Capture presets routed to $label")
        } else {
            logger(Log.WARN, "Capture preset routing failed for ${report.failedPresets.joinToString()}")
        }
    }

    private fun AudioDeviceInfo.toIdentity(microphones: List<MicrophoneInfo>): InputDeviceIdentity {
        val microphone = microphones.firstOrNull { it.id == id.toString() }
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
