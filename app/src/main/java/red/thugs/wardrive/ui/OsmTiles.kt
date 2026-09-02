package red.thugs.wardrive.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import red.thugs.wardrive.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenStreetMap raster tiles for the map, opt-in. No API key. Tiles are cached
 * in memory (LRU) and on disk (`cacheDir/osm-tiles/z/x/y.png`); only tiles in
 * the current viewport are ever requested, with a descriptive User-Agent, per
 * the OSM tile usage policy. Attribution is drawn on the map.
 */
class OsmTiles(context: Context) {

    private val dir = File(context.applicationContext.cacheDir, "osm-tiles")
    private val mem = object : LruCache<String, ImageBitmap>(120) {}
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun key(z: Int, x: Int, y: Int) = "$z/$x/$y"

    /**
     * The tile if it is already decoded in memory, else null — and a background
     * load is kicked off (disk, then network) that calls [onLoaded] when done.
     */
    fun tile(z: Int, x: Int, y: Int, onLoaded: () -> Unit): ImageBitmap? {
        val k = key(z, x, y)
        mem.get(k)?.let { return it }
        if (!inFlight.add(k)) return null
        scope.launch {
            val bmp = loadFromDisk(z, x, y) ?: fetch(z, x, y)
            if (bmp != null) {
                mem.put(k, bmp)
                onLoaded()
            }
            inFlight.remove(k)
        }
        return null
    }

    private fun loadFromDisk(z: Int, x: Int, y: Int): ImageBitmap? {
        val f = File(dir, "$z/$x/$y.png")
        if (!f.isFile || f.length() == 0L) return null
        return runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
    }

    private fun fetch(z: Int, x: Int, y: Int): ImageBitmap? {
        val url = "https://tile.openstreetmap.org/$z/$x/$y.png"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                File(dir, "$z/$x").mkdirs()
                runCatching { File(dir, "$z/$x/$y.png").writeBytes(bytes) }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()
    }

    private companion object {
        val USER_AGENT =
            "THUGS-Wardrive-Android/${BuildConfig.VERSION_NAME} (+https://github.com/kawaiipantsu/thugsred-wardrive-apk)"
    }
}
