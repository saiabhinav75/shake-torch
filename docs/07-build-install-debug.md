# 07 · Build, install on your A37, and debug

## Tools on your Mac

| Tool | Where | Notes |
|---|---|---|
| JDK 17 | Homebrew `openjdk@17` | Gradle and the Android Gradle Plugin need Java 17 |
| Android SDK | `~/Library/Android/sdk` | platforms 35 + 36, build-tools, platform-tools (adb), emulator |
| Gradle | downloaded by `./gradlew` | pinned to 8.13 in `gradle/wrapper/gradle-wrapper.properties` |

Tip: add adb to your PATH (in `~/.zshrc`):

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

The commands below assume you've done this. Otherwise use the full path
`~/Library/Android/sdk/platform-tools/adb`.

## Build commands

Run these from the `shake-to-torch/` folder:

```bash
./gradlew testDebugUnitTest   # run the ShakeDetector unit tests (no phone needed)
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk   (~24 MB)
./gradlew assembleRelease     # → app/build/outputs/apk/release/app-release.apk (~1.6 MB, use this)
./gradlew lintDebug           # static checks → app/build/reports/lint-results-debug.html
./gradlew clean               # delete build outputs
```

Or open the folder in **Android Studio** (File → Open → `shake-to-torch`). It reads the same Gradle files,
and you get autocomplete, a debugger, and a ▶ button.

## Put the phone in developer mode (one-time)

1. *Settings → About phone → Software information* → tap **Build number** 7 times.
2. *Settings → Developer options* (now at the bottom of Settings) → enable **USB debugging**.

### Option A: USB

Plug in the cable, accept the "Allow USB debugging?" prompt on the phone, then:

```bash
adb devices                   # should list your phone as "device"
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` = replace, keeping the app's data and settings.

### Option B: Wireless debugging (no cable)

1. Phone and Mac on the same Wi-Fi.
2. *Developer options → Wireless debugging* → on → **Pair device with pairing code**.
3. On the Mac:
   ```bash
   adb pair 192.168.x.x:PAIRPORT      # enter the 6-digit code shown on the phone
   adb connect 192.168.x.x:PORT       # the IP:port shown on the main Wireless debugging screen
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

### Option C: No adb at all

Copy `app-release.apk` to the phone (Quick Share, Google Drive, USB file transfer), tap it in *My Files*, and
allow "Install unknown apps" for that file manager when asked.

> **Signatures:** debug builds use the debug key, and release builds use your upload key (`keystore/`). Android refuses
> to update an app signed with a different key, so to switch between a debug and a release install, run
> `adb uninstall com.abhinav.shaketorch` first. Once the app is on Play, installs from Play are signed with
> Google's key, so uninstall before sideloading your own build over them.

## First-run checklist on the A37

1. Open **Shake Torch** → flip the main switch → allow notifications.
2. Tap **Allow background running** → Battery → **Unrestricted**.
3. *Settings → Battery → Background usage limits → Never sleeping apps* → add Shake Torch.
4. Use the **Calibrate** card to find a comfortable strength.
5. Lock the phone, shake it: torch on (one buzz). Shake again after the cooldown: torch off (two buzzes).
6. Optional: add the **Shake to torch** Quick Settings tile.

## Reading logs (logcat)

```bash
adb logcat -s TorchController BootReceiver AndroidRuntime
```

- `AndroidRuntime` shows crashes with full stack traces.
- To add your own logs anywhere: `Log.d("ShakeTorch", "peak g=$g")`, then run
  `adb logcat -s ShakeTorch`.

### Useful adb commands for this app

```bash
# is the service running?
adb shell dumpsys activity services com.abhinav.shaketorch

# is our wake lock held (screen off)?
adb shell dumpsys power | grep ShakeTorch

# is the accelerometer registered and at what rate?
adb shell dumpsys sensorservice | grep -A2 shaketorch

# simulate a reboot broadcast without rebooting (tests BootReceiver)
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.abhinav.shaketorch

# force-stop, as if Samsung killed it
adb shell am force-stop com.abhinav.shaketorch

# uninstall
adb uninstall com.abhinav.shaketorch
```

## Emulator

An Android 16 emulator (`Medium_Phone_API_36`) is installed. It's handy for checking that the UI doesn't crash,
but it has no real flash. Use *Extended controls (⋯) → Virtual sensors* to fake accelerometer movement.
Always test the real gesture on the phone.

```bash
emulator -avd Medium_Phone_API_36 &
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

| Problem | Likely cause / fix |
|---|---|
| Works with screen on, not off | *Work with screen off* disabled, or Samsung put the app to sleep: redo checklist steps 2–3 |
| Stops working after a few hours | Samsung "Deep sleeping apps"; set battery to **Unrestricted** |
| Shake does nothing, no vibration, notification visible | Camera app or a video call is using the flash; check `adb logcat -s TorchController` |
| No notification at all | Notifications denied: *Settings → Apps → Shake Torch → Notifications*. The service still runs |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Signature mismatch: `adb uninstall com.abhinav.shaketorch` and install again |
| Gradle: "SDK location not found" | `local.properties` is missing: `echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties` |

Next: [08-customize.md](08-customize.md)
