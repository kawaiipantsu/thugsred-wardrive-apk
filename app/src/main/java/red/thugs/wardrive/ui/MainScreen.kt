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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import red.thugs.wardrive.R
import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind
import red.thugs.wardrive.net.LiveState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    vm: MainViewModel,
    onRequestScanStart: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(Screen.LIST) }
    var credPurpose by remember { mutableStateOf<CredentialPurpose?>(null) }
    var pendingUploadFile by remember { mutableStateOf<File?>(null) }
    var showSaved by remember { mutableStateOf(false) }

    val observations by vm.session.observations.collectAsStateWithLifecycle()
    val counts by vm.session.counts.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val powerSaving by vm.powerSaving.collectAsStateWithLifecycle()
    val live by vm.liveStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val track by vm.track.collectAsStateWithLifecycle()
    val currentLatLon by vm.currentLatLon.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopBanner(
                onGoLive = { credPurpose = CredentialPurpose.GO_LIVE },
                onUpload = { pendingUploadFile = null; credPurpose = CredentialPurpose.UPLOAD },
                onAbout = { screen = Screen.ABOUT },
                onList = { screen = Screen.LIST },
                onSavedSessions = { showSaved = true },
                onExport = { vm.exportOnly() },
                onStopLive = { vm.stopLive() },
                onMap = { screen = Screen.MAP },
                liveActive = live.state != LiveState.OFF,
            )
        },
        bottomBar = {
            SessionFooter(
                counts.wifiAp, counts.btClassic, counts.btLe, counts.withFix, observations.size,
                powerSaving = scanning && powerSaving,
            )
        },
        floatingActionButton = {
            if (screen != Screen.ABOUT) {
                ExtendedFloatingActionButton(
                    onClick = { if (scanning) vm.stopScan() else onRequestScanStart() },
                    icon = {
                        Icon(if (scanning) AppIcons.Stop else Icons.Filled.PlayArrow, null)
                    },
                    text = { Text(if (scanning) "Stop" else "Start scan") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (screen) {
                Screen.ABOUT -> AboutScreen()
                Screen.MAP -> Column(Modifier.fillMaxSize()) {
                    LiveStrip(live)
                    MapScreen(points = observations, track = track, current = currentLatLon)
                }
                Screen.LIST -> Column(Modifier.fillMaxSize()) {
                    LiveStrip(live)
                    if (observations.isEmpty()) {
                        EmptyState(scanning)
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(observations, key = { it.kind.name + it.bssid }) { ObservationRow(it) }
                        }
                    }
                }
            }

            if (busy is Busy.Working) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text((busy as Busy.Working).what, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }

    credPurpose?.let { purpose ->
        CredentialsDialog(
            purpose = purpose,
            initialUsername = vm.prefs.username,
            initialBaseUrl = vm.prefs.baseUrl,
            onDismiss = { credPurpose = null },
            onSubmit = { user, pass, url ->
                vm.prefs.baseUrl = url
                when (purpose) {
                    CredentialPurpose.GO_LIVE -> vm.goLive(user, pass)
                    CredentialPurpose.UPLOAD -> vm.upload(user, pass, pendingUploadFile)
                }
                credPurpose = null
            },
        )
    }

    if (showSaved) {
        SavedSessionsDialog(
            files = vm.savedSessions(),
            onDismiss = { showSaved = false },
            onUpload = { f ->
                showSaved = false
                pendingUploadFile = f
                credPurpose = CredentialPurpose.UPLOAD
            },
        )
    }
}

@Composable
private fun EmptyState(scanning: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_launcher_foreground),
            null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (scanning) "Scanning… waiting for a GPS fix and the first results."
            else "Tap Start scan to begin a run.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun LiveStrip(live: red.thugs.wardrive.net.LiveStatus) {
    if (live.state == LiveState.OFF) return
    val color = when (live.state) {
        LiveState.LIVE -> MaterialTheme.colorScheme.primary
        LiveState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    Surface(color = color.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Podcasts, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "LIVE · sent ${live.sentOk} · queued ${live.pending}" +
                    (live.detail?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun ObservationRow(o: Observation) {
    val (icon, tint) = when (o.kind) {
        RadioKind.WIFI_AP -> AppIcons.Wifi to MaterialTheme.colorScheme.primary
        RadioKind.BT_CLASSIC -> AppIcons.Bluetooth to MaterialTheme.colorScheme.secondary
        RadioKind.BT_LE -> AppIcons.Bluetooth to MaterialTheme.colorScheme.tertiary
    }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    o.ssid?.takeIf { it.isNotBlank() } ?: "(hidden)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    o.bssid + (o.capabilities?.let { "  ·  " + it.take(28) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    buildString {
                        o.channel?.let { append("ch $it  ") }
                        o.signalDbm?.let { append("$it dBm  ") }
                        append(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(o.timestampMs)))
                        if (!o.hasFix) append("  · no fix")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

@Composable
fun SessionFooter(wifi: Int, bt: Int, ble: Int, withFix: Int, devices: Int, powerSaving: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            if (powerSaving) {
                Text(
                    "power-saving · stationary — scan cadence reduced",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stat("WiFi", wifi, AppIcons.Wifi, MaterialTheme.colorScheme.primary)
                Stat("BT", bt, AppIcons.Bluetooth, MaterialTheme.colorScheme.secondary)
                Stat("BLE", ble, AppIcons.Bluetooth, MaterialTheme.colorScheme.tertiary)
                Stat("Fixes", withFix, Icons.Filled.PlayArrow, MaterialTheme.colorScheme.onSurfaceVariant)
                Stat("Devices", devices, Icons.Filled.MoreVert, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopBanner(
    onGoLive: () -> Unit,
    onUpload: () -> Unit,
    onAbout: () -> Unit,
    onList: () -> Unit,
    onSavedSessions: () -> Unit,
    onExport: () -> Unit,
    onStopLive: () -> Unit,
    onMap: () -> Unit,
    liveActive: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_launcher_foreground),
                    null,
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "THUGS Wardrive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    "wardrive.thugs.red",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onGoLive) {
                Icon(AppIcons.Podcasts, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Go Live")
            }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Go Live") },
                    leadingIcon = { Icon(AppIcons.Podcasts, null) },
                    onClick = { menu = false; onGoLive() },
                )
                if (liveActive) {
                    DropdownMenuItem(
                        text = { Text("Stop live ingest") },
                        leadingIcon = { Icon(AppIcons.Stop, null) },
                        onClick = { menu = false; onStopLive() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Upload current session") },
                    leadingIcon = { Icon(AppIcons.CloudUpload, null) },
                    onClick = { menu = false; onUpload() },
                )
                DropdownMenuItem(
                    text = { Text("Saved sessions…") },
                    onClick = { menu = false; onSavedSessions() },
                )
                DropdownMenuItem(
                    text = { Text("Export CSV to storage") },
                    onClick = { menu = false; onExport() },
                )
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Live list") }, onClick = { menu = false; onList() })
                DropdownMenuItem(text = { Text("Map") }, onClick = { menu = false; onMap() })
                DropdownMenuItem(text = { Text("About") }, onClick = { menu = false; onAbout() })
            }
        }
    }
}

@Composable
private fun SavedSessionsDialog(files: List<File>, onDismiss: () -> Unit, onUpload: (File) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved sessions") },
        text = {
            if (files.isEmpty()) {
                Text("No saved sessions yet. Use “Export CSV to storage” or Upload to create one.")
            } else {
                LazyColumn {
                    items(files, key = { it.name }) { f ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    "${f.length()} bytes · " +
                                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(f.lastModified())),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onUpload(f) }) { Text("Upload") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
