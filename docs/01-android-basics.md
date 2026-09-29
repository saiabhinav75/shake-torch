# 01 · How an Android app is put together

If you've built Expo/React Native apps, much of this is the native layer that Expo normally hides from you.

## 1. The build system: Gradle

Android apps are built with **Gradle**. A build runs Kotlin source code through a compiler, then packages
it with resources (icons, strings) into an **APK**: a zip file the phone can install.

| File | Job |
|---|---|
| `settings.gradle.kts` | Lists the modules (`:app`) and the repositories libraries download from (`google()`, `mavenCentral()`). |
| `build.gradle.kts` (root) | Declares the plugins that sub-modules use. `apply false` means "make available, don't apply here". |
| `gradle/libs.versions.toml` | A **version catalog**: every library and version in one file. In code you write `libs.androidx.core.ktx` instead of repeating the full coordinates. |
| `app/build.gradle.kts` | The real config: SDK levels, app ID, build types, dependencies. |
| `gradle.properties` | JVM memory for Gradle, AndroidX flags. |
| `gradlew` + `gradle/wrapper/` | The **wrapper**. `./gradlew` downloads and runs the exact Gradle version pinned in `gradle-wrapper.properties`, so builds are reproducible. |
| `local.properties` | Machine-specific (path to your Android SDK). Git-ignored. |

The `.kts` extension means these files are written in Kotlin, not Groovy.

### SDK levels (`app/build.gradle.kts`)

```kotlin
compileSdk = 36   // Android APIs we compile against (Android 16)
minSdk = 31       // oldest Android that can install this (Android 12)
targetSdk = 36    // the Android version whose behaviour rules we opt into
```

- **compileSdk** decides which APIs you *can call*.
- **minSdk** decides which APIs you can call *without checking the version first*. Anything newer than 31
  must be wrapped in `if (Build.VERSION.SDK_INT >= ...)`. You'll see this in `TorchController.kt` for
  brightness control (Android 13 = API 33).
- **targetSdk** tells the OS "I've tested against these rules". For example, targeting 34+ makes it
  mandatory to declare a foreground service *type*.

The A37 runs Android 16 (API 36), the same level we target. Google Play also requires a recent targetSdk (see 09-publishing.md).

### Build types

- **debug**: fast to build, includes debugging info, ~24 MB here because nothing is stripped.
- **release**: `isMinifyEnabled = true` runs **R8**, which strips unused code and shortens names.
  Result: ~1.6 MB. It's signed with your **upload key** (`keystore/`) if present, otherwise the debug key. See 09-publishing.md.

## 2. The manifest: `AndroidManifest.xml`

The manifest is the app's contract with the OS. Android reads it **at install time**, before any of your
code runs. It declares:

- **Permissions** (`<uses-permission>`): what the app is allowed to do. Some are granted at install
  (WAKE_LOCK, VIBRATE); "dangerous" ones like POST_NOTIFICATIONS need a runtime prompt.
- **Features** (`<uses-feature>`): the hardware the app needs (flash, accelerometer).
- **Components**: every Activity, Service and Receiver. If a component isn't in the manifest, the
  OS won't start it.

Note what's *missing*: no `INTERNET` permission. This app physically can't send data anywhere.
And no `CAMERA` permission either: turning on the torch doesn't count as using the camera.

## 3. The four building blocks (components)

Android doesn't run your app from a `main()` function. The OS creates **components** when they're
needed, and it may destroy them whenever it wants.

| Component | What it is | In this app |
|---|---|---|
| **Activity** | A screen the user interacts with | `MainActivity`: the settings screen |
| **Service** | Code that runs without a UI | `ShakeService`: listens to the accelerometer; `ShakeTileService`: the Quick Settings tile |
| **BroadcastReceiver** | Reacts to system-wide events | `BootReceiver`: phone booted / app updated. Plus a runtime receiver for screen on/off inside `ShakeService` |
| **ContentProvider** | Shares data between apps | not used |

### Lifecycles

Each component has **lifecycle callbacks**: methods the OS calls as the component is created,
shown, hidden or destroyed. For an Activity:

```
onCreate → onStart → onResume  (visible & interactive)
                       ↓
onDestroy ← onStop ← onPause   (user leaves)
```

`MainActivity` uses this. It starts watching the torch in `onStart` and stops in `onStop`, so it
doesn't waste resources while you can't see it. In `onResume` it restarts the service if Samsung
killed it.

A Service has a simpler lifecycle: `onCreate → onStartCommand (every start request) → onDestroy`.

## 4. Intents

An **Intent** is a message that says "start this component" or "do this action". Examples from the code:

```kotlin
// start our own service explicitly
Intent(context, ShakeService::class.java)

// open this app's page in system Settings (where Battery → Unrestricted lives)
Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
```

A **PendingIntent** is an Intent wrapped in a token that you hand to *another* process (like the
notification system), so it can fire the Intent later on your app's behalf. The notification's
"Turn torch on" button is a PendingIntent that sends `ACTION_TOGGLE_TORCH` to `ShakeService`.

## 5. Resources (`res/`)

| Folder | Contents |
|---|---|
| `values/strings.xml` | User-visible text, referenced as `R.string.notif_title` |
| `values/themes.xml`, `values-night/` | The window theme before Compose draws (light vs dark) |
| `drawable/` | Vector icons (XML paths, sharp at any size) |
| `mipmap-anydpi-v26/` | The adaptive launcher icon (background + foreground layers, plus a monochrome layer for One UI's themed icons) |

The build generates a class called `R` with an integer ID for every resource. Code references
`R.drawable.ic_torch`; the build turns that into the actual file.

## 6. How it all fits together here

```
         ┌─────────────── you tap the app icon
         ▼
   MainActivity ──(Compose UI)── SettingsScreen
         │  flips switch → Prefs.enabled = true
         │  ShakeService.start()
         ▼
   ShakeService (foreground, has notification)
     ├── SensorManager → onSensorChanged() → ShakeDetector.onSample()
     │                                          │ returns true on a shake
     │                                          ▼
     ├── TorchController.toggle() → CameraManager.setTorchMode()
     ├── screen on/off receiver → wake lock on/off
     └── listens to Prefs changes → updates detector live

   ShakeTileService ── QS tile → starts/stops ShakeService
   BootReceiver     ── after reboot → starts ShakeService if enabled
```

Next: [02-kotlin-primer.md](02-kotlin-primer.md)
