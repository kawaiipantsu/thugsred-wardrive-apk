package red.thugs.wardrive

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import red.thugs.wardrive.data.Prefs
import red.thugs.wardrive.data.SessionStore
import red.thugs.wardrive.location.LocationProvider
import red.thugs.wardrive.net.LiveIngestManager

/** Process-wide singletons. The scanning service and the UI both read these. */
class WardriveApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var session: SessionStore
        private set
    lateinit var location: LocationProvider
        private set
    lateinit var liveIngest: LiveIngestManager
        private set

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _powerSaving = MutableStateFlow(false)
    val powerSaving: StateFlow<Boolean> = _powerSaving.asStateFlow()

    private val _driveMode = MutableStateFlow(false)
    val driveMode: StateFlow<Boolean> = _driveMode.asStateFlow()

    fun setScanning(value: Boolean) {
        _scanning.value = value
    }

    fun setPowerSaving(value: Boolean) {
        _powerSaving.value = value
    }

    fun setDriveMode(value: Boolean) {
        _driveMode.value = value
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        session = SessionStore(this)
        location = LocationProvider(this)
        liveIngest = LiveIngestManager(prefs, session)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SCAN,
                getString(R.string.channel_scan_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.channel_scan_desc) },
        )
    }

    companion object {
        const val CHANNEL_SCAN = "scan"

        @Volatile
        lateinit var instance: WardriveApp
            private set
    }
}
