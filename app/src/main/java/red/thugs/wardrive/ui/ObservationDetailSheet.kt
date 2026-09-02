package red.thugs.wardrive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind
import red.thugs.wardrive.data.haversineMeters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationDetailSheet(
    observation: Observation,
    sightings: List<Observation>,
    current: Pair<Double, Double>?,
    baseUrl: String,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    val o = observation
    val clip = LocalClipboardManager.current
    val uris = LocalUriHandler.current
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.US) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                o.ssid?.takeIf { it.isNotBlank() } ?: "(hidden network)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                o.bssid,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            Field(
                "Type",
                when (o.kind) {
                    RadioKind.WIFI_AP -> "WiFi AP"
                    RadioKind.BT_CLASSIC -> "Bluetooth Classic"
                    RadioKind.BT_LE -> "Bluetooth LE"
                },
            )
            o.capabilities?.let { Field("Security", it) }
            if (o.kind == RadioKind.WIFI_AP) {
                Field(
                    "Channel",
                    buildString {
                        append(o.channel?.toString() ?: "?")
                        o.frequencyMhz?.let { append("  ·  $it MHz") }
                        o.channelWidthMhz?.let { append("  ·  $it MHz wide") }
                    },
                )
            }
            val signals = sightings.mapNotNull { it.signalDbm }
            if (signals.isNotEmpty()) {
                Field("Signal", "now ${o.signalDbm ?: "?"} dBm  ·  best ${signals.max()} dBm")
            }
            Field("Sightings", "${sightings.size}")
            sightings.firstOrNull()?.let { Field("First seen", fmt.format(Date(it.timestampMs))) }
            Field("Last seen", fmt.format(Date(o.timestampMs)))
            if (current != null && o.hasFix) {
                val d = haversineMeters(current.first, current.second, o.lat, o.lon)
                Field("Distance", if (d >= 1000) "%.2f km".format(d / 1000) else "${d.toInt()} m")
            }

            if (signals.size >= 2) {
                Spacer(Modifier.height(10.dp))
                Text("Signal over time", style = MaterialTheme.typography.labelMedium)
                val min = signals.min().toFloat()
                val max = signals.max().toFloat()
                val span = (max - min).coerceAtLeast(1f)
                val color = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(64.dp).padding(top = 4.dp)) {
                    val path = Path()
                    signals.forEachIndexed { i, s ->
                        val x = i / (signals.size - 1f) * size.width
                        val y = size.height - ((s - min) / span) * (size.height - 4f)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 2.5f))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    clip.setText(AnnotatedString(o.bssid))
                    onToast("BSSID copied")
                }) { Text("Copy BSSID") }
                if (o.kind == RadioKind.WIFI_AP) {
                    TextButton(onClick = {
                        runCatching { uris.openUri("${baseUrl.trimEnd('/')}/networks/${o.bssid}") }
                            .onFailure { onToast("Couldn't open a browser") }
                    }) { Text("Open on the site") }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
