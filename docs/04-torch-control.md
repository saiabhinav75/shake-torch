# 04 · Controlling the flashlight

Code: `app/src/main/java/com/abhinav/shaketorch/core/TorchController.kt`

## 1. The flashlight belongs to the camera

On Android, the LED flash is part of a camera module, so you control it through the **Camera2 API**,
specifically `CameraManager`. The good news: since Android 6, **torch mode doesn't need the CAMERA
permission** and doesn't open the camera. We never see any images.

```kotlin
private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
```

`getSystemService` is how you reach most of the OS: `SENSOR_SERVICE`, `POWER_SERVICE`,
`VIBRATOR_MANAGER_SERVICE`, `NOTIFICATION_SERVICE`, and so on.

## 2. Finding the right camera

Phones have several cameras. The A37 has multiple rear lenses plus a front camera, and usually only
one of them has a flash unit. Every camera has an ID string (`"0"`, `"1"`, …) and a set of
**characteristics**:

```kotlin
private fun findFlashCamera(): String? {
    val withFlash = cameraManager.cameraIdList.filter {
        cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
    return withFlash.firstOrNull {
        ...get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } ?: withFlash.firstOrNull()
}
```

We prefer a back camera with a flash, and fall back to any camera with a flash. `== true` is there because
`get()` returns `Boolean?` (the key might be missing), and a nullable Boolean can't be used directly in an `if`.

## 3. Turning it on and off

```kotlin
cameraManager.setTorchMode(id, true)   // on
cameraManager.setTorchMode(id, false)  // off
```

That's the whole API. The call can fail, though:

- **`CameraAccessException`**: another app has the camera open (e.g. you're in the camera app or on a
  video call). The camera app needs the flash hardware for itself.
- **`IllegalArgumentException`**: bad camera ID or an invalid brightness level.

We catch both, log them, and return `false`, so `onShake()` knows not to vibrate for a toggle that
didn't happen.

## 4. Staying in sync: `TorchCallback`

Problem: the user can also turn the torch on from **Quick Settings**, the lock screen, Bixby, or another app.
If we kept our own `isOn` variable, it would drift out of sync, and the next shake would "toggle" in the wrong direction.

Solution: ask the system to tell us about every change:

```kotlin
private val callback = object : CameraManager.TorchCallback() {
    override fun onTorchModeChanged(id: String, enabled: Boolean) {
        if (id == cameraId) update(enabled)
    }
    override fun onTorchModeUnavailable(id: String) {   // camera app grabbed it
        if (id == cameraId) update(false)
    }
}
cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
```

- Right after registering, the system immediately sends the **current** state, so we start in sync.
- `Handler(Looper.getMainLooper())` means "deliver callbacks on the main thread". See the threading note below.
- **Our own** `setTorchMode()` calls also come back through this callback. So the rule is:
  *commands go out through `setTorchMode`, truth comes in through the callback.* `toggle()` reads
  `isOn` (the truth) to decide the direction.

This is also why the service's auto-off timer works even when you turned the torch on from Quick Settings:
the callback fires, and `onTorchChanged` schedules the timer.

`update()` pushes the new state to two places:

- `_isOn` (a `StateFlow`) is what the Compose UI observes.
- `onChange` (a plain lambda) is what `ShakeService` uses to refresh the notification and schedule auto-off.

## 5. Brightness levels (Android 13+)

Android 13 added adjustable torch strength:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    val max = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
}
cameraManager.turnOnTorchWithStrengthLevel(id, level)   // 1..max
```

Whether this works depends on the **hardware and Samsung's camera driver**. If `max` is 1, brightness can't be
changed, and the brightness slider stays hidden (`if (torch.maxStrength > 1)` in `SettingsScreen`).
If your A37 reports more than 1, the slider appears automatically.

`Build.VERSION.SDK_INT >= ...` is required because `minSdk` is 31, so the compiler (and lint) forces
you to guard calls to APIs from newer versions. On an older phone, calling a missing method would crash.

## 6. A note on threads

Android UI code and most callbacks run on the **main thread** (its "Looper"). Sensor events,
torch callbacks, the screen receiver and the auto-off `Handler` are all delivered on the main thread here.
That's a deliberate choice: since everything runs one at a time on a single thread, there are **no race conditions**
and no locks to manage. The work per event is tiny (a square root and a few comparisons), so it
never makes the UI lag.

Next: [05-foreground-service.md](05-foreground-service.md)
