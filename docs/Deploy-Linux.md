# Deploy on Linux

Install `THUGS-Wardrive-v1.0-debug.apk` onto an Android phone from Linux. It is a
**debug** build signed with the standard Android debug key.

> **Note — needs an approved account for sync.** Go Live and Upload require a
> THUGS(red) Wardrive account approved by staff: register at
> <https://wardrive.thugs.red/register>, then ask THUGS(red) staff on Discord to
> approve it. Without one, the installed app still works for WiFi / Bluetooth
> scanning, the map, and local CSV export.

Get the file:
<https://github.com/kawaiipantsu/thugsred-wardrive-apk/releases/latest/download/THUGS-Wardrive-v1.0-debug.apk>

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
  install apps?"* — allow it for that app. Path: Settings → Apps → Special app
  access → **Install unknown apps**.

---

## Option A — ADB (recommended)

```bash
# Debian / Ubuntu
sudo apt install adb
# Fedora
sudo dnf install android-tools
# Arch
sudo pacman -S android-tools

adb devices
adb install -r THUGS-Wardrive-v1.0-debug.apk   # -r = replace, keeps data
```

If the device shows as **`no permissions`**, add a udev rule and replug:

```bash
# 18d1 = Google; run `lsusb` to find your phone's vendor id
echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0660", GROUP="plugdev"' \
  | sudo tee /etc/udev/rules.d/51-android.rules
sudo udevadm control --reload-rules && sudo udevadm trigger
sudo usermod -aG plugdev "$USER"     # then log out and back in
```

`adb kill-server && adb start-server` after the rule change also helps.

## Option B — no cable

- **MTP:** most file managers (GNOME Files, Dolphin, Thunar with `gvfs-mtp`)
  mount the phone over USB — copy the APK to **Download**, then tap it on the
  phone.
- **HTTP:** in the folder with the APK, `python3 -m http.server 8000`, then on
  the phone (same WiFi) open `http://<pc-ip>:8000/` and download it.
- **KDE Connect / Syncthing / `scp`** to the phone's storage also work.

---

## Installing the tapped APK on the phone

1. Open **Files** / Downloads, tap `THUGS-Wardrive-v1.0-debug.apk`.
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
| device is `unauthorized` | Accept the on-screen prompt; `adb kill-server` then retry. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Different signing key installed — `adb uninstall red.thugs.wardrive.debug` then install again. |
| `INSTALL_FAILED_OLDER_SDK` | Phone is below Android 15 (`minSdk 35`). Lower it in `app/build.gradle.kts` and rebuild. |
| "App not installed" when tapping | Not enough storage, or a corrupt transfer — re-copy and check the size. |
