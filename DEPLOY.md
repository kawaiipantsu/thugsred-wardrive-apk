# Getting the APK onto an Android phone

The file is `app/build/outputs/apk/debug/app-debug.apk` (also copied to the repo
root as `THUGS-Wardrive-v1.0-debug.apk`). It is a **debug** build signed with the
standard Android debug key — fine for personal use and testing, not for the Play
Store.

Two routes: **ADB** (cable, best for repeat installs and logs) or **file
transfer** (copy the APK to the phone and tap it). Pick one.

---

## 0. One-time phone setup

### If you'll sideload by tapping the file
- **Android 8+**: the first time you open an APK from an app (Files, Chrome,
  Drive), Android asks *"Allow this source to install apps?"* — enable it for
  that app. Settings path: **Settings → Apps → Special app access → Install
  unknown apps → (your file manager / browser) → Allow**.

### If you'll use ADB
- **Settings → About phone →** tap **Build number** 7 times to unlock Developer
  options.
- **Settings → System → Developer options →** enable **USB debugging**.
- Plug in the cable; on the phone tap **Allow** on the "Allow USB debugging?"
  dialog (tick "Always allow from this computer").
- Some phones also need **Developer options → Install via USB** enabled.

---

## macOS

### Option A — ADB (recommended)
```bash
# Install platform-tools (adb)
brew install --cask android-platform-tools     # or: brew install android-platform-tools

adb devices                                    # should list your phone as "device"
adb install -r THUGS-Wardrive-v1.0-debug.apk   # -r = replace existing
```
If `adb devices` shows `unauthorized`, unlock the phone and accept the prompt.
No Homebrew? Download "SDK Platform-Tools for Mac" from
<https://developer.android.com/tools/releases/platform-tools>, unzip, then
`./adb install -r ...` from that folder.

### Option B — no cable
- **AirDrop** the APK to the phone, open it from the **Files** app, tap
  **Install** (allow the "unknown source" prompt if asked).
- Or upload to Google Drive / Nextcloud / email it to yourself, then open on the
  phone and tap **Install**.

---

## Windows

### Option A — ADB (recommended)
1. Download **SDK Platform-Tools for Windows** from
   <https://developer.android.com/tools/releases/platform-tools> and unzip it
   (e.g. to `C:\platform-tools`).
2. If Windows doesn't see the phone, install the OEM USB driver (Samsung,
   Google "Android USB Driver", Xiaomi, etc.).
3. Open **PowerShell** in that folder:
   ```powershell
   cd C:\platform-tools
   .\adb devices
   .\adb install -r C:\path\to\THUGS-Wardrive-v1.0-debug.apk
   ```
   `winget install Google.PlatformTools` also works and puts `adb` on PATH.

### Option B — no ADB
- **USB / MTP:** plug in, unlock the phone, set USB mode to **File transfer
  (MTP)**, open the phone in File Explorer, copy the APK to **Download**. On the
  phone, open **Files → Download**, tap the APK, tap **Install**.
- Or share it via OneDrive / Google Drive / email and open it on the phone.

---

## Linux

### Option A — ADB (recommended)
```bash
# Debian/Ubuntu
sudo apt install adb
# Fedora
sudo dnf install android-tools
# Arch
sudo pacman -S android-tools

adb devices
adb install -r THUGS-Wardrive-v1.0-debug.apk
```
If the device shows as `no permissions`, add a udev rule and replug:
```bash
# 18d1 = Google; use `lsusb` to find your vendor id
echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0660", GROUP="plugdev"' \
  | sudo tee /etc/udev/rules.d/51-android.rules
sudo udevadm control --reload-rules && sudo udevadm trigger
sudo usermod -aG plugdev "$USER"    # log out/in afterwards
```

### Option B — no cable
- MTP: most file managers (GNOME Files, Dolphin) mount the phone over USB — copy
  the APK to **Download**, then tap it on the phone.
- Or `python3 -m http.server 8000` in the folder with the APK, then on the phone
  (same Wi-Fi) browse to `http://<computer-ip>:8000/` and download it.
- `scp`/KDE Connect/Syncthing to the phone's storage also works.

---

## Installing the tapped APK on the phone

1. Open **Files** (or your browser's Downloads), tap `THUGS-Wardrive-v1.0-debug.apk`.
2. If prompted *"For your security, your phone is not allowed to install unknown
   apps from this source"* → **Settings** → enable **Allow from this source** →
   back → **Install**.
3. Play Protect may warn *"Unsafe app blocked"* or *"app was not scanned"* — tap
   **More details → Install anyway** (it's unrecognised, not malicious).
4. Launch **THUGS Wardrive** and grant Location (allow **all the time** for
   screen-off scanning), Nearby Wi-Fi devices, and Nearby devices / Bluetooth
   when asked.

---

## Updating later

- **ADB:** `adb install -r THUGS-Wardrive-v1.0-debug.apk` keeps app data.
- **Tapping:** just open the new APK; Android updates in place as long as it's
  the same debug signing key (it is, on every build from this project).
- The debug build's package is `red.thugs.wardrive.debug`, so it can sit
  side-by-side with a future release build (`red.thugs.wardrive`).

## Troubleshooting

| Symptom | Fix |
|---|---|
| `adb: no devices/emulators found` | Unlock phone, accept USB-debugging prompt, try another cable/port, set USB mode to "File transfer". |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / signature mismatch | A build with a different key is installed — `adb uninstall red.thugs.wardrive.debug` then install again. |
| `INSTALL_FAILED_OLDER_SDK` | The phone is below Android 15 — this build sets `minSdk 35`. Lower it in `app/build.gradle.kts` and rebuild. |
| "App not installed" when tapping | Not enough storage, or a corrupt transfer — re-copy the APK and check the file size matches. |
| Play Protect keeps blocking | Settings → Play Store → Play Protect → gear → turn off "Scan apps" for the install, then re-enable. |
