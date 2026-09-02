package red.thugs.wardrive.data

/** Derived WiFi facts used by the Stats and Scope views. Pure functions. */
object WifiInfo {

    enum class Enc { OPEN, WEP, WPA, WPA2, WPA3, UNKNOWN }

    fun enc(capabilities: String?): Enc {
        val c = capabilities?.uppercase() ?: return Enc.UNKNOWN
        return when {
            c.contains("WPA3") || c.contains("SAE") -> Enc.WPA3
            c.contains("WPA2") || c.contains("RSN") -> Enc.WPA2
            c.contains("WPA") -> Enc.WPA
            c.contains("WEP") -> Enc.WEP
            else -> Enc.OPEN
        }
    }

    /** 2, 5 or 6 (GHz), or 0 if unknown. */
    fun band(freqMhz: Int?): Int = when (freqMhz) {
        null -> 0
        in 2401..2500 -> 2
        in 4901..5899 -> 5
        in 5900..7125 -> 6
        else -> 0
    }

    /** For a 2.4 GHz plan: how many APs sit on or adjacent to channel [ch] (±2 channels overlap). */
    fun crowding24(observations: List<Observation>, ch: Int): Int =
        observations.count {
            it.kind == RadioKind.WIFI_AP && band(it.frequencyMhz) == 2 &&
                it.channel != null && kotlin.math.abs(it.channel - ch) <= 2
        }

    /** The least-crowded of the non-overlapping 2.4 GHz channels {1, 6, 11}. */
    fun bestChannel24(observations: List<Observation>): Pair<Int, Int>? {
        val has24 = observations.any { it.kind == RadioKind.WIFI_AP && band(it.frequencyMhz) == 2 }
        if (!has24) return null
        return listOf(1, 6, 11).map { it to crowding24(observations, it) }.minByOrNull { it.second }
    }
}
