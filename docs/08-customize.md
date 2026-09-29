# 08 · Customizing and extending

This is the point of owning the code. Here's where to make common changes, then some feature ideas ranked by
difficulty, each with a sketch of how to build it.

## Quick changes

| Change | Where |
|---|---|
| Default sensitivity / shakes / cooldown | `core/Prefs.kt`: the second argument of each `by float(...)` / `by int(...)` |
| Slider ranges | `ui/SettingsScreen.kt`: `range = 1.6f..5f`, `steps = 33` (steps = number of stops *between* the ends) |
| Auto-off choices | `AUTO_OFF_OPTIONS` at the top of `SettingsScreen.kt` |
| Time window for multi-shakes | `ShakeDetector.Config.fromSettings` → `windowMs = 400L * shakesRequired + 400` |
| Vibration patterns | `ShakeService.vibrate()`: `createWaveform(longArrayOf(delay, on, off, on), -1)` |
| Sampling rate | `SENSOR_DELAY_GAME` (~50 Hz) → `SENSOR_DELAY_UI` (~16 Hz, saves power but may miss quick flicks) |
| Notification text | `res/values/strings.xml` |
| App name | `app_name` in `strings.xml` |
| Icon colours | `res/values/colors.xml` and `drawable/ic_launcher_foreground.xml` |

After any change: `./gradlew testDebugUnitTest assembleRelease` and then `adb install -r ...`.

If you change `ShakeDetector`, add a test to `ShakeDetectorTest.kt` first. It's the fastest way to
check the behaviour without shaking your phone 50 times.

## Feature ideas

### Easy: "Only when screen is off" mode

Some people only want the gesture in the dark or when the phone is locked.
1. Add `var onlyScreenOff by boolean("only_screen_off", false)` to `Prefs`.
2. In `ShakeService.applyScreenState`, when `interactive && prefs.onlyScreenOff` → `unregisterSensor()`.
3. Add a `SwitchRow` in the Behaviour card and handle the key in `onSharedPreferenceChanged`.

### Easy: Different gesture to turn off

For example, 2 shakes to turn on and 1 to turn off. In `onShake()` you know `torch.isOn.value`, so swap the
detector config depending on the torch state inside `onTorchChanged`:
`detector.config = if (on) offConfig else onConfig`.

### Medium: Pocket / face-down protection

Stops accidental triggers in a bag or pocket.
- **Proximity sensor** (`Sensor.TYPE_PROXIMITY`): if it reads "near", ignore shakes. Note: on many Samsung
  A-series phones the proximity sensor is virtual (it uses the touchscreen or light sensor) and may not report while the
  screen is off. Test with `adb shell dumpsys sensorservice`.
- **Light sensor** (`Sensor.TYPE_LIGHT`): lux near 0 plus screen off suggests a pocket. But it's also dark at night,
  and that's exactly when you want the torch. So combine it with...
- **Orientation**: in a pocket the phone is usually vertical. `z` close to 0 and `|y|` close to 9.8 → upright.

Put the decision logic in a pure class (like `ShakeDetector`) so it's testable.

### Medium: Chop-chop gesture (like Motorola)

Motorola's gesture is two fast downward "chops". Instead of the magnitude, look at the **sign of one axis**:
a chop is a strong negative-then-positive swing on the y-axis. Record `y` peaks instead of the
magnitude, and require alternating signs. That rejects random jostling much better than magnitude alone.

### Medium: Strobe / SOS mode

Three quick shakes → SOS in Morse code. Use a `Handler` with `postDelayed` to step through a pattern like
`[short, short, short, long, long, long, short, short, short]`, calling `torch.setOn()` at each step.
Stop on the next shake. Watch out: `TorchCallback` fires on each blink, so pause auto-off scheduling while the strobe runs.

### Medium: Per-app pause

Don't trigger while specific apps are in the foreground (games, maps while driving). This needs
`UsageStatsManager` (the user must grant *Usage access* in Settings) to find the current foreground app.

### Harder: Wake-up sensor instead of a wake lock

Some phones expose a *wake-up* accelerometer: `sensorManager.getDefaultSensor(TYPE_ACCELEROMETER, true)`.
It can wake the CPU by itself, so no wake lock is needed, and when combined with `maxReportLatencyUs`
batching, battery use drops. Many phones don't have one; check `sensor.isWakeUpSensor`. You could use it when
it's available and fall back to the current approach otherwise.

### Harder: Hardware "significant motion" pre-filter

`Sensor.TYPE_SIGNIFICANT_MOTION` is a one-shot, very low-power trigger that fires when the phone starts moving.
You could keep the accelerometer off entirely while the phone is still, and turn it on when significant motion fires.
This takes careful handling, because the trigger is designed for walking-level motion and has seconds of latency.

## Code structure principles to keep

1. **Pure logic in `core/`, Android glue elsewhere.** New detection rules go in testable classes.
2. **Prefs is the single source of truth.** The UI writes, the service listens. Don't add direct UI→service
   calls for settings.
3. **Commands out, truth in.** Change hardware state through an API, and learn the result through callbacks (as
   `TorchController` does), never by assuming the command worked.
4. **Always guard newer APIs** with `Build.VERSION.SDK_INT` checks. Lint will remind you.

Next: [09-publishing.md](09-publishing.md)
