package red.thugs.wardrive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.thugs.wardrive.data.CongestionSample
import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind
import red.thugs.wardrive.data.WifiInfo
import kotlin.math.roundToInt

private data class BandView(val label: String, val lo: Int, val hi: Int, val ticks: List<Pair<Int, String>>)

private val BAND_24 = BandView(
    "2.4 GHz", 2400, 2500,
    (1..13).map { (2407 + it * 5) to it.toString() },
)
private val BAND_5 = BandView(
    "5 GHz", 5150, 5895,
    listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 149, 153, 157, 161, 165)
        .map { (5000 + it * 5) to it.toString() },
)

/**
 * A live channel *congestion* scope — not an RF spectrum. Stock Android exposes
 * no noise floor, so this is built from beacons: every AP is drawn at its real
 * channel width so overlap and crowding are visible, with a rolling waterfall of
 * per-frequency AP density underneath.
 */
@Composable
fun ScopeScreen(
    observations: List<Observation>,
    congestion: List<CongestionSample>,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    var band by remember { mutableStateOf(BAND_24) }

    val aps = remember(observations, band) {
        observations.filter {
            it.kind == RadioKind.WIFI_AP && it.frequencyMhz != null &&
                it.frequencyMhz in band.lo..band.hi
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = band == BAND_24, onClick = { band = BAND_24 }, label = { Text("2.4 GHz") })
            FilterChip(selected = band == BAND_5, onClick = { band = BAND_5 }, label = { Text("5 GHz") })
        }
        Text(
            "${aps.size} APs in band · congestion from beacons, not RF noise",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
        )

        // --- overlap / occupancy ---
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            fun fx(mhz: Int) = ((mhz - band.lo).toFloat() / (band.hi - band.lo)) * size.width

            drawRect(cs.surface, size = size)

            // channel ticks
            band.ticks.forEach { (mhz, label) ->
                val x = fx(mhz)
                val major = label in setOf("1", "6", "11", "36", "48", "100", "149")
                drawLine(cs.outline.copy(alpha = if (major) 0.5f else 0.2f), Offset(x, 0f), Offset(x, size.height - 14f), strokeWidth = 1f)
                if (major) drawText(measurer, label, Offset(x + 2f, size.height - 13f), TextStyle(color = cs.onSurfaceVariant, fontSize = 8.sp))
            }

            // one translucent band per AP, at its real width — overlaps add up
            aps.forEach { o ->
                val span = o.freqSpan ?: return@forEach
                val x0 = fx(span.first.coerceAtLeast(band.lo))
                val x1 = fx(span.second.coerceAtMost(band.hi))
                val strength = ((((o.signalDbm ?: -95) + 95).coerceIn(0, 60)) / 60f)
                drawRect(
                    cs.primary.copy(alpha = 0.10f + 0.30f * strength),
                    topLeft = Offset(x0, 8f),
                    size = Size((x1 - x0).coerceAtLeast(2f), size.height - 30f),
                )
                val cx = fx(o.centerFreqMhz ?: o.frequencyMhz!!)
                drawLine(cs.primary.copy(alpha = 0.6f), Offset(cx, 8f), Offset(cx, size.height - 22f), strokeWidth = 1.5f)
            }
        }

        Text("Waterfall — last ${congestion.size}s", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        // --- waterfall: time (newest at top) vs frequency, brightness = AP density ---
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            drawRect(cs.surface, size = size)
            val rows = congestion.takeLast(140)
            if (rows.isEmpty()) return@Canvas
            val rowH = size.height / rows.size
            fun fx(mhz: Int) = ((mhz - band.lo).toFloat() / (band.hi - band.lo)) * size.width
            val binW = (size.width / (band.ticks.size + 2)).coerceAtLeast(3f)
            rows.forEachIndexed { i, sample ->
                val y = size.height - (i + 1) * rowH // newest (last) at top
                sample.load.forEach { (freq, load) ->
                    if (freq !in band.lo..band.hi) return@forEach
                    drawRect(
                        congestionColor(load.count, cs),
                        topLeft = Offset(fx(freq) - binW / 2, y),
                        size = Size(binW, rowH + 0.5f),
                    )
                }
            }
            band.ticks.filter { it.second in setOf("1", "6", "11", "36", "100", "149") }.forEach { (mhz, label) ->
                val x = fx(mhz)
                drawLine(cs.outline.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                drawText(measurer, label, Offset(x + 2f, 2f), TextStyle(color = cs.onSurfaceVariant, fontSize = 8.sp))
            }
        }

        Text(
            "density: dim → bright = more APs on that frequency at that moment",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
        )
    }
}

private fun congestionColor(count: Int, cs: androidx.compose.material3.ColorScheme): Color = when {
    count <= 0 -> Color.Transparent
    count <= 2 -> cs.primary.copy(alpha = 0.30f)
    count <= 4 -> cs.primary.copy(alpha = 0.65f)
    count <= 6 -> cs.secondary.copy(alpha = 0.8f)
    else -> cs.tertiary
}
