# Shake to Torch — plan & status

## Decision
Standalone Kotlin app. Injecting into One UI's *Motions and gestures* isn't possible without root
(closed-source system app, no API; rooting trips Knox). See README.md → "Why an app".

## Architecture
- `core/ShakeDetector` — pure, unit-tested peak/window/cooldown detector on accelerometer magnitude
- `core/TorchController` — Camera2 torch on/off/strength, synced via TorchCallback
- `core/Prefs` — SharedPreferences = single source of truth (UI writes, service listens)
- `service/ShakeService` — specialUse foreground service; wake lock only while screen off
- `service/ShakeTileService` — QS tile to start/stop listening
- `service/BootReceiver` — restart after boot / app update
- `ui/` — Compose settings + live calibration meter

## Status
- [x] Detection + tests (7 passing)
- [x] Torch control incl. brightness (Android 13+, hardware-dependent)
- [x] Foreground service, screen-off mode, vibration, auto-off
- [x] QS tile, notification actions, boot restart
- [x] Settings UI with calibration meter, Samsung battery setup prompts
- [x] Docs (docs/01–09)
- [x] Play-ready: targetSdk 36, upload key in keystore/ (backup!), restricted battery permission removed, signed AAB, PRIVACY_POLICY.md
- [ ] Host privacy policy, record FGS demo video, closed test (12 testers × 14 days)
- [ ] Verify on the physical A37 (gesture feel, screen-off reliability, brightness levels)

## Ideas backlog
See docs/08-customize.md (pocket protection, chop gesture, SOS strobe, wake-up sensor).
