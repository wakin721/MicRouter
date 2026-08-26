package dev.wakin.microuter

enum class ThemePalette(val storageKey: String) {
    Green("green"),
    Teal("teal"),
    Cyan("cyan"),
    Blue("blue"),
    Indigo("indigo"),
    Purple("purple"),
    Lavender("lavender"),
    Rose("rose"),
    Orange("orange"),
    Sand("sand"),
    Lime("lime");

    companion object {
        fun fromStoredValue(value: String?): ThemePalette =
            entries.firstOrNull { it.storageKey.equals(value, ignoreCase = true) } ?: Blue
    }
}

object ThemeSettingsPolicy {
    private const val ANDROID_12_SDK = 31

    fun dynamicColorActive(requested: Boolean, sdkInt: Int): Boolean =
        requested && sdkInt >= ANDROID_12_SDK

    fun paletteSelectionEnabled(dynamicRequested: Boolean, sdkInt: Int): Boolean =
        !dynamicColorActive(dynamicRequested, sdkInt)
}
