# 09 · Publishing on Google Play

## 1. APK vs AAB

| | APK | AAB (Android App Bundle) |
|---|---|---|
| What it is | An installable app | A *publishing* format, which is not installable directly |
| Who uses it | Sideloading, testers | Required by Google Play for all new apps |
| Size for the user | Everything for every device | Play generates a slimmed APK per device (screen density, CPU, language) |

```bash
./gradlew bundleRelease   # → app/build/outputs/bundle/release/app-release.aab
```

## 2. Signing: upload key vs app signing key

Play uses **two** keys:

```
 you ──sign with UPLOAD KEY──► app-release.aab ──► Play Console
                                                      │ verifies it's you,
                                                      │ strips your signature,
                                                      ▼
                          re-signs with APP SIGNING KEY (Google holds it) ──► users' phones
```

- **App signing key**: generated and kept by Google (*Play App Signing*, mandatory for new apps).
- **Upload key**: yours. It proves each upload really comes from you.
  - File: `keystore/upload-keystore.jks`
  - Password: `keystore/keystore.properties`
  - SHA-256 fingerprint: `A6:C4:13:D8:E6:E5:D0:90:11:F2:74:FF:41:1B:81:8E:B6:6B:B0:2F:BC:DA:6F:BC:CF:3F:C3:90:14:3C:25:19`

`app/build.gradle.kts` reads `keystore/keystore.properties` automatically. If the file is missing
(e.g. on another computer), release builds fall back to the debug key: fine for sideloading,
**rejected by Play**.

> ⚠️ **Back up both files in `keystore/`** (password manager, encrypted drive). They're git-ignored
> on purpose, so never commit or share them. If you lose the upload key, you can ask Google support to reset it
> (Play Console → Setup → App signing), but that takes days.

## 3. Play policy decisions already made in the code

- **Target API 36.** Play requires new apps and updates to target the latest-but-one Android version
  or newer. Each August the bar rises; bump `targetSdk` yearly.
- **No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.** That permission is restricted to specific app categories
  (e.g. messaging, device companions) and would likely get the app rejected. Instead, the *Allow background running*
  button opens the app's system settings, where the user taps Battery → Unrestricted themselves.
- **No internet, no ads, no analytics**, so the Data safety form is simple (see below).

## 4. Play Console checklist

### Create app
| Field | Value |
|---|---|
| App name | Shake Torch |
| Default language | English (United States), en-US |
| App or game | App |
| Free or paid | Free (**permanent**: a free app can never become paid) |

The package name is **not** typed here. Play reads it from your first uploaded bundle:
`com.abhinav.shaketorch`, and it can never change afterwards.

### App content (Policy → App content)
| Section | Answer |
|---|---|
| Privacy policy | Required. Host `PRIVACY_POLICY.md` publicly (GitHub Pages, a public Notion page, Google Sites) and paste the URL |
| Ads | No |
| App access | All functionality available without special access |
| Content rating | Fill the IARC questionnaire: category *Utility*, answer No to everything → rated Everyone / 3+ |
| Target audience | 18+ (or 13+). Avoid choosing under-13 ages, which triggers Families policy requirements |
| Data safety | "Does your app collect or share any required user data types?" → **No**. Encrypted in transit → not applicable. Deletion request → not applicable |
| Government / financial / health apps | No |
| **Foreground service permissions** | Declare **Special use**. Description: *"Listens to the accelerometer while in the background so the user can toggle the flashlight by shaking the phone. The user explicitly enables this; a persistent notification is shown and offers a Stop button."* Google asks for a **video link**: record a 30–60 s screen recording of enabling the switch, locking the phone, shaking and the torch turning on, and upload it unlisted to YouTube |

### Store listing
| Asset | Spec |
|---|---|
| Short description (≤80 chars) | *Shake your phone to toggle the flashlight. No ads, fully customizable.* |
| Full description (≤4000) | Features, customization options, privacy (no data collected) |
| App icon | 512×512 PNG, 32-bit, ≤1 MB |
| Feature graphic | 1024×500 PNG/JPG |
| Phone screenshots | 2–8, at least 1080 px on the short side |

The launcher icon is a vector. To export a 512×512 PNG, open the project in Android Studio →
right-click `res` → New → Image Asset, or screenshot the icon in a design tool.

### Testing before production (new personal accounts)

Personal developer accounts created after November 2023 must run a **closed test with at least 12 testers
opted in for 14 continuous days** before they can apply for production access.

1. Testing → Closed testing → Create track → upload `app-release.aab`.
2. Add testers by email list or Google Group, and share the opt-in link.
3. After 14 days → Dashboard → *Apply for production*.

(Organization accounts skip this, going through Internal testing → Production directly.)

## 5. Releasing updates

1. Bump `versionCode` (must increase every upload) and `versionName` in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 2
   versionName = "1.1"
   ```
2. `./gradlew bundleRelease`
3. Play Console → the track → Create new release → upload → release notes → roll out.
