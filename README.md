# Sizzlr

<p align="center">
  <img src="sizzlr-app-logo.png" width="160" alt="Sizzlr app icon" />
</p>

<p align="center"><strong>Your Performance Is Human</strong></p>

In a world where AI-generated faces and voices are rising, the industry needs the raw, unedited truth of a real human performance. Technology can mimic, but it cannot replace the soul you bring to a character. Sizzlr ensures that casting directors see the real you — without the friction of a complicated setup.

---

## Features

- **Auto-scrolling teleprompter** — load your script from any `.txt`, `.md`, or `.html` file and watch it scroll hands-free while you perform; position it above or below the camera
- **5–4–3–2–1 countdown** — audible beep on each count so you're never caught off-guard, silence on 1 so you ease into the scene
- **Face-alignment guide** — dotted overlay so you nail the headshot/medium-shot framing every take
- **Front camera recording** — HD video with audio, mirrored preview so it feels natural
- **ON AIR indicator** — pulsing red dot and live timer while recording
- **Submit via email** — tap Submit Tape to share the clip directly to your configured casting director email
- **On-device save** — clips saved automatically to `Movies/Sizzlr/` in your gallery
- **Retake flow** — record as many takes as you want, keep the best one
- **Dark cinema theme** — clean dark UI that keeps focus on the viewfinder

## Settings

| Setting | Description |
|---------|-------------|
| Submission email | Email address the video is sent to (default: your casting contact) |
| Scroll speed | 1–10 slider controlling teleprompter px/sec |
| Font size | 16–36 sp range |
| Text theme | White on black (default) or Black on white |
| Teleprompter position | Below camera (default) or above camera |
| Script | Paste directly or load any `.txt` / `.md` / `.html` file |

## Screens

| Idle | Recording | Review | Submitted |
|------|-----------|--------|-----------|
| ![Idle](screenshots/01_idle.png) | ![Recording](screenshots/02_recording.png) | ![Review](screenshots/03_review.png) | ![Submitted](screenshots/04_submitted.png) |

## Requirements

- Android 8.0+ (API 26)
- Camera + microphone permissions (prompted on first launch)

## Build

```bash
export ANDROID_HOME=/path/to/Android/sdk
export JAVA_HOME=/path/to/jdk
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

Install to a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tech Stack

- **Kotlin** / Android SDK 34
- **CameraX 1.3.1** — `VideoCapture<Recorder>` + `MediaStoreOutputOptions`
- **Material Components** + ViewBinding
- **Gradle 8.4** / AGP 8.2.2

## Project Structure

```
app/src/main/
├── java/com/tekphreak/sizzlr/
│   ├── SplashActivity.kt    # 3-second branded launch screen
│   ├── MainActivity.kt      # Camera setup, recording state machine, teleprompter
│   ├── SettingsActivity.kt  # Script, email, speed, font, theme, position
│   └── FaceGuideView.kt     # Custom Canvas overlay (face alignment guide)
└── res/
    ├── layout/
    │   ├── activity_splash.xml
    │   ├── activity_main.xml
    │   └── activity_settings.xml
    └── values/colors.xml    # Zinc/amber design tokens
```
