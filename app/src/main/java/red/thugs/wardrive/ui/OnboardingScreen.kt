package red.thugs.wardrive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import red.thugs.wardrive.R

private val FOREGROUND_PERMS: Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}.toTypedArray()

/** First-run walkthrough: what the app needs, the account, and the permissions. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val uris = LocalUriHandler.current
    var step by remember { mutableIntStateOf(0) }
    var permTick by remember { mutableIntStateOf(0) }

    fun granted(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    val fineGranted = permTick.let { granted(Manifest.permission.ACCESS_FINE_LOCATION) }
    val bgGranted = permTick.let {
        Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    val coreGranted = permTick.let { FOREGROUND_PERMS.all { p -> granted(p) } }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permTick++ }
    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permTick++ }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    painterResource(R.drawable.ic_launcher_foreground),
                    null,
                    Modifier.size(40.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.width(10.dp))
                Text("THUGS Wardrive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                "Step ${step + 1} of 3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> StepWelcome(uris)
                    1 -> StepPermissions(
                        coreGranted = coreGranted,
                        fineGranted = fineGranted,
                        bgGranted = bgGranted,
                        onGrantCore = { permLauncher.launch(FOREGROUND_PERMS) },
                        onGrantBackground = {
                            if (Build.VERSION.SDK_INT >= 29) {
                                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        },
                    )
                    else -> StepReady()
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    TextButton(onClick = { step-- }) { Text("Back") }
                } else {
                    TextButton(onClick = onDone) { Text("Skip") }
                }
                if (step < 2) {
                    Button(onClick = { step++ }) { Text("Next") }
                } else {
                    Button(onClick = onDone) { Text("Get started") }
                }
            }
        }
    }
}

@Composable
private fun StepWelcome(uris: UriHandler) {
    Text("Scan WiFi + Bluetooth as you drive", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        "Every access point and BT/BLE device you pass is logged with a GPS fix. " +
            "Watch them on the live list, the map, Stats and Scope; when you're done, " +
            "stream them live or upload the session to wardrive.thugs.red.",
        style = MaterialTheme.typography.bodyMedium,
    )

    Spacer(Modifier.height(4.dp))
    Text("You need an approved account for sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Go Live and Upload sign in to wardrive.thugs.red, and that account is not " +
            "self-serve:\n" +
            "1. Register on the site.\n" +
            "2. New accounts are pending until a THUGS(red) moderator approves them.\n" +
            "3. Ask THUGS(red) staff on Discord to approve you.\n\n" +
            "Everything else — scanning, all four views, saving/exporting a CSV — works " +
            "without an account.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(onClick = { runCatching { uris.openUri("https://wardrive.thugs.red/register") } }) {
        Text("Open the register page")
    }
}

@Composable
private fun StepPermissions(
    coreGranted: Boolean,
    fineGranted: Boolean,
    bgGranted: Boolean,
    onGrantCore: () -> Unit,
    onGrantBackground: () -> Unit,
) {
    Text("Permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        "The app needs these to scan and place observations. Nothing is uploaded " +
            "unless you sign in and choose to.",
        style = MaterialTheme.typography.bodyMedium,
    )

    PermRow("Location", "every observation is placed at a GPS fix", fineGranted)
    PermRow("Nearby Wi-Fi devices", "read scan results on Android 13+", coreGranted)
    PermRow("Nearby devices / Bluetooth", "find BT + BLE devices and their names", coreGranted)
    PermRow("Notifications", "the running-session notification with a Stop button", coreGranted)

    Button(onClick = onGrantCore, enabled = !coreGranted, modifier = Modifier.fillMaxWidth()) {
        Text(if (coreGranted) "All set" else "Grant permissions")
    }

    if (fineGranted && !bgGranted) {
        Spacer(Modifier.height(4.dp))
        PermRow("Location — all the time", "keep scanning with the screen off / app backgrounded", false)
        OutlinedButton(onClick = onGrantBackground, modifier = Modifier.fillMaxWidth()) {
            Text("Allow all the time")
        }
        Text(
            "Android opens its settings for this one — pick “Allow all the time”.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(
        "You can skip and grant these later from Android Settings, but scanning won't " +
            "work until Location is allowed.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StepReady() {
    Text("You're set", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Bullet("Tap Start scan to begin a run — a foreground service keeps it going with the screen off.")
    Bullet("Switch views with the List · Map · Stats · Scope strip under the header.")
    Bullet("Serious drive? Settings → Drive mode: fastest GPS + full-power scanning. Keep the phone charged.")
    Bullet("The session CSV is written to disk as you go, so a crash won't lose it.")
    Bullet("About has a full checklist for de-cluttering your phone before a drive.")
}

@Composable
private fun PermRow(title: String, why: String, granted: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (granted) "✓" else "•",
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(why, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("▸", color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
