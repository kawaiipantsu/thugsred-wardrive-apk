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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.thugs.wardrive.data.GrowthPoint
import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind
import red.thugs.wardrive.data.SessionCounts
import red.thugs.wardrive.data.WifiInfo
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    observations: List<Observation>,
    growth: List<GrowthPoint>,
    counts: SessionCounts,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val wifi = remember(observations) { observations.filter { it.kind == RadioKind.WIFI_AP } }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (observations.isEmpty()) {
            Text("Nothing scanned yet.", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        // Summary
        val open = wifi.count { WifiInfo.enc(it.capabilities) == WifiInfo.Enc.OPEN }
        val hidden = wifi.count { it.ssid.isNullOrBlank() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Devices", counts.total, cs.onSurface)
            Metric("WiFi", counts.wifiAp, cs.primary)
            Metric("BT+BLE", counts.btClassic + counts.btLe, cs.secondary)
            Metric("Open", open, cs.tertiary)
            Metric("Hidden", hidden, cs.onSurfaceVariant)
        }

        WifiInfo.bestChannel24(observations)?.let { (ch, n) ->
            Text(
                "Least-busy 2.4 GHz channel: ch $ch  ($n APs on/near it)",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.primary,
                fontWeight = FontWeight.Medium,
            )
        }

        // 2.4 GHz channels
        val ch24 = (1..14).associateWith { c -> wifi.count { it.channel == c && WifiInfo.band(it.frequencyMhz) == 2 } }
        ChartCard("2.4 GHz — APs per channel", cs) {
            BarChart(ch24.mapKeys { it.key.toString() }, measurer, cs.primary, cs.onSurfaceVariant, highlight = setOf("1", "6", "11"), highlightColor = cs.tertiary)
        }

        // 5 GHz channels
        val fiveChannels = listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 149, 153, 157, 161, 165)
        val ch5 = fiveChannels.associateWith { c -> wifi.count { it.channel == c && WifiInfo.band(it.frequencyMhz) == 5 } }
            .filterValues { it > 0 }
        if (ch5.isNotEmpty()) {
            ChartCard("5 GHz — APs per channel", cs) {
                BarChart(ch5.mapKeys { it.key.toString() }, measurer, cs.primary, cs.onSurfaceVariant)
            }
        }

        // Encryption
        val encOrder = listOf(WifiInfo.Enc.OPEN, WifiInfo.Enc.WEP, WifiInfo.Enc.WPA, WifiInfo.Enc.WPA2, WifiInfo.Enc.WPA3)
        val encCounts = encOrder.associateWith { e -> wifi.count { WifiInfo.enc(it.capabilities) == e } }.filterValues { it > 0 }
        ChartCard("Encryption", cs) {
            HBars(
                encCounts.mapKeys { it.key.name },
                measurer,
                colorFor = { name ->
                    when (name) {
                        "OPEN" -> cs.tertiary
                        "WEP" -> cs.tertiary.copy(alpha = 0.7f)
                        "WPA3" -> cs.primary
                        else -> cs.secondary
                    }
                },
                labelColor = cs.onSurface,
            )
        }

        // Band split
        val bands = mapOf(
            "2.4 GHz" to wifi.count { WifiInfo.band(it.frequencyMhz) == 2 },
            "5 GHz" to wifi.count { WifiInfo.band(it.frequencyMhz) == 5 },
            "6 GHz" to wifi.count { WifiInfo.band(it.frequencyMhz) == 6 },
        ).filterValues { it > 0 }
        if (bands.isNotEmpty()) {
            ChartCard("Band", cs) {
                HBars(bands, measurer, colorFor = { cs.primary }, labelColor = cs.onSurface)
            }
        }

        // Signal histogram
        val buckets = IntArray(8) // -100..-20 in 10 dB steps
        wifi.forEach { o ->
            val s = o.signalDbm ?: return@forEach
            val idx = ((s + 100) / 10).coerceIn(0, 7)
            buckets[idx]++
        }
        val sigMap = (0 until 8).associate { i -> "${-100 + i * 10}" to buckets[i] }
        ChartCard("Signal (dBm)", cs) {
            BarChart(sigMap, measurer, cs.secondary, cs.onSurfaceVariant)
        }

        // Discovery rate
        if (growth.size >= 2) {
            val spanMin = (growth.last()[0] - growth.first()[0]).coerceAtLeast(1) / 60_000.0
            val rate = if (spanMin > 0) (growth.last()[1] - growth.first()[1]) / spanMin else 0.0
            ChartCard("Discovery — ${counts.total} devices, ${rate.roundToInt()}/min", cs) {
                Sparkline(growth.map { it[1].toFloat() }, measurer, cs.primary)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartCard(title: String, cs: androidx.compose.material3.ColorScheme, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun BarChart(
    values: Map<String, Int>,
    measurer: TextMeasurer,
    barColor: Color,
    labelColor: Color,
    highlight: Set<String> = emptySet(),
    highlightColor: Color = barColor,
) {
    val maxV = max(1, values.values.maxOrNull() ?: 1)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = values.size.coerceAtLeast(1)
        val gap = 3f
        val bw = (size.width - gap * (n - 1)) / n
        val baseY = size.height - 16f
        values.entries.forEachIndexed { i, (label, v) ->
            val x = i * (bw + gap)
            val h = (v / maxV.toFloat()) * (baseY - 4f)
            drawRect(
                if (label in highlight) highlightColor else barColor,
                topLeft = Offset(x, baseY - h),
                size = Size(bw, h),
            )
            if (v > 0) {
                drawText(measurer, "$v", Offset(x, baseY - h - 12f), TextStyle(color = labelColor, fontSize = 8.sp))
            }
            drawText(measurer, label, Offset(x, baseY + 2f), TextStyle(color = labelColor, fontSize = 8.sp))
        }
    }
}

@Composable
private fun HBars(
    values: Map<String, Int>,
    measurer: TextMeasurer,
    colorFor: (String) -> Color,
    labelColor: Color,
) {
    val maxV = max(1, values.values.maxOrNull() ?: 1)
    Canvas(Modifier.fillMaxWidth().height((values.size * 26).dp)) {
        val rowH = size.height / values.size.coerceAtLeast(1)
        val trackW = (size.width - 140f).coerceAtLeast(20f)
        values.entries.forEachIndexed { i, (label, v) ->
            val y = i * rowH + 3f
            val w = ((v / maxV.toFloat()) * trackW).coerceAtLeast(2f)
            drawRect(colorFor(label), topLeft = Offset(90f, y), size = Size(w, rowH - 8f))
            drawText(measurer, label, Offset(0f, y), TextStyle(color = labelColor, fontSize = 10.sp))
            drawText(measurer, "$v", Offset(90f + w + 6f, y), TextStyle(color = labelColor, fontSize = 10.sp))
        }
    }
}

@Composable
private fun Sparkline(points: List<Float>, measurer: TextMeasurer, color: Color) {
    if (points.size < 2) return
    val maxV = max(1f, points.maxOrNull() ?: 1f)
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = i / (points.size - 1f) * size.width
            val y = size.height - (v / maxV) * (size.height - 6f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5f))
        drawText(measurer, "${points.last().roundToInt()}", Offset(size.width - 36f, 2f), TextStyle(color = color, fontSize = 10.sp))
    }
}
