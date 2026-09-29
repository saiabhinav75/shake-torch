# 05 · Running in the background

Code: `service/ShakeService.kt`, `service/BootReceiver.kt`, `service/ShakeTileService.kt`

This is the hardest part of the app. Android works hard to stop apps running in the background,
and Samsung works even harder.

## 1. Why a *foreground* service?

Three Android rules force this design:

1. **Background apps can't read continuous sensors.** Since Android 9, if your app is in the background
   (no visible screen and no foreground service), the accelerometer simply stops sending events.
2. **Background services get killed.** Since Android 8, a plain background service is stopped about a minute
   after you leave the app.
3. **Foreground services are the exception.** A foreground service shows a notification (so the user
   *knows* something is running) and in exchange gets to keep running and keep using sensors.

That's why there's a permanent "Shake to torch is active" notification. It's not optional; it's the
price of listening in the background. You can collapse it in One UI: long-press it → set the channel to *Silent*
/ minimised. It's already low-importance, so it makes no sound and shows no status-bar icon on most setups.

## 2. Starting a foreground service

```kotlin
// caller (ShakeService.start)
context.startForegroundService(Intent(context, ShakeService::class.java))

// inside the service, in onCreate: you have ~5 seconds to do this or the app crashes
startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
```

### Foreground service types (Android 14+)

Apps targeting Android 14 must declare *why* they run in the foreground: `location`, `mediaPlayback`,
`camera`, and so on. None of the standard types fit "listening for a gesture", so we use
**`specialUse`** and describe it in the manifest:

```xml
<service android:name=".service.ShakeService" android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
              android:value="Listens to the accelerometer so the user can toggle the flashlight..." />
</service>
```

(On the Play Store, Google would review that description. For a sideloaded personal app it's just metadata.)

We deliberately don't use the `camera` type. It requires the CAMERA permission and is meant for apps that
capture images, which we don't do.

## 3. The service lifecycle in this app

```
startForegroundService()
      │
      ▼
 onCreate()            set up prefs, sensor, torch, detector
      │                startForeground() → notification appears
      │                register screen on/off receiver + prefs listener
      ▼
 onStartCommand()      called on EVERY start request, including notification buttons:
      │                  ACTION_TOGGLE_TORCH → toggle
      │                  ACTION_STOP         → stopSelf()
      │                returns START_STICKY = "if you kill me, restart me when you can"
      ▼
 onDestroy()           unregister everything, release wake lock
```

The service holds no state that matters. Everything important lives in `Prefs`. If Android kills and
restarts it, it rebuilds itself from preferences, and nothing is lost.

### How settings reach the service live

The UI writes to SharedPreferences. The service is registered as an
`OnSharedPreferenceChangeListener`, so when you move the sensitivity slider it immediately calls
`detector.config = prefs.detectorConfig()`. There's no restart and no messaging code. Both sides use SharedPreferences
as the shared source of truth.

## 4. Screen off: wake locks

When the screen turns off, Android puts the **CPU to sleep** within seconds (Doze and suspend).
A sleeping CPU can't run `onSensorChanged`, so shakes are missed.

A **partial wake lock** keeps the CPU running while the screen stays off:

```kotlin
powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShakeTorch:listener").acquire()
```

We hold it **only while the screen is off** (with the screen on, the CPU is awake anyway):

```kotlin
private fun applyScreenState(interactive: Boolean) {
    when {
        interactive -> { releaseWakeLock(); registerSensor() }
        prefs.screenOffEnabled -> { acquireWakeLock(); registerSensor() }
        else -> { unregisterSensor(); releaseWakeLock() }
    }
}
```

`ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` can't be declared in the manifest. Android only delivers them
to receivers registered in code while the app is running, which is why `screenReceiver` is registered in
`onCreate`.

### Battery cost

With *Work with screen off* enabled, the CPU stays in a light-awake state and the accelerometer samples at 50 Hz.
Expect roughly **1–3 % extra battery per day** on a modern phone, mostly from the CPU. If that matters
more to you than pocket use, switch it off: the app then only listens while the screen is on
(including on the lock screen).

## 5. Samsung's battery management (important!)

One UI has its own layer on top of Android that kills background apps even when Android wouldn't.
The app handles this in three ways:

1. **"Allow background running" button** opens the app's system settings page, where you tap
   Battery → **Unrestricted**. (There's a permission that shows a one-tap "allow" dialog instead,
   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, but Google Play restricts it to certain app categories, so we don't use it.)
2. **`MainActivity.onResume`** restarts the service if it was killed and you open the app.
3. **`START_STICKY`** asks Android to restart it after a kill.

Also check these on the A37 yourself:

- *Settings → Battery → Background usage limits* → make sure Shake Torch is **not** in "Sleeping apps"
  or "Deep sleeping apps". Add it to **Never sleeping apps**.
- *Settings → Apps → Shake Torch → Battery* → **Unrestricted**.
- Don't swipe it away with "Close all" in recents if you've enabled any "lock app" settings. The
  service normally survives that, but some One UI versions are aggressive.

## 6. Starting on boot

```xml
<receiver android:name=".service.BootReceiver" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
    </intent-filter>
</receiver>
```

- `BOOT_COMPLETED` fires after the phone restarts (needs the `RECEIVE_BOOT_COMPLETED` permission).
- `MY_PACKAGE_REPLACED` fires after you install an update of this app. That's handy during development:
  `adb install -r` and listening resumes on its own.

Android normally blocks starting foreground services from the background, but these two broadcasts
are on the list of exemptions.

## 7. The Quick Settings tile

`ShakeTileService` extends `TileService`. The system binds to it when you open the notification shade:

- `onStartListening()` runs when the tile becomes visible, so we refresh its on/off look there.
- `onClick()` runs when you tap it, and starts or stops `ShakeService`.

Starting a foreground service from a tile is usually allowed. If Android refuses
(`ForegroundServiceStartNotAllowedException`), we open the app instead, which starts it from the
foreground. To add the tile: pull down Quick Settings → ✎ (edit) → drag **Shake to torch** in.

The tile toggles *listening*, not the torch. One UI already has a flashlight tile.

## 8. Notification buttons

The two notification actions are `PendingIntent.getService(...)` with actions
`ACTION_TOGGLE_TORCH` and `ACTION_STOP`. Tapping them delivers an Intent to `onStartCommand`.
`FLAG_IMMUTABLE` is required on Android 12+ and means the notification system can't modify our Intent.

Next: [06-ui-compose.md](06-ui-compose.md)
