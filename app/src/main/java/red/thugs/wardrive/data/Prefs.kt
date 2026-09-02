package red.thugs.wardrive.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import red.thugs.wardrive.BuildConfig

/**
 * Small encrypted key/value store (AES-256, Android Keystore-backed) for the
 * things the app remembers between runs: the server URL, the sign-in
 * credentials (only when the user opts in), and the issued ingest token.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "wardrive_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var baseUrl: String
        get() = sp.getString(K_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_BASE_URL
        set(v) = sp.edit().putString(K_BASE_URL, v.trim().trimEnd('/')).apply()

    var username: String
        get() = sp.getString(K_USERNAME, "") ?: ""
        set(v) = sp.edit().putString(K_USERNAME, v).apply()

    /** Only set when [rememberCredentials] is on. Encrypted at rest, device-local. */
    var password: String
        get() = sp.getString(K_PASSWORD, "") ?: ""
        set(v) = sp.edit().apply { if (v.isEmpty()) remove(K_PASSWORD) else putString(K_PASSWORD, v) }.apply()

    var rememberCredentials: Boolean
        get() = sp.getBoolean(K_REMEMBER, false)
        set(v) = sp.edit().putBoolean(K_REMEMBER, v).apply()

    /** Show OpenStreetMap tiles under the map (uses data). On by default. */
    var mapTiles: Boolean
        get() = sp.getBoolean(K_MAP_TILES, true)
        set(v) = sp.edit().putBoolean(K_MAP_TILES, v).apply()

    /** Keep the map centred on the current position. */
    var mapFollow: Boolean
        get() = sp.getBoolean(K_MAP_FOLLOW, true)
        set(v) = sp.edit().putBoolean(K_MAP_FOLLOW, v).apply()

    /** Hold the screen on while the Map view is open (car mount). */
    var keepScreenOn: Boolean
        get() = sp.getBoolean(K_KEEP_SCREEN_ON, false)
        set(v) = sp.edit().putBoolean(K_KEEP_SCREEN_ON, v).apply()

    /** Short haptic tick when a device is seen for the first time this session. */
    var newDeviceHaptic: Boolean
        get() = sp.getBoolean(K_HAPTIC, false)
        set(v) = sp.edit().putBoolean(K_HAPTIC, v).apply()

    /**
     * Drive mode: fastest GPS updates and full-power scanning with no idle
     * back-off — maximum coverage, heavy battery use. Off by default.
     */
    var driveMode: Boolean
        get() = sp.getBoolean(K_DRIVE_MODE, false)
        set(v) = sp.edit().putBoolean(K_DRIVE_MODE, v).apply()

    /** "metric" or "imperial" — affects the map scale bar and distances. */
    var units: String
        get() = sp.getString(K_UNITS, "metric") ?: "metric"
        set(v) = sp.edit().putString(K_UNITS, v).apply()

    val imperial: Boolean get() = units == "imperial"

    var ingestToken: String?
        get() = sp.getString(K_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(v) = sp.edit().apply { if (v.isNullOrBlank()) remove(K_TOKEN) else putString(K_TOKEN, v) }.apply()

    /** Persist or wipe the credentials according to the "remember" choice. */
    fun saveCredentials(user: String, pass: String, remember: Boolean) {
        rememberCredentials = remember
        username = user
        password = if (remember) pass else ""
    }

    /** Wipe everything tied to an account so a different user can sign in. */
    fun forgetLogin() {
        sp.edit()
            .remove(K_USERNAME)
            .remove(K_PASSWORD)
            .remove(K_REMEMBER)
            .remove(K_TOKEN)
            .apply()
    }

    private companion object {
        const val K_BASE_URL = "base_url"
        const val K_USERNAME = "username"
        const val K_PASSWORD = "password"
        const val K_REMEMBER = "remember_credentials"
        const val K_MAP_TILES = "map_tiles"
        const val K_MAP_FOLLOW = "map_follow"
        const val K_KEEP_SCREEN_ON = "keep_screen_on"
        const val K_HAPTIC = "new_device_haptic"
        const val K_DRIVE_MODE = "drive_mode"
        const val K_UNITS = "units"
        const val K_TOKEN = "ingest_token"
    }
}
