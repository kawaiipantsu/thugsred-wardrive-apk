package red.thugs.wardrive.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import red.thugs.wardrive.WardriveApp
import red.thugs.wardrive.net.WardriveClient
import red.thugs.wardrive.net.WardriveException
import red.thugs.wardrive.scan.ScanService
import java.io.File

enum class Screen { LIST, MAP, STATS, SCOPE, ABOUT, SETTINGS }

/** The four screens reachable from the quick-nav strip. */
val QUICK_NAV_SCREENS = listOf(Screen.LIST, Screen.MAP, Screen.STATS, Screen.SCOPE)

/** Which action the credentials dialog is collecting a login for. */
enum class CredentialPurpose { GO_LIVE, UPLOAD }

sealed interface Busy {
    data object None : Busy
    data class Working(val what: String) : Busy
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appx get() = getApplication<WardriveApp>()
    val session get() = appx.session
    val prefs get() = appx.prefs
    val scanning get() = appx.scanning
    val powerSaving get() = appx.powerSaving
    val driveMode get() = appx.driveMode
    val liveStatus get() = appx.liveIngest.status
    val track get() = appx.session.track
    val congestion get() = appx.session.congestion
    val growth get() = appx.session.growth

    val currentLatLon = appx.location.location
        .map { it?.let { l -> l.latitude to l.longitude } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentAccuracy = appx.location.location
        .map { it?.takeIf { l -> l.hasAccuracy() }?.accuracy }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _busy = kotlinx.coroutines.flow.MutableStateFlow<Busy>(Busy.None)
    val busy = _busy

    private fun toast(msg: String) {
        viewModelScope.launch { _events.send(msg) }
    }

    fun toastPublic(msg: String) = toast(msg)

    // -- Scanning -------------------------------------------------------

    fun startScan() = ScanService.start(appx)

    fun stopScan() {
        appx.liveIngest.stop()
        ScanService.stop(appx)
    }

    fun resetSession() {
        session.reset()
        toast("Session cleared.")
    }

    /** Drive mode: fastest GPS + full-power scanning, no idle back-off. */
    fun setDriveMode(on: Boolean) {
        prefs.driveMode = on
        appx.setDriveMode(on && scanning.value)
        if (scanning.value) ScanService.reconfigure(appx)
        toast(if (on) "Drive mode on — GPS + radios at full rate. Keep it charged." else "Drive mode off.")
    }

    // -- Go Live ------------------------------------------------------

    /** True when Go Live can start straight away without asking for a password. */
    val hasSavedToken: Boolean get() = prefs.ingestToken != null

    /** Start streaming with the cached token, no dialog. Falls back to sign-in on 401. */
    fun startLiveWithSavedToken() {
        if (prefs.ingestToken == null) return
        if (!scanning.value) startScan()
        appx.liveIngest.start()
        toast("Live. Observations will stream to the map as you drive.")
    }

    fun goLive(username: String, password: String, remember: Boolean) {
        prefs.saveCredentials(username, password, remember)
        viewModelScope.launch {
            _busy.value = Busy.Working("Signing in…")
            try {
                val client = WardriveClient(prefs.baseUrl)
                client.login(username, password)
                _busy.value = Busy.Working("Creating an ingest token…")
                val token = client.ensureIngestToken("THUGS Wardrive app — ${android.os.Build.MODEL}")
                prefs.ingestToken = token
                if (!scanning.value) startScan()
                appx.liveIngest.start()
                toast("Live. Observations will stream to the map as you drive.")
            } catch (e: WardriveException) {
                toast(e.message ?: "Go Live failed.")
            } catch (e: Exception) {
                toast("Go Live failed: ${e.message}")
            } finally {
                _busy.value = Busy.None
            }
        }
    }

    fun stopLive() {
        appx.liveIngest.stop()
        toast("Live ingest stopped.")
    }

    fun forgetLogin() {
        appx.liveIngest.stop()
        prefs.forgetLogin()
        toast("Saved login cleared.")
    }

    // -- Upload -----------------------------------------------------

    /** [existing] null means "export the current session and upload that". */
    fun upload(username: String, password: String, remember: Boolean, existing: File?) {
        prefs.saveCredentials(username, password, remember)
        viewModelScope.launch {
            _busy.value = Busy.Working("Preparing file…")
            try {
                val file = existing ?: run {
                    if (session.isEmpty()) {
                        toast("Nothing scanned yet.")
                        return@launch
                    }
                    session.exportCurrentCsv()
                }
                _busy.value = Busy.Working("Signing in…")
                val client = WardriveClient(prefs.baseUrl)
                client.login(username, password)
                _busy.value = Busy.Working("Uploading ${file.name}…")
                val id = client.uploadCsv(file)
                toast(
                    if (id.length == 36) "Uploaded. Queued for moderation (id ${id.take(8)}…)."
                    else "Uploaded. Queued for moderation.",
                )
            } catch (e: WardriveException) {
                toast(e.message ?: "Upload failed.")
            } catch (e: Exception) {
                toast("Upload failed: ${e.message}")
            } finally {
                _busy.value = Busy.None
            }
        }
    }

    fun exportOnly() {
        viewModelScope.launch {
            if (session.isEmpty()) {
                toast("Nothing scanned yet.")
                return@launch
            }
            val f = session.exportCurrentCsv()
            toast("Saved ${f.name} (${f.length()} bytes) to app storage.")
        }
    }

    /** Hand the current session's CSV to the Android share sheet. */
    fun shareCsv() {
        if (session.isEmpty()) {
            toast("Nothing scanned yet.")
            return
        }
        val f = session.exportCurrentCsv()
        val uri = FileProvider.getUriForFile(appx, "${appx.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, f.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            appx.startActivity(
                Intent.createChooser(send, "Share ${f.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { toast("No app to share the file with.") }
    }

    fun savedSessions(): List<File> = session.savedSessions()
}
