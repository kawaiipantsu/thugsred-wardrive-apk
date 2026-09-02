package red.thugs.wardrive.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class SessionCounts(
    val wifiAp: Int = 0,
    val btClassic: Int = 0,
    val btLe: Int = 0,
    val withFix: Int = 0,
) {
    val total: Int get() = wifiAp + btClassic + btLe
}

/** A location on the driving track: [0]=lat, [1]=lon. */
typealias TrackPoint = DoubleArray

/**
 * Everything found in the current run.
 *
 * Two collections on purpose: [observations] is one row per device (latest
 * sighting, newest first) for the on-screen list and footer; [allSightings] is
 * every sighting in order, which is what the WiGLE CSV needs so repeated
 * sightings of one BSSID draw a driving path.
 *
 * Battery: [add] is O(1) and only flips a dirty flag; the derived flows are
 * recomputed at most once per second by a single background coroutine, so a
 * burst of a few hundred scan results does not trigger a few hundred re-sorts
 * and recompositions.
 */
class SessionStore(private val appContext: Context) {

    private val lock = Any()
    private val latest = LinkedHashMap<String, Observation>()
    private val allSightings = ArrayList<Observation>()
    private val trackList = ArrayList<TrackPoint>()
    private var lastTrackLat = Double.NaN
    private var lastTrackLon = Double.NaN

    private val liveBuffer = ArrayDeque<Observation>()
    private val liveMutex = Mutex()

    private val dirty = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _observations = MutableStateFlow<List<Observation>>(emptyList())
    val observations: StateFlow<List<Observation>> = _observations.asStateFlow()

    private val _counts = MutableStateFlow(SessionCounts())
    val counts: StateFlow<SessionCounts> = _counts.asStateFlow()

    private val _sightingCount = MutableStateFlow(0)
    val sightingCount: StateFlow<Int> = _sightingCount.asStateFlow()

    private val _track = MutableStateFlow<List<TrackPoint>>(emptyList())
    val track: StateFlow<List<TrackPoint>> = _track.asStateFlow()

    val startedAtMs: Long = System.currentTimeMillis()

    init {
        scope.launch {
            while (isActive) {
                delay(FLUSH_MS)
                if (dirty.getAndSet(false)) recompute()
            }
        }
    }

    private fun key(o: Observation) = o.kind.name + "|" + o.bssid

    fun add(o: Observation) {
        synchronized(lock) {
            allSightings.add(o)
            latest[key(o)] = o
            if (o.hasFix) appendTrackLocked(o.lat, o.lon)
        }
        if (o.isWifi && o.hasFix) {
            synchronized(liveBuffer) { liveBuffer.addLast(o) }
        }
        dirty.set(true)
    }

    fun addAll(list: List<Observation>) = list.forEach(::add)

    private fun appendTrackLocked(lat: Double, lon: Double) {
        if (!lastTrackLat.isNaN() && haversineMeters(lastTrackLat, lastTrackLon, lat, lon) < TRACK_MIN_MOVE_M) {
            return
        }
        lastTrackLat = lat
        lastTrackLon = lon
        trackList.add(doubleArrayOf(lat, lon))
    }

    private fun recompute() {
        val rows: List<Observation>
        val sightings: Int
        val trackSnapshot: List<TrackPoint>
        synchronized(lock) {
            rows = latest.values.sortedByDescending { it.timestampMs }
            sightings = allSightings.size
            trackSnapshot = if (trackList.size != _track.value.size) ArrayList(trackList) else _track.value
        }
        _observations.value = rows
        _counts.value = SessionCounts(
            wifiAp = rows.count { it.kind == RadioKind.WIFI_AP },
            btClassic = rows.count { it.kind == RadioKind.BT_CLASSIC },
            btLe = rows.count { it.kind == RadioKind.BT_LE },
            withFix = rows.count { it.hasFix },
        )
        _sightingCount.value = sightings
        if (trackSnapshot !== _track.value) _track.value = trackSnapshot
    }

    fun reset() {
        synchronized(lock) {
            latest.clear()
            allSightings.clear()
            trackList.clear()
            lastTrackLat = Double.NaN
            lastTrackLon = Double.NaN
        }
        synchronized(liveBuffer) { liveBuffer.clear() }
        recompute()
    }

    fun snapshotSightings(): List<Observation> = synchronized(lock) { ArrayList(allSightings) }

    fun isEmpty(): Boolean = synchronized(lock) { allSightings.isEmpty() }

    // -- Live ingest buffer -------------------------------------------------

    suspend fun drainForLive(max: Int): List<Observation> = liveMutex.withLock {
        synchronized(liveBuffer) {
            val out = ArrayList<Observation>(minOf(max, liveBuffer.size))
            while (out.size < max && liveBuffer.isNotEmpty()) out.add(liveBuffer.removeFirst())
            out
        }
    }

    suspend fun requeueForLive(items: List<Observation>) = liveMutex.withLock {
        synchronized(liveBuffer) {
            for (i in items.indices.reversed()) liveBuffer.addFirst(items[i])
        }
    }

    fun livePending(): Int = synchronized(liveBuffer) { liveBuffer.size }

    // -- Persistence ------------------------------------------------------

    private val sessionsDir: File get() = File(appContext.filesDir, "sessions").apply { mkdirs() }

    fun exportCurrentCsv(): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(startedAtMs)
        val file = File(sessionsDir, "wardrive-$stamp.csv")
        WigleCsvWriter.write(file, snapshotSightings())
        return file
    }

    fun savedSessions(): List<File> =
        sessionsDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    private companion object {
        const val FLUSH_MS = 1_000L
        const val TRACK_MIN_MOVE_M = 5.0
    }
}
