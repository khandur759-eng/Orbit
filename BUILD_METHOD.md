# Orbit — how to build the APK fast

## The one rule that decides everything

**Look at what changed. That picks the method.**

---

## Why Orbit has no repack path (the key difference from Kiraya)

Kiraya is a WebView that loads one self-contained `assets/index.html`, so a single-file change can be swapped into the existing APK and re-signed without Gradle. Orbit is a native **Kotlin + Jetpack Compose** app. There is no single swappable asset: code, resources, strings, icons, the manifest and the Gradle files are all compiled into `classes.dex` / `resources.arsc`. **Every change must go through Gradle.** There is no repack shortcut here.

---

## FAST — warm incremental Gradle build (~2–4 min)

**Use when the sandbox is warm** (JDK, Android SDK, Gradle and the Gradle cache still present). Gradle recompiles only the changed sources and reuses the cached D8 dex transforms.

Steps:

1. Bump `versionCode` +1 and `versionName` (e.g. `1.1.0` → `1.1.1`) in `app/build.gradle.kts`.
2. Restore the newest source from the repo and **confirm the version before building**.
3. `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
4. Verify: `aapt2 dump badging` (versionCode/versionName) and `apksigner verify`.

---

## SLOW — cold full build (~10–20 min)

**Use when the sandbox has reset** (toolchain + Gradle cache gone), or a dependency / Gradle plugin / SDK version changed. Needs a real toolchain: **JDK 17**, Android SDK **platform 35 + build-tools 35.0.0**, **Gradle 8.9**, and re-downloading ~250 MB of dependencies. A cold run spills across several commands (per-command cap ~4 min).

---

## Signing

Orbit currently uses the **auto-generated debug keystore**, which is recreated on every cold sandbox. A new APK therefore has a *different* signature and cannot install over the previous one. To make updates install in place (and to allow any future repack), switch to **one stable keystore** and reuse it forever.

---

## Never do these

- **Never regenerate the keystore.** A different key cannot install over the old app.
- **Never skip the version bump.** Android refuses to install an equal or lower `versionCode`.
- **Never trust a leftover working copy.** Verify `versionName` before building.
- **Never add a heavy dependency casually.** `material-icons-extended` alone made D8 dexing exceed the per-run time cap and stalled every build; `material-icons-core` covered the three icons we needed and cut the APK from ~17 MB to ~10 MB.

---

## GitHub

`khandur759-eng/Orbit` (branch `main`) is the **source of truth**. Restore from it before building; commit changes after — each changed file as its own commit.

---

## The 10-second decision

> Is the sandbox warm and did only source/resource files change?
> **Yes → warm incremental Gradle build. No → cold full build.**
