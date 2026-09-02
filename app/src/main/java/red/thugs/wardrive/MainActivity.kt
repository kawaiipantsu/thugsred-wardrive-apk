package red.thugs.wardrive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import red.thugs.wardrive.ui.MainScreen
import red.thugs.wardrive.ui.MainViewModel
import red.thugs.wardrive.ui.theme.WardriveTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val locationOk = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (locationOk) {
            vm.startScan()
            maybeAskBackgroundLocation()
        } else {
            Toast.makeText(
                this,
                "Location permission is required to place observations.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional — scanning still works while the app is visible without it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WardriveTheme {
                MainScreen(vm = vm, onRequestScanStart = ::requestScanPermissionsThenStart)
            }
        }
    }

    private fun requestScanPermissionsThenStart() {
        val missing = requiredPermissions.filterNot { hasPermission(it) }
        if (missing.isEmpty()) {
            vm.startScan()
            maybeAskBackgroundLocation()
        } else {
            permLauncher.launch(missing.toTypedArray())
        }
    }

    private fun maybeAskBackgroundLocation() {
        if (!hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}
