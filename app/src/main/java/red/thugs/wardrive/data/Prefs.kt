package red.thugs.wardrive.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import red.thugs.wardrive.BuildConfig

/**
 * Small encrypted key/value store for the things the app must remember between
 * runs: the server URL, the last username typed, and the issued ingest token.
 * The password is never written to disk.
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

    var ingestToken: String?
        get() = sp.getString(K_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(v) = sp.edit().apply { if (v.isNullOrBlank()) remove(K_TOKEN) else putString(K_TOKEN, v) }.apply()

    private companion object {
        const val K_BASE_URL = "base_url"
        const val K_USERNAME = "username"
        const val K_TOKEN = "ingest_token"
    }
}
