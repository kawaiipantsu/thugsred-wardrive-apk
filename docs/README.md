# THUGS Wardrive — documentation

A simple **WiFi + Bluetooth wardriving app for Android 15+**. Scan for WiFi
access points and Bluetooth (Classic + BLE) devices while you move, watch the
live session list and offline map, then sync to
[wardrive.thugs.red](https://wardrive.thugs.red):

- **Go Live** — stream WiFi observations to the ingest API as you drive
- **Upload** — export the session to WiGLE CSV and post it to the moderation queue

## ⚠️ You need an approved THUGS(red) Wardrive account

**Go Live** and **Upload** both sign in to `wardrive.thugs.red`, and that account
is **not self-serve**:

1. Register at <https://wardrive.thugs.red/register>.
2. New accounts land **pending** — a THUGS(red) moderator has to approve yours.
3. **Tell THUGS(red) staff on Discord** that you've registered and want wardrive
   access, so they can approve the account.

Until it's approved, the app is **scan-only**: you can scan WiFi + Bluetooth and
use the live list, map and counters, and export a CSV to local storage — but
**Go Live and Upload will fail at sign-in** without an approved account.

## Get the app

Latest debug APK:
<https://github.com/kawaiipantsu/thugsred-wardrive-apk/releases/latest/download/THUGS-Wardrive-v1.0-debug.apk>

```
adb install -r THUGS-Wardrive-v1.0-debug.apk
```

Signed with the standard Android **debug** key — fine for personal use, not the
Play Store.

## Pages

| Page | What's in it |
|---|---|
| [Using the App](Using-the-App.md) | Scanning, the list, the map, Go Live, Upload, saved sessions |
| [Optimising your phone](Optimising-your-phone.md) | De-clutter & settings checklist for a long drive, and how the app saves battery |
| [Deploy on macOS](Deploy-macOS.md) | Install the APK from a Mac (ADB or file transfer) |
| [Deploy on Linux](Deploy-Linux.md) | Install the APK from Linux (ADB or file transfer) |
| [Deploy on Windows](Deploy-Windows.md) | Install the APK from Windows (ADB or file transfer) |
| [Screenshots](screenshots/) | Rendered from the real UI (Robolectric + Roborazzi); how to regenerate |

## The Wardrive project

`wardrive.thugs.red` is a community site for uploading and exploring wardrive
dumps — Kismet, ESP32 rigs, Hak5 WiFi Pineapple and similar. It holds a
searchable archive, a map with markers / clustering / heatmap / driving paths, a
live-ingest API with bearer tokens, a moderated upload pipeline, build guides and
a forum. This app is a phone-sized front end to the live API and the upload
queue.

- API guide: <https://wardrive.thugs.red/guides/api>
- API reference: <https://wardrive.thugs.red/api>

## Build from source

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk   # platforms;android-35, build-tools;35.0.0
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Kotlin · Jetpack Compose (Material3) · AGP 8.7 · minSdk / targetSdk / compileSdk 35 · OkHttp.
