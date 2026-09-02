package red.thugs.wardrive.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin wrapper over [LocationManager]. Every scan result is stamped with
 * [current], the freshest fix we have; a stale fix (older than [MAX_AGE_MS]) is
 * treated as no fix so an observation is not placed where the car used to be.
 *
 * Battery: updates are requested at [UPDATE_INTERVAL_MS], not as fast as the
 * chipset can go, and the NETWORK provider is only a fallback for an early fix.
 */
class LocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val best = _location.value
            if (best == null || location.time >= best.time) _location.value = location
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun start() {
        if (!hasPermission()) return
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(listener::onLocationChanged)
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, listener, Looper.getMainLooper(),
            )
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
                if (_location.value == null) listener.onLocationChanged(it)
            }
            lm.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, NETWORK_INTERVAL_MS, 0f, listener, Looper.getMainLooper(),
            )
        }
    }

    fun stop() {
        runCatching { lm.removeUpdates(listener) }
    }

    /** The current fix if it is fresh enough to tie an observation to, else null. */
    fun current(): Location? =
        _location.value?.takeIf { System.currentTimeMillis() - it.time <= MAX_AGE_MS }

    /** Roughly "the car is moving" — used to pick a scan cadence. */
    fun isMoving(): Boolean {
        val l = current() ?: return false
        return l.hasSpeed() && l.speed >= MOVING_SPEED_MS
    }

    private companion object {
        const val MAX_AGE_MS = 15_000L
        const val UPDATE_INTERVAL_MS = 2_000L
        const val NETWORK_INTERVAL_MS = 10_000L
        const val MOVING_SPEED_MS = 1.2f // ~4.3 km/h
    }
}
