package red.thugs.wardrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import red.thugs.wardrive.BuildConfig

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("THUGS Wardrive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)

        Section(
            "Account required for Go Live & Upload",
            "Both Go Live and Upload sign in to wardrive.thugs.red, and that account is not " +
                "self-serve:\n" +
                "1. Register at wardrive.thugs.red/register.\n" +
                "2. New accounts are pending until a THUGS(red) moderator approves them.\n" +
                "3. Message THUGS(red) staff on Discord that you've registered and want wardrive " +
                "access, so they can approve you.\n\n" +
                "Without an approved account this app is scan-only: WiFi + Bluetooth scanning, the " +
                "list, the map, the counters and local CSV export all work — Go Live and Upload will " +
                "fail at sign-in.",
        )

        Section(
            "The Wardrive project",
            "wardrive.thugs.red is a community site for uploading and exploring wardrive dumps — " +
                "Kismet, ESP32 rigs, Hak5 WiFi Pineapple and similar. It holds a searchable archive of " +
                "access points, a Leaflet map with markers / clustering / heatmap / driving paths, " +
                "per-network detail pages, stats, a live-ingest API with bearer tokens, a moderated " +
                "upload pipeline, build guides, and a forum. This app is a phone-sized front end to two " +
                "of those doors: the live API and the upload queue.\n\n" +
                "Site sections: /map, /networks, /stats, /guides, /guides/api, /api, /tokens, /uploads, /forum, /about.",
        )

        Section(
            "What the app does",
            "Scans for WiFi access points and Bluetooth (Classic + BLE) devices while you move, " +
                "stamping every sighting with a GPS fix. The list shows one row per device; the Map " +
                "screen plots located points and the driving path (offline, no tiles); the footer counts " +
                "what this run has found.",
        )
        Section(
            "Go Live",
            "Needs a data connection. Signs in to wardrive.thugs.red, creates an ingest token for this " +
                "device, and streams WiFi observations to POST /api/v1/ingest as you drive — batched, " +
                "with back-off on rate limits, and nothing re-sent once the server has answered.",
        )
        Section(
            "Upload",
            "For offline drives. The session is written as WiGLE CSV; when you have a connection, Upload " +
                "signs in and posts the file to the moderation queue at /uploads. Saved sessions stay in " +
                "app storage so you can upload them later.",
        )
        Section(
            "Bluetooth",
            "BT and BLE are scanned and shown, and written into the CSV tagged BT/BLE, but " +
                "wardrive.thugs.red only ingests WiFi — those rows are skipped server-side by design.",
        )

        Text(
            "De-clutter & optimise your phone for wardriving",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "A wardriving run holds GPS, Wi-Fi and Bluetooth scanning on for hours. Before a drive:",
            style = MaterialTheme.typography.bodyMedium,
        )
        Checklist(
            "Power the phone. Car USB, 12 V adapter or a power bank — continuous scanning will outrun the battery on a long drive.",
            "Exempt this app from battery optimisation. Settings → Apps → THUGS Wardrive → Battery → Unrestricted / “Don't optimise”. Otherwise the OS throttles or kills the scan service.",
            "Turn Adaptive Battery / Adaptive brightness off for the drive (Settings → Battery).",
            "Location on, high accuracy. Settings → Location → on; Location services → Google Location Accuracy on. Do not use “battery saving” location mode.",
            "Enable Wi-Fi scanning and Bluetooth scanning. Settings → Location → Wi-Fi & Bluetooth scanning — turn both on so the OS keeps scanning even when Wi-Fi/BT are “off”.",
            "Keep Wi-Fi toggled ON but stop it associating: turn off “Auto-connect to open networks / hotspots” and “Notify for public networks”, and forget saved networks you'll pass. A radio busy joining an AP is not scanning.",
            "Keep Bluetooth ON and disconnect everything: unpair or disconnect headphones, watch, car kit, tracker tags — an active link ties up the BT stack.",
            "Mobile data: ON for Go Live, OFF for offline Upload runs (saves power; you upload later).",
            "Turn off VPN and Private DNS while using Go Live / Upload — they are the usual cause of a login or ingest failure.",
            "Screen: dark theme, low brightness, Always-On Display off. You can let the screen sleep — scanning continues in the foreground service.",
            "Do Not Disturb on, auto-rotate off, haptics off. Fewer wake-ups, fewer interruptions.",
            "Close other apps and pause background sync / auto-update / photo backup so nothing competes for the radios and CPU.",
            "Free up storage for CSV logs, and mount the phone where the sky is visible (windshield/dash) for a solid GPS fix.",
            "Optional: Developer options → “Stay awake while charging”, and keep animation scales low.",
        )

        Section(
            "How this app already saves power",
            "• Scan results are coalesced and the list/counters refresh at most once a second, so a burst " +
                "of a few hundred APs is one redraw, not hundreds.\n" +
                "• When the car stops moving, Wi-Fi and BLE drop to a low-power cadence automatically and " +
                "come back the instant you move (the footer shows “power-saving”).\n" +
                "• BLE results are delivered in batches so the radio can sleep between them.\n" +
                "• Wi-Fi leans on the OS's own periodic scans; the active nudge is infrequent and pauses " +
                "when stationary.\n" +
                "• The map is drawn locally with no tile downloads.\n" +
                "• GPS is polled every ~2 s, not as fast as the chipset allows.",
        )

        Section(
            "Known limits",
            "• Android throttles Wi-Fi scans, so results refresh every 20–90 s, not continuously.\n" +
                "• An observation is only recorded with a fresh GPS fix; the 0,0 no-lock artefact is dropped.\n" +
                "• Wi-Fi client detection needs monitor mode and is not possible on a stock device.\n" +
                "• The password is used once to sign in and is never written to disk; only the issued token is kept.",
        )
        Section("Server", BuildConfig.DEFAULT_BASE_URL + "  ·  guide: /guides/api")
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Checklist(vararg items: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (item in items) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("▸", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
