package red.thugs.wardrive.data

import android.os.Build
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * WiGLE CSV (`WigleWifi-1.4`) — the format `App\Parsers\WigleCsvParser` on
 * wardrive.thugs.red reads. The server locates columns by header name, keeps
 * rows whose `Type` is `WIFI`/`WLAN` and skips the rest, so Bluetooth rows are
 * written for a complete local record but ignored on ingest.
 *
 * [preHeader] + [HEADER] + [row] are exposed so a session can be streamed to
 * disk as it is scanned (crash-safe) with identical formatting to a batch write.
 */
object WigleCsvWriter {

    const val HEADER =
        "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type"

    fun preHeader(): String =
        "WigleWifi-1.4,appRelease=1.0,model=${Build.MODEL},release=${Build.VERSION.RELEASE}," +
            "device=${Build.DEVICE},display=${Build.DISPLAY},board=${Build.BOARD},brand=${Build.BRAND}"

    /** One CSV line for an observation, or null if it has no usable fix. */
    fun row(o: Observation): String? {
        if (!o.hasFix) return null
        return listOf(
            o.bssid,
            o.ssid.orEmpty(),
            o.capabilities ?: authModeFor(o.kind),
            ts().format(Date(o.timestampMs)),
            o.channel?.toString().orEmpty(),
            o.signalDbm?.toString().orEmpty(),
            trimZeros(o.lat, 8),
            trimZeros(o.lon, 8),
            o.altitudeM?.let { trimZeros(it, 1) }.orEmpty(),
            o.accuracyM?.let { trimZeros(it, 1) }.orEmpty(),
            o.kind.wigleType,
        ).joinToString(",") { csv(it) }
    }

    fun write(target: File, observations: List<Observation>) {
        target.parentFile?.mkdirs()
        target.bufferedWriter().use { w -> write(w, observations) }
    }

    fun write(w: Writer, observations: List<Observation>) {
        w.write(preHeader()); w.write("\n")
        w.write(HEADER); w.write("\n")
        for (o in observations) {
            val line = row(o) ?: continue
            w.write(line); w.write("\n")
        }
    }

    private fun ts() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun authModeFor(kind: RadioKind): String = when (kind) {
        RadioKind.WIFI_AP -> "[ESS]"
        RadioKind.BT_CLASSIC -> "Misc [BT]"
        RadioKind.BT_LE -> "Misc [BLE]"
    }

    private fun trimZeros(v: Double, maxDecimals: Int): String {
        val s = String.format(Locale.US, "%.${maxDecimals}f", v)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }

    private fun csv(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
