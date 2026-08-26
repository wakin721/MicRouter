# MicRouter

MicRouter is an LSPosed/libxposed module that selects one preferred microphone for standard Android recording sources system-wide. It hooks `AudioService` in `system_server`, so applications do not need to be selected or hooked individually.

## Requirements

- Android 11 or newer for global microphone routing (the APK remains installable on Android 9/10 and shows an unsupported-version notice)
- Root access
- A modern LSPosed/libxposed-compatible framework

## Usage

1. Install MicRouter.
2. Enable the module in LSPosed. Its static scope is `system`; there is no application scope list to configure.
3. Reboot the device after installing or updating the module.
4. Open MicRouter and select `System default` or one of the connected physical microphones.

The launch page contains only the microphone selector. The About page retains LSPosed connection status, language, appearance, dynamic color, and theme-color controls.

MicRouter applies the selected device to Android's standard capture presets and reapplies it when the setting or connected input devices change. If a saved device is disconnected, Android's default is used temporarily and the saved selection is retried when devices change. Selecting `System default` clears MicRouter's capture-preset preferences.

Vendor-specific recording paths and communication policy may override Android's standard capture preference. MicRouter does not change telephony, hotword, remote-submix, echo-reference, radio, or ultrasound sources.

## Build

GitHub Actions runs JVM tests, assembles the debug APK, and uploads it as the `MicRouter-debug` artifact.
