# Using the App

## You need an approved THUGS(red) Wardrive account

**Go Live** and **Upload** sign in to `wardrive.thugs.red`. That account is **not
self-serve**:

1. Register at <https://wardrive.thugs.red/register>.
2. New accounts land **pending** — a THUGS(red) moderator must approve yours.
3. **Message THUGS(red) staff on Discord** that you've registered and want
   wardrive access, so they can approve it.

Without an approved account the app is **scan-only** — WiFi + Bluetooth scanning,
the live list, the map, the counters and local CSV export all work; Go Live and
Upload fail at sign-in.

## First launch — permissions

Grant these when asked (Settings → Apps → THUGS Wardrive → Permissions to change
later):

- **Location — Allow all the time.** Every observation is placed at a GPS fix,
  and "all the time" is what lets scanning continue with the screen off.
- **Nearby Wi-Fi devices** — required to read scan results on Android 13+.
- **Nearby devices / Bluetooth** — for BT + BLE scanning and device names.
- **Notifications** — the running session shows an ongoing notification with a
  Stop button.

## The screen

```
┌───────────────────────────────────────┐
│  [logo] THUGS Wardrive     Go Live  ⋮  │  ← banner + menu
├───────────────────────────────────────┤
│  LIVE · sent 240 · queued 12          │  ← only while Go Live is on
├───────────────────────────────────────┤
│  MyNet          AA:BB:CC:DD:EE:FF      │
│  ch 6  -55 dBm  12:44:03              │  ← one row per device
│  …                                    │
├───────────────────────────────────────┤
│  WiFi 128 · BT 9 · BLE 44 · Fixes 96  │  ← session footer
│  · Devices 181                        │
└───────────────────────────────────────┘
              [ ▶ Start scan ]
```

- **List** — one row per device: SSID / name, BSSID, channel, signal, last seen.
  Repeated sightings collapse to the most recent; the full sighting history is
  kept for the CSV and the driving path.
- **Footer** — running totals for this session. `Fixes` is how many rows have a
  usable GPS position; `Devices` is distinct devices. A `power-saving` line
  appears when the scan cadence has backed off (see
  [Optimising your phone](Optimising-your-phone.md)).
- **Start scan / Stop** — starts a foreground service that owns the radios, so a
  drive survives the screen turning off. Stop from the button or the
  notification.

## Menu (⋮)

| Item | Does |
|---|---|
| **Go Live** | Sign in and start streaming (below) |
| **Stop live ingest** | Stop streaming but keep scanning |
| **Upload current session** | Sign in, export to WiGLE CSV, post to the queue |
| **Saved sessions…** | List previously exported CSVs; upload one later |
| **Export CSV to storage** | Write the current session to app storage without uploading |
| **Forget saved login** | Wipe the stored token + credentials (shown once you've signed in) |
| **Live list** | Back to the list |
| **Map** | The map — offline plot, optional OSM tiles |
| **About** | Project info + the optimise-your-phone checklist |

## Map

Reachable from the menu. Scanned points and the driving path drawn on a local
projection. It works **fully offline** — a lat/long graticule, scale bar and north arrow instead of a basemap. The **Tiles** button turns on OpenStreetMap tiles (needs a connection the first time; tiles are then cached, and it stays off until you ask). No API key.

- **Pinch** to zoom, **drag** to pan.
- **Double-tap** or the **Fit** button to re-fit the whole session.
- Points are coloured by radio: green = WiFi, amber = BT, red = BLE. The white
  dot is your current position.

## Go Live

1. Menu → **Go Live** (or the banner button).
2. Enter your `wardrive.thugs.red` username and password. Tick **Stay signed in
   on this device** to skip this next time. *Change server* points at a different
   host.
3. The app signs in, creates an ingest token for this device
   (`THUGS Wardrive app — <model>`), starts scanning if it isn't already, and
   begins streaming WiFi observations to `POST /api/v1/ingest`.
4. The **LIVE** strip shows how many observations the server has accepted and how
   many are still queued. Batches are re-tried with back-off on rate limits;
   nothing is re-sent once the server has answered.

**Staying signed in.** With *Stay signed in* ticked, the app keeps the ingest
token and, if provided, your username + password — encrypted via the Android
Keystore, on this device only. After that, **Go Live** starts with one tap and
**Upload** pre-fills the sign-in. Menu → **Forget saved login** wipes all of it;
switch accounts the same way. Only WiFi is sent; the server does not ingest
Bluetooth.

## Upload (offline drives)

1. Drive with **Start scan** only — no data connection needed.
2. When you have a connection: menu → **Upload current session**.
3. Enter your credentials. The session is written to WiGLE CSV and posted to
   `/uploads`, where a moderator reviews it before it appears on the map.

Sessions are also saved locally (menu → **Export CSV to storage**), and
**Saved sessions…** lets you upload any of them later. Bluetooth rows are written
into the CSV tagged `BT` / `BLE` for a complete local record; the server skips
them.

## What a WiFi client is

Detecting WiFi *clients* (stations) needs monitor mode and is not possible on a
stock Android device. In this app, Bluetooth devices are the "clients" you
collect alongside APs.
