package red.thugs.wardrive.data

import android.os.Build
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes a session to WiGLE CSV (`WigleWifi-1.4`), the format
 * `App\Parsers\WigleCsvParser` on wardrive.thugs.red reads.
 *
 * The server locates columns by header name, keeps rows whose `Type` is
 * `WIFI`/`WLAN` and silently skips the rest, so Bluetooth rows are written for a
 * complete local record but ignored on ingest.
 */
object WigleCsvWriter {

    private val TS: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private const val HEADER =
        "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type"

    fun write(target: File, observations: List<Observation>) {
        target.parentFile?.mkdirs()
        target.bufferedWriter().use { w -> write(w, observations) }
    }

    fun write(w: Writer, observations: List<Observation>) {
        w.write(
            "WigleWifi-1.4,appRelease=1.0,model=${Build.MODEL},release=${Build.VERSION.RELEASE}," +
                "device=${Build.DEVICE},display=${Build.DISPLAY},board=${Build.BOARD},brand=${Build.BRAND}\n",
        )
        w.write(HEADER)
        w.write("\n")
        val fmt = TS
        for (o in observations) {
            if (!o.hasFix) continue
            w.write(
                listOf(
                    o.bssid,
                    o.ssid.orEmpty(),
                    o.capabilities ?: authModeFor(o.kind),
                    fmt.format(Date(o.timestampMs)),
                    o.channel?.toString().orEmpty(),
                    o.signalDbm?.toString().orEmpty(),
                    trimZeros(o.lat, 8),
                    trimZeros(o.lon, 8),
                    o.altitudeM?.let { trimZeros(it, 1) }.orEmpty(),
                    o.accuracyM?.let { trimZeros(it, 1) }.orEmpty(),
                    o.kind.wigleType,
                ).joinToString(",") { csv(it) },
            )
            w.write("\n")
        }
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
