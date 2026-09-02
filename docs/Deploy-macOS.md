# Deploy on macOS

Install `THUGS-Wardrive-debug.apk` onto an Android phone from a Mac. It is a
**debug** build signed with the standard Android debug key.

> **Note — needs an approved account for sync.** Go Live and Upload require a
> THUGS(red) Wardrive account approved by staff: register at
> <https://wardrive.thugs.red/register>, then ask THUGS(red) staff on Discord to
> approve it. Without one, the installed app still works for WiFi / Bluetooth
> scanning, the map, and local CSV export.

Get the file:
<https://github.com/kawaiipantsu/thugsred-wardrive-apk/releases/latest/download/THUGS-Wardrive-debug.apk>

---

## One-time phone setup

### For ADB (cable)
1. Settings → About phone → tap **Build number** 7 times.
2. Settings → System → **Developer options** → enable **USB debugging**.
3. Plug in; on the phone tap **Allow** on "Allow USB debugging?" (tick "Always
   allow from this computer").
4. Some phones also need **Developer options → Install via USB**.

### For tapping the file
- The first time you open an APK from an app, Android asks *"Allow this source to
  install apps?"* — allow it for that app (Files, Chrome, Drive…). Path:
  Settings → Apps → Special app access → **Install unknown apps**.

---

## Option A — ADB (recommended)

```bash
# install adb
brew install --cask android-platform-tools
#   (or: brew install android-platform-tools)

adb devices                                    # phone shows as "device"
adb install -r THUGS-Wardrive-debug.apk   # -r = replace, keeps data
```

- `unauthorized` in `adb devices` → unlock the phone and accept the prompt.
- No Homebrew? Download **SDK Platform-Tools for Mac** from
  <https://developer.android.com/tools/releases/platform-tools>, unzip, and run
  `./adb` from that folder.
- Apple silicon: works natively; if Gatekeeper blocks `adb`, right-click → Open
  once, or `xattr -dr com.apple.quarantine platform-tools`.

## Option B — no cable

- **AirDrop** the APK to the phone → open from the **Files** app → **Install**
  (allow the unknown-source prompt if asked).
- Or upload to Google Drive / Nextcloud / email it to yourself, then open on the
  phone and tap **Install**.
- Or serve it: `cd` to the folder, `python3 -m http.server 8000`, then on the
  phone (same WiFi) open `http://<mac-ip>:8000/` and download.

---

## Installing the tapped APK on the phone

1. Open **Files** / Downloads, tap `THUGS-Wardrive-debug.apk`.
2. If prompted about an unknown source → enable **Allow from this source** →
   back → **Install**.
3. Play Protect may say *"Unsafe app blocked"* / *"not scanned"* → **More
   details → Install anyway** (unrecognised, not malicious).
4. Launch **THUGS Wardrive**; grant **Location — all the time**, Nearby WiFi
   devices, and Nearby devices / Bluetooth.

## Updating later

`adb install -r …` (keeps data), or just open a newer APK — it updates in place
as long as it's the same debug key (every build from this project is).

## Troubleshooting

| Symptom | Fix |
|---|---|
| `adb: no devices/emulators found` | Unlock phone, accept USB-debugging prompt, try another cable/port, set USB mode to "File transfer". |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Different signing key installed — `adb uninstall red.thugs.wardrive.debug` then install again. |
| `INSTALL_FAILED_OLDER_SDK` | Phone is below Android 15 (`minSdk 35`). Lower it in `app/build.gradle.kts` and rebuild. |
| "App not installed" when tapping | Not enough storage, or a corrupt transfer — re-copy and check the size. |
