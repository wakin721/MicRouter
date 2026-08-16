package dev.wakin.microuter

import android.content.SharedPreferences
import org.json.JSONObject

data class RouteRule(
    val packageName: String,
    val enabled: Boolean = true,
    val deviceType: Int = -1,
    val deviceAddress: String = "",
    val deviceName: String = "System default",
    val gainDb: Float = 0f,
) {
    fun toJson(): String = JSONObject()
        .put("packageName", packageName)
        .put("enabled", enabled)
        .put("deviceType", deviceType)
        .put("deviceAddress", deviceAddress)
        .put("deviceName", deviceName)
        .put("gainDb", gainDb.toDouble())
        .toString()

    companion object {
        fun fromJson(packageName: String, raw: String?): RouteRule {
            if (raw.isNullOrBlank()) return defaultFor(packageName)
            return runCatching {
                val j = JSONObject(raw)
                RouteRule(
                    packageName = packageName,
                    enabled = j.optBoolean("enabled", true),
                    deviceType = j.optInt("deviceType", -1),
                    deviceAddress = j.optString("deviceAddress", ""),
                    deviceName = j.optString("deviceName", "System default"),
                    gainDb = j.optDouble("gainDb", 0.0).toFloat().coerceIn(-12f, 24f),
                )
            }.getOrElse { defaultFor(packageName) }
        }

        fun defaultFor(packageName: String): RouteRule = when (packageName) {
            "com.tencent.mm" -> RouteRule(packageName, deviceType = 11, deviceName = "USB microphone")
            "com.discord" -> RouteRule(packageName, deviceType = 15, deviceName = "Built-in microphone")
            else -> RouteRule(packageName)
        }
    }
}

object RouteStore {
    const val PREFS = "routes"
    private const val PREFIX = "rule:"

    fun read(prefs: SharedPreferences, packageName: String): RouteRule =
        RouteRule.fromJson(packageName, prefs.getString(PREFIX + packageName, null))

    fun write(prefs: SharedPreferences, rule: RouteRule) {
        prefs.edit().putString(PREFIX + rule.packageName, rule.toJson()).apply()
    }

    fun configuredPackages(prefs: SharedPreferences): List<String> = prefs.all.keys
        .asSequence()
        .filter { it.startsWith(PREFIX) }
        .map { it.removePrefix(PREFIX) }
        .toList()
}
