package red.thugs.wardrive.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind
import red.thugs.wardrive.location.LocationProvider

/**
 * Turns WiFi scan results into [Observation]s.
 *
 * Android throttles `startScan()` hard (a few calls per two minutes), so this
 * leans on the system's own periodic scans: it listens for
 * `SCAN_RESULTS_AVAILABLE_ACTION` and reads whatever is currently cached.
 *
 * Battery: it only *nudges* an active scan on a timer, and in power-saving mode
 * (car not moving) it stops nudging entirely and just consumes the passive
 * results the OS produces anyway.
 */
class WifiScanner(
    context: Context,
    private val location: LocationProvider,
    private val onObservation: (Observation) -> Unit,
) {
    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    @Volatile private var powerSaving = false
    @Volatile private var driveMode = false
    @Volatile private var nudgeMs = NUDGE_MOVING_MS

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) ingest()
        }
    }

    private val nudge = object : Runnable {
        override fun run() {
            if (!running) return
            if (driveMode || !powerSaving) {
                @Suppress("DEPRECATION")
                runCatching { wifi.startScan() }
            }
            ingest()
            handler.postDelayed(this, nudgeMs)
        }
    }

    fun start() {
        if (running) return
        running = true
        appContext.registerReceiver(
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            Context.RECEIVER_EXPORTED,
        )
        handler.post(nudge)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(nudge)
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    /** power-saving = the drive is paused; back off the active scan nudge. */
    fun setPowerSaving(value: Boolean) {
        powerSaving = value
        recomputeNudge()
    }

    fun setDriveMode(value: Boolean) {
        driveMode = value
        recomputeNudge()
    }

    private fun recomputeNudge() {
        nudgeMs = when {
            driveMode -> NUDGE_DRIVE_MS
            powerSaving -> NUDGE_IDLE_MS
            else -> NUDGE_MOVING_MS
        }
    }

    private fun ingest() {
        val fix = location.current() ?: return
        val now = System.currentTimeMillis()
        val results: List<ScanResult> = runCatching {
            @Suppress("MissingPermission", "DEPRECATION")
            wifi.scanResults
        }.getOrDefault(emptyList())

        for (r in results) {
            val bssid = r.BSSID ?: continue
            @Suppress("DEPRECATION")
            val rawSsid = r.SSID
            onObservation(
                Observation(
                    bssid = Observation.normaliseBssid(bssid),
                    kind = RadioKind.WIFI_AP,
                    ssid = rawSsid?.trim()?.trim('"')?.takeIf { it.isNotEmpty() },
                    capabilities = r.capabilities?.takeIf { it.isNotBlank() },
                    signalDbm = r.level,
                    channel = channelForFrequency(r.frequency),
                    frequencyMhz = r.frequency,
                    lat = fix.latitude,
                    lon = fix.longitude,
                    altitudeM = if (fix.hasAltitude()) fix.altitude else null,
                    accuracyM = if (fix.hasAccuracy()) fix.accuracy.toDouble() else null,
                    timestampMs = now,
                    channelWidthMhz = widthMhz(r.channelWidth),
                    centerFreqMhz = r.centerFreq0.takeIf { it > 0 },
                ),
            )
        }
    }

    private fun widthMhz(channelWidth: Int): Int = when (channelWidth) {
        android.net.wifi.ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        android.net.wifi.ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        android.net.wifi.ScanResult.CHANNEL_WIDTH_160MHZ -> 160
        android.net.wifi.ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
        5 -> 320 // CHANNEL_WIDTH_320MHZ (API 33+); literal avoids a hard dep
        else -> 20
    }

    private companion object {
        const val NUDGE_DRIVE_MS = 10_000L
        const val NUDGE_MOVING_MS = 25_000L
        const val NUDGE_IDLE_MS = 90_000L
    }
}
