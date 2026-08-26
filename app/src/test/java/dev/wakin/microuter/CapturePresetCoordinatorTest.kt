package dev.wakin.microuter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CapturePresetCoordinatorTest {
    @Test
    fun selectedDeviceVisitsEveryPresetAndReportsRejectedPreset() {
        val backend = RecordingBackend(rejectedPreset = 7)

        val report = CapturePresetCoordinator(backend).apply("usb")

        assertArrayEquals(CapturePresets.STANDARD, backend.preferred.toIntArray())
        assertEquals(listOf(7), report.failedPresets)
        assertEquals(
            CapturePresets.STANDARD.filterNot { it == 7 },
            report.successfulPresets,
        )
    }

    @Test
    fun systemDefaultClearsEveryPreset() {
        val backend = RecordingBackend()

        val report = CapturePresetCoordinator(backend).apply(null)

        assertArrayEquals(CapturePresets.STANDARD, backend.cleared.toIntArray())
        assertEquals(CapturePresets.STANDARD.toList(), report.successfulPresets)
        assertEquals(emptyList<Int>(), report.failedPresets)
    }

    private class RecordingBackend(
        private val rejectedPreset: Int? = null,
    ) : CapturePresetBackend<String> {
        val preferred = mutableListOf<Int>()
        val cleared = mutableListOf<Int>()

        override fun prefer(preset: Int, device: String): Boolean {
            preferred += preset
            return preset != rejectedPreset
        }

        override fun clear(preset: Int): Boolean {
            cleared += preset
            return preset != rejectedPreset
        }
    }
}
