package dev.wakin.microuter

import android.content.SharedPreferences
import org.json.JSONObject

@Deprecated("Temporary compatibility model; remove with the per-app UI")
data class RouteRule(
    val packageName: String,
    val enabled: Boolean = true,
    val deviceType: Int = -1,
    val deviceAddress: String = "",
    val deviceIdHint: Int = -1,
    val microphoneDescription: String = "",
    val microphoneGroup: Int = -1,
    val microphoneIndex: Int = -1,
    val deviceName: String = "System default",
    val gainDb: Float = 0f,
) {
    fun toJson(): String = JSONObject()
        .put("packageName", packageName)
        .put("enabled", enabled)
        .put("deviceType", deviceType)
        .put("deviceAddress", deviceAddress)
        .put("deviceIdHint", deviceIdHint)
        .put("microphoneDescription", microphoneDescription)
        .put("microphoneGroup", microphoneGroup)
        .put("microphoneIndex", microphoneIndex)
        .put("deviceName", deviceName)
        .put("gainDb", gainDb.toDouble())
        .toString()

    companion object {
        fun fromJson(packageName: String, raw: String?): RouteRule {
            if (raw.isNullOrBlank()) return defaultFor(packageName)
            return runCatching {
                val json = JSONObject(raw)
                RouteRule(
                    packageName = packageName,
                    enabled = json.optBoolean("enabled", true),
                    deviceType = json.optInt("deviceType", -1),
                    deviceAddress = json.optString("deviceAddress", ""),
                    deviceIdHint = json.optInt("deviceIdHint", -1),
                    microphoneDescription = json.optString("microphoneDescription", ""),
                    microphoneGroup = json.optInt("microphoneGroup", -1),
                    microphoneIndex = json.optInt("microphoneIndex", -1),
                    deviceName = json.optString("deviceName", "System default"),
                    gainDb = json.optDouble("gainDb", 0.0).toFloat().coerceIn(-12f, 24f),
                )
            }.getOrElse { defaultFor(packageName) }
        }

        fun defaultFor(packageName: String): RouteRule = RouteRule(packageName)
    }
}

object RouteStore {
    const val PREFS = "routes"
    const val GLOBAL_KEY = "global_rule"
    const val GLOBAL_PACKAGE = "__global__"
    private const val PREFIX = "rule:"

    fun readSystemRoute(prefs: SharedPreferences): SystemRoute =
        SystemRoute.fromJson(prefs.getString(GLOBAL_KEY, null))

    fun writeSystemRoute(prefs: SharedPreferences, route: SystemRoute) {
        prefs.edit().putString(GLOBAL_KEY, route.toJson()).apply()
    }

    @Deprecated("Temporary compatibility API; remove with the per-app UI")
    fun read(prefs: SharedPreferences, packageName: String): RouteRule =
        RouteRule.fromJson(packageName, prefs.getString(PREFIX + packageName, null))

    @Deprecated("Temporary compatibility API; remove with the per-app UI")
    fun hasRule(prefs: SharedPreferences, packageName: String): Boolean =
        prefs.contains(PREFIX + packageName)

    @Deprecated("Temporary compatibility API; remove with the per-app UI")
    fun write(prefs: SharedPreferences, rule: RouteRule) {
        prefs.edit().putString(PREFIX + rule.packageName, rule.toJson()).apply()
    }

    @Deprecated("Temporary compatibility API; remove with the per-app UI")
    fun readGlobal(prefs: SharedPreferences): RouteRule =
        RouteRule.fromJson(GLOBAL_PACKAGE, prefs.getString(GLOBAL_KEY, null))
            .copy(packageName = GLOBAL_PACKAGE)

    @Deprecated("Temporary compatibility API; remove with the per-app UI")
    fun writeGlobal(prefs: SharedPreferences, rule: RouteRule) {
        prefs.edit().putString(GLOBAL_KEY, rule.copy(packageName = GLOBAL_PACKAGE).toJson()).apply()
    }

    @Deprecated("Temporary compatibility API; remove with the per-app UI")
    fun readEffective(prefs: SharedPreferences, packageName: String): RouteRule {
        if (hasRule(prefs, packageName)) return read(prefs, packageName)
        val global = readGlobal(prefs)
        return if (global.enabled) {
            global.copy(packageName = packageName)
        } else {
            RouteRule.defaultFor(packageName)
        }
    }
}
