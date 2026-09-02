package red.thugs.wardrive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind
import red.thugs.wardrive.data.TrackPoint
import kotlin.math.cos
import kotlin.math.max

/**
 * Dependency-free, fully-offline map: scanned points and the driving path drawn
 * on a Compose [Canvas] in a local equirectangular projection. Pinch to zoom,
 * drag to pan, double-tap or "Fit" to reset. No tile downloads, so it works with
 * no signal and costs almost no battery.
 */
@Composable
fun MapScreen(
    points: List<Observation>,
    track: List<TrackPoint>,
    current: Pair<Double, Double>?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val wifiColor = cs.primary
    val btColor = cs.secondary
    val bleColor = cs.tertiary
    val trackColor = cs.primary.copy(alpha = 0.55f)
    val gridColor = cs.outline.copy(alpha = 0.25f)

    var userScale by remember { mutableFloatStateOf(1f) }
    var userPan by remember { mutableStateOf(Offset.Zero) }
    fun reset() {
        userScale = 1f
        userPan = Offset.Zero
    }

    val located = remember(points) { points.filter { it.hasFix } }
    val bounds = remember(located, track, current) { Bounds.of(located, track, current) }

    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

    Box(modifier.fillMaxSize().background(cs.background)) {
        if (bounds == null) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No located points yet.\nWaiting for a GPS fix and the first scan results.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            userScale = (userScale * zoom).coerceIn(0.3f, 80f)
                            userPan += pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { reset() })
                    },
            ) {
                val lat0 = (bounds.minLat + bounds.maxLat) / 2
                val lon0 = (bounds.minLon + bounds.maxLon) / 2
                val kx = cos(Math.toRadians(lat0))

                fun wx(lon: Double) = ((lon - lon0) * kx).toFloat()
                fun wy(lat: Double) = (-(lat - lat0)).toFloat()

                val wLeft = wx(bounds.minLon)
                val wRight = wx(bounds.maxLon)
                val wTop = wy(bounds.maxLat)
                val wBottom = wy(bounds.minLat)
                val spanX = max(wRight - wLeft, 1e-6f)
                val spanY = max(wBottom - wTop, 1e-6f)
                val baseScale = 0.86f * minOf(size.width / spanX, size.height / spanY)
                val scale = baseScale * userScale
                val wcx = (wLeft + wRight) / 2
                val wcy = (wTop + wBottom) / 2

                fun screen(lat: Double, lon: Double) = Offset(
                    size.width / 2 + (wx(lon) - wcx) * scale + userPan.x,
                    size.height / 2 + (wy(lat) - wcy) * scale + userPan.y,
                )

                // Track
                if (track.size >= 2) {
                    val path = Path()
                    track.forEachIndexed { i, p ->
                        val s = screen(p[0], p[1])
                        if (i == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
                    }
                    drawPath(path, trackColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }

                // Points
                val r = 3.dp.toPx()
                for (o in located) {
                    val color = when (o.kind) {
                        RadioKind.WIFI_AP -> wifiColor
                        RadioKind.BT_CLASSIC -> btColor
                        RadioKind.BT_LE -> bleColor
                    }
                    drawCircle(color, radius = r, center = screen(o.lat, o.lon))
                }

                // Current position
                current?.let { (lat, lon) ->
                    val c = screen(lat, lon)
                    drawCircle(Color.White, radius = 6.dp.toPx(), center = c)
                    drawCircle(cs.primary, radius = 6.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                    drawCircle(cs.primary.copy(alpha = 0.18f), radius = 16.dp.toPx(), center = c)
                }
            }
        }

        // Overlays
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = topInset + 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${located.size} located · ×${"%.1f".format(userScale)}",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier
                    .background(cs.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { reset() }, contentPadding = PaddingCompact) { Text("Fit") }
        }

        Legend(
            wifiColor, btColor, bleColor,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
                .background(cs.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private val PaddingCompact = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)

@Composable
private fun Legend(wifi: Color, bt: Color, ble: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dot("WiFi", wifi)
        Dot("BT", bt)
        Dot("BLE", ble)
    }
}

@Composable
private fun Dot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

private class Bounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    companion object {
        fun of(points: List<Observation>, track: List<TrackPoint>, current: Pair<Double, Double>?): Bounds? {
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            var any = false
            fun acc(lat: Double, lon: Double) {
                any = true
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
            }
            for (o in points) acc(o.lat, o.lon)
            for (p in track) acc(p[0], p[1])
            current?.let { acc(it.first, it.second) }
            if (!any) return null
            // Pad so a single-point session is not infinitely zoomed.
            val padLat = ((maxLat - minLat) * 0.1).coerceAtLeast(0.0008)
            val padLon = ((maxLon - minLon) * 0.1).coerceAtLeast(0.0008)
            return Bounds(minLat - padLat, maxLat + padLat, minLon - padLon, maxLon + padLon)
        }
    }
}
