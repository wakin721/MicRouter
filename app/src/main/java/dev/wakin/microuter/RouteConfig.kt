package dev.wakin.microuter

import android.content.SharedPreferences
import org.json.JSONObject

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
                val j = JSONObject(raw)
                RouteRule(
                    packageName = packageName,
                    enabled = j.optBoolean("enabled", true),
                    deviceType = j.optInt("deviceType", -1),
                    deviceAddress = j.optString("deviceAddress", ""),
                    deviceIdHint = j.optInt("deviceIdHint", -1),
                    microphoneDescription = j.optString("microphoneDescription", ""),
                    microphoneGroup = j.optInt("microphoneGroup", -1),
                    microphoneIndex = j.optInt("microphoneIndex", -1),
                    deviceName = j.optString("deviceName", "System default"),
                    gainDb = j.optDouble("gainDb", 0.0).toFloat().coerceIn(-12f, 24f),
                )
            }.getOrElse { defaultFor(packageName) }
        }

        fun defaultFor(packageName: String): RouteRule = RouteRule(packageName)
    }
}

object RouteStore {
    const val PREFS = "routes"
    const val GLOBAL_PACKAGE = "__global__"
    private const val PREFIX = "rule:"
    private const val GLOBAL_KEY = "global_rule"

    fun read(prefs: SharedPreferences, packageName: String): RouteRule =
        RouteRule.fromJson(packageName, prefs.getString(PREFIX + packageName, null))

    fun hasRule(prefs: SharedPreferences, packageName: String): Boolean =
        prefs.contains(PREFIX + packageName)

    fun write(prefs: SharedPreferences, rule: RouteRule) {
        prefs.edit()?.putString(PREFIX + rule.packageName, rule.toJson())?.apply()
    }

    fun readGlobal(prefs: SharedPreferences): RouteRule {
        val raw = prefs.getString(GLOBAL_KEY, null) ?: return globalDefault()
        return RouteRule.fromJson(GLOBAL_PACKAGE, raw).copy(packageName = GLOBAL_PACKAGE)
    }

    fun writeGlobal(prefs: SharedPreferences, rule: RouteRule) {
        prefs.edit()?.putString(GLOBAL_KEY, rule.copy(packageName = GLOBAL_PACKAGE).toJson())?.apply()
    }

    fun globalDefault(): RouteRule = RouteRule(
        packageName = GLOBAL_PACKAGE,
        enabled = false,
        deviceType = -1,
        deviceName = "System default",
    )

    fun readEffective(prefs: SharedPreferences, packageName: String): RouteRule {
        if (hasRule(prefs, packageName)) return read(prefs, packageName)
        val global = readGlobal(prefs)
        return if (global.enabled) global.copy(packageName = packageName) else RouteRule.defaultFor(packageName)
    }

    fun configuredPackages(prefs: SharedPreferences): List<String> = prefs.all.keys
        .asSequence()
        .filter { it.startsWith(PREFIX) }
        .map { it.removePrefix(PREFIX) }
        .toList()
}
