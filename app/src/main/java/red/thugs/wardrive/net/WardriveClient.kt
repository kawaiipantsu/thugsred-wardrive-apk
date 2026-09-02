package red.thugs.wardrive.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import red.thugs.wardrive.data.Observation
import java.io.File
import java.util.concurrent.TimeUnit

/** Outcome of one POST to /api/v1/ingest. */
data class IngestResult(
    val httpStatus: Int,
    val accepted: Int,
    val rejected: Int,
    val retryAfterSec: Int?,
    val message: String?,
) {
    /** The server has the batch (even if some records were rejected). */
    val serverHasIt: Boolean get() = httpStatus in 200..299
    val rateLimited: Boolean get() = httpStatus == 429
    val ingestDisabled: Boolean get() = httpStatus == 503
    val unauthorized: Boolean get() = httpStatus == 401
    val tooLarge: Boolean get() = httpStatus == 413
}

class WardriveException(message: String) : Exception(message)

/**
 * Talks to wardrive.thugs.red the way a browser would: there is no JSON login,
 * so [login] scrapes the CSRF token and keeps the session cookie, and
 * [ensureIngestToken] drives the /tokens form. [ingest] then uses the bearer
 * token directly.
 */
class WardriveClient(baseUrl: String) {

    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrlOrThrowFriendly()

    private val cookies = mutableMapOf<String, MutableList<Cookie>>()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
                val host = url.host
                val bucket = cookies.getOrPut(host) { mutableListOf() }
                for (c in list) {
                    bucket.removeAll { it.name == c.name }
                    bucket.add(c)
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                cookies[url.host]?.filter { it.matches(url) } ?: emptyList()
        })
        .build()

    private fun url(vararg segments: String): HttpUrl =
        base.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()

    // -- Auth -------------------------------------------------------------

    /** Sign in. Leaves an authenticated session cookie in [cookies] on success. */
    suspend fun login(username: String, password: String): Unit = withContext(Dispatchers.IO) {
        val csrf = fetchCsrf(url("login"))
        val body = FormBody.Builder()
            .add("csrf_token", csrf)
            .add("identifier", username)
            .add("password", password)
            .build()
        val resp = http.newCall(Request.Builder().url(url("login")).post(body).build()).execute()
        resp.use {
            val finalPath = it.request.url.encodedPath
            val text = it.body?.string().orEmpty()
            if (finalPath.endsWith("/login") || text.contains("Invalid username or password")) {
                throw WardriveException(loginFailureReason(text))
            }
            if (finalPath.endsWith("/login") && it.code >= 400) {
                throw WardriveException("Login failed (HTTP ${it.code}).")
            }
        }
    }

    private fun loginFailureReason(html: String): String = when {
        html.contains("awaiting approval") -> "Account is awaiting moderator approval."
        html.contains("suspended") -> "This account is suspended."
        html.contains("banned") -> "This account is banned."
        html.contains("Too many failed attempts") -> "Too many failed attempts. Try again later."
        else -> "Invalid username or password."
    }

    /**
     * Return a usable `wdrv_` ingest token, creating one via the /tokens form.
     * The site shows a token exactly once, so this must be called right after it
     * is minted; the caller is expected to cache the result.
     */
    suspend fun ensureIngestToken(label: String): String = withContext(Dispatchers.IO) {
        val tokensUrl = url("tokens")
        val csrf = fetchCsrf(tokensUrl)
        val create = FormBody.Builder()
            .add("csrf_token", csrf)
            .add("label", label)
            .build()
        http.newCall(Request.Builder().url(tokensUrl).post(create).build()).execute().use {
            if (it.request.url.encodedPath.endsWith("/login")) {
                throw WardriveException("Session expired before a token could be created.")
            }
        }
        val page = http.newCall(Request.Builder().url(tokensUrl).get().build()).execute().use {
            it.body?.string().orEmpty()
        }
        TOKEN_VALUE.find(page)?.groupValues?.get(1)?.trim()
            ?: throw WardriveException("Token was created but could not be read back from the page.")
    }

    // -- Live ingest ----------------------------------------------------

    suspend fun ingest(token: String, batch: List<Observation>): IngestResult = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        for (o in batch) {
            if (!o.isWifi || !o.hasFix) continue
            arr.put(
                JSONObject().apply {
                    put("bssid", o.bssid)
                    o.ssid?.let { put("ssid", it) }
                    o.capabilities?.let { put("enc", it) }
                    o.signalDbm?.let { put("rssi", it) }
                    o.channel?.let { put("ch", it) }
                    o.frequencyMhz?.let { put("freq", it) }
                    put("lat", o.lat)
                    put("lon", o.lon)
                    o.altitudeM?.let { put("alt", it) }
                    put("ts", o.timestampMs / 1000)
                },
            )
        }
        val payload = JSONObject().put("observations", arr).toString()
        val req = Request.Builder()
            .url(url("api", "v1", "ingest"))
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "THUGS-Wardrive-Android/1.0")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            IngestResult(
                httpStatus = resp.code,
                accepted = json?.optInt("accepted", 0) ?: 0,
                rejected = json?.optInt("rejected", 0) ?: 0,
                retryAfterSec = resp.header("Retry-After")?.toIntOrNull(),
                message = json?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("error")?.takeIf { it.isNotBlank() },
            )
        }
    }

    // -- File upload --------------------------------------------------

    /** Upload a finished session file. Returns the upload id (or a status word). */
    suspend fun uploadCsv(file: File): String = withContext(Dispatchers.IO) {
        val newUrl = url("uploads", "new")
        val csrf = fetchCsrf(newUrl)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("csrf_token", csrf)
            .addFormDataPart(
                "dump", file.name,
                file.asRequestBody("text/csv".toMediaType()),
            )
            .build()
        http.newCall(Request.Builder().url(url("uploads")).post(body).build()).execute().use { resp ->
            val finalPath = resp.request.url.encodedPath
            val text = resp.body?.string().orEmpty()
            when {
                finalPath.endsWith("/login") ->
                    throw WardriveException("Not signed in. Enter your credentials and try again.")
                UPLOAD_ID_IN_PATH.matches(finalPath) ->
                    UPLOAD_ID_IN_PATH.find(finalPath)!!.groupValues[1]
                finalPath.endsWith("/uploads/new") ->
                    throw WardriveException(extractFlash(text) ?: "The server rejected the file.")
                resp.code in 200..299 -> "accepted"
                else -> throw WardriveException("Upload failed (HTTP ${resp.code}).")
            }
        }
    }

    // -- helpers -----------------------------------------------------

    private fun fetchCsrf(pageUrl: HttpUrl): String {
        val html = http.newCall(Request.Builder().url(pageUrl).get().build()).execute().use {
            if (it.request.url.encodedPath.endsWith("/login") && !pageUrl.encodedPath.endsWith("/login")) {
                throw WardriveException("Not signed in.")
            }
            it.body?.string().orEmpty()
        }
        return CSRF_FIELD.find(html)?.groupValues?.get(1)
            ?: throw WardriveException("Could not read a CSRF token from ${pageUrl.encodedPath}.")
    }

    private fun extractFlash(html: String): String? =
        FLASH.find(html)?.groupValues?.get(1)?.trim()?.replace(Regex("<[^>]+>"), "")?.takeIf { it.isNotBlank() }

    private companion object {
        val CSRF_FIELD = Regex("""name="csrf_token"\s+value="([^"]+)"""")
        val TOKEN_VALUE = Regex("""class="token-value">([^<]+)<""")
        val UPLOAD_ID_IN_PATH = Regex("""^/uploads/([0-9a-fA-F-]{36})$""")
        val FLASH = Regex("""class="flash[^"]*"[^>]*>(.*?)</""", RegexOption.DOT_MATCHES_ALL)
    }
}

private fun String.toHttpUrlOrThrowFriendly(): HttpUrl =
    (toHttpUrlOrNull() ?: "https://$this".toHttpUrlOrNull())
        ?: throw WardriveException("“$this” is not a valid server URL.")
