package red.thugs.wardrive.scan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind
import red.thugs.wardrive.location.LocationProvider

/**
 * Bluetooth side of a drive: BLE scanning plus a repeating classic discovery
 * cycle. Both funnel through [onObservation]. These rows go in the local CSV but
 * wardrive.thugs.red ignores non-WiFi on ingest.
 *
 * Battery: BLE results are delivered in batches ([ScanSettings.setReportDelay])
 * so the radio can sleep between flushes, and [setPowerSaving] drops the scan to
 * low-power mode and stretches the discovery interval when the car is not moving.
 */
class BluetoothScanner(
    context: Context,
    private val location: LocationProvider,
    private val onObservation: (Observation) -> Unit,
) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var leScanning = false

    @Volatile private var powerSaving = false
    @Volatile private var driveMode = false
    @Volatile private var discoveryIntervalMs = DISCOVERY_MOVING_MS

    fun hasScanPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED

    private fun canReadName(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private val leCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            emit(result.device, result.rssi, RadioKind.BT_LE, result.scanRecord?.deviceName)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
    }

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_FOUND) return
            @Suppress("DEPRECATION")
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
            if (device != null) {
                emit(device, rssi.takeIf { it != Short.MIN_VALUE.toInt() }, RadioKind.BT_CLASSIC, null)
            }
        }
    }

    private val discoveryCycle = object : Runnable {
        @Suppress("MissingPermission")
        override fun run() {
            if (!running) return
            adapter?.let { a ->
                if (a.isEnabled) {
                    if (a.isDiscovering) a.cancelDiscovery()
                    if (driveMode || !powerSaving) runCatching { a.startDiscovery() }
                }
            }
            handler.postDelayed(this, discoveryIntervalMs)
        }
    }

    @Suppress("MissingPermission")
    fun start() {
        val a = adapter
        if (running || a == null || !hasScanPermission()) return
        running = true
        appContext.registerReceiver(
            classicReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND),
            Context.RECEIVER_EXPORTED,
        )
        startLe(a)
        handler.post(discoveryCycle)
    }

    @Suppress("MissingPermission")
    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(discoveryCycle)
        stopLe()
        runCatching { adapter?.let { if (it.isDiscovering) it.cancelDiscovery() } }
        runCatching { appContext.unregisterReceiver(classicReceiver) }
    }

    @Suppress("MissingPermission")
    fun setPowerSaving(value: Boolean) {
        if (value == powerSaving) return
        powerSaving = value
        reapply()
    }

    @Suppress("MissingPermission")
    fun setDriveMode(value: Boolean) {
        if (value == driveMode) return
        driveMode = value
        reapply()
    }

    @Suppress("MissingPermission")
    private fun reapply() {
        discoveryIntervalMs = when {
            driveMode -> DISCOVERY_DRIVE_MS
            powerSaving -> DISCOVERY_IDLE_MS
            else -> DISCOVERY_MOVING_MS
        }
        val a = adapter
        if (running && a != null && a.isEnabled) {
            stopLe()
            startLe(a)
        }
    }

    @Suppress("MissingPermission")
    private fun startLe(a: BluetoothAdapter) {
        if (leScanning || !a.isEnabled) return
        val lowPower = powerSaving && !driveMode
        val settings = ScanSettings.Builder()
            .setScanMode(if (lowPower) ScanSettings.SCAN_MODE_LOW_POWER else ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(
                when {
                    driveMode -> 0L // deliver immediately — maximum catch
                    powerSaving -> REPORT_DELAY_IDLE_MS
                    else -> REPORT_DELAY_MOVING_MS
                },
            )
            .build()
        runCatching { a.bluetoothLeScanner?.startScan(null, settings, leCallback) }
            .onSuccess { leScanning = true }
    }

    @Suppress("MissingPermission")
    private fun stopLe() {
        if (!leScanning) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(leCallback) }
        leScanning = false
    }

    @Suppress("MissingPermission")
    private fun emit(device: BluetoothDevice, rssi: Int?, kind: RadioKind, advName: String?) {
        val fix = location.current() ?: return
        val name = advName ?: if (canReadName()) runCatching { device.name }.getOrNull() else null
        onObservation(
            Observation(
                bssid = Observation.normaliseBssid(device.address),
                kind = kind,
                ssid = name?.trim()?.takeIf { it.isNotEmpty() },
                capabilities = if (kind == RadioKind.BT_LE) "Misc [BLE]" else "Misc [BT]",
                signalDbm = rssi,
                channel = null,
                frequencyMhz = null,
                lat = fix.latitude,
                lon = fix.longitude,
                altitudeM = if (fix.hasAltitude()) fix.altitude else null,
                accuracyM = if (fix.hasAccuracy()) fix.accuracy.toDouble() else null,
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val DISCOVERY_DRIVE_MS = 8_000L
        const val DISCOVERY_MOVING_MS = 13_000L
        const val DISCOVERY_IDLE_MS = 45_000L
        const val REPORT_DELAY_MOVING_MS = 2_000L
        const val REPORT_DELAY_IDLE_MS = 8_000L
    }
}
