# Deploy on Windows

Install `THUGS-Wardrive-debug.apk` onto an Android phone from Windows. It is
a **debug** build signed with the standard Android debug key.

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
  install apps?"* — allow it for that app (Files, Edge, Drive…). Path:
  Settings → Apps → Special app access → **Install unknown apps**.

---

## Option A — ADB (recommended)

```powershell
# easiest: winget puts adb on PATH
winget install --id Google.PlatformTools

adb devices
adb install -r C:\path\to\THUGS-Wardrive-debug.apk   # -r = replace, keeps data
```

Manual alternative:
1. Download **SDK Platform-Tools for Windows** from
   <https://developer.android.com/tools/releases/platform-tools> and unzip to
   e.g. `C:\platform-tools`.
2. If Windows doesn't detect the phone, install the OEM USB driver (Samsung
   Smart Switch, Google "Android USB Driver", Xiaomi Mi USB Driver, etc.).
3. In **PowerShell**:
   ```powershell
   cd C:\platform-tools
   .\adb devices
   .\adb install -r C:\Users\you\Downloads\THUGS-Wardrive-debug.apk
   ```

## Option B — no ADB

- **USB / MTP:** plug in, unlock the phone, set USB mode to **File transfer
  (MTP)**, open the phone in **File Explorer**, copy the APK into **Download**.
  On the phone: **Files → Download**, tap the APK, **Install**.
- Or share via OneDrive / Google Drive / email and open it on the phone.

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
| `adb: no devices/emulators found` | Unlock phone, accept USB-debugging prompt, install the OEM USB driver, try another cable/port, set USB mode to "File transfer". |
| device is `unauthorized` | Accept the on-screen prompt; `adb kill-server` then retry. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Different signing key installed — `adb uninstall red.thugs.wardrive.debug` then install again. |
| `INSTALL_FAILED_OLDER_SDK` | Phone is below Android 15 (`minSdk 35`). Lower it in `app/build.gradle.kts` and rebuild. |
| "App not installed" when tapping | Not enough storage, or a corrupt transfer — re-copy and check the size. |
