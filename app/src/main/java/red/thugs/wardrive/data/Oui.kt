package red.thugs.wardrive.data

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * IEEE OUI → vendor lookup, from the compact `assets/oui.txt.gz` (first 3 octets
 * of the MAC → short vendor name). Loaded once, lazily.
 */
object Oui {

    @Volatile private var table: Map<String, String>? = null

    fun ensureLoaded(context: Context) {
        if (table != null) return
        synchronized(this) {
            if (table != null) return
            table = runCatching {
                context.applicationContext.assets.open("oui.txt.gz").use { raw ->
                    GZIPInputStream(raw).bufferedReader().useLines { lines ->
                        HashMap<String, String>(45_000).apply {
                            for (line in lines) {
                                val tab = line.indexOf('\t')
                                if (tab == 6) put(line.substring(0, 6), line.substring(tab + 1))
                            }
                        }
                    }
                }
            }.getOrDefault(emptyMap())
        }
    }

    /** Vendor for a BSSID/MAC, or null. [ensureLoaded] must have run. */
    fun vendor(bssid: String): String? {
        val hex = bssid.filter { it != ':' && it != '-' }.uppercase()
        if (hex.length < 6) return null
        return table?.get(hex.substring(0, 6))
    }

    /**
     * A locally-administered address — bit 1 of the first octet set. Phones and
     * many BLE devices use these as rotating privacy ("randomised") MACs, so the
     * OUI is meaningless and the address won't be stable enough to tail you.
     */
    fun isRandomised(bssid: String): Boolean {
        val first = bssid.take(2).toIntOrNull(16) ?: return false
        return (first and 0x02) != 0
    }
}
