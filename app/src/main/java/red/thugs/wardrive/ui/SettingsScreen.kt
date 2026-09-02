package red.thugs.wardrive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import red.thugs.wardrive.BuildConfig
import red.thugs.wardrive.data.Prefs

/** Live settings. Reads/writes [Prefs] directly; [onChanged] lets the host recompose. */
@Composable
fun SettingsScreen(
    prefs: Prefs,
    onChanged: () -> Unit,
    onDriveMode: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
    onForgetLogin: () -> Unit,
    onResetSession: () -> Unit,
    onReplayIntro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SectionLabel("Drive mode")
        ToggleRow(
            "Drive mode",
            "Fastest GPS + full-power WiFi/BT scanning, no idle back-off. Best coverage, heavy battery — keep the phone charged.",
            prefs.driveMode,
        ) { onDriveMode(it); onChanged() }

        SectionLabel("Map")
        ToggleRow("Show map tiles (OpenStreetMap)", "Uses data. Off = graticule only.", prefs.mapTiles) {
            prefs.mapTiles = it; onChanged()
        }
        ToggleRow("Follow my location", "Keep the map centred on you.", prefs.mapFollow) {
            prefs.mapFollow = it; onChanged()
        }
        ToggleRow("Keep screen on (Map)", "For a windscreen mount.", prefs.keepScreenOn) {
            prefs.keepScreenOn = it; onChanged()
        }

        SectionLabel("Scanning")
        ToggleRow("Haptic on new device", "Short tick the first time a device is seen.", prefs.newDeviceHaptic) {
            prefs.newDeviceHaptic = it; onChanged()
        }
        val ctx = LocalContext.current
        val throttled = remember(prefs.driveMode) {
            runCatching {
                (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isScanThrottleEnabled
            }.getOrDefault(true)
        }
        Text(
            if (throttled) {
                "Wi-Fi scan throttling: ON — Android limits scans to ~4 per 2 min. " +
                    "Turn it off in Developer options → “Wi-Fi scan throttling” and the app scans much faster."
            } else {
                "Wi-Fi scan throttling: OFF — high-rate scan mode is active."
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (throttled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        SectionLabel("Units")
        val options = listOf("metric" to "Metric", "imperial" to "Imperial")
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    selected = prefs.units == value,
                    onClick = { prefs.units = value; onChanged() },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label) }
            }
        }

        SectionLabel("Server")
        Text(prefs.baseUrl, style = MaterialTheme.typography.bodyMedium)
        Text("Change it in the Go Live / Upload dialog.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        TextButton(onClick = onForgetLogin) { Text("Forget saved login") }
        TextButton(onClick = onResetSession) { Text("Clear current session") }
        TextButton(onClick = onReplayIntro) { Text("Show the intro again") }
        TextButton(onClick = onOpenAbout) { Text("About & phone-optimise guide") }

        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
