# 06 · The settings screen in Jetpack Compose

Code: `MainActivity.kt`, `ui/SettingsScreen.kt`, `ui/LiveShakeMeter.kt`, `ui/theme/Theme.kt`

**Jetpack Compose** is Android's modern UI toolkit. Coming from React Native, the concepts map almost 1:1:

| React / RN | Compose |
|---|---|
| Function component | `@Composable fun` |
| `useState` | `remember { mutableStateOf(...) }` |
| Re-render on state change | **Recomposition** |
| `useEffect(() => { ...; return cleanup }, [deps])` | `DisposableEffect(key) { ...; onDispose { cleanup } }` |
| `useEffect` running async code | `LaunchedEffect(key) { delay(...) }` (a coroutine) |
| `<View style={{flexDirection:'column'}}>` | `Column { }` |
| `<View style={{flexDirection:'row'}}>` | `Row { }` |
| `style` prop | `Modifier` chain |
| `ScrollView` | `Modifier.verticalScroll(rememberScrollState())` |
| Context / theme provider | `MaterialTheme`, `LocalContext.current` |

## 1. Entry point

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()                 // draw behind status/navigation bars
        setContent {                       // replaces XML layouts with Compose
            ShakeTorchTheme { SettingsScreen(prefs = prefs, torch = torch) }
        }
    }
}
```

## 2. Theme: Material You

```kotlin
val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
MaterialTheme(colorScheme = colors, content = content)
```

`dynamic*ColorScheme` pulls colours from your **wallpaper** (Android 12+), so the app matches your One UI
colour palette automatically, in light and dark mode. Components read colours with `MaterialTheme.colorScheme.primary`.

## 3. State

```kotlin
var threshold by remember { mutableFloatStateOf(prefs.thresholdG) }
```

- `mutableFloatStateOf` creates an observable box. Any composable that *reads* `threshold` gets re-run
  when it changes.
- `remember` keeps the box across recompositions. Without it, every re-run would reset the value.
- `by` (delegation, see 02) lets you read and write `threshold` like a plain variable.

### Persisting to preferences: live preview vs commit

```kotlin
Slider(
    value = threshold,
    onValueChange = { threshold = ... },          // every drag frame: update the UI only
    onValueChangeFinished = { prefs.thresholdG = threshold },  // finger lifted: save
)
```

While dragging, only local UI state changes (smooth, cheap). The calibration meter reads `threshold`
directly, so it previews the new value live. When you lift your finger, it's written to prefs, and the
running service picks it up via its preference listener (see 05).

### Observing flows

```kotlin
val serviceRunning by ShakeService.isRunning.collectAsStateWithLifecycle()
val torchOn by torch.isOn.collectAsStateWithLifecycle()
```

This turns a `StateFlow` into Compose state and automatically pauses collection while the app is in the
background ("with lifecycle").

### Refreshing when you come back

```kotlin
LifecycleResumeEffect(Unit) {
    enabled = prefs.enabled
    batteryUnrestricted = isIgnoringBatteryOptimizations(context)
    onPauseOrDispose { }
}
```

When you return from the system battery dialog (or changed things via the QS tile), this block runs on
`onResume` and re-reads the real values. That's how the *Setup* card disappears once you've granted everything.

## 4. Runtime permissions

```kotlin
val notificationLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted -> notificationsGranted = granted }

notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
```

This is the modern way to ask for a permission: register a launcher, `launch()` it, and receive the result
in the callback. `POST_NOTIFICATIONS` only exists on Android 13+, which is why `requestNotificationPermission()`
checks the version first.

## 5. Reusable pieces

`SettingsScreen` is built from small private composables. Each is just a function with parameters:

- `MasterCard`: the big on/off switch, status text, torch state and test button
- `SectionCard(title) { ... }`: a titled card. Its `content: @Composable ColumnScope.() -> Unit` parameter
  is like `children` in React; `ColumnScope` means the children are laid out in a column
- `LabeledSlider`: label, current value, slider and hint text
- `SwitchRow`: title, subtitle and switch

This is **state hoisting**: the small components don't own state. They receive values and report
changes through callbacks (`onCheckedChange`, `onValueChange`), and the parent owns the state.
It's the same idea as "controlled components" in React.

## 6. The live calibration meter

`LiveShakeMeter` is the most interesting composable:

```kotlin
DisposableEffect(accelerometer) {
    val listener = object : SensorEventListener { ... update peakG, run detector ... }
    sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    onDispose { sensorManager.unregisterListener(listener) }
}
```

- The sensor is registered when the meter appears on screen and unregistered when it leaves
  (or the app closes). That's exactly `useEffect` with a cleanup function.
- **Peak-hold with decay**: `peakG = maxOf(g, peakG - 0.05f)`. A raw 60 ms spike would flash by too fast
  to see, so the bar jumps up instantly and then falls slowly (~2.5 g/s), like the level meter on audio gear.
- It runs its **own** `ShakeDetector` with the slider values, so "Shake detected!" shows exactly what the
  service would do with those settings, even before you save them.
- `SideEffect { detector.config = config }` pushes new settings into the detector after each successful
  recomposition. Changing non-Compose objects directly in the function body is discouraged.
- `LaunchedEffect(detections) { flash = true; delay(1200); flash = false }` restarts every time the count
  changes, which gives the 1.2 s highlight.

Drawing the bar uses `Canvas`, an immediate-mode drawing API (like HTML canvas):

```kotlin
Canvas(Modifier.fillMaxWidth().height(28.dp)) {
    drawRoundRect(track)                                  // background
    drawRoundRect(fill, size = Size(width * fraction, height))
    drawLine(red, Offset(thresholdX, 0f), Offset(thresholdX, height))
}
```

## 7. `dp` and units

`16.dp` means *density-independent pixels*: the same physical size on any screen density. Text uses `sp`
(scales with the user's font size setting); Material typography styles like `bodyMedium` handle that for you.

Next: [07-build-install-debug.md](07-build-install-debug.md)
