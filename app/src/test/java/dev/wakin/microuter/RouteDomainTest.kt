package dev.wakin.microuter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDomainTest {
    @Test
    fun legacyGlobalRuleRetainsDeviceAndDropsPerAppFields() {
        val route = SystemRoute.fromJson(
            """{"packageName":"__global__","enabled":true,"deviceType":15,"deviceAddress":"usb:2","deviceIdHint":7,"microphoneDescription":"USB","microphoneGroup":3,"microphoneIndex":1,"deviceName":"USB microphone","gainDb":12}"""
        )

        assertTrue(route.enabled)
        assertEquals(15, route.deviceType)
        assertEquals("usb:2", route.deviceAddress)
        assertEquals("USB", route.microphoneDescription)
        assertEquals(3, route.microphoneGroup)
        assertEquals(1, route.microphoneIndex)
        assertFalse(route.toJson().contains("packageName"))
        assertFalse(route.toJson().contains("gainDb"))
    }

    @Test
    fun resolverUsesAddressBeforeLessStableIdentityFields() {
        val devices = listOf(
            device(address = "usb:first", id = 4, group = 3, index = 0, name = "first"),
            device(address = "usb:wanted", id = 7, group = 3, index = 1, name = "wanted"),
        )
        val route = route(address = "usb:wanted", id = 4, group = 3, index = 0)

        assertEquals("wanted", InputDeviceResolver.resolve(route, devices)?.name)
    }

    @Test
    fun resolverFallsBackThroughMicrophoneIdentityIdAndType() {
        val devices = listOf(
            device(address = "first", id = 4, group = 3, index = 0, name = "group"),
            device(address = "second", id = 7, group = 5, index = 1, name = "id"),
            device(address = "third", id = 9, group = 6, index = 2, name = "type"),
        )

        assertEquals(
            "group",
            InputDeviceResolver.resolve(
                route(address = "missing", id = 7, group = 3, index = 0),
                devices,
            )?.name,
        )
        assertEquals(
            "id",
            InputDeviceResolver.resolve(
                route(address = "missing", id = 7, group = 8, index = 8),
                devices,
            )?.name,
        )
        assertEquals(
            "type",
            InputDeviceResolver.resolve(
                route(address = "missing", id = 11, group = 8, index = 8),
                listOf(devices.last()),
            )?.name,
        )
    }

    @Test
    fun resolverRejectsDisabledAndWrongTypeRoutes() {
        val input = device(address = "same", id = 1, group = 1, index = 1, name = "input")

        assertNull(InputDeviceResolver.resolve(route(enabled = false), listOf(input)))
        assertNull(InputDeviceResolver.resolve(route(type = 7, address = "same"), listOf(input)))
    }

    private fun route(
        enabled: Boolean = true,
        type: Int = 15,
        address: String = "",
        id: Int = -1,
        group: Int = -1,
        index: Int = -1,
    ) = SystemRoute(
        enabled = enabled,
        deviceType = type,
        deviceAddress = address,
        deviceIdHint = id,
        microphoneGroup = group,
        microphoneIndex = index,
        deviceName = "selected",
    )

    private fun device(
        address: String,
        id: Int,
        group: Int,
        index: Int,
        name: String,
    ) = InputDeviceIdentity(
        type = 15,
        address = address,
        id = id,
        microphoneGroup = group,
        microphoneIndex = index,
        name = name,
    )
}
