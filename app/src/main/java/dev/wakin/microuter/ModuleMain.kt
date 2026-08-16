package dev.wakin.microuter

import android.app.ActivityThread
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import kotlin.math.pow

class ModuleMain : XposedModule() {
    companion object { const val TAG = "MicRouter" }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        val pkg = param.packageName
        val prefs = getRemotePreferences(RouteStore.PREFS)
        fun rule() = RouteStore.read(prefs, pkg)

        runCatching {
            val start = AudioRecord::class.java.getDeclaredMethod("startRecording")
            hook(start).intercept { chain ->
                val r = rule()
                val record = chain.thisObject as AudioRecord
                if (r.enabled) applyDevice(record, r)
                val out = chain.proceed()
                runCatching { log(Log.INFO, TAG, "$pkg routed=${record.routedDevice?.productName} type=${record.routedDevice?.type}") }
                out
            }
        }.onFailure { log(Log.ERROR, TAG, "AudioRecord hook failed for $pkg: $it") }

        runCatching {
            val prepare = MediaRecorder::class.java.getDeclaredMethod("prepare")
            hook(prepare).intercept { chain ->
                val r = rule()
                if (r.enabled) applyDevice(chain.thisObject as MediaRecorder, r)
                chain.proceed()
            }
        }.onFailure { log(Log.ERROR, TAG, "MediaRecorder hook failed for $pkg: $it") }

        AudioRecord::class.java.declaredMethods
            .filter { it.name == "read" && it.parameterTypes.isNotEmpty() && (it.parameterTypes[0] == ShortArray::class.java || it.parameterTypes[0] == FloatArray::class.java || it.parameterTypes[0] == ByteArray::class.java) }
            .forEach { method ->
                runCatching {
                    hook(method).intercept { chain ->
                        val result = chain.proceed()
                        val count = result as? Int ?: return@intercept result
                        val r = rule()
                        if (r.enabled && count > 0 && r.gainDb != 0f) amplify(chain.thisObject as AudioRecord, chain.args, count, r.gainDb)
                        result
                    }
                }
            }
    }

    private fun context(): Context? = runCatching { ActivityThread.currentApplication() }.getOrNull()

    private fun findDevice(rule: RouteRule): AudioDeviceInfo? {
        if (rule.deviceType < 0) return null
        val am = context()?.getSystemService(AudioManager::class.java) ?: return null
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.isSource && it.type == rule.deviceType }
        return inputs.firstOrNull { rule.deviceAddress.isNotBlank() && it.address == rule.deviceAddress } ?: inputs.firstOrNull()
    }

    private fun applyDevice(record: AudioRecord, rule: RouteRule) {
        if (rule.deviceType < 0) return
        val d = findDevice(rule)
        if (d == null) log(Log.WARN, TAG, "No matching input for ${rule.packageName}: ${rule.deviceName}")
        else log(Log.INFO, TAG, "AudioRecord preferred=${d.productName} accepted=${record.setPreferredDevice(d)}")
    }

    private fun applyDevice(recorder: MediaRecorder, rule: RouteRule) {
        if (rule.deviceType < 0) return
        val d = findDevice(rule)
        if (d == null) log(Log.WARN, TAG, "No matching input for ${rule.packageName}: ${rule.deviceName}")
        else log(Log.INFO, TAG, "MediaRecorder preferred=${d.productName} accepted=${recorder.setPreferredDevice(d)}")
    }

    private fun amplify(record: AudioRecord, args: Array<Any?>, count: Int, db: Float) {
        val gain = 10.0.pow(db / 20.0).toFloat()
        val offset = (args.getOrNull(1) as? Int ?: 0).coerceAtLeast(0)
        when (val buffer = args[0]) {
            is ShortArray -> {
                val end = (offset + count).coerceAtMost(buffer.size)
                for (i in offset until end) buffer[i] = (buffer[i] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            is FloatArray -> {
                val end = (offset + count).coerceAtMost(buffer.size)
                for (i in offset until end) buffer[i] = (buffer[i] * gain).coerceIn(-1f, 1f)
            }
            is ByteArray -> if (record.audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                val end = (offset + count).coerceAtMost(buffer.size) - 1
                var i = offset
                while (i < end) {
                    val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xff)).toShort().toInt()
                    val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    buffer[i] = (scaled and 0xff).toByte(); buffer[i + 1] = ((scaled shr 8) and 0xff).toByte(); i += 2
                }
            }
        }
    }
}
