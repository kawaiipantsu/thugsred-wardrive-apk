# Optimising your phone for wardriving

A run holds GPS, WiFi and Bluetooth scanning on for hours. Two parts: **settings
to change before a drive**, and **what the app already does** so you know what
not to fight.

## Before a drive — checklist

### Power
- [ ] **Power the phone.** Car USB, a 12 V adapter, or a power bank. Continuous
      GPS + dual-radio scanning will outrun the battery on a long drive.
- [ ] **Exempt the app from battery optimisation.** Settings → Apps → THUGS
      Wardrive → Battery → **Unrestricted** / "Don't optimise". Otherwise the OS
      throttles or kills the foreground service.
- [ ] Turn **Adaptive Battery** off for the drive (Settings → Battery).

### Location
- [ ] **Location on, high accuracy.** Settings → Location → on. Settings →
      Location → Location services → **Google Location Accuracy** on. Do **not**
      use "battery saving" location mode.
- [ ] **Enable "WiFi scanning" and "Bluetooth scanning".** Settings → Location →
      WiFi & Bluetooth scanning — turn **both** on. This is what keeps the OS
      scanning even when the WiFi/BT toggles look "off", and the app leans on it.
- [ ] Mount the phone where it can see the sky (windshield / dash) for a solid
      fix.

### Radios
- [ ] **Keep WiFi toggled ON, but stop it associating.** Turn off "Auto-connect
      to open networks / hotspots" and "Notify for public networks", and forget
      saved networks on your route. A radio busy joining an AP is not scanning.
- [ ] **Keep Bluetooth ON and disconnect everything** — headphones, watch, car
      kit, tracker tags. An active link ties up the BT stack.
- [ ] **Mobile data:** ON for Go Live, OFF for offline Upload runs (saves power;
      you upload later).
- [ ] **Turn off VPN and Private DNS** while using Go Live / Upload — they are
      the usual cause of a login or ingest failure.

### Screen & interruptions
- [ ] Dark theme, low brightness, **Always-On Display off**. You can let the
      screen sleep — scanning continues in the foreground service.
- [ ] **Do Not Disturb on**, auto-rotate off, haptics off.
- [ ] Close other apps; pause background sync, auto-update and photo backup so
      nothing competes for the radios and CPU.
- [ ] Free up storage for CSV logs.
- [ ] Optional: Developer options → "Stay awake while charging"; keep animation
      scales low (or off).

### After the drive
- [ ] Re-enable Adaptive Battery, VPN, auto-connect, and your Bluetooth devices.

## What the app already does

You don't need to micro-manage these:

- **Coalesced state.** A sighting is recorded in O(1); the list and counters
  recompute at most **once a second**, so a burst of a few hundred APs is one
  redraw, not hundreds.
- **Movement-aware cadence.** When the car stops moving, WiFi and BLE drop to a
  low-power cadence automatically (the footer shows *power-saving*) and snap back
  the instant you move again.
- **Batched BLE.** Results are delivered in batches so the Bluetooth radio can
  sleep between flushes.
- **Passive WiFi.** It consumes the OS's own periodic scans; the active nudge is
  infrequent and pauses when stationary. (This is also why results refresh every
  20–90 s rather than continuously — Android throttles `startScan()`.)
- **Local map.** Drawn on-device; OpenStreetMap tiles are opt-in (the *Tiles* button) and cached, off by default.
- **GPS every ~2 s**, not as fast as the chipset allows.
- **Wake lock** is held only while a session is running, for screen-off scanning.

## Realistic expectations

- Plan for the phone to *not* gain charge while scanning even on a good charger —
  aim for "holds steady".
- WiFi result latency of up to a minute is normal; drive at a steady speed rather
  than sprinting between stops.
- The `0,0` "no satellite lock" position is dropped on purpose (the server
  rejects it too), so early rows before a fix won't appear on the map.
