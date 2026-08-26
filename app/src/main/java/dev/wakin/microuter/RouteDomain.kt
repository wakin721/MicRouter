package dev.wakin.microuter

import org.json.JSONObject

data class InputDeviceIdentity(
    val type: Int,
    val address: String,
    val id: Int,
    val microphoneDescription: String = "",
    val microphoneGroup: Int = -1,
    val microphoneIndex: Int = -1,
    val name: String,
)

data class SystemRoute(
    val enabled: Boolean = false,
    val deviceType: Int = -1,
    val deviceAddress: String = "",
    val deviceIdHint: Int = -1,
    val microphoneDescription: String = "",
    val microphoneGroup: Int = -1,
    val microphoneIndex: Int = -1,
    val deviceName: String = "System default",
) {
    fun toJson(): String = JSONObject()
        .put("enabled", enabled)
        .put("deviceType", deviceType)
        .put("deviceAddress", deviceAddress)
        .put("deviceIdHint", deviceIdHint)
        .put("microphoneDescription", microphoneDescription)
        .put("microphoneGroup", microphoneGroup)
        .put("microphoneIndex", microphoneIndex)
        .put("deviceName", deviceName)
        .toString()

    companion object {
        fun systemDefault(): SystemRoute = SystemRoute()

        fun fromDevice(device: InputDeviceIdentity): SystemRoute = SystemRoute(
            enabled = true,
            deviceType = device.type,
            deviceAddress = device.address,
            deviceIdHint = device.id,
            microphoneDescription = device.microphoneDescription,
            microphoneGroup = device.microphoneGroup,
            microphoneIndex = device.microphoneIndex,
            deviceName = device.name,
        )

        fun fromJson(raw: String?): SystemRoute {
            if (raw.isNullOrBlank()) return SystemRoute()
            return runCatching {
                val json = JSONObject(raw)
                SystemRoute(
                    enabled = json.optBoolean("enabled", false),
                    deviceType = json.optInt("deviceType", -1),
                    deviceAddress = json.optString("deviceAddress", ""),
                    deviceIdHint = json.optInt("deviceIdHint", -1),
                    microphoneDescription = json.optString("microphoneDescription", ""),
                    microphoneGroup = json.optInt("microphoneGroup", -1),
                    microphoneIndex = json.optInt("microphoneIndex", -1),
                    deviceName = json.optString("deviceName", "System default"),
                )
            }.getOrDefault(SystemRoute())
        }
    }
}

object InputDeviceResolver {
    fun resolve(route: SystemRoute, devices: List<InputDeviceIdentity>): InputDeviceIdentity? {
        if (!route.enabled || route.deviceType < 0) return null
        val matchingType = devices.filter { it.type == route.deviceType }
        return matchingType.firstOrNull {
            route.deviceAddress.isNotBlank() && it.address == route.deviceAddress
        } ?: matchingType.firstOrNull {
            route.microphoneGroup >= 0 &&
                route.microphoneIndex >= 0 &&
                it.microphoneGroup == route.microphoneGroup &&
                it.microphoneIndex == route.microphoneIndex
        } ?: matchingType.firstOrNull {
            route.deviceIdHint >= 0 && it.id == route.deviceIdHint
        } ?: matchingType.firstOrNull()
    }
}
