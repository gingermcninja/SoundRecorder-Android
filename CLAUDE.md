# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew assembleRelease        # build release APK
./gradlew test                   # run unit tests
./gradlew connectedAndroidTest   # run instrumented tests (requires connected device/emulator)
./gradlew lint                   # run lint checks
./gradlew :app:testDebugUnitTest --tests "com.example.soundrecorder_android.SomeTest"  # run a single test
```

## Architecture

This is an early-stage Android app (minSdk 24, targetSdk 36) built with Kotlin. Single-module project (`app`), single activity (`MainActivity`).

**UI approach — mixed, currently View-based:** `MainActivity` uses `setContentView` with an XML layout (`activity_main.xml`), not Compose. However, the Compose dependency stack and a full theme scaffold (`ui/theme/`) are already present. New UI work can go either direction; clarify with the user before committing to one.

**Current state:** The app has six placeholder buttons in a 2×3 `GridLayout` inside a `ConstraintLayout`. Each shows a Toast on click. No audio recording logic exists yet.

**Key files:**
- `app/src/main/java/com/example/soundrecorder_android/MainActivity.kt` — only activity
- `app/src/main/res/layout/activity_main.xml` — View-based layout (ConstraintLayout > GridLayout with 6 buttons)
- `app/src/main/java/com/example/soundrecorder_android/ui/theme/` — Compose Material3 theme (Color, Theme, Type), unused by the activity today
- `app/src/main/AndroidManifest.xml` — no permissions declared yet (RECORD_AUDIO will be needed)
- `gradle/libs.versions.toml` — version catalog: AGP 9.2.0, Kotlin 2.2.10, Compose BOM 2024.09.00

**Permissions:** `RECORD_AUDIO` (and likely `WRITE_EXTERNAL_STORAGE` or `MediaStore` access) must be added to the manifest and requested at runtime before implementing recording.
