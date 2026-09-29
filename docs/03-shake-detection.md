# 03 · Shake detection: sensors, physics and the algorithm

Code: `app/src/main/java/com/abhinav/shaketorch/core/ShakeDetector.kt`
Tests: `app/src/test/java/com/abhinav/shaketorch/core/ShakeDetectorTest.kt`

## 1. What the accelerometer measures

The accelerometer reports acceleration along three axes, in m/s²:

```
        +y (top of phone)
         ▲
         │
         │
         └────► +x (right edge)
        ╱
       ╱
     +z (out of the screen, toward your face)
```

Surprise: **a phone lying still on a table does not read 0.** It reads about `(0, 0, 9.81)`. The sensor
measures *proper acceleration*: gravity pushing on the chip. Only in free fall does it read zero.

So the sensor always sees gravity (1 g) plus whatever motion your hand adds.

## 2. From three numbers to one: g-force

We don't care which direction you shake, only how hard. So we take the length (magnitude)
of the 3D vector and divide by gravity:

```
gForce = √(x² + y² + z²) / 9.80665
```

| Situation | gForce |
|---|---|
| Phone at rest, any orientation | ≈ 1.0 |
| Walking with phone in hand | 1.1 – 1.6 |
| Running / phone bouncing in a pocket | up to ~2.2 |
| Deliberate wrist flick / shake | 2.5 – 4+ |

That's why the default threshold is **2.6 g** and the slider starts at **1.6 g**.

> **Why not use `TYPE_LINEAR_ACCELERATION` (gravity already removed)?**
> It's a *virtual* sensor, computed by fusing accelerometer and gyroscope data. That makes it more
> power-hungry and slower to settle, and on some budget phones it's noisy. Magnitude over threshold on
> the raw accelerometer is simple, cheap, and works the same in any orientation.

## 3. Getting samples from Android

In `ShakeService`:

```kotlin
sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)

override fun onSensorChanged(event: SensorEvent) {
    val (x, y, z) = event.values
    if (detector.onSample(x, y, z, event.timestamp / 1_000_000)) onShake()
}
```

- `SENSOR_DELAY_GAME` ≈ 50 samples/second (every ~20 ms). That's fast enough to catch a flick that lasts
  50–100 ms, and still very cheap. The accelerometer is one of the lowest-power sensors on the phone.
- `event.timestamp` is in **nanoseconds** since boot, so we divide by 1,000,000 to get milliseconds. We use the
  sensor's own timestamp rather than the wall clock because events can arrive in batches.

## 4. The algorithm

One sample above threshold is not enough. A single bump (dropping the phone on a sofa) would trigger it.
So we look for a **pattern**: *N distinct peaks within a time window*.

```
 g
 4 ┤        ██                 ██
 3 ┤ ─ ─ ─ ─██─ ─ ─ ─ ─ ─ ─ ─ ─██─ ─ ─ ─ ─ threshold (2.6)
 2 ┤       ████               ████
   ┤ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ re-arm level (1.95)
 1 ┤▁▁▁▁▁▁█    █▁▁▁▁▁▁▁▁▁▁▁▁▁█    █▁▁▁▁▁▁
   └────────────────────────────────────── time
            peak #1              peak #2  → TRIGGER
            │◄──── within window ────►│
```

Walking through `onSample()` step by step:

### Step 1: hysteresis (the `armed` flag)

```kotlin
if (!armed) {
    if (g < rearmThreshold()) armed = true
    return false
}
```

While your hand is moving, the force stays above the threshold for several samples (e.g. 60 ms = 3 samples).
Without protection, one flick would count as 3 peaks. So after counting a peak we **disarm**, and only
re-arm once the force drops clearly below the threshold (75 % of it, and never below 1.3 g).

"Hysteresis" means using two different thresholds for going up and coming down. Thermostats do the same thing
so they don't flicker on and off.

The test `oneLongJoltCountsAsSingleShake` proves it: 800 ms of constant 3.5 g, zero triggers.

### Step 2: threshold check

```kotlin
if (g < config.thresholdG) return false
armed = false
```

### Step 3: cooldown

```kotlin
if (timeMs < cooldownUntilMs) return false
```

After a toggle we ignore everything for `cooldownMs` (1.5 s by default). Otherwise the tail of your shake,
or you shaking one extra time, would immediately flip the torch back off.

### Step 4: minimum gap between peaks

```kotlin
if (timeMs - lastPeakMs < config.minPeakGapMs) return false
```

This is a safety net against jittery signals: peaks closer than 150 ms are treated as the same peak.

### Step 5: sliding window

```kotlin
peaks.addLast(timeMs)
while (timeMs - peaks.first() > config.windowMs) peaks.removeFirst()
if (peaks.size < config.shakesRequired) return false
```

`peaks` is an `ArrayDeque` (a double-ended queue) of timestamps. Old peaks fall out the front once they're
older than the window, so only *recent* peaks count. If enough remain, it's a shake: we clear the queue,
start the cooldown, and return `true`.

The window grows with the shake count (`400 ms × shakes + 400 ms`): 2 shakes → 1.2 s, 4 shakes → 2 s.
Asking for 4 shakes in 1.2 s would be exhausting.

## 5. Why it's a separate, Android-free class

`ShakeDetector` imports nothing from Android. It takes plain numbers and returns a Boolean. That means:

1. **It's unit-testable on your Mac** in milliseconds, with no phone or emulator needed:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   The tests generate fake 50 Hz sample streams (`feed(fromMs, toMs, g)`) and check trigger times.
2. **The same logic drives two places**: the real service and the calibration meter in the UI.
   The meter creates its own `ShakeDetector` with your current settings, so what you see in calibration
   is exactly what the service will do.

This split between "pure logic" and "platform glue" is the most useful habit to take from this project.

## 6. Tuning guide

| Symptom | Fix |
|---|---|
| Triggers while walking/running | Raise *Shake strength*, or set *Shakes required* to 3 |
| Have to shake really hard | Lower *Shake strength* (use the meter: see what your normal shake peaks at, set the threshold ~0.3 g below) |
| Torch flips on then straight back off | Increase *Cooldown* |
| Triggers in a bag | *Shakes required* 3+ and a higher strength. Also see the pocket-detection idea in 08-customize.md |

Next: [04-torch-control.md](04-torch-control.md)
