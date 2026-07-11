# Codex Limits Wallpaper

Minimal live wallpaper and home-screen widget for Huawei Pura 70 and other Android devices. The app shows Codex limit percentages, reset times, and the last successful update on a pure black OLED background.

Package: `ru.ftfour.codexwallpaper`

## Architecture

- `MainActivity` is a lightweight settings screen with a live preview, demo data editor, server URL, connection test, accent/position/size controls, refresh interval, app-update check, and a direct live-wallpaper setup button.
- `CodexWallpaperService` is a standard Android `WallpaperService` with a custom `Engine` that draws through `SurfaceHolder` and stops the minute timer when the wallpaper is not visible.
- `CodexLimitsWidgetProvider` is a home-screen widget that shows the same saved/demo limits and includes a manual refresh button.
- `WallpaperRenderer` owns Canvas rendering, responsive layout, progress bars, reset-time formatting, and local countdown calculation. It contains no network code.
- `SettingsRepository` stores all settings and last known data in DataStore Preferences.
- `CodexLimitsRepository` fetches JSON through OkHttp, validates it, saves only valid data, and keeps showing the last saved value if the server is unavailable.
- `CodexSyncWorker` runs unique periodic WorkManager sync with network constraints. Supported intervals are 15 minutes, 30 minutes, 1 hour, and manual only.
- `AppUpdateRepository` checks the latest GitHub release and exposes an update button when a newer APK release is available.

No Google Play Services, Firebase, Accessibility Service, Notification Listener, embedded OpenAI credentials, cookies, or tokens are used.

## Widget and updates

Add the **Codex Limits** widget from the Android launcher widget picker. It uses the same demo/server mode and saved settings as the app. The widget refresh button runs the same configured refresh path as the settings screen. For the companion server's `/api/codex-limits` endpoint, manual refresh first calls `POST /api/codex-limits/refresh` so the server checks current Codex limits before the phone saves the returned JSON.

The settings screen can check `ftfour/codexwall` GitHub Releases. If a release tag is newer than the installed `versionName`, the app shows an update button that opens the APK asset when one is attached, or the release page otherwise.

## JSON endpoint

The server must return:

```json
{
  "five_hour_percent_left": 63,
  "five_hour_resets_at": "2026-07-06T18:27:00+03:00",
  "weekly_percent_left": 28,
  "weekly_resets_at": "2026-07-10T14:20:00+03:00",
  "updated_at": "2026-07-06T16:09:00+03:00"
}
```

Percentages are clamped to `0..100`. Dates must be ISO-8601 timestamps with timezone offsets. Release builds require HTTPS endpoints. Debug builds additionally allow local HTTP addresses such as `localhost`, `127.0.0.1`, `10.x.x.x`, `172.16.x.x..172.31.x.x`, and `192.168.x.x`.

## VDS server

The `server/` folder contains a ready companion server for a VDS:

- `GET /api/codex-limits` for the Android app.
- `/admin` for manual updates from a phone.
- A systemd collector that can run a configured Codex CLI command and update `limits.json`.
- Nginx and systemd examples.

See [server/README.md](server/README.md).

## Build

Open the project in a current stable Android Studio, let Gradle sync, then use:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:bundleRelease
```

Debug APK path:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release APK path:

```text
app/build/outputs/apk/release/app-release.apk
```

Release AAB path:

```text
app/build/outputs/bundle/release/app-release.aab
```

## GitHub releases

Push a version tag such as `v1.1.0` to run `.github/workflows/android-release.yml`. The workflow runs unit tests and attaches an APK to the GitHub Release. If signing secrets are configured (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`), it publishes a signed release APK; otherwise it falls back to a debug APK. Signed local APK/AAB builds still use the keystore flow below.

## Release signing

Create a release keystore:

```powershell
keytool -genkeypair -v -keystore codex-limits-wallpaper.jks -alias codex-limits-wallpaper -keyalg RSA -keysize 2048 -validity 10000
```

Create `keystore.properties` in the project root. It is ignored by Git:

```properties
storeFile=C:\\absolute\\path\\to\\codex-limits-wallpaper.jks
storePassword=your_store_password
keyAlias=codex-limits-wallpaper
keyPassword=your_key_password
```

Then build:

```powershell
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:bundleRelease
```

## Install on Huawei Pura 70

1. Build the debug APK or release APK.
2. Copy the APK to the Huawei Pura 70.
3. Open the file on the phone and allow installation from the chosen file manager if prompted.
4. Launch **Codex Limits Wallpaper**.
5. Tap **Set live wallpaper** and confirm in the Huawei live wallpaper preview.
6. Use demo mode immediately, or switch to server mode and enter your HTTPS JSON URL.

ADB alternative:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Huawei battery optimization

WorkManager follows Android and Huawei background rules. If periodic updates are delayed too aggressively:

1. Open Huawei **Settings**.
2. Go to **Apps** -> **Codex Limits Wallpaper** -> **Battery**.
3. Disable strict automatic management for this app.
4. Allow background activity if the firmware exposes that option.

The wallpaper keeps showing the last saved data even when background refresh is delayed.

## AppGallery Connect

Upload the signed `app-release.aab` to AppGallery Connect. Keep the signed APK locally for device testing. Prepare screenshots of:

- Settings screen with preview.
- Huawei live wallpaper preview.
- Wallpaper applied on the home screen.
- Server mode with URL field and update status.

Suggested short description:

```text
Minimal live wallpaper that shows your Codex limit status from demo data or your own private JSON endpoint.
```

Suggested full description:

```text
Codex Limits Wallpaper is a minimal OLED live wallpaper for tracking Codex limit percentages, reset times, and last successful update time. It supports offline demo data and an optional user-provided HTTPS JSON endpoint. The app stores settings locally and does not include Google services, Firebase, OpenAI credentials, cookies, tokens, Accessibility Service, or Notification Listener.
```

Why internet is required:

```text
Internet access is used only when the user enables server mode and enters a JSON endpoint. The app sends a GET request to that user-provided URL to refresh limit data.
```

Privacy policy example:

```text
Codex Limits Wallpaper stores settings and the latest valid limit data locally on the device. If server mode is enabled, the app connects only to the URL entered by the user and downloads JSON limit data. The app does not collect analytics, does not use Google Play Services or Firebase, does not read data from other apps, and does not include or request OpenAI, ChatGPT, cookie, or token credentials. Uninstalling the app removes its local data according to Android system behavior.
```

AppGallery submission outline:

1. Create a new app in AppGallery Connect with package `ru.ftfour.codexwallpaper`.
2. Fill app name, category, description, screenshots, privacy policy URL or text, and contact details.
3. Upload the signed AAB.
4. Explain that `INTERNET` is needed for the user-configured JSON endpoint and `ACCESS_NETWORK_STATE` is needed by WorkManager network constraints.
5. Submit for review.

## Development notes

- Live wallpaper rendering is always black by default, regardless of settings screen light/dark theme.
- Countdown values are calculated locally and redrawn at most once per minute while visible.
- If the endpoint fails, the wallpaper keeps the last saved valid data and shows `NO FRESH DATA`.
- The app intentionally does not attempt to access Codex or ChatGPT internal/mobile APIs.
