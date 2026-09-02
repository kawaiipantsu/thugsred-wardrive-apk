package red.thugs.wardrive.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    /**
     * Whether the OS is rate-limiting `startScan()` (~4 per 2 min). Default off in
     * Developer options → "Wi-Fi scan throttling"; when a user has turned it off
     * (as the optimise guide suggests) we scan much faster.
     */
    @Volatile private var throttled = true
    private var lastThrottleCheck = 0L

    /** True when scan throttling is disabled on the device and we're scanning at the fast cadence. */
    val highRate: Boolean get() = !throttled

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) ingest()
        }
    }

    private var lastStartScan = 0L

    private fun refreshThrottle() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastThrottleCheck < THROTTLE_RECHECK_MS && lastThrottleCheck != 0L) return
        lastThrottleCheck = now
        val was = throttled
        // isScanThrottleEnabled() is API 31+; minSdk is 35 so it's always here.
        throttled = runCatching { wifi.isScanThrottleEnabled }.getOrDefault(true)
        if (throttled != was) recomputeNudge()
    }

    private val nudge = object : Runnable {
        override fun run() {
            if (!running) return
            refreshThrottle()
            // Always ask for a full (all-band) scan on a cadence — even when
            // stationary. Skipping it leaves getScanResults() returning the OS's
            // own connectivity scans, which are biased to the connected band and
            // often miss 5/6 GHz. When throttling is on, stay under ~4/2 min.
            val gap = if (throttled) THROTTLED_GAP_MS else UNTHROTTLED_GAP_MS
            val now = SystemClock.elapsedRealtime()
            if (now - lastStartScan >= gap) {
                lastStartScan = now
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
        refreshThrottle()
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
            driveMode -> if (throttled) NUDGE_DRIVE_MS else NUDGE_DRIVE_FAST_MS
            powerSaving -> NUDGE_IDLE_MS
            else -> if (throttled) NUDGE_MOVING_MS else NUDGE_MOVING_FAST_MS
        }
        // A fresh startScan() at the next nudge, so a mode change takes effect promptly.
        lastStartScan = 0L
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
        /** Read the cache this often (broadcasts also drive updates). */
        const val NUDGE_DRIVE_MS = 8_000L
        const val NUDGE_MOVING_MS = 15_000L
        const val NUDGE_IDLE_MS = 45_000L

        /** Faster cadence when the device has scan throttling turned off. */
        const val NUDGE_DRIVE_FAST_MS = 3_000L
        const val NUDGE_MOVING_FAST_MS = 6_000L

        /** Min spacing between our own startScan() calls. */
        const val THROTTLED_GAP_MS = 32_000L    // under Android's ~4 per 2 min
        const val UNTHROTTLED_GAP_MS = 2_500L   // throttling off — go fast

        const val THROTTLE_RECHECK_MS = 60_000L
    }
}
