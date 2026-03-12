# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Sizzlr** is an Android application for actors to record and submit video auditions directly to casting directors. The visual design reference is at http://tek.chaosnet.org/sizzle/

## Build Commands

```bash
# Build debug APK
export ANDROID_HOME=/home/tekphreak/Android/sdk
export JAVA_HOME=/home/tekphreak/.sdkman/candidates/java/current
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk

# Build release APK (requires signing config)
./gradlew assembleRelease

# Install directly to connected device
./gradlew installDebug

# Clean build
./gradlew clean assembleDebug
```

## Architecture

**Single-activity app** — `MainActivity` owns the full UI lifecycle.

**Key classes:**
- `MainActivity` — camera setup, recording state machine, UI transitions
- `FaceGuideView` — custom `View` that draws the face-alignment overlay using `Canvas` (SVG path converted to Android `Path` calls, scaled from 0–100 coordinate space to view pixels)

**Camera:** CameraX `VideoCapture<Recorder>` with `MediaStoreOutputOptions`. Videos are saved to `Movies/Sizzlr/` on-device gallery. Front camera preferred, falls back to rear.

**UI state machine** (four states driven by `showIdleUI / showRecordingUI / showReviewUI / showSuccessUI`):
```
IDLE → [Record] → RECORDING → [Stop] → REVIEW → [Submit] → SUCCESS
                                      ↗ [Retake] ↙
                               SUCCESS → [New Clip] → IDLE
```

**Layout:** `activity_main.xml` — `ConstraintLayout` root with:
1. Black header bar (CASTING CALL ONLINE)
2. `FrameLayout` viewport at `3:4` aspect ratio (`app:layout_constraintDimensionRatio="3:4"`)
3. `NestedScrollView` controls panel below (script box, buttons, success card)

**Design tokens:** Dark zinc theme + amber accents — defined in `res/values/colors.xml`.

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0) — required for `canvas.clipOutPath()`
- **Target SDK:** 34
- **Build tools:** 34.0.0, Gradle 8.4, AGP 8.2.2
- **Key deps:** CameraX 1.3.1, Material Components, ViewBinding

## Product Spec

Full spec in `sizzlr.txt`. Core user flow:
1. Log in → select casting project/role
2. Read on-screen script (teleprompter)
3. Record video (unlimited takes)
4. Choose best take → Submit
5. Video saved to `Movies/Sizzlr/` on device
