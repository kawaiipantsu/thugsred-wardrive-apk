package red.thugs.wardrive.data

import android.content.Context
import android.util.Log
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
import java.io.Writer
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

/** APs on one WiFi frequency at one instant. */
data class ChannelLoad(val count: Int, val strongestDbm: Int)

/** A 1 Hz snapshot of WiFi congestion, keyed by centre frequency in MHz. */
data class CongestionSample(val tMs: Long, val load: Map<Int, ChannelLoad>)

/** A 1 Hz point on the "devices found so far" curve: [0]=epochMs, [1]=count. */
typealias GrowthPoint = LongArray

/** A device that has stayed with you across several separated points on your route. */
data class Follower(
    val kind: RadioKind,
    val bssid: String,
    val name: String?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val sightings: Int,
    /** Metres of your route travelled between first and last sighting of this device. */
    val spanMeters: Int,
    /** Metres you've moved since you last saw it. */
    val lastSeenMetersAgo: Int,
    /** Distinct ~10 m-separated points where it was seen. */
    val waypoints: Int,
) {
    val score: Double get() = waypoints * 2.0 + spanMeters / 60.0 - lastSeenMetersAgo / 40.0
}

/**
 * Everything found in the current run.
 *
 * Two collections on purpose: [observations] is one row per device (latest
 * sighting, newest first) for the on-screen list and footer; [allSightings] is
 * every sighting in order, which is what the WiGLE CSV needs.
 *
 * Battery: [add] is O(1) and only flips a dirty flag; the derived flows, the
 * crash-safe CSV append and the history buffers are all updated at most once a
 * second by a single background coroutine.
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

    private val _congestion = MutableStateFlow<List<CongestionSample>>(emptyList())
    /** Rolling ~4-minute history of per-frequency WiFi congestion, for the Scope view. */
    val congestion: StateFlow<List<CongestionSample>> = _congestion.asStateFlow()

    private val _growth = MutableStateFlow<List<GrowthPoint>>(emptyList())
    /** Rolling history of distinct-device count over time, for the Stats sparkline. */
    val growth: StateFlow<List<GrowthPoint>> = _growth.asStateFlow()

    private val _followers = MutableStateFlow<List<Follower>>(emptyList())
    /** Devices that look like they're travelling with you — for Spy mode. */
    val followers: StateFlow<List<Follower>> = _followers.asStateFlow()

    private val _trackedDevices = MutableStateFlow(0)
    /** Distinct devices seen at ≥1 fix this session (the pool Spy mode watches). */
    val trackedDevices: StateFlow<Int> = _trackedDevices.asStateFlow()

    @Volatile
    var startedAtMs: Long = System.currentTimeMillis()
        private set

    // Crash-safe streaming CSV
    private var liveWriter: Writer? = null
    private var liveFile: File = sessionFile(startedAtMs)
    private var flushedCount = 0

    private val congestionHistory = ArrayDeque<CongestionSample>()
    private val growthHistory = ArrayDeque<GrowthPoint>()

    // Follower tracking
    private var pathMeters = 0.0
    private class DevTrack(
        val kind: RadioKind,
        val firstMs: Long,
        var lastMs: Long,
        var count: Int,
        val firstPath: Double,
        var lastPath: Double,
        var lastWaypointPath: Double,
        var waypoints: Int,
        var name: String?,
    )
    private val devTracks = HashMap<String, DevTrack>()

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
            if (o.hasFix) {
                appendTrackLocked(o.lat, o.lon)
                trackDeviceLocked(o)
            }
        }
        if (o.isWifi && o.hasFix) {
            synchronized(liveBuffer) { liveBuffer.addLast(o) }
        }
        dirty.set(true)
    }

    private fun trackDeviceLocked(o: Observation) {
        val k = key(o)
        val t = devTracks.getOrPut(k) {
            DevTrack(o.kind, o.timestampMs, o.timestampMs, 0, pathMeters, pathMeters, pathMeters, 0, o.ssid)
        }
        t.lastMs = o.timestampMs
        t.count++
        t.lastPath = pathMeters
        if (o.ssid != null && t.name == null) t.name = o.ssid
        if (pathMeters - t.lastWaypointPath >= FOLLOW_WAYPOINT_M) {
            t.lastWaypointPath = pathMeters
            t.waypoints++
        }
    }

    fun addAll(list: List<Observation>) = list.forEach(::add)

    private fun appendTrackLocked(lat: Double, lon: Double) {
        if (!lastTrackLat.isNaN()) {
            val d = haversineMeters(lastTrackLat, lastTrackLon, lat, lon)
            if (d < TRACK_MIN_MOVE_M) return
            pathMeters += d
        }
        lastTrackLat = lat
        lastTrackLon = lon
        trackList.add(doubleArrayOf(lat, lon))
    }

    private fun recompute() {
        val rows: List<Observation>
        val sightings: Int
        val trackSnapshot: List<TrackPoint>
        val newForDisk: List<Observation>
        synchronized(lock) {
            rows = latest.values.sortedByDescending { it.timestampMs }
            sightings = allSightings.size
            trackSnapshot = if (trackList.size != _track.value.size) ArrayList(trackList) else _track.value
            newForDisk = if (flushedCount < allSightings.size) {
                ArrayList(allSightings.subList(flushedCount, allSightings.size)).also { flushedCount = allSightings.size }
            } else {
                emptyList()
            }
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

        appendToDisk(newForDisk)
        pushHistory(rows)
        computeFollowers()
    }

    private fun computeFollowers() {
        val here: Double
        val followers = ArrayList<Follower>()
        synchronized(lock) {
            here = pathMeters
            _trackedDevices.value = devTracks.size
            for ((k, t) in devTracks) {
                val span = t.lastPath - t.firstPath
                if (t.waypoints < FOLLOW_MIN_WAYPOINTS) continue
                if (span < FOLLOW_MIN_SPAN_M) continue
                if (t.lastMs - t.firstMs < FOLLOW_MIN_MS) continue
                if (here - t.lastPath > FOLLOW_STALE_M) continue // lost them a while ago
                followers += Follower(
                    kind = t.kind,
                    bssid = k.substringAfter('|'),
                    name = t.name,
                    firstSeenMs = t.firstMs,
                    lastSeenMs = t.lastMs,
                    sightings = t.count,
                    spanMeters = span.toInt(),
                    lastSeenMetersAgo = (here - t.lastPath).toInt(),
                    waypoints = t.waypoints,
                )
            }
        }
        _followers.value = followers.sortedByDescending { it.score }
    }

    private fun appendToDisk(newSightings: List<Observation>) {
        if (newSightings.isEmpty()) return
        val w = liveWriter ?: runCatching { openLiveWriter() }.getOrElse {
            Log.w("SessionStore", "cannot open session file", it)
            return
        }
        runCatching {
            for (o in newSightings) WigleCsvWriter.row(o)?.let { w.write(it); w.write("\n") }
            w.flush()
        }.onFailure { Log.w("SessionStore", "session file append failed", it) }
    }

    private fun openLiveWriter(): Writer {
        sessionsDir.mkdirs()
        val w = liveFile.bufferedWriter()
        w.write(WigleCsvWriter.preHeader()); w.write("\n")
        w.write(WigleCsvWriter.HEADER); w.write("\n")
        w.flush()
        liveWriter = w
        return w
    }

    private fun pushHistory(rows: List<Observation>) {
        val now = System.currentTimeMillis()

        val load = HashMap<Int, IntArray>() // freq -> [count, strongestDbm]
        for (o in rows) {
            if (o.kind != RadioKind.WIFI_AP) continue
            val f = o.frequencyMhz ?: continue
            val e = load.getOrPut(f) { intArrayOf(0, -999) }
            e[0]++
            val s = o.signalDbm ?: -999
            if (s > e[1]) e[1] = s
        }
        congestionHistory.addLast(CongestionSample(now, load.mapValues { ChannelLoad(it.value[0], it.value[1]) }))
        while (congestionHistory.size > MAX_HISTORY) congestionHistory.removeFirst()
        _congestion.value = ArrayList(congestionHistory)

        growthHistory.addLast(longArrayOf(now, rows.size.toLong()))
        while (growthHistory.size > MAX_HISTORY) growthHistory.removeFirst()
        _growth.value = ArrayList(growthHistory)
    }

    fun reset() {
        synchronized(lock) {
            latest.clear()
            allSightings.clear()
            trackList.clear()
            devTracks.clear()
            pathMeters = 0.0
            lastTrackLat = Double.NaN
            lastTrackLon = Double.NaN
            flushedCount = 0
            runCatching { liveWriter?.close() }
            liveWriter = null
            startedAtMs = System.currentTimeMillis()
            liveFile = sessionFile(startedAtMs)
        }
        synchronized(liveBuffer) { liveBuffer.clear() }
        congestionHistory.clear()
        growthHistory.clear()
        _congestion.value = emptyList()
        _growth.value = emptyList()
        _followers.value = emptyList()
        _trackedDevices.value = 0
        recompute()
    }

    fun snapshotSightings(): List<Observation> = synchronized(lock) { ArrayList(allSightings) }

    fun isEmpty(): Boolean = synchronized(lock) { allSightings.isEmpty() }

    /** Every sighting of one device, oldest first — for the detail sheet. */
    fun sightingsFor(kind: RadioKind, bssid: String): List<Observation> = synchronized(lock) {
        allSightings.filter { it.kind == kind && it.bssid == bssid }
    }

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

    private fun sessionFile(startMs: Long): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(startMs)
        return File(File(appContext.filesDir, "sessions"), "wardrive-$stamp.csv")
    }

    /**
     * The current session's file, flushed. It is written continuously as the
     * drive happens (crash-safe), so this just makes sure everything scanned so
     * far is on disk and hands back the path.
     */
    fun exportCurrentCsv(): File {
        synchronized(lock) {
            val pending = if (flushedCount < allSightings.size) {
                ArrayList(allSightings.subList(flushedCount, allSightings.size)).also { flushedCount = allSightings.size }
            } else {
                emptyList()
            }
            appendToDisk(pending)
            runCatching { liveWriter?.flush() }
        }
        return liveFile
    }

    fun savedSessions(): List<File> =
        sessionsDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    private companion object {
        const val FLUSH_MS = 1_000L
        const val TRACK_MIN_MOVE_M = 5.0
        const val MAX_HISTORY = 240 // ~4 minutes at 1 Hz

        // Follower thresholds
        const val FOLLOW_WAYPOINT_M = 10.0    // distance that separates two "waypoints"
        const val FOLLOW_MIN_WAYPOINTS = 3    // seen at this many separated points
        const val FOLLOW_MIN_SPAN_M = 40.0    // over at least this much of your route
        const val FOLLOW_MIN_MS = 45_000L     // and this long
        const val FOLLOW_STALE_M = 120.0      // drop once you've moved this far past the last sighting
    }
}
