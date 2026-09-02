package red.thugs.wardrive.screenshots

import red.thugs.wardrive.data.ChannelLoad
import red.thugs.wardrive.data.CongestionSample
import red.thugs.wardrive.data.GrowthPoint
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
            val ch = if (kind == RadioKind.WIFI_AP) listOf(1, 6, 6, 11, 36, 44, 100, 149).random(r) else null
            val freq = ch?.let(::freqFor)
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
                channel = ch,
                frequencyMhz = freq,
                lat = lat,
                lon = lon,
                altitudeM = 12.0,
                accuracyM = 4.0 + r.nextInt(8),
                timestampMs = base - i * 37_000L,
                channelWidthMhz = when {
                    kind != RadioKind.WIFI_AP -> null
                    (ch ?: 0) <= 14 -> listOf(20, 20, 40).random(r)
                    else -> listOf(20, 40, 80).random(r)
                },
                centerFreqMhz = freq,
            )
        }
    }

    private fun freqFor(ch: Int): Int = when {
        ch == 14 -> 2484
        ch in 1..13 -> 2407 + ch * 5
        else -> 5000 + ch * 5
    }

    fun congestion(samples: Int = 90): List<CongestionSample> {
        val r = Random(11)
        val freqs = listOf(2412, 2437, 2462, 5180, 5220, 5745)
        val t0 = 1_756_800_000_000L - samples * 1000L
        return (0 until samples).map { s ->
            val load = freqs.associateWith { f ->
                val base = when (f) { 2437 -> 6; 2412 -> 3; 5180 -> 4; else -> 2 }
                val n = (base + (2 * sin(s / 9.0 + f / 500.0)) + r.nextInt(2)).toInt().coerceAtLeast(0)
                ChannelLoad(n, -(45 + r.nextInt(40)))
            }
            CongestionSample(t0 + s * 1000L, load)
        }
    }

    fun growth(samples: Int = 120): List<GrowthPoint> {
        val t0 = 1_756_800_000_000L - samples * 1000L
        var c = 0L
        return (0 until samples).map { s ->
            if (s % 3 == 0) c += (1..3).random()
            longArrayOf(t0 + s * 1000L, c)
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
