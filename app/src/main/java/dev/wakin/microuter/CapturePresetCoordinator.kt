package dev.wakin.microuter

object CapturePresets {
    val STANDARD = intArrayOf(0, 1, 5, 6, 7, 9, 10)
}

interface CapturePresetBackend<T> {
    fun prefer(preset: Int, device: T): Boolean
    fun clear(preset: Int): Boolean
}

data class CapturePresetApplyReport(
    val successfulPresets: List<Int>,
    val failedPresets: List<Int>,
)

class ExceptionIsolatingTask(
    private val action: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) : Runnable {
    override fun run() {
        try {
            action()
        } catch (failure: Throwable) {
            runCatching { onFailure(failure) }
        }
    }
}

class CapturePresetCoordinator<T>(
    private val backend: CapturePresetBackend<T>,
) {
    fun apply(device: T?): CapturePresetApplyReport {
        val successful = mutableListOf<Int>()
        val failed = mutableListOf<Int>()
        CapturePresets.STANDARD.forEach { preset ->
            val accepted = runCatching {
                if (device == null) {
                    backend.clear(preset)
                } else {
                    backend.prefer(preset, device)
                }
            }.getOrDefault(false)
            if (accepted) successful += preset else failed += preset
        }
        return CapturePresetApplyReport(successful, failed)
    }
}
