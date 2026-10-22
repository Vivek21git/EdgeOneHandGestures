# Edge One-Hand Gestures

An Android app that recreates the core of Samsung Good Lock's *One Hand Operation+* on stock-style Android:
thin touch strips on the left and right screen edges, each with five swipe gestures mapped to system actions.

Kotlin, Jetpack Compose (Material 3), Gradle Kotlin DSL, Jetpack DataStore. Min SDK 29 (Android 10), target/compile SDK 36.
No root, no hidden APIs, no internet permission, no analytics.

## Honest status: what is and is not verified

| Item | Status |
|---|---|
| Gesture recogniser, zone geometry, action registry, settings codec (`core/` package) | **Compiled and tested here.** 36 unit tests pass. They were run with the Kotlin 2.0.21 compiler bundled in Gradle and a minimal stand-in for JUnit's `@Test` / `Assert`, because Maven was unreachable. |
| DataStore persistence test (`SettingsRepositoryTest`) | Written, **not run** (needs DataStore, not downloadable here). |
| Android code: service, overlay, Compose UI, manifest, resources | Written and reviewed by hand, **never compiled**. Expect to fix a few compile errors on the first build. |
| Gradle build, **APK** | **Not built in this environment.** There is no Android SDK installed, and the Gradle wrapper download failed because `services.gradle.org` could not be resolved. Dependency resolution and the Android compilation still need to be verified by CI. |
| Behaviour on a real phone, including the Motorola Edge 70 Fusion | **Untested.** See "Known risks". |

So there is no APK in this delivery. The two ways to get one are below.

## Build the APK

### Option A: Android Studio (easiest)
1. Install Android Studio (current stable) and let it install the SDK Platform 36 and build-tools when prompted.
2. *File → Open* the `EdgeOneHandGestures` folder and wait for Gradle sync.
3. If sync reports a missing dependency or plugin version, edit the version in `gradle/libs.versions.toml` (one place) and sync again.
4. *Build → Build APK(s)*. The file is `app/build/outputs/apk/debug/app-debug.apk`.

### Option B: command line
Requires JDK 17+ and an Android SDK (`ANDROID_HOME` set, or `sdk.dir` in `local.properties`, with platform 36 and build-tools installed; licences accepted via `sdkmanager --licenses`).

```bash
cd EdgeOneHandGestures
./gradlew testDebugUnitTest      # run unit tests
./gradlew assembleDebug          # build the APK
# -> app/build/outputs/apk/debug/app-debug.apk
```

### Option C: GitHub Actions (recommended for this project)
The workflow at `.github/workflows/build.yml` installs JDK 17, Android SDK Platform 36 and Build Tools 36.0.0, runs the unit tests, builds the debug APK, and uploads it as a workflow artifact.

1. Create a GitHub repository. Upload the **contents** of the `EdgeOneHandGestures` folder (so `gradlew`, `settings.gradle.kts`, `app/` and `.github/` are at the repository root), then commit them to `main`.
2. Open the repository's **Actions** tab and select **Build debug APK**. If it does not start automatically after the push, choose **Run workflow**.
3. Open the completed run and download the artifact named `edge-one-hand-gestures-debug-apk`.
4. Extract the artifact; it contains `app-debug.apk`. If the workflow fails, open the failed step's log first. The test report is uploaded separately when available.

GitHub Actions has not been run from this conversation, so the workflow and APK are not yet verified. The artifact is only available after a successful workflow run.

## Install on the phone
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
or copy the APK to the phone and open it (allow "Install unknown apps" for your file manager or browser). It is debug-signed, which is fine for personal use.

## First-time setup (also shown in the app's Setup screen)
1. **Allow restricted settings.** Because the APK is sideloaded, Android 13+ greys out the accessibility switch. *Settings → Apps → Edge One-Hand Gestures → ⋮ → Allow restricted settings.* If the item is missing, try enabling the service once (it is refused), then look again.
2. **Enable the accessibility service:** *Settings → Accessibility → Edge One-Hand Gestures*. The app never does this for you.
3. **Battery:** App info → Battery → *Unrestricted*, so the system does not stop the service.
4. Open the app, turn on **Show strips on screen**, and use the **Gesture test area** to tune the zones.

"Display over other apps" is **not** needed (see architecture).

## Default gestures (editable per edge)
| Gesture | Default |
|---|---|
| Swipe inward (either edge) | Back |
| Swipe up | Lock screen |
| Swipe down | Recent apps |
| Short diagonal up (inward + up) | Home |
| Short diagonal down (inward + down) | Notifications |

Other actions available: Quick settings, Screenshot, Split screen, Launch app, Launch shortcut, Open this app's settings, No action.
Every gesture can be turned off individually; each edge has its own width, vertical range, sensitivity, minimum distance, haptics and animation toggles.

## How gesture recognition works
A touch that begins inside a strip is tracked until release, then classified on the angle of its straight-line displacement, normalised so "inward" always means toward the screen centre:

* 0° ± tolerance → **inward**; 90° ± tolerance → **up**; −90° ± tolerance → **down**
* between inward and up/down → **diagonal up / down**; pointing toward the screen edge → rejected
* default tolerance 25° (so "up" needs vertical movement roughly 2× the horizontal); the sensitivity slider moves it between 18° and 32° and scales the required distance between 1.5× and 0.5× of the minimum-distance setting
* also rejected: shorter than the minimum distance, slower than 700 ms, path not straight (displacement < 60% of path length), or a second finger touching

"Short diagonal" is implemented as the diagonal angle sector with the same minimum distance. There is no maximum-length cap.

## Architecture decision (what I investigated)
Packages: `core/` (pure Kotlin: models, classifier, geometry, registry, settings codec), `data/` (DataStore repository, in-memory runtime/diagnostics state), `service/` (accessibility service, overlay manager, strip view, action executor), `ui/` (Compose screens).

**Strips as `TYPE_ACCESSIBILITY_OVERLAY` windows owned by the accessibility service**, rather than `TYPE_APPLICATION_OVERLAY`:
* An accessibility service is already required for `performGlobalAction()` (Back, Home, Recents, Lock, Notifications, Quick Settings, Screenshot, Split screen), so a second privileged permission buys nothing.
* Accessibility overlays need no "Display over other apps" permission and no foreground-service notification; Android keeps an enabled accessibility service alive. (`TYPE_APPLICATION_OVERLAY` would add the overlay permission, and in practice a foreground service, for no benefit.)
* The accessibility service does **not** receive raw touches from other apps, and this app does not pretend it does. Touches come only from the narrow strip windows it creates itself. The service subscribes to no accessibility events and cannot read screen content.
* Window flags: `FLAG_NOT_FOCUSABLE` (never takes keyboard focus), `FLAG_NOT_TOUCH_MODAL` (touches outside the strip reach the app below), layout-in-screen/no-limits and short-edge cut-out mode so the strip sits flush against the physical edge. Strips are added/updated/removed idempotently and rebuilt on rotation/display change.
* Samsung's feature is implemented inside the system, so exact parity is impossible with public APIs.

## Known risks and limitations (read these)
1. **Conflict with the system Back gesture (unverified).** Android's gesture navigation uses the same edges. Apps can ask the system not to start Back inside specific rectangles (`setSystemGestureExclusionRects`, API 29; confirmed in the [Android gesture-navigation guide](https://developer.android.com/training/gestures/gesturenav)), and each strip requests that for itself. The guide I fetched does not state the per-edge size limit or whether the system honours it for overlay windows, and I have not tested either. As far as I know, the system caps exclusion at a limited height per edge. Expect that inward swipes may be taken by the system Back gesture (the strip then sees a cancelled touch, logged in Diagnostics), or that both may fire. Mitigations: lower system Back sensitivity, use a narrower strip, or use 3-button navigation. **Vertical and diagonal swipes should conflict less because the system Back gesture needs inward movement, but that is also untested.**
2. **Blocked screen strip.** Touches starting in a strip belong to it, so the app underneath loses that thin slice. Defaults are 16 dp wide and cover 25–80% of the height.
3. **Restricted settings.** Android 13+ blocks accessibility access for sideloaded apps until you allow it manually ([Android Authority on the restriction](https://www.androidauthority.com/android-13-sideloading-apps-restrictions-3161162)). The "Allow restricted settings" steps in this README come from general knowledge, not from that article, and the wording may differ on Motorola's software.
4. **Lock screen** uses `GLOBAL_ACTION_LOCK_SCREEN`, which locks the device (like a power-button press); it is not a shutdown and needs no device-admin rights. After locking, your normal unlock method is required; whether fingerprint works immediately is decided by the phone. Not tested on the Edge 70 Fusion.
5. **Launching apps / shortcuts from the background** can be blocked by Android's background-activity-start rules; failures appear in Diagnostics. Only apps that still offer an old-style shortcut creator appear under "Launch shortcut".
6. **Screenshot / Split screen** depend on the foreground app allowing them.
7. **Motorola battery management:** I found no reliable, specific documentation about Edge 70 Fusion background limits. Setting battery usage to Unrestricted is the standard precaution; if strips disappear after idle time, that is the first suspect.
8. **Dependency resolution** has not been verified in this environment. Versions are centralized in `gradle/libs.versions.toml`; if CI reports an unavailable artifact, check the exact failed coordinate before changing its version.
9. **Accessibility policy.** The service is declared `isAccessibilityTool="false"` and described honestly as a system-gesture utility. That is the honest declaration but it matters if you ever want to publish it on Google Play (their accessibility policy is strict); for personal sideloading it is not an issue.

## Privacy
No internet permission; no analytics; settings are stored locally in DataStore; the diagnostics log is in memory only (last 60 events, no screen content). `allowBackup` is off. Permissions declared: none. The only package visibility query is the standard launcher intent, used for the app picker.

## Troubleshooting
| Symptom | Likely cause / fix |
|---|---|
| Accessibility switch is greyed out | Setup step 1 (Allow restricted settings) |
| Dashboard says "Enabled, but not running yet" | Toggle the service off and on; set battery to Unrestricted |
| Nothing happens on swipe, no log lines | Strip not shown (check Dashboard status), or you started outside the strip. Turn on "Show strips on screen" |
| Log shows "Touch cancelled by the system" | The system Back gesture took the touch (see risk 1) |
| Log shows "Ignored: ... too short / too slow / not straight" | Lower the minimum distance, raise sensitivity, or swipe faster/straighter |
| Lock gesture triggers by accident | Raise the minimum distance, lower sensitivity, or shrink the vertical range |
| Strips misplaced after rotation | Toggle the master switch; please report it, it is a bug |
| App launch does nothing | See Diagnostics for Android's background-start refusal; try a different action |

## Manual test plan
See [`docs/MANUAL_TEST_PLAN.md`](docs/MANUAL_TEST_PLAN.md).

## Project layout
```
app/src/main/java/com/edgeone/gestures/
  core/     Models, GestureClassifier (+Tracker), EdgeZoneGeometry, ActionRegistry, SettingsCodec   (pure Kotlin)
  data/     SettingsRepository (DataStore), ServiceRuntime (status + in-memory log)
  service/  EdgeAccessibilityService, EdgeOverlayManager, EdgeStripView, ActionExecutor
  ui/       MainActivity, MainViewModel, Dashboard / EdgeConfig / Setup / Diagnostics screens, pickers
app/src/main/res/xml/accessibility_service_config.xml
app/src/test/...   unit tests (classifier, geometry, registry/mapping, codec, DataStore persistence)
```
