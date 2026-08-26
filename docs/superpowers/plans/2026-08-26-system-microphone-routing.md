# System Microphone Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-application recording hooks and app management with one system-wide preferred-microphone selector backed by Android capture-preset routing.

**Architecture:** A pure Kotlin route domain owns persistence-compatible device identity, matching, and capture-preset coordination. The libxposed entry point runs only in `system_server`, captures `AudioService` readiness, and delegates reflective hidden-API calls to an Android adapter; the Compose app only writes the shared global route and preserves the existing About/settings experience.

**Tech Stack:** Kotlin 2.2, Android SDK 36/minSdk 28, Jetpack Compose Material 3, libxposed API/service, JUnit 4, `org.json` JVM tests.

**Spec:** `docs/superpowers/specs/2026-08-26-system-microphone-routing-design.md`

## Global Constraints

- The launch page contains only the global microphone selector; About, language, dynamic color, appearance mode, manual theme color, and LSPosed status remain.
- Per-application rules, app queries, app scope requests, and software gain are removed.
- Xposed static scope is exactly `system`; no target application process is hooked.
- System routing modifies only `DEFAULT`, `MIC`, `CAMCORDER`, `VOICE_RECOGNITION`, `VOICE_COMMUNICATION`, `UNPROCESSED`, and `VOICE_PERFORMANCE`.
- The APK remains installable from Android 9 (`minSdk = 28`), but system-wide routing is enabled only on Android 11+.
- Hidden API failures never escape into or crash `system_server`.

---

### Task 1: Replace per-application configuration with a global route domain

**Files:**
- Create: `app/src/main/java/dev/wakin/microuter/RouteDomain.kt`
- Modify: `app/src/main/java/dev/wakin/microuter/RouteConfig.kt`
- Create: `app/src/test/java/dev/wakin/microuter/RouteDomainTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `SystemRoute`, `InputDeviceIdentity`, `InputDeviceResolver.resolve(SystemRoute, List<InputDeviceIdentity>)`, and `RouteStore.readSystemRoute/writeSystemRoute`.
- Preserves: migration from the existing `global_rule` JSON while ignoring `packageName` and `gainDb`.

- [ ] **Step 1: Add JVM test dependencies**

Add to `dependencies`:

```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("org.json:json:20250517")
```

- [ ] **Step 2: Write failing route-domain tests**

Create tests covering legacy decoding and identity priority:

```kotlin
@Test
fun legacyGlobalRuleRetainsSelectedDeviceAndDropsAppFields() {
    val route = SystemRoute.fromJson(
        """{"packageName":"__global__","enabled":true,"deviceType":15,"deviceAddress":"usb:2","deviceIdHint":7,"microphoneGroup":3,"microphoneIndex":1,"deviceName":"USB","gainDb":12}"""
    )

    assertTrue(route.enabled)
    assertEquals(15, route.deviceType)
    assertEquals("usb:2", route.deviceAddress)
    assertFalse(route.toJson().contains("packageName"))
    assertFalse(route.toJson().contains("gainDb"))
}

@Test
fun resolverPrefersAddressThenMicrophoneIdentityThenIdThenType() {
    val devices = listOf(
        InputDeviceIdentity(type = 15, address = "usb:first", id = 4, microphoneGroup = 3, microphoneIndex = 0, name = "first"),
        InputDeviceIdentity(type = 15, address = "usb:wanted", id = 7, microphoneGroup = 3, microphoneIndex = 1, name = "wanted"),
    )
    val route = SystemRoute(
        enabled = true,
        deviceType = 15,
        deviceAddress = "usb:wanted",
        deviceIdHint = 4,
        microphoneGroup = 3,
        microphoneIndex = 0,
        deviceName = "USB",
    )

    assertEquals("wanted", InputDeviceResolver.resolve(route, devices)?.name)
}
```

- [ ] **Step 3: Run the tests and verify RED**

Run: `gradle --no-daemon :app:testDebugUnitTest --tests dev.wakin.microuter.RouteDomainTest`

Expected: compilation fails because `SystemRoute`, `InputDeviceIdentity`, and `InputDeviceResolver` do not exist.

- [ ] **Step 4: Implement the minimal global route domain**

Implement immutable `SystemRoute` and `InputDeviceIdentity` values. `InputDeviceIdentity` has `type`, `address`, `id`, `microphoneDescription`, `microphoneGroup`, `microphoneIndex`, and `name`; identity metadata defaults to empty/`-1`. `SystemRoute.fromJson` must use defaults for missing legacy fields, and `toJson` must emit only the global fields from the design. Implement the resolver with this exact priority: nonblank address, valid group/index pair, nonnegative boot ID, first matching device type.

Replace `RouteStore` with:

```kotlin
object RouteStore {
    const val PREFS = "routes"
    const val GLOBAL_KEY = "global_rule"

    fun readSystemRoute(prefs: SharedPreferences): SystemRoute =
        SystemRoute.fromJson(prefs.getString(GLOBAL_KEY, null))

    fun writeSystemRoute(prefs: SharedPreferences, route: SystemRoute) {
        prefs.edit().putString(GLOBAL_KEY, route.toJson()).apply()
    }
}
```

Delete package-rule accessors, configured-package enumeration, `packageName`, and `gainDb`.

- [ ] **Step 5: Run the focused tests and verify GREEN**

Run: `gradle --no-daemon :app:testDebugUnitTest --tests dev.wakin.microuter.RouteDomainTest`

Expected: all `RouteDomainTest` tests pass.

- [ ] **Step 6: Commit the domain change**

```bash
git add app/build.gradle.kts app/src/main/java/dev/wakin/microuter/RouteConfig.kt app/src/main/java/dev/wakin/microuter/RouteDomain.kt app/src/test/java/dev/wakin/microuter/RouteDomainTest.kt
git commit -m "refactor: replace app routes with system route"
```

---

### Task 2: Add testable capture-preset coordination

**Files:**
- Create: `app/src/main/java/dev/wakin/microuter/CapturePresetCoordinator.kt`
- Create: `app/src/test/java/dev/wakin/microuter/CapturePresetCoordinatorTest.kt`

**Interfaces:**
- Consumes: the resolved target supplied by the Android routing adapter.
- Produces: `CapturePresetBackend<T>`, `CapturePresetCoordinator<T>.apply(T?)`, `CapturePresetApplyReport`, and `CapturePresets.STANDARD`.

- [ ] **Step 1: Write failing coordinator tests**

```kotlin
private class FakeBackend : CapturePresetBackend<String> {
    val preferred = mutableListOf<Int>()
    val cleared = mutableListOf<Int>()
    override fun prefer(preset: Int, device: String): Boolean =
        (preset != 7).also { preferred += preset }
    override fun clear(preset: Int): Boolean = true.also { cleared += preset }
}

@Test
fun selectedDeviceIsAppliedToEveryStandardPresetAndFailuresAreReported() {
    val backend = FakeBackend()
    val report = CapturePresetCoordinator(backend).apply("usb")

    assertArrayEquals(CapturePresets.STANDARD, backend.preferred.toIntArray())
    assertEquals(listOf(7), report.failedPresets)
}

@Test
fun nullDeviceClearsEveryStandardPreset() {
    val backend = FakeBackend()
    CapturePresetCoordinator(backend).apply(null)

    assertArrayEquals(CapturePresets.STANDARD, backend.cleared.toIntArray())
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `gradle --no-daemon :app:testDebugUnitTest --tests dev.wakin.microuter.CapturePresetCoordinatorTest`

Expected: compilation fails because the coordinator types do not exist.

- [ ] **Step 3: Implement minimal coordination**

Define `STANDARD` as `intArrayOf(0, 1, 5, 6, 7, 9, 10)`. Implement a generic backend interface and coordinator that visits all presets and returns successful and failed preset lists. Exception isolation is added under its dedicated failing test in Task 3.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run: `gradle --no-daemon :app:testDebugUnitTest --tests dev.wakin.microuter.CapturePresetCoordinatorTest`

Expected: all coordinator tests pass.

- [ ] **Step 5: Commit the coordinator**

```bash
git add app/src/main/java/dev/wakin/microuter/CapturePresetCoordinator.kt app/src/test/java/dev/wakin/microuter/CapturePresetCoordinatorTest.kt
git commit -m "feat: coordinate capture preset routing"
```

---

### Task 3: Route microphones from system_server only

**Files:**
- Create: `app/src/main/java/dev/wakin/microuter/SystemMicrophoneRouter.kt`
- Modify: `app/src/main/java/dev/wakin/microuter/ModuleMain.kt`
- Modify: `app/src/main/resources/META-INF/xposed/scope.list`

**Interfaces:**
- Consumes: `RouteStore.GLOBAL_KEY`, `SystemRoute`, `InputDeviceResolver`, and `CapturePresetCoordinator<AudioDeviceInfo>`.
- Produces: one process-local system controller that starts after `AudioService.systemReady`, observes route preferences and audio-device changes, and applies/clears hidden capture-preset preferences.

- [ ] **Step 1: Extend coordinator tests for exception isolation**

Add a backend that throws for preset `6`; assert `apply` continues through preset `10` and reports `6` as failed.

- [ ] **Step 2: Run the exception test and verify RED**

Run: `gradle --no-daemon :app:testDebugUnitTest --tests dev.wakin.microuter.CapturePresetCoordinatorTest`

Expected: FAIL until exception isolation is implemented exactly as required.

- [ ] **Step 3: Implement the Android system router**

Create `SystemMicrophoneRouter` with:

```kotlin
fun start(context: Context, prefs: SharedPreferences)
fun applySavedRoute()
```

It must:

- be idempotent;
- keep strong references to `OnSharedPreferenceChangeListener` and `AudioDeviceCallback`;
- post routing work to a `Handler` instead of blocking Xposed callbacks;
- map `AudioDeviceInfo`/`MicrophoneInfo` into `InputDeviceIdentity` and resolve the saved target;
- use reflection for `AudioDeviceAttributes(AudioDeviceInfo)`, `AudioManager.setPreferredDeviceForCapturePreset`, and `AudioManager.clearPreferredDevicesForCapturePreset`;
- refuse hidden routing on SDK < 30;
- log failed preset IDs without throwing.

- [ ] **Step 4: Replace the per-app module entry point**

`ModuleMain.onSystemServerStarting` loads `com.android.server.audio.AudioService` from the system-server class loader, hooks every constructor to capture its `Context` argument, and hooks `systemReady` to call `SystemMicrophoneRouter.start` after `chain.proceed()`.

Delete every `AudioRecord`, `MediaRecorder`, PCM amplification, application-context lookup, and per-package rule path.

- [ ] **Step 5: Restrict static scope**

Set `scope.list` content to exactly:

```text
system
```

- [ ] **Step 6: Run unit tests and compile the module**

Run:

```bash
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:compileDebugKotlin
```

Expected: all tests pass and Kotlin compilation exits 0.

- [ ] **Step 7: Commit the system module**

```bash
git add app/src/main/java/dev/wakin/microuter/CapturePresetCoordinator.kt app/src/main/java/dev/wakin/microuter/SystemMicrophoneRouter.kt app/src/main/java/dev/wakin/microuter/ModuleMain.kt app/src/main/resources/META-INF/xposed/scope.list app/src/test/java/dev/wakin/microuter/CapturePresetCoordinatorTest.kt
git commit -m "feat: route microphones from system server"
```

---

### Task 4: Replace the app list with the global microphone selector

**Files:**
- Modify: `app/src/main/java/dev/wakin/microuter/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `README.md`

**Interfaces:**
- Consumes: `RouteStore.readSystemRoute/writeSystemRoute`, `SystemRoute`, and connected `AudioDeviceInfo` values.
- Produces: `MainPage.Microphone` as the launch destination and preserves `MainPage.About`.

- [ ] **Step 1: Add a route-selection state test**

Extend `RouteDomainTest`:

```kotlin
@Test
fun systemDefaultIsDisabledAndPhysicalDeviceCreatesEnabledRoute() {
    assertFalse(SystemRoute.systemDefault().enabled)
    val selected = SystemRoute.fromDevice(
        InputDeviceIdentity(
            type = 15,
            address = "usb:2",
            id = 7,
            microphoneGroup = 3,
            microphoneIndex = 1,
            name = "USB microphone",
        )
    )
    assertTrue(selected.enabled)
    assertEquals("usb:2", selected.deviceAddress)
}
```

- [ ] **Step 2: Run the selection test and verify RED**

Run: `gradle --no-daemon :app:testDebugUnitTest --tests dev.wakin.microuter.RouteDomainTest`

Expected: compilation fails until `systemDefault` and `fromDevice` exist.

- [ ] **Step 3: Implement the selection factories and verify GREEN**

Add the two factories without adding UI behavior, then rerun the focused test and confirm it passes.

- [ ] **Step 4: Simplify Compose navigation and launch content**

In `MainActivity.kt`:

- replace `Apps` with `Microphone` in `MainPage`;
- remove `AppItem`, installed-app loading, `AppsPage`, `AppIcon`, `RouteDialog`, request-scope helpers, search, app cards, app icons, auto-scope preference, and gain UI/copy;
- add `MicrophonePage` that renders `System default` plus `currentInputs`, persists immediately on selection, and shows Android 11+/LSPosed disabled guidance;
- keep `AboutPage`, theme functions, language, dynamic color, appearance mode, theme color, and LSPosed status;
- replace the About auto-scope switch with fixed `system` scope/reboot instructions.

- [ ] **Step 5: Remove obsolete package visibility and update README**

Delete `QUERY_ALL_PACKAGES` from the manifest. Rewrite README to describe system-wide routing, Android 11+, root/LSPosed, and the fixed `system` scope; remove all per-app/gain claims.

- [ ] **Step 6: Compile and scan for removed behavior**

Run:

```bash
gradle --no-daemon :app:compileDebugKotlin
rg -n "AppItem|AppsPage|installedApps|QUERY_ALL_PACKAGES|gainDb|AudioRecord|MediaRecorder|requestAppScope|configuredPackages|rule:" app README.md
```

Expected: compilation exits 0 and `rg` returns no matches for removed behavior.

- [ ] **Step 7: Commit the UI and metadata change**

```bash
git add app/src/main/java/dev/wakin/microuter/MainActivity.kt app/src/main/AndroidManifest.xml README.md app/src/main/java/dev/wakin/microuter/RouteDomain.kt app/src/test/java/dev/wakin/microuter/RouteDomainTest.kt
git commit -m "feat: show global microphone selector"
```

---

### Task 5: Full verification and handoff

**Files:**
- Verify: all changed project files

**Interfaces:**
- Produces: a tested debug APK and a precise rooted-device validation checklist.

- [ ] **Step 1: Run all unit tests**

Run: `gradle --no-daemon :app:testDebugUnitTest`

Expected: exit 0 with zero failed tests.

- [ ] **Step 2: Build the debug APK**

Run: `gradle --no-daemon :app:assembleDebug`

Expected: exit 0 and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Run repository checks**

Run:

```bash
git diff --check
rg -n "AppItem|AppsPage|installedApps|QUERY_ALL_PACKAGES|gainDb|AudioRecord|MediaRecorder|requestAppScope|configuredPackages|rule:" app README.md
Get-Content app/src/main/resources/META-INF/xposed/scope.list
git status --short
```

Expected: no whitespace errors, removed-symbol scan has no matches, scope output is only `system`, and status contains only intended changes (or is clean after task commits).

- [ ] **Step 4: Document device-only verification**

Report that hardware verification still requires installing the APK on an Android 11+ rooted device, enabling the module with its fixed `system` scope, rebooting, and checking Java plus native-backed recorders. Do not claim actual routed-device behavior from JVM/build evidence alone.
