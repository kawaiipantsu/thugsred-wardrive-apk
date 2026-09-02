package red.thugs.wardrive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import red.thugs.wardrive.R
import red.thugs.wardrive.data.CongestionSample
import red.thugs.wardrive.data.GrowthPoint
import red.thugs.wardrive.data.Observation
import red.thugs.wardrive.data.RadioKind
import red.thugs.wardrive.data.SessionCounts
import red.thugs.wardrive.data.TrackPoint
import red.thugs.wardrive.net.LiveState
import red.thugs.wardrive.net.LiveStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything [WardriveScaffold] needs to draw, with no ViewModel dependency (renders in previews/screenshot tests). */
data class WardriveUiState(
    val screen: Screen = Screen.LIST,
    val observations: List<Observation> = emptyList(),
    val counts: SessionCounts = SessionCounts(),
    val scanning: Boolean = false,
    val powerSaving: Boolean = false,
    val live: LiveStatus = LiveStatus(),
    val track: List<TrackPoint> = emptyList(),
    val current: Pair<Double, Double>? = null,
    val mapTiles: Boolean = false,
    val congestion: List<CongestionSample> = emptyList(),
    val growth: List<GrowthPoint> = emptyList(),
)

/** Callbacks the chrome fires. Default no-ops keep previews trivial. */
data class WardriveActions(
    val onGoLive: () -> Unit = {},
    val onUpload: () -> Unit = {},
    val onAbout: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onSelectScreen: (Screen) -> Unit = {},
    val onSavedSessions: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onStopLive: () -> Unit = {},
    val onForgetLogin: () -> Unit = {},
    val onToggleScan: () -> Unit = {},
    val onToggleMapTiles: () -> Unit = {},
    val showForgetLogin: Boolean = false,
)

@Composable
fun MainScreen(
    vm: MainViewModel,
    onRequestScanStart: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }

    var screen by remember { mutableStateOf(Screen.LIST) }
    var credPurpose by remember { mutableStateOf<CredentialPurpose?>(null) }
    var pendingUploadFile by remember { mutableStateOf<File?>(null) }
    var showSaved by remember { mutableStateOf(false) }
    var mapTiles by remember { mutableStateOf(vm.prefs.mapTiles) }
    var mapFollow by remember { mutableStateOf(vm.prefs.mapFollow) }
    var settingsRev by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Observation?>(null) }

    val observations by vm.session.observations.collectAsStateWithLifecycle()
    val counts by vm.session.counts.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val powerSaving by vm.powerSaving.collectAsStateWithLifecycle()
    val live by vm.liveStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val track by vm.track.collectAsStateWithLifecycle()
    val currentLatLon by vm.currentLatLon.collectAsStateWithLifecycle()
    val currentAccuracy by vm.currentAccuracy.collectAsStateWithLifecycle()
    val congestion by vm.congestion.collectAsStateWithLifecycle()
    val growth by vm.growth.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    // Keep the screen on while the Map is up, if the user asked for it.
    KeepScreenOn(enabled = screen == Screen.MAP && vm.prefs.keepScreenOn && settingsRev >= 0)

    val state = WardriveUiState(
        screen = screen,
        observations = observations,
        counts = counts,
        scanning = scanning,
        powerSaving = powerSaving,
        live = live,
        track = track,
        current = currentLatLon,
        mapTiles = mapTiles,
        congestion = congestion,
        growth = growth,
    )
    val actions = WardriveActions(
        onGoLive = {
            when {
                live.state != LiveState.OFF -> Unit
                vm.hasSavedToken -> vm.startLiveWithSavedToken()
                else -> credPurpose = CredentialPurpose.GO_LIVE
            }
        },
        onUpload = { pendingUploadFile = null; credPurpose = CredentialPurpose.UPLOAD },
        onAbout = { screen = Screen.ABOUT },
        onSettings = { screen = Screen.SETTINGS },
        onSelectScreen = { screen = it },
        onSavedSessions = { showSaved = true },
        onExport = { vm.exportOnly() },
        onShare = { vm.shareCsv() },
        onStopLive = { vm.stopLive() },
        onForgetLogin = { vm.forgetLogin() },
        onToggleScan = { if (scanning) vm.stopScan() else onRequestScanStart() },
        onToggleMapTiles = { mapTiles = !mapTiles; vm.prefs.mapTiles = mapTiles },
        showForgetLogin = vm.prefs.rememberCredentials || vm.hasSavedToken,
    )

    Box(Modifier.fillMaxSize()) {
        WardriveScaffold(state, actions, snackbarHost = { SnackbarHost(snackbar) }) { s ->
            when (s) {
                Screen.SETTINGS -> SettingsScreen(
                    prefs = vm.prefs,
                    onChanged = {
                        settingsRev++
                        mapTiles = vm.prefs.mapTiles
                    },
                    onOpenAbout = { screen = Screen.ABOUT },
                    onForgetLogin = { vm.forgetLogin() },
                    onResetSession = { vm.resetSession() },
                )
                Screen.LIST -> Column(Modifier.fillMaxSize()) {
                    LiveStrip(state.live)
                    ListView(
                        observations = state.observations,
                        scanning = state.scanning,
                        onRowClick = { detail = it },
                    )
                }
                Screen.MAP -> Column(Modifier.fillMaxSize()) {
                    LiveStrip(state.live)
                    MapScreen(
                        points = state.observations,
                        track = state.track,
                        current = state.current,
                        tilesEnabled = mapTiles,
                        onToggleTiles = { mapTiles = !mapTiles; vm.prefs.mapTiles = mapTiles },
                        accuracyM = currentAccuracy,
                        follow = mapFollow,
                        onToggleFollow = { mapFollow = !mapFollow; vm.prefs.mapFollow = mapFollow },
                    )
                }
                else -> DefaultScreenContent(state, actions, s)
            }
        }

        if (busy is Busy.Working) {
            Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), modifier = Modifier.fillMaxSize()) {
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

    detail?.let { o ->
        ObservationDetailSheet(
            observation = o,
            sightings = vm.session.sightingsFor(o.kind, o.bssid),
            current = currentLatLon,
            baseUrl = vm.prefs.baseUrl,
            onDismiss = { detail = null },
            onToast = { vm.toastPublic(it) },
        )
    }

    credPurpose?.let { purpose ->
        CredentialsDialog(
            purpose = purpose,
            initialUsername = vm.prefs.username,
            initialPassword = vm.prefs.password,
            initialRemember = vm.prefs.rememberCredentials,
            initialBaseUrl = vm.prefs.baseUrl,
            onDismiss = { credPurpose = null },
            onSubmit = { user, pass, remember, url ->
                vm.prefs.baseUrl = url
                when (purpose) {
                    CredentialPurpose.GO_LIVE -> vm.goLive(user, pass, remember)
                    CredentialPurpose.UPLOAD -> vm.upload(user, pass, remember, pendingUploadFile)
                }
                credPurpose = null
            },
        )
    }

    if (showSaved) {
        SavedSessionsDialog(
            files = vm.savedSessions(),
            onDismiss = { showSaved = false },
            onUpload = { f -> showSaved = false; pendingUploadFile = f; credPurpose = CredentialPurpose.UPLOAD },
        )
    }
}

/** Chrome (banner + quick-nav + footer + FAB). [screenContent] renders the current screen. */
@Composable
internal fun WardriveScaffold(
    state: WardriveUiState,
    actions: WardriveActions,
    snackbarHost: @Composable () -> Unit = {},
    screenContent: @Composable (Screen) -> Unit = { DefaultScreenContent(state, actions, it) },
) {
    Scaffold(
        snackbarHost = snackbarHost,
        topBar = {
            Column {
                TopBanner(
                    onGoLive = actions.onGoLive,
                    onUpload = actions.onUpload,
                    onAbout = actions.onAbout,
                    onSettings = actions.onSettings,
                    onSavedSessions = actions.onSavedSessions,
                    onExport = actions.onExport,
                    onShare = actions.onShare,
                    onStopLive = actions.onStopLive,
                    onForgetLogin = actions.onForgetLogin,
                    liveActive = state.live.state != LiveState.OFF,
                    showForgetLogin = actions.showForgetLogin,
                )
                QuickNav(state.screen, actions.onSelectScreen)
            }
        },
        bottomBar = {
            SessionFooter(
                state.counts.wifiAp, state.counts.btClassic, state.counts.btLe,
                state.counts.withFix, state.observations.size,
                powerSaving = state.scanning && state.powerSaving,
            )
        },
        floatingActionButton = {
            if (state.screen in QUICK_NAV_SCREENS) {
                ExtendedFloatingActionButton(
                    onClick = actions.onToggleScan,
                    containerColor = if (state.scanning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(),
                    icon = { Icon(if (state.scanning) AppIcons.Stop else Icons.Filled.PlayArrow, null) },
                    text = { Text(if (state.scanning) "Stop" else "Start scan") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) { screenContent(state.screen) }
    }
}

@Composable
internal fun DefaultScreenContent(state: WardriveUiState, actions: WardriveActions, screen: Screen) {
    when (screen) {
        Screen.ABOUT -> AboutScreen()
        Screen.SETTINGS -> Box(Modifier.fillMaxSize().padding(24.dp)) { Text("Settings") }
        Screen.STATS -> Column(Modifier.fillMaxSize()) {
            LiveStrip(state.live)
            StatsScreen(state.observations, state.growth, state.counts)
        }
        Screen.SCOPE -> Column(Modifier.fillMaxSize()) {
            LiveStrip(state.live)
            ScopeScreen(state.observations, state.congestion)
        }
        Screen.MAP -> Column(Modifier.fillMaxSize()) {
            LiveStrip(state.live)
            MapScreen(
                points = state.observations,
                track = state.track,
                current = state.current,
                tilesEnabled = state.mapTiles,
                onToggleTiles = actions.onToggleMapTiles,
            )
        }
        Screen.LIST -> Column(Modifier.fillMaxSize()) {
            LiveStrip(state.live)
            ListView(state.observations, state.scanning, onRowClick = {})
        }
    }
}

@Composable
private fun QuickNav(current: Screen, onSelect: (Screen) -> Unit) {
    val labels = mapOf(Screen.LIST to "List", Screen.MAP to "Map", Screen.STATS to "Stats", Screen.SCOPE to "Scope")
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth()) {
            QUICK_NAV_SCREENS.forEach { s ->
                val sel = s == current
                Box(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(s) }
                        .background(
                            if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labels.getValue(s),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
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
private fun LiveStrip(live: LiveStatus) {
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
                "LIVE · sent ${live.sentOk} · queued ${live.pending}" + (live.detail?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun ObservationRow(o: Observation, onClick: (() -> Unit)? = null) {
    val (icon, tint) = when (o.kind) {
        RadioKind.WIFI_AP -> AppIcons.Wifi to MaterialTheme.colorScheme.primary
        RadioKind.BT_CLASSIC -> AppIcons.Bluetooth to MaterialTheme.colorScheme.secondary
        RadioKind.BT_LE -> AppIcons.Bluetooth to MaterialTheme.colorScheme.tertiary
    }
    Column(Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
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
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
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
                Stat("WiFi", wifi, MaterialTheme.colorScheme.primary)
                Stat("BT", bt, MaterialTheme.colorScheme.secondary)
                Stat("BLE", ble, MaterialTheme.colorScheme.tertiary)
                Stat("Fixes", withFix, MaterialTheme.colorScheme.onSurfaceVariant)
                Stat("Devices", devices, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, tint: androidx.compose.ui.graphics.Color) {
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
    onSettings: () -> Unit,
    onSavedSessions: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onStopLive: () -> Unit,
    onForgetLogin: () -> Unit,
    liveActive: Boolean,
    showForgetLogin: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_launcher_foreground), null, tint = androidx.compose.ui.graphics.Color.Unspecified)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("THUGS Wardrive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text("wardrive.thugs.red", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                DropdownMenuItem(text = { Text("Share session CSV…") }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text("Saved sessions…") }, onClick = { menu = false; onSavedSessions() })
                DropdownMenuItem(text = { Text("Export CSV to storage") }, onClick = { menu = false; onExport() })
                if (showForgetLogin) {
                    DropdownMenuItem(text = { Text("Forget saved login") }, onClick = { menu = false; onForgetLogin() })
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
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
                Text("No saved sessions yet. Drive a bit, or use “Export CSV to storage”.")
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
