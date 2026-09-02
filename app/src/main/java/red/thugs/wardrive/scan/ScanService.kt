package red.thugs.wardrive.scan

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import red.thugs.wardrive.MainActivity
import red.thugs.wardrive.R
import red.thugs.wardrive.WardriveApp

/**
 * Foreground service that owns the radios for the length of a drive, so
 * scanning survives the screen going off. Start/stop it from the UI with the
 * [ACTION_START] / [ACTION_STOP] intents.
 *
 * Battery: a partial wake lock keeps the scan timers firing with the screen
 * off, and a tuning loop drops WiFi + BLE into low-power cadence whenever the
 * car has stopped moving, bringing them back the moment it does.
 */
class ScanService : Service() {

    private lateinit var app: WardriveApp
    private lateinit var wifi: WifiScanner
    private lateinit var bt: BluetoothScanner
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false
    private var lastPowerSaving: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        app = application as WardriveApp
        wifi = WifiScanner(this, app.location) { app.session.add(it) }
        bt = BluetoothScanner(this, app.location) { app.session.add(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            else -> startEverything()
        }
        return START_STICKY
    }

    private fun startEverything() {
        if (started) return
        started = true
        startForeground(
            NOTIF_ID,
            buildNotification("Starting…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wardrive:scan")
            .apply { setReferenceCounted(false); acquire() }

        app.location.start()
        wifi.start()
        bt.start()
        app.setScanning(true)
        handler.post(tuning)

        var lastTotal = -1
        combine(app.session.counts, app.session.sightingCount) { c, s -> c to s }
            .onEach { (c, s) ->
                val idle = if (app.powerSaving.value) " · idle" else ""
                notify(
                    "WiFi ${c.wifiAp} · BT ${c.btClassic} · BLE ${c.btLe} · $s sightings" +
                        (if (app.location.current() == null) " · no GPS fix" else "") + idle,
                )
                if (lastTotal in 0 until c.total && app.prefs.newDeviceHaptic) tick()
                lastTotal = c.total
            }
            .launchIn(scope)
    }

    private fun tick() {
        val vib = (getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator) ?: return
        runCatching {
            vib.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** Every [TUNE_MS] decide whether we are moving and set the radio cadence. */
    private val tuning = object : Runnable {
        override fun run() {
            if (!started) return
            val saving = !app.location.isMoving()
            if (saving != lastPowerSaving) {
                lastPowerSaving = saving
                wifi.setPowerSaving(saving)
                bt.setPowerSaving(saving)
                app.setPowerSaving(saving)
            }
            handler.postDelayed(this, TUNE_MS)
        }
    }

    private fun stopEverything() {
        handler.removeCallbacks(tuning)
        wifi.stop()
        bt.stop()
        app.location.stop()
        app.setScanning(false)
        app.setPowerSaving(false)
        lastPowerSaving = null
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        started = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (started) stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notify(text: String) =
        app.getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text))

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, WardriveApp.CHANNEL_SCAN)
            .setContentTitle("Wardrive session running")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        const val ACTION_START = "red.thugs.wardrive.START"
        const val ACTION_STOP = "red.thugs.wardrive.STOP"
        private const val NOTIF_ID = 42
        private const val TUNE_MS = 15_000L

        fun start(context: Context) {
            val i = Intent(context, ScanService::class.java).setAction(ACTION_START)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScanService::class.java).setAction(ACTION_STOP))
        }
    }
}
