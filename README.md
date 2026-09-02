# THUGS Wardrive (Android)

A simple wardriving app for Android 15+. Scans for WiFi access points and
Bluetooth (Classic + BLE) devices while you move, shows a live list, and syncs to
[wardrive.thugs.red](https://wardrive.thugs.red) either **live** (streaming) or by
**uploading** a finished session.

## Screens

- **Top banner** — logo, wordmark, and a menu: *Go Live*, *Upload current session*,
  *Saved sessions…*, *Export CSV to storage*, *Live list*, *Map*, *About*.
- **List** — one row per device (SSID/name, BSSID, channel, signal, last-seen).
- **Map** — scanned points and the driving path drawn on a Compose `Canvas` in a
  local equirectangular projection. Pinch-zoom, drag-pan, double-tap / *Fit* to
  reset. Fully offline — no map tiles are downloaded.
- **Footer** — running counts for this session: WiFi APs, BT, BLE, GPS fixes,
  distinct devices; shows *power-saving* when the cadence has backed off.
- **Start scan / Stop** — floating button; scanning runs in a foreground service
  so a drive survives the screen turning off.

## Battery

- Scan results are coalesced; the list/counters recompute at most once a second.
- A tuning loop in `ScanService` drops WiFi + BLE to a low-power cadence when the
  car is stationary (`LocationProvider.isMoving()`), restoring it on movement.
- BLE uses batched delivery (`setReportDelay`) so the radio sleeps between flushes.
- WiFi relies on the OS's passive scans; the active nudge is infrequent and
  pauses when stationary.
- GPS is polled every ~2 s. The map draws locally with no network.
- A partial wake lock is held only while a session is running (screen-off scans).

See the in-app **About** screen for the "de-clutter & optimise your phone"
checklist, and **DEPLOY.md** for installing the APK from macOS / Windows / Linux.

## Go Live vs Upload

| | Go Live | Upload |
|---|---|---|
| Needs data now | yes | only when you upload |
| Auth | you enter your wardrive.thugs.red username/password once | same |
| What the app does | logs in, creates a `wdrv_` ingest token, streams WiFi observations to `POST /api/v1/ingest` in batches | logs in, exports the session to WiGLE CSV, posts it to `/uploads` (moderated) |
| Bluetooth | not sent (server ingests WiFi only) | written to the CSV tagged `BT`/`BLE`; server skips them |

The password is used once to sign in and is **never written to disk** — only the
issued token is kept (in `EncryptedSharedPreferences`).

## Output format

WiGLE CSV (`WigleWifi-1.4`), which `wardrive.thugs.red`'s `WigleCsvParser` reads.
Files are saved under the app's private storage (`files/sessions/`) and listed in
*Saved sessions…* so an offline drive can be uploaded later.

## Building

Open in Android Studio (Ladybug or newer) and Run, **or** headless:

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk      # needs platforms;android-35, build-tools;35.0.0
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

- `namespace` / `applicationId`: `red.thugs.wardrive` (debug build: `red.thugs.wardrive.debug`)
- `minSdk` / `targetSdk` / `compileSdk`: 35. Lower `minSdk` in `app/build.gradle.kts`
  (e.g. to 30) to widen device support.
- Default server URL is `https://wardrive.thugs.red`, overridable in the
  credentials dialog ("Change server").

## Permissions

Location (fine + background), `NEARBY_WIFI_DEVICES`, `BLUETOOTH_SCAN` /
`BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, foreground-service (location), internet.

## Known limits

- Android throttles `WifiManager.startScan()`, so results refresh every 20–30 s,
  not continuously.
- An observation is only recorded with a fresh GPS fix; the `0,0` no-lock
  artefact is dropped (the server rejects it too).
- WiFi *client* detection needs monitor mode and is not possible on a stock
  device — "clients" in the footer means Bluetooth devices.

## Project layout

```
app/src/main/java/red/thugs/wardrive/
  WardriveApp.kt            process-wide singletons
  MainActivity.kt           permissions + Compose host
  data/     Observation, SessionStore (coalesced + track), WigleCsvWriter, Prefs, Geo
  scan/     WifiScanner, BluetoothScanner, ScanService (foreground + power tuning)
  location/ LocationProvider (isMoving)
  net/      WardriveClient (login/token/ingest/upload), LiveIngestManager
  ui/       MainScreen, MapScreen, CredentialsDialog, AboutScreen, MainViewModel, AppIcons, theme/
```
