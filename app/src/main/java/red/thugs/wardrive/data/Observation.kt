package red.thugs.wardrive.data

/** The kind of radio an [Observation] came from. */
enum class RadioKind {
    /** A WiFi access point / beacon. The only kind wardrive.thugs.red ingests. */
    WIFI_AP,

    /** A classic (BR/EDR) Bluetooth device found by discovery. */
    BT_CLASSIC,

    /** A Bluetooth Low Energy advertiser. */
    BT_LE,
    ;

    val isBluetooth: Boolean get() = this == BT_CLASSIC || this == BT_LE

    /** The WiGLE CSV `Type` column value. Server keeps WIFI/WLAN, skips the rest. */
    val wigleType: String
        get() = when (this) {
            WIFI_AP -> "WIFI"
            BT_CLASSIC -> "BT"
            BT_LE -> "BLE"
        }
}

/**
 * One sighting of one device at one place and time. Repeated sightings of the
 * same BSSID are kept (they draw a driving path on the server), but the in-app
 * list collapses them to the most recent per identifier.
 */
data class Observation(
    /** MAC / BSSID, upper-case colon-separated, e.g. `AA:BB:CC:DD:EE:FF`. */
    val bssid: String,
    val kind: RadioKind,
    val ssid: String?,
    /** Free-text capabilities/auth string, e.g. `[WPA2-PSK-CCMP][ESS]`. */
    val capabilities: String?,
    val signalDbm: Int?,
    val channel: Int?,
    val frequencyMhz: Int?,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val accuracyM: Double?,
    /** Epoch millis of the fix this sighting was tied to. */
    val timestampMs: Long,
) {
    /** True once we have a real position (the server rejects the 0,0 no-fix artefact). */
    val hasFix: Boolean get() = (lat != 0.0 || lon != 0.0) && lat in -90.0..90.0 && lon in -180.0..180.0

    val isWifi: Boolean get() = kind == RadioKind.WIFI_AP

    companion object {
        fun normaliseBssid(raw: String): String =
            raw.trim().uppercase().let { s ->
                if (s.contains(':') || s.length != 12) s
                else s.chunked(2).joinToString(":")
            }
    }
}
