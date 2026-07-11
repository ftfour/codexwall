# Codexwall Agent Notes

## Project Shape

- Android live wallpaper app in `app/`, package `ru.ftfour.codexwallpaper`.
- Dependency-free Node.js companion server in `server/`.
- Root Gradle project uses Kotlin DSL and version catalog in `gradle/libs.versions.toml`.

## Android App

- `MainActivity` builds the settings UI programmatically: preview, demo/server mode, endpoint testing, visual controls, refresh interval, wallpaper picker, battery settings shortcut.
- `SettingsRepository` stores settings, demo data, server data, stale/error flags in DataStore Preferences.
- `CodexLimitsRepository` validates/fetches the configured endpoint with OkHttp, parses JSON, persists only valid data, and broadcasts data changes.
- `CodexSyncWorker` owns WorkManager scheduling. It only schedules periodic work in server mode with a valid endpoint and a non-manual interval.
- `WallpaperRenderer` is pure Canvas rendering. Keep network/storage concerns out of it.
- `CodexWallpaperService` connects repository state to the live wallpaper engine.
- Release builds require HTTPS endpoints. Debug builds also allow local/private HTTP addresses via `UrlValidator`.

## Server

- Entry point: `server/src/server.mjs`.
- Main endpoints:
  - `GET /health`
  - `GET /api/codex-limits`
  - `GET/POST /admin` with Basic Auth
  - `POST /internal/limits` with `Bearer $INTERNAL_TOKEN`
- Data file defaults to `server/data/limits.json`; override with `DATA_DIR`.
- `server/src/collect-codex-limits.mjs` can read Codex CLI app-server rate limits or a fallback command.
- `server/src/codex-app-server-client.mjs` normalizes Codex `usedPercent` into percent-left wallpaper fields.

## Useful Commands

Android:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Server:

```bash
cd server
node tests/normalize-rate-limits.mjs
node src/validate-limits.mjs data/limits.json
node src/server.mjs
```

When `npm` is available, `cd server && npm test` runs the Node checks declared in `server/package.json`.

## Current Local Environment Notes

- Java 17 is available.
- Node is available, but `npm` was not found in the current shell.
- `/opt/android-sdk` is root-owned. This workspace uses an ignored local SDK at `.tools/android-sdk`, selected by ignored `local.properties`.
- A local ignored release keystore exists for signed APK builds through ignored `keystore.properties`.
- `gradlew` should be executable in git so `./gradlew ...` works once Java is installed.

## Test Status Last Checked

- `node tests/normalize-rate-limits.mjs`: passes.
- `node src/validate-limits.mjs data/limits.json`: passes.
- `./gradlew :app:testDebugUnitTest`: passes.
- `./gradlew :app:assembleRelease`: passes and produces a signed local APK when ignored signing files are present.

## Editing Guidance

- Keep Android UI consistent with the current programmatic, simple settings-screen style unless the user asks for a broader redesign.
- Add focused unit tests for parser/formatter/validation changes.
- Preserve the server's no-runtime-dependency design unless there is a strong reason to introduce dependencies.
- Do not put credentials, OpenAI tokens, cookies, or private endpoint secrets in the app.
