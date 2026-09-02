package red.thugs.wardrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind

private enum class ListFilter(val label: String, val kind: RadioKind?) {
    ALL("All", null), WIFI("WiFi", RadioKind.WIFI_AP), BT("BT", RadioKind.BT_CLASSIC), BLE("BLE", RadioKind.BT_LE)
}

private enum class ListSort(val label: String) { RECENT("Recent"), STRONGEST("Strongest"), SSID("SSID"), CHANNEL("Channel") }

@Composable
fun ListView(
    observations: List<Observation>,
    scanning: Boolean,
    onRowClick: (Observation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(ListFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(ListSort.RECENT) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var newOnly by rememberSaveable { mutableStateOf(false) }
    var sortMenu by rememberSaveable { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val shown = observations
        .asSequence()
        .filter { filter.kind == null || it.kind == filter.kind }
        .filter { !newOnly || now - it.timestampMs < 60_000 }
        .filter {
            query.isBlank() ||
                it.ssid?.contains(query, ignoreCase = true) == true ||
                it.bssid.contains(query, ignoreCase = true)
        }
        .toList()
        .let { list ->
            when (sort) {
                ListSort.RECENT -> list.sortedByDescending { it.timestampMs }
                ListSort.STRONGEST -> list.sortedByDescending { it.signalDbm ?: -999 }
                ListSort.SSID -> list.sortedBy { it.ssid?.lowercase() ?: "￿" }
                ListSort.CHANNEL -> list.sortedBy { it.channel ?: 9999 }
            }
        }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ListFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
            FilterChip(selected = newOnly, onClick = { newOnly = !newOnly }, label = { Text("New") })
            TextButton(onClick = { sortMenu = true }) { Text("↕ ${sort.label}") }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                ListSort.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; sortMenu = false })
                }
            }
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        }
        if (searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("SSID or BSSID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        Text(
            "${shown.size} of ${observations.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
        )

        if (shown.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when {
                        observations.isEmpty() && scanning -> "Scanning… waiting for a GPS fix and the first results."
                        observations.isEmpty() -> "Tap Start scan to begin a run."
                        else -> "Nothing matches the current filter."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.kind.name + it.bssid }) { o ->
                    ObservationRow(o, onClick = { onRowClick(o) })
                }
            }
        }
    }
}
