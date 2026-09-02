package red.thugs.wardrive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import red.thugs.wardrive.data.Follower
import red.thugs.wardrive.data.Oui
import red.thugs.wardrive.data.RadioKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Spy mode: watch for a device (Wi-Fi AP, Bluetooth or BLE) that keeps
 * reappearing at points spread along your route — i.e. something that is
 * travelling with you rather than a fixed installation you drove past.
 *
 * A "follower" here is a MAC seen at [Follower.waypoints] separate ~10 m points,
 * over at least ~40 m and ~45 s of your route, and not lost long ago.
 */
@Composable
fun SpyScreen(
    active: Boolean,
    trackedDevices: Int,
    followers: List<Follower>,
    imperial: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Oui.ensureLoaded(ctx) }

    Column(Modifier.fillMaxSize()) {
        HeaderCard(active, trackedDevices, followers.size, onToggle)

        if (!active) {
            Info(
                "Spy mode is off. Turn it on, then keep moving. It compares every MAC " +
                    "against your route and flags any that stay with you across several " +
                    "separated points.",
            )
            return
        }

        if (followers.isEmpty()) {
            Info(
                "Nothing is tailing you yet. Devices show up here once they've been seen " +
                    "at 3+ points at least ~10 m apart over ~45 s. Many phones and BLE " +
                    "tags rotate their MAC, so a genuine tail usually has a real vendor.",
            )
            return
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(followers, key = { it.kind.name + it.bssid }) { f ->
                FollowerRow(f, imperial)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HeaderCard(active: Boolean, tracked: Int, following: Int, onToggle: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppIcons.Visibility,
                    null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Spy mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (active) "Watching. BT/BLE + GPS held at full rate."
                        else "Off",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = active, onCheckedChange = onToggle)
            }
            if (active) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Metric("$tracked", "tracked")
                    Metric("$following", "following you", if (following > 0) MaterialTheme.colorScheme.error else null)
                }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String, tint: androidx.compose.ui.graphics.Color? = null) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Info(text: String) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FollowerRow(f: Follower, imperial: Boolean) {
    val (icon, tint) = when (f.kind) {
        RadioKind.WIFI_AP -> AppIcons.Wifi to MaterialTheme.colorScheme.primary
        RadioKind.BT_CLASSIC -> AppIcons.Bluetooth to MaterialTheme.colorScheme.secondary
        RadioKind.BT_LE -> AppIcons.Bluetooth to MaterialTheme.colorScheme.tertiary
    }
    val randomised = Oui.isRandomised(f.bssid)
    val vendor = if (randomised) null else Oui.vendor(f.bssid)
    val title = f.name?.takeIf { it.isNotBlank() }
        ?: vendor
        ?: if (randomised) "Randomised MAC" else "Unknown device"

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    f.bssid + when {
                        f.kind == RadioKind.WIFI_AP -> "  ·  Wi-Fi"
                        f.kind == RadioKind.BT_LE -> "  ·  BLE"
                        else -> "  ·  BT"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    dist(f.lastSeenMetersAgo, imperial) + " ago",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text("${f.waypoints} points", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            if (vendor != null) Chip(vendor, MaterialTheme.colorScheme.primary)
            if (randomised) Chip("randomised MAC", MaterialTheme.colorScheme.tertiary)
        }
        if (vendor != null || randomised) Spacer(Modifier.height(6.dp))
        Text(
            "With you ${dist(f.spanMeters, imperial)} over ${dur(f.lastSeenMs - f.firstSeenMs)} · ${f.sightings} sightings",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "First ${clock(f.firstSeenMs)} · last ${clock(f.lastSeenMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Chip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private fun dist(meters: Int, imperial: Boolean): String =
    if (imperial) {
        val ft = meters * 3.28084
        if (ft >= 5280) String.format(Locale.US, "%.1f mi", ft / 5280) else "${ft.toInt()} ft"
    } else {
        if (meters >= 1000) String.format(Locale.US, "%.1f km", meters / 1000.0) else "$meters m"
    }

private fun dur(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${s % 60}s"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }
}

private fun clock(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
