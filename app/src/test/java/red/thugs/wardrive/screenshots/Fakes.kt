package red.thugs.wardrive.screenshots

import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind
import red.thugs.wardrive.data.SessionCounts
import red.thugs.wardrive.data.TrackPoint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Deterministic sample data so the screenshots look like a real drive through Copenhagen. */
object Fakes {

    private val wifiSsids = listOf(
        "HomeNet_5G", "TELE2_9F3A", "", "AndroidAP", "eduroam", "GuestWiFi",
        "Loke-Kaffe", "NETGEAR-Guest", "FRITZ!Box 7590", "Sonos-Living", "", "Kbh-Free-WiFi",
    )
    private val btNames = listOf("Mi Band 7", "Tile", "JBL Flip 6", "Galaxy Buds", "", "Forerunner 255")
    private val caps = listOf(
        "[WPA2-PSK-CCMP][ESS]", "[WPA3-SAE][ESS]", "[ESS]",
        "[WPA2-PSK-CCMP][WPS][ESS]", "[WPA2-EAP-CCMP][ESS]",
    )

    fun observations(n: Int = 34, seed: Long = 7): List<Observation> {
        val r = Random(seed)
        val base = 1_756_800_000_000L
        return (0 until n).map { i ->
            val kind = when {
                i % 5 == 0 -> RadioKind.BT_LE
                i % 11 == 0 -> RadioKind.BT_CLASSIC
                else -> RadioKind.WIFI_AP
            }
            val t = i / n.toDouble()
            val lat = 55.665 + t * 0.02 + 0.004 * sin(t * 10) + (r.nextDouble() - 0.5) * 0.0015
            val lon = 12.545 + t * 0.03 + 0.006 * cos(t * 7) + (r.nextDouble() - 0.5) * 0.002
            Observation(
                bssid = (0 until 6).joinToString(":") { "%02X".format(r.nextInt(256)) },
                kind = kind,
                ssid = when (kind) {
                    RadioKind.WIFI_AP -> wifiSsids.random(r)
                    else -> btNames.random(r)
                }.ifBlank { null },
                capabilities = when (kind) {
                    RadioKind.WIFI_AP -> caps.random(r)
                    RadioKind.BT_LE -> "Misc [BLE]"
                    RadioKind.BT_CLASSIC -> "Misc [BT]"
                },
                signalDbm = -(35 + r.nextInt(62)),
                channel = if (kind == RadioKind.WIFI_AP) listOf(1, 6, 11, 36, 44, 100, 149).random(r) else null,
                frequencyMhz = null,
                lat = lat,
                lon = lon,
                altitudeM = 12.0,
                accuracyM = 4.0 + r.nextInt(8),
                timestampMs = base - i * 37_000L,
            )
        }
    }

    fun track(n: Int = 140): List<TrackPoint> = (0 until n).map { i ->
        val t = i / n.toDouble()
        doubleArrayOf(
            55.665 + t * 0.02 + 0.004 * sin(t * 10),
            12.545 + t * 0.03 + 0.006 * cos(t * 7),
        )
    }

    fun counts(obs: List<Observation>) = SessionCounts(
        wifiAp = obs.count { it.kind == RadioKind.WIFI_AP },
        btClassic = obs.count { it.kind == RadioKind.BT_CLASSIC },
        btLe = obs.count { it.kind == RadioKind.BT_LE },
        withFix = obs.count { it.hasFix },
    )
}
