# Orbit — Circular App Launcher

A standalone native Android utility that presents your installed apps on a
rotatable, smartwatch-inspired circular wheel. Rotate with your finger, tap an
icon, and Android opens that app.

- **Language / UI:** Kotlin + Jetpack Compose + Material 3
- **Build:** Gradle with Kotlin DSL (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01)
- **Min / target SDK:** 26 / 35 (tested target range includes Android 13+)
- **Application ID:** `com.orbit.launcher`
- **No** account, onboarding, internet, subscription or special permissions.

## What works

- **Real app discovery** via `PackageManager.queryIntentActivities` for
  `ACTION_MAIN` + `CATEGORY_LAUNCHER`, with the API-appropriate typed-flags
  overload on Android 13+. Uses a narrow `<queries>` declaration — no
  `QUERY_ALL_PACKAGES`.
- **Authentic icons** loaded from the system drawable pipeline off the main
  thread and cached in memory; aspect ratio preserved, adaptive icons intact.
- **Virtualized radial carousel** — a continuous floating-point offset drives
  positions; only the visible window (6–12 slots, auto-sized to radius and a
  minimum touch target) is composed. Wraps seamlessly in both directions with no
  teleporting at index boundaries.
- **Gesture controller** — `atan2`-based angular drag with wrap-safe
  normalization, touch-slop gating (so taps stay taps), centre-dead-zone to avoid
  angle instability, and decay-based inertial fling that cancels on new touch.
- **Direct launch** by exact launcher component, with graceful failure handling
  and history recorded before the activity transition.
- **Search** filters the same data feeding the wheel (case-insensitive substring,
  clearable, empty-state aware).
- Orbit-recorded **recents**, persisted with Preferences DataStore; **Selected /
  All / Recent** browsing modes.
- **Settings screen**: haptics, ordering (A–Z / recent), icon size, app count,
  version, and a confirmed preference reset.
- **Accessibility**: each icon exposes its label and a Button role to TalkBack.

## Project layout

```
app/src/main/java/com/orbit/launcher/
  MainActivity.kt                 entry point + foreground refresh
  apps/InstalledApp.kt            immutable app-entry model
  apps/AppRepository.kt           discovery, icon/label loading, dedupe, sort
  apps/AppLauncher.kt             launch + failure handling
  ui/OrbitScreen.kt               main screen composition
  ui/CircularAppWheel.kt          radial carousel + gesture handling
  ui/AppIcon.kt                   accessible icon rendering
  ui/SearchBar.kt                 search field
  ui/SettingsScreen.kt            settings
  ui/AppEmptyState.kt             empty / no-match states
  ui/theme/Theme.kt               palette + Material theme
  animation/WheelMotion.kt        wheel math + drag/fling state
  preferences/AppPreferences.kt   DataStore-backed settings/recents/selection
  viewmodel/OrbitViewModel.kt     UI state, filtering, user actions
app/src/test/java/com/orbit/launcher/WheelMathTest.kt   JVM unit tests
```

## Build & run

Requires JDK 17 and the Android SDK (platform 35, build-tools). Open the
`orbit/` folder in Android Studio and let Gradle sync, or from the command line:

```bash
# from the orbit/ project root
./gradlew :app:testDebugUnitTest     # run the wheel-math unit tests
./gradlew :app:assembleDebug         # build app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If `gradlew`/the wrapper JAR is missing, generate it once with a local Gradle:
`gradle wrapper --gradle-version 8.9`.

## Changes in this revision

- Removed the "APP SPACE" label from the wheel centre; the selected app's icon
  and name are now the only centre content, geometrically centred.
- Added an **Edit Apps** editor (top-bar sliders icon): a searchable list of real
  launchable apps with checkboxes, a "N selected" count, Select all, Clear, Save
  and Cancel. It edits a temporary copy, so Cancel never saves, and searching
  never drops hidden picks.
- Added a **Selected** browsing mode that becomes the default view once a custom
  selection has been saved (first run still opens All Apps). A saved empty
  selection is distinguished from "never configured" and shows a dedicated empty
  state with an Edit apps action.
- The **home-screen widget** now renders your selected apps directly in a circular
  RemoteViews arrangement. Each icon has its own direct-launch PendingIntent
  (through an invisible trampoline activity), so tapping it opens that app with no
  picker or Orbit screen in between. Prev/next page through larger selections
  inside the widget; an empty selection shows an "Edit in Orbit" action. A
  **Quick Picker** static shortcut is also registered.
- **Favourites removed entirely** — no favourites mode, long-press toggle, star
  badge, or favourites persistence.
- Added a dedicated lightweight **PickerActivity** that reuses the shared
  CircularAppWheel and launches then finishes, so it never lingers over the app.
- Every screen shares one source of truth: the same AppRepository, AppPreferences
  (DataStore) and AppLauncher.

## Build status

Built successfully in the authoring environment with JDK 17, Android SDK
platform 35 / build-tools 35.0.0 and Gradle 8.9:

- `:app:testDebugUnitTest` — **passes** (4/4 wheel-math tests).
- `:app:assembleDebug` — **BUILD SUCCESSFUL**, producing a signed debug APK
  (`app-debug.apk`, ~10 MB, `com.orbit.launcher` v1.1.0, launcher activity
  `com.orbit.launcher.MainActivity`, plus `PickerActivity`, `AppLaunchActivity`
  and the `OrbitWidgetProvider` receiver). Dropping the oversized
  `material-icons-extended` library (only three icons needed it, all replaced
  with `material-icons-core` equivalents) cut the APK from ~17 MB to ~10 MB and
  removed the slowest D8 dex step.

**Not verified:** on-device runtime behaviour. The authoring environment has no
Android device or emulator, so gesture feel, icon rendering against real
installed apps, TalkBack, and launch flow were not exercised at runtime. Those
require installing the APK on a phone (or an emulator) and using it.
