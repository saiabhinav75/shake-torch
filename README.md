# Shake Torch

Shake your phone to toggle the flashlight. No ads, no tracking, no internet permission. Everything is tunable.

Built for a Samsung Galaxy A37 (One UI / Android 16), and works on any Android 12+ phone with a flash.

## Features

| Feature | Where it lives |
|---|---|
| Shake detection with adjustable strength, shake count and cooldown | `core/ShakeDetector.kt` |
| Works with the screen off (optional) | `service/ShakeService.kt` |
| Live calibration meter, so you can tune by feel | `ui/LiveShakeMeter.kt` |
| Vibration feedback (1 buzz = on, 2 = off) | `service/ShakeService.kt` |
| Auto turn-off timer | `service/ShakeService.kt` |
| Flashlight brightness (if the hardware supports it) | `core/TorchController.kt` |
| Quick Settings tile to turn listening on/off | `service/ShakeTileService.kt` |
| Notification with Torch on/off and Stop buttons | `service/ShakeService.kt` |
| Restarts after a reboot or app update | `service/BootReceiver.kt` |

## Why an app and not a One UI gesture?

Samsung's *Settings → Advanced features → Motions and gestures* screen is part of Samsung's closed-source
system software. There's no public API for adding an entry there. The only way in would be rooting the
phone, which trips Knox permanently (Samsung Pay, Secure Folder and some banking apps stop working).
A small app that you control fully does the same job.

## Quick start

```bash
# build (the first build downloads dependencies, ~2–3 min)
./gradlew assembleRelease

# Play Store bundle (signed with the upload key in keystore/)
./gradlew bundleRelease

# install on your phone (USB or wireless debugging, see docs/07)
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
```

Then open **Shake Torch**, flip the main switch, and tap **Allow background running** → Battery → **Unrestricted**.

## Learning path: read the docs in this order

1. [docs/01-android-basics.md](docs/01-android-basics.md): how an Android app is put together (Gradle, manifest, components)
2. [docs/02-kotlin-primer.md](docs/02-kotlin-primer.md): the Kotlin features this codebase uses, with examples from it
3. [docs/03-shake-detection.md](docs/03-shake-detection.md): accelerometer physics and the detection algorithm
4. [docs/04-torch-control.md](docs/04-torch-control.md): controlling the flashlight with the Camera2 API
5. [docs/05-foreground-service.md](docs/05-foreground-service.md): running in the background, wake locks, and Samsung battery management
6. [docs/06-ui-compose.md](docs/06-ui-compose.md): the settings screen in Jetpack Compose
7. [docs/07-build-install-debug.md](docs/07-build-install-debug.md): building, installing on your A37, reading logs
8. [docs/08-customize.md](docs/08-customize.md): how to change things and ideas for new features
9. [docs/09-publishing.md](docs/09-publishing.md): AAB, signing keys, and the Google Play Console checklist

## Project layout

```
shake-to-torch/
├── settings.gradle.kts          # which modules exist + where to download libraries
├── build.gradle.kts             # root build: declares plugins
├── gradle/libs.versions.toml    # every dependency version in one place
├── gradlew                      # Gradle wrapper, pins the exact Gradle version
└── app/
    ├── build.gradle.kts         # app module: SDK levels, dependencies
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/abhinav/shaketorch/
        │   │   ├── MainActivity.kt
        │   │   ├── core/        # ShakeDetector, TorchController, Prefs
        │   │   ├── service/     # ShakeService, ShakeTileService, BootReceiver
        │   │   └── ui/          # Compose screens + theme
        │   └── res/             # strings, icons, themes
        └── test/                # JVM unit tests for ShakeDetector
```
