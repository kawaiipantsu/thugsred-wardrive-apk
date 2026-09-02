package red.thugs.wardrive.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import red.thugs.wardrive.data.Prefs
import red.thugs.wardrive.data.SessionStore

enum class LiveState { OFF, CONNECTING, LIVE, PAUSED, ERROR }

data class LiveStatus(
    val state: LiveState = LiveState.OFF,
    val sentOk: Int = 0,
    val pending: Int = 0,
    val detail: String? = null,
)

/**
 * Pushes buffered WiFi sightings to `POST /api/v1/ingest` while Go Live is on.
 *
 * Rules from the API guide: buffer, only clear on a 2xx, back off on 429, keep
 * buffering on 503, never resend an answered batch.
 */
class LiveIngestManager(
    private val prefs: Prefs,
    private val store: SessionStore,
) {
    private val _status = MutableStateFlow(LiveStatus())
    val status: StateFlow<LiveStatus> = _status.asStateFlow()

    private var scope: CoroutineScope? = null
    private var loop: Job? = null

    val isRunning: Boolean get() = loop?.isActive == true

    fun start() {
        if (isRunning) return
        val token = prefs.ingestToken
        if (token.isNullOrBlank()) {
            _status.value = LiveStatus(LiveState.ERROR, detail = "No ingest token. Sign in via Go Live first.")
            return
        }
        val s = CoroutineScope(SupervisorJob())
        scope = s
        _status.value = LiveStatus(LiveState.CONNECTING)
        loop = s.launch { runLoop(token) }
    }

    fun stop() {
        loop?.cancel()
        scope?.cancel()
        loop = null
        scope = null
        _status.value = LiveStatus(LiveState.OFF)
    }

    private suspend fun runLoop(token: String) {
        val client = WardriveClient(prefs.baseUrl)
        var sentOk = _status.value.sentOk
        var idleWaits = 0

        while (scope?.isActive == true) {
            val batch = store.drainForLive(MAX_BATCH)
            if (batch.isEmpty()) {
                _status.value = LiveStatus(LiveState.LIVE, sentOk, store.livePending(), "Waiting for scans…")
                idleWaits++
                delay(if (idleWaits > 4) 15_000 else 5_000)
                continue
            }
            idleWaits = 0
            _status.value = LiveStatus(LiveState.CONNECTING, sentOk, store.livePending() + batch.size, "Sending ${batch.size}…")

            val result = try {
                client.ingest(token, batch)
            } catch (e: Exception) {
                // Network error: server may not have it — put it back and retry.
                store.requeueForLive(batch)
                _status.value = LiveStatus(LiveState.ERROR, sentOk, store.livePending(), "Network error, retrying…")
                delay(8_000)
                null
            }
            if (result == null) continue

            when {
                result.serverHasIt -> {
                    sentOk += result.accepted
                    _status.value = LiveStatus(
                        LiveState.LIVE, sentOk, store.livePending(),
                        "Accepted ${result.accepted}" + if (result.rejected > 0) ", ${result.rejected} rejected" else "",
                    )
                }
                result.rateLimited -> {
                    store.requeueForLive(batch)
                    val wait = (result.retryAfterSec ?: 60).coerceIn(5, 300)
                    _status.value = LiveStatus(LiveState.PAUSED, sentOk, store.livePending(), "Rate limited, waiting ${wait}s")
                    delay(wait * 1000L)
                }
                result.ingestDisabled -> {
                    store.requeueForLive(batch)
                    _status.value = LiveStatus(LiveState.PAUSED, sentOk, store.livePending(), "Server has live ingest disabled")
                    delay(120_000)
                }
                result.unauthorized -> {
                    store.requeueForLive(batch)
                    prefs.ingestToken = null
                    _status.value = LiveStatus(LiveState.ERROR, sentOk, store.livePending(), "Token rejected. Re-run Go Live.")
                    return
                }
                result.tooLarge -> {
                    // Split: send half now, requeue the rest.
                    val half = batch.size / 2
                    if (half > 0) {
                        store.requeueForLive(batch.subList(half, batch.size))
                        runCatching { client.ingest(token, batch.subList(0, half)) }
                    }
                }
                else -> {
                    store.requeueForLive(batch)
                    _status.value = LiveStatus(LiveState.ERROR, sentOk, store.livePending(), result.message ?: "HTTP ${result.httpStatus}")
                    delay(10_000)
                }
            }
            delay(1_000)
        }
    }

    private companion object {
        const val MAX_BATCH = 200
    }
}
