# System Microphone Routing Design

## Objective

MicRouter will select one preferred microphone for the whole Android system without loading recording hooks into individual application processes. The launch page will contain only the global microphone selector, while the existing About page, language selection, appearance mode, theme color, and LSPosed connection status remain available.

## User Experience

- Rename the existing app-management destination to a microphone destination.
- Remove the installed-application query, search field, application cards, per-application editor, and per-application scope requests.
- Show the currently selected input and all connected physical input devices directly on the launch page.
- Keep a `System default` choice. Selecting it clears MicRouter's capture-preset preferences.
- Save and apply a device immediately when its choice is selected; no secondary route dialog is required.
- Disable selection and show the existing LSPosed guidance when the module service is unavailable.
- Preserve the About destination and its language, dynamic color, appearance, and manual theme-color controls.
- Remove the software input-gain control and its explanatory text because buffer amplification required application-process hooks.
- Replace the automatic per-application scope option with guidance that MicRouter uses the fixed `system` scope.

## Configuration Model

The persisted route is a single global value containing:

- enabled state;
- public `AudioDeviceInfo` type;
- device address;
- device-ID hint for the current boot;
- microphone description/group/index as fallback identity metadata;
- user-visible device name.

There are no package names, per-package keys, configured-package enumeration, or gain values. The existing `global_rule` value is read compatibly so a user's prior global microphone selection can be retained; obsolete package-rule keys are ignored and no longer displayed or written.

Device resolution prefers the stable address, then microphone group/index metadata where available, then the boot-local device ID, and finally the first connected input with the selected device type. `System default` is represented by a disabled route or device type `-1`.

## System Routing Architecture

The libxposed module loads only in `system_server` through `onSystemServerStarting` and the static `system` scope. It no longer hooks `AudioRecord`, `MediaRecorder`, or PCM `read` methods.

The module hooks Android audio-service initialization to capture its system `Context`. After that service is ready, the module obtains the system audio manager and applies the selected device through Android's hidden capture-preset routing API. Reflection is isolated behind a `SystemMicrophoneRouter` component so Android-version and vendor differences do not leak into configuration or UI code.

The preferred device is applied to these ordinary microphone capture presets:

- `DEFAULT`;
- `MIC`;
- `CAMCORDER`;
- `VOICE_RECOGNITION`;
- `VOICE_COMMUNICATION`;
- `UNPROCESSED`;
- `VOICE_PERFORMANCE` when present.

Call-only, hotword, remote-submix, echo-reference, radio, and ultrasound sources are not modified. This avoids interfering with telephony, assistants, playback capture, or vendor-only capture paths.

The router performs one of two operations for every supported preset:

- selected physical input: call `setPreferredDeviceForCapturePreset` using an `AudioDeviceAttributes` created from the resolved input device;
- system default: call `clearPreferredDevicesForCapturePreset`.

Calls execute from `system_server`, where the system identity satisfies `MODIFY_AUDIO_ROUTING`. The module logs individual preset failures and continues applying the remaining presets so one unsupported source does not prevent normal microphone routing.

## Applying Configuration Changes

The module reads the same libxposed remote preferences used by the application and keeps a strong reference to a shared-preference change listener. A change to the global route schedules a new apply operation after the audio service is ready. It also registers an `AudioDeviceCallback` from the captured system context and reapplies the saved route when input devices are added or removed.

The route is also reapplied when the system audio service initializes. Connected-device changes are handled by resolving the stored identity again before each apply. If the selected device is absent, the saved choice remains intact and routing falls back to Android's default until a later reapply finds the device.

The application UI writes only configuration; it never calls hidden audio APIs and does not request privileged Android permissions.

## Platform Support and Failure Handling

- Keep the existing application `minSdk` so the APK remains installable on Android 9 and later.
- System-wide capture-preset routing is supported on Android 11 and later, where the required capture-preset APIs exist. On Android 9/10 the launch page explains that Android 11 or later is required and disables device selection rather than restoring per-application hooks.
- On a newer vendor build where reflective API discovery still fails, log the exact missing class/method and keep Android's default route; never crash `system_server`.
- Hidden API discovery and invocation failures must not crash `system_server`.
- A missing or disconnected input must not clear the saved selection.
- `System default` must clear every preset previously changed by MicRouter.
- The selected device remains a preference; Android communication policy or vendor audio policy can still override the final active route.

## Scope and Metadata

The static Xposed scope contains only `system`. UI copy and README instructions tell users to enable MicRouter and reboot; no application scope selection is needed. No installed-app query or `QUERY_ALL_PACKAGES` permission remains.

## Testing

Automated JVM tests cover configuration serialization/migration, device-identity matching, capture-preset selection, and apply-result aggregation through an injectable routing facade. UI/build verification confirms that application-list and gain symbols are absent and that the Compose application compiles.

Manual rooted-device verification covers:

1. enable the module with its fixed `system` scope and reboot;
2. select each connected input and start recording from Java and native-backed applications;
3. confirm the routed input through Android recording diagnostics and module logs;
4. select System default and confirm MicRouter preferences are cleared;
5. disconnect and reconnect an external input and confirm safe fallback/reapplication;
6. verify language, appearance, theme, About, and LSPosed status behavior remains unchanged.

## Out of Scope

- Per-application microphone selection;
- software or hardware input gain;
- forcing one physical capsule when several built-in microphones share one Android input device;
- native `audioserver`, Audio HAL, or vendor mixer patching;
- overriding telephony or hotword routing.
