# 02 · Kotlin primer, using this codebase

This covers only the Kotlin you need to read this project. Each item points at real code. If you know
TypeScript, Kotlin will feel familiar: static types, type inference, lambdas, null safety.

## `val` vs `var`

```kotlin
val cameraId: String? = findFlashCamera()   // read-only (like TS `const`)
var sensorRegistered = false                // can be reassigned (like `let`)
```

Types are usually inferred. You only write them when it helps readability.

## Null safety: `?`, `?.`, `?:`, `!!`

A type without `?` can **never** be null, and the compiler enforces it.

```kotlin
val cameraId: String?                       // might be null (phone with no flash)

val id = cameraId ?: return false           // Elvis: "if null, do this instead"
                                            // (TorchController.setOn)

wakeLock?.takeIf { it.isHeld }?.release()   // safe call: skip the whole chain if null
                                            // (ShakeService.releaseWakeLock)

val tile = qsTile ?: return                 // common "guard clause" pattern
```

`!!` means "trust me, it's not null" and crashes if you're wrong. This codebase avoids it.

## Classes, constructors, properties

```kotlin
class TorchController(context: Context) {           // primary constructor in the header
    private val cameraManager = context.getSystemService(...) as CameraManager
    val maxStrength: Int = ...                       // property computed once at construction
}
```

`as CameraManager` is a **cast**. `getSystemService` returns a generic `Any`, and we tell the compiler
its real type.

### `lateinit var`

```kotlin
private lateinit var prefs: Prefs   // ShakeService
```

Android creates Services for you, so you can't pass things in through the constructor. `lateinit`
says "this will be set before use (in `onCreate`)". Reading it too early throws an exception.

## `data class`

```kotlin
data class Config(
    val thresholdG: Float = 2.6f,
    val shakesRequired: Int = 2,
    ...
)
```

A data class gets `equals`, `hashCode`, `toString` and `copy()` for free. The tests use
`config.copy(shakesRequired = 1)`, which clones the object with one field changed (like `{...config, shakesRequired: 1}` in JS).
`LiveShakeMeter` compares two configs with `!=`, which works because of the generated `equals`.

Default parameter values (`= 2.6f`) plus **named arguments** mean you rarely need builder patterns:

```kotlin
ShakeDetector.Config(thresholdG = 2.5f, shakesRequired = 2)
```

## `companion object`: Kotlin's "static"

```kotlin
class ShakeService : Service() {
    companion object {
        const val ACTION_STOP = "com.saiabhinavgandesree.shaketorch.action.STOP"
        fun start(context: Context) { ... }
    }
}
// call site: ShakeService.start(context)
```

`const val` is a compile-time constant (strings and numbers only).

## `object` expressions (anonymous classes)

```kotlin
private val screenReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { ... }
}
```

This creates a one-off subclass instance inline, which is common for Android callbacks.

## `when`: a switch statement that returns a value

```kotlin
when (intent.action) {
    Intent.ACTION_SCREEN_ON -> applyScreenState(interactive = true)
    Intent.ACTION_SCREEN_OFF -> applyScreenState(interactive = false)
}

text = when {                   // with no subject it works like an if/else-if chain
    !enabled -> "Off"
    serviceRunning -> "Listening for shakes"
    else -> "Starting…"
}
```

## Lambdas and function references

```kotlin
var onChange: ((Boolean) -> Unit)? = null     // nullable property holding a function
onChange?.invoke(enabled)                     // call it if set

torch.onChange = ::onTorchChanged             // :: = reference to an existing function
onEnabledChange = ::setEnabled
```

If a lambda is the last argument, it goes outside the parentheses (trailing lambda):

```kotlin
repeat(10) { i -> ... }
Button(onClick = onTestTorch) { Text("Test torch") }   // Compose uses this everywhere
```

A lambda with one parameter can use the implicit name `it`: `withFlash.firstOrNull { ... it ... }`.

## Scope functions: `apply`, `also`, `let`, `takeIf`

```kotlin
IntentFilter().apply {                  // `this` = the new object; returns the object
    addAction(Intent.ACTION_SCREEN_ON)
    addAction(Intent.ACTION_SCREEN_OFF)
}

cameraId?.let(::readMaxStrength) ?: 1   // run only if non-null, return the result

prefs.strengthLevel.takeIf { it in 1..max }  // value if the condition holds, else null
```

## Destructuring

```kotlin
val (x, y, z) = event.values    // FloatArray → first three elements
```

## Ranges

```kotlin
strength in 1..maxStrength      // inclusive range check
range = 1.6f..5f                // passed to Slider as a ClosedFloatingPointRange
```

## Extension functions

You can add methods to classes you don't own. The tests add a `feed()` helper to `ShakeDetector`
that only exists inside the test file:

```kotlin
private fun ShakeDetector.feed(fromMs: Long, toMs: Long, g: Float): List<Long> { ... }
```

`sp.edit { putBoolean(key, value) }` in `Prefs.kt` is an extension from `androidx.core` that
opens an editor, runs your block, and calls `apply()` for you.

## Property delegation (`by`)

This is the most "advanced" Kotlin in the project, in `Prefs.kt`:

```kotlin
var vibrate by boolean(KEY_VIBRATE, true)
```

`by` hands the property's get/set to another object. `boolean(...)` returns an object
with `getValue` (reads SharedPreferences) and `setValue` (writes). So:

```kotlin
prefs.vibrate = false     // → sp.edit { putBoolean("vibrate", false) }
if (prefs.vibrate) ...    // → sp.getBoolean("vibrate", true)
```

Compose uses the same mechanism: `var enabled by remember { mutableStateOf(...) }` delegates to a
`State` object, and that's what triggers UI updates.

## Flows (a taste of coroutines)

```kotlin
private val _isOn = MutableStateFlow(false)
val isOn: StateFlow<Boolean> = _isOn.asStateFlow()
```

A `StateFlow` is an observable value, a bit like a tiny store/observable in JS. The UI subscribes with
`torch.isOn.collectAsStateWithLifecycle()` and re-renders on change. The `_private` mutable plus
public read-only pair is a standard convention.

Next: [03-shake-detection.md](03-shake-detection.md)
