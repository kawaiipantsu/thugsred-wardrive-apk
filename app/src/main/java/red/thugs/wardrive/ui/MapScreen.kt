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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind
import red.thugs.wardrive.data.TrackPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh

/**
 * Web-Mercator slippy map on a Compose [Canvas]. Scanned points and the driving
 * path are always drawn locally (offline). OpenStreetMap raster tiles are
 * optional ([tilesEnabled]) — off by default; when off, a lat/long graticule
 * plus a scale bar and north arrow keep the view readable.
 *
 * Pinch to zoom, drag to pan, double-tap or "Fit" to re-frame the session.
 */
@Composable
fun MapScreen(
    points: List<Observation>,
    track: List<TrackPoint>,
    current: Pair<Double, Double>?,
    modifier: Modifier = Modifier,
    tilesEnabled: Boolean = false,
    onToggleTiles: () -> Unit = {},
    accuracyM: Float? = null,
    follow: Boolean = false,
    onToggleFollow: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val ctx = LocalContext.current
    val tiles = remember { OsmTiles(ctx) }
    androidx.compose.runtime.DisposableEffect(tiles) { onDispose { tiles.shutdown() } }

    val located = remember(points) { points.filter { it.hasFix } }
    val bounds = remember(located, track, current) { MapBounds.of(located, track, current) }

    var zoom by remember { mutableDoubleStateOf(Double.NaN) }
    var centerLat by remember { mutableDoubleStateOf(0.0) }
    var centerLon by remember { mutableDoubleStateOf(0.0) }
    var userMoved by remember { mutableStateOf(false) }
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    var tileTick by remember { mutableIntStateOf(0) }

    // (Re)frame to the data until the user takes over; "Fit" / double-tap re-arms this.
    LaunchedEffect(bounds, canvas, userMoved) {
        val b = bounds ?: return@LaunchedEffect
        if (canvas.width == 0 || canvas.height == 0) return@LaunchedEffect
        if ((userMoved || follow) && !zoom.isNaN()) return@LaunchedEffect
        centerLat = (b.minLat + b.maxLat) / 2
        centerLon = (b.minLon + b.maxLon) / 2
        zoom = fitZoom(b, canvas)
    }

    // Follow mode keeps the current fix centred without disturbing the zoom.
    LaunchedEffect(current, follow) {
        val c = current
        if (follow && c != null && c.first.isFinite() && c.second.isFinite() && (c.first != 0.0 || c.second != 0.0)) {
            centerLat = c.first
            centerLon = c.second
            if (zoom.isNaN()) zoom = 16.0
        }
    }

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
                    .onSizeChanged { canvas = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            if (zoom.isNaN()) return@detectTransformGestures
                            userMoved = true
                            if (follow) onToggleFollow() // a manual pan cancels follow
                            // Pan: shift centre opposite the drag.
                            centerLon = worldXToLon(lonToWorldX(centerLon, zoom) - pan.x, zoom)
                            centerLat = worldYToLat(latToWorldY(centerLat, zoom) - pan.y, zoom)
                            // Zoom about the pinch centroid, keeping that ground point fixed.
                            if (gestureZoom != 1f) {
                                val dx = centroid.x - size.width / 2
                                val dy = centroid.y - size.height / 2
                                val fLon = worldXToLon(lonToWorldX(centerLon, zoom) + dx, zoom)
                                val fLat = worldYToLat(latToWorldY(centerLat, zoom) + dy, zoom)
                                zoom = (zoom + log2(gestureZoom.toDouble())).coerceIn(2.0, 19.0)
                                centerLon = worldXToLon(lonToWorldX(fLon, zoom) - dx, zoom)
                                centerLat = worldYToLat(latToWorldY(fLat, zoom) - dy, zoom)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { userMoved = false })
                    },
            ) {
                if (!zoom.isFinite() || !centerLat.isFinite() || !centerLon.isFinite()) return@Canvas
                if (size.width < 1f || size.height < 1f) return@Canvas
                tileTick // read so tile arrivals trigger a redraw

                fun sx(lon: Double) = (size.width / 2 + (lonToWorldX(lon, zoom) - lonToWorldX(centerLon, zoom))).toFloat()
                fun sy(lat: Double) = (size.height / 2 + (latToWorldY(lat, zoom) - latToWorldY(centerLat, zoom))).toFloat()

              try {
                if (tilesEnabled) {
                    drawTiles(tiles, zoom, centerLat, centerLon, DARK_TILE_FILTER) { tileTick++ }
                    // Light scrim to settle the darkened basemap into the palette.
                    drawRect(cs.background.copy(alpha = 0.18f))
                } else {
                    drawGraticule(measurer, zoom, centerLat, centerLon, cs.outline.copy(alpha = 0.28f), cs.onSurfaceVariant.copy(alpha = 0.55f))
                }

                // Track
                if (track.size >= 2) {
                    val path = Path()
                    track.forEachIndexed { i, p ->
                        val o = Offset(sx(p[1]), sy(p[0]))
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    drawPath(path, cs.primary.copy(alpha = 0.7f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }

                // Points
                val r = 3.dp.toPx()
                for (o in located) {
                    val color = when (o.kind) {
                        RadioKind.WIFI_AP -> cs.primary
                        RadioKind.BT_CLASSIC -> cs.secondary
                        RadioKind.BT_LE -> cs.tertiary
                    }
                    val c = Offset(sx(o.lon), sy(o.lat))
                    drawCircle(Color.Black.copy(alpha = 0.5f), radius = r + 1f, center = c)
                    drawCircle(color, radius = r, center = c)
                }

                // Current position
                current?.let { (lat, lon) ->
                    val c = Offset(sx(lon), sy(lat))
                    if (accuracyM != null && accuracyM > 0f) {
                        val rPx = (accuracyM / metresPerPixel(centerLat, zoom)).toFloat()
                        if (rPx in 8f..size.maxDimension) {
                            drawCircle(cs.primary.copy(alpha = 0.10f), radius = rPx, center = c)
                            drawCircle(cs.primary.copy(alpha = 0.35f), radius = rPx, center = c, style = Stroke(1.5f))
                        }
                    }
                    drawCircle(cs.primary.copy(alpha = 0.18f), radius = 16.dp.toPx(), center = c)
                    drawCircle(Color.White, radius = 6.dp.toPx(), center = c)
                    drawCircle(cs.primary, radius = 6.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
                }

                drawScaleBar(measurer, zoom, centerLat, cs.onSurface, cs.surface.copy(alpha = 0.7f))
                drawNorthArrow(measurer, cs.onSurface, cs.surface.copy(alpha = 0.7f), topInsetPx = topInset.toPx())
              } catch (_: Throwable) {
                // A transient bad projection state must never take the app down.
              }
            }
        }

        // -- overlays --------------------------------------------------
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = topInset + 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${located.size} located" + if (!zoom.isNaN()) " · z${zoom.roundToInt()}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier
                    .background(cs.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onToggleTiles, contentPadding = CompactPadding) {
                Text(if (tilesEnabled) "Plot" else "Tiles")
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalButton(onClick = onToggleFollow, contentPadding = CompactPadding) {
                Text(if (follow) "Loc ●" else "Loc", color = if (follow) cs.primary else Color.Unspecified)
            }
            Spacer(Modifier.width(6.dp))
            FilledTonalButton(onClick = { userMoved = false }, contentPadding = CompactPadding) { Text("Fit") }
        }

        Legend(
            cs.primary, cs.secondary, cs.tertiary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
                .background(cs.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )

        if (tilesEnabled) {
            Text(
                "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
                    .background(cs.surface.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private val CompactPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)

@Composable
private fun Legend(wifi: Color, bt: Color, ble: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dot("WiFi", wifi); Dot("BT", bt); Dot("BLE", ble)
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

// -- Web Mercator ---------------------------------------------------------

/** Ground resolution at a latitude/zoom, for the scale bar and accuracy ring. */
private fun metresPerPixel(lat: Double, zoom: Double) =
    156543.03392 * cos(lat * PI / 180.0) / 2.0.pow(zoom)

/** "Dark map" colour filter for the bright OSM basemap: invert + per-channel trim. */
private val DARK_TILE_FILTER = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
    androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            -0.92f, 0.00f, 0.00f, 0f, 238f,
            0.00f, -0.92f, 0.00f, 0f, 238f,
            0.00f, 0.00f, -0.86f, 0f, 228f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private fun worldSize(zoom: Double) = 256.0 * 2.0.pow(zoom)

private fun lonToWorldX(lon: Double, zoom: Double) = (lon + 180.0) / 360.0 * worldSize(zoom)

private fun latToWorldY(lat: Double, zoom: Double): Double {
    val s = sin(lat.coerceIn(-85.05, 85.05) * PI / 180.0)
    return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * worldSize(zoom)
}

private fun worldXToLon(x: Double, zoom: Double) = x / worldSize(zoom) * 360.0 - 180.0

private fun worldYToLat(y: Double, zoom: Double): Double {
    val n = PI - 2.0 * PI * y / worldSize(zoom)
    return atan(sinh(n)) * 180.0 / PI
}

private fun fitZoom(b: MapBounds, canvas: IntSize): Double {
    val margin = 0.82
    fun spanOk(z: Double): Boolean {
        val wpx = abs(lonToWorldX(b.maxLon, z) - lonToWorldX(b.minLon, z))
        val hpx = abs(latToWorldY(b.minLat, z) - latToWorldY(b.maxLat, z))
        return wpx <= canvas.width * margin && hpx <= canvas.height * margin
    }
    var z = 2.0
    while (z < 18.5 && spanOk(z + 0.5)) z += 0.5
    return z.coerceIn(2.0, 18.0)
}

// -- Canvas layers ------------------------------------------------------

private fun DrawScope.drawTiles(
    tiles: OsmTiles,
    zoom: Double,
    centerLat: Double,
    centerLon: Double,
    colorFilter: androidx.compose.ui.graphics.ColorFilter?,
    onLoaded: () -> Unit,
) {
    val z = zoom.roundToInt().coerceIn(2, 19)
    val scale = 2.0.pow(zoom - z).toFloat()
    val tilePx = 256f * scale
    val n = 1 shl z
    // Screen position of world-pixel (0,0) at integer zoom z.
    val cwx = (centerLon + 180.0) / 360.0 * (256.0 * n)
    val s = sin(centerLat.coerceIn(-85.05, 85.05) * PI / 180.0)
    val cwy = (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * (256.0 * n)
    val originX = size.width / 2 - (cwx * scale).toFloat()
    val originY = size.height / 2 - (cwy * scale).toFloat()

    val x0 = floor((-originX) / tilePx).toInt() - 1
    val x1 = floor((size.width - originX) / tilePx).toInt() + 1
    val y0 = floor((-originY) / tilePx).toInt() - 1
    val y1 = floor((size.height - originY) / tilePx).toInt() + 1

    for (tx in x0..x1) for (ty in y0..y1) {
        if (ty < 0 || ty >= n) continue
        val wx = ((tx % n) + n) % n
        val left = originX + tx * tilePx
        val top = originY + ty * tilePx
        val bmp = tiles.tile(z, wx, ty, onLoaded)
        if (bmp != null) {
            drawImage(
                image = bmp,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(tilePx.roundToInt() + 1, tilePx.roundToInt() + 1),
                colorFilter = colorFilter,
            )
        } else {
            drawRect(Color(0xFF1B2130), topLeft = Offset(left, top), size = Size(tilePx, tilePx))
        }
    }
}

private val GRAT_STEPS = doubleArrayOf(
    30.0, 10.0, 5.0, 2.0, 1.0, 0.5, 0.2, 0.1, 0.05, 0.02, 0.01, 0.005, 0.002, 0.001, 0.0005, 0.0002,
)

private fun DrawScope.drawGraticule(
    measurer: androidx.compose.ui.text.TextMeasurer,
    zoom: Double,
    centerLat: Double,
    centerLon: Double,
    line: Color,
    label: Color,
) {
    val pxPerDegLon = abs(lonToWorldX(centerLon + 1, zoom) - lonToWorldX(centerLon, zoom))
    val step = GRAT_STEPS.firstOrNull { it * pxPerDegLon in 55.0..200.0 } ?: GRAT_STEPS.last()
    val cwx = lonToWorldX(centerLon, zoom)
    val cwy = latToWorldY(centerLat, zoom)
    fun sx(lon: Double) = (size.width / 2 + (lonToWorldX(lon, zoom) - cwx)).toFloat()
    fun sy(lat: Double) = (size.height / 2 + (latToWorldY(lat, zoom) - cwy)).toFloat()

    val decimals = when {
        step >= 1 -> 1
        step >= 0.1 -> 2
        step >= 0.01 -> 3
        step >= 0.001 -> 4
        else -> 5
    }
    if (step <= 0.0 || !step.isFinite()) return
    var guard = 0
    val lonStart = floor((centerLon - 180.0 / 2.0.pow(zoom - 2)) / step) * step
    var lon = lonStart
    while (sx(lon) < size.width + 1 && guard++ < 500) {
        val x = sx(lon)
        if (x >= -1) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            drawText(measurer, fmtDeg(lon, decimals), Offset(x + 3f, 4f), TextStyle(color = label, fontSize = 9.sp))
        }
        lon += step
    }
    guard = 0
    val latStart = floor((centerLat - 90.0) / step) * step
    var lat = latStart
    while (lat < 85.0 && guard++ < 2000) {
        val y = sy(lat)
        if (y in -1f..(size.height + 1f)) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            drawText(measurer, fmtDeg(lat, decimals), Offset(3f, y + 2f), TextStyle(color = label, fontSize = 9.sp))
        }
        lat += step
        if (y < -size.height) break
    }
}

private fun fmtDeg(d: Double, decimals: Int): String = "%.${decimals}f°".format(d)

private fun DrawScope.drawScaleBar(
    measurer: androidx.compose.ui.text.TextMeasurer,
    zoom: Double,
    centerLat: Double,
    fg: Color,
    bg: Color,
) {
    val mPerPx = 156543.03392 * cos(centerLat * PI / 180.0) / 2.0.pow(zoom)
    val target = 90.0 * mPerPx // aim for ~90px
    val niceM = niceDistance(target)
    val barPx = (niceM / mPerPx).toFloat().coerceIn(30f, 240f)
    val label = if (niceM >= 1000) {
        val km = niceM / 1000
        (if (km == km.toLong().toDouble()) km.toLong().toString() else "%.1f".format(km)) + " km"
    } else {
        "${niceM.toLong()} m"
    }
    val bx = 16f
    val by = size.height - 178f
    drawRect(bg, topLeft = Offset(bx - 6f, by - 14f), size = Size(barPx + 52f, 30f))
    drawLine(fg, Offset(bx, by + 6f), Offset(bx + barPx, by + 6f), strokeWidth = 3f)
    drawLine(fg, Offset(bx, by), Offset(bx, by + 12f), strokeWidth = 3f)
    drawLine(fg, Offset(bx + barPx, by), Offset(bx + barPx, by + 12f), strokeWidth = 3f)
    drawText(measurer, label, Offset(bx + barPx + 6f, by - 2f), TextStyle(color = fg, fontSize = 10.sp))
}

private fun niceDistance(m: Double): Double {
    if (m <= 0) return 1.0
    val pow = 10.0.pow(floor(log2(m) / log2(10.0)))
    val f = m / pow
    val nice = when {
        f < 1.5 -> 1.0
        f < 3.5 -> 2.0
        f < 7.5 -> 5.0
        else -> 10.0
    }
    return nice * pow
}

private fun DrawScope.drawNorthArrow(
    measurer: androidx.compose.ui.text.TextMeasurer,
    fg: Color,
    bg: Color,
    topInsetPx: Float,
) {
    val cx = 24f
    val cy = topInsetPx + 66f
    drawRect(bg, topLeft = Offset(cx - 14f, cy - 16f), size = Size(28f, 40f))
    val tri = Path().apply {
        moveTo(cx, cy - 12f); lineTo(cx - 7f, cy + 6f); lineTo(cx + 7f, cy + 6f); close()
    }
    drawPath(tri, fg)
    drawText(measurer, "N", Offset(cx - 4f, cy + 8f), TextStyle(color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold))
}

// -- bounds -----------------------------------------------------------

private class MapBounds(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double) {
    companion object {
        fun of(points: List<Observation>, track: List<TrackPoint>, current: Pair<Double, Double>?): MapBounds? {
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            var any = false
            fun acc(lat: Double, lon: Double) {
                // Skip non-finite and the 0,0 "no satellite lock" artefact.
                if (!lat.isFinite() || !lon.isFinite()) return
                if (lat == 0.0 && lon == 0.0) return
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return
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
            val padLat = ((maxLat - minLat) * 0.12).coerceAtLeast(0.0006)
            val padLon = ((maxLon - minLon) * 0.12).coerceAtLeast(0.0006)
            return MapBounds(minLat - padLat, maxLat + padLat, minLon - padLon, maxLon + padLon)
        }
    }
}
