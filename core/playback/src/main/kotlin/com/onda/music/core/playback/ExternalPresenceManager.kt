package com.onda.music.core.playback

import com.onda.music.core.database.OndaUserSettings
import com.onda.music.core.model.Track
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Singleton
class ExternalPresenceManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var scrobbleEnabled: Boolean = false

    @Volatile
    private var scrobbleService: String = "librefm"

    @Volatile
    private var scrobbleSessionKey: String = ""

    @Volatile
    private var scrobbleApiKey: String = ""

    @Volatile
    private var scrobbleApiSecret: String = ""

    @Volatile
    private var discordRpcEnabled: Boolean = false

    @Volatile
    private var discordUserToken: String? = null

    private var lastNowPlayingTrackId: String? = null
    private var lastDiscordTrackId: String? = null
    private var lastDiscordPlaying: Boolean? = null
    private var lastDiscordSentAtMs: Long = 0L
    private var lastDiscordAnchorPosMs: Long = 0L
    private var discordSocket: WebSocket? = null
    private var discordHeartbeatJob: Job? = null
    private var discordLastSeq: Long? = null

    @Volatile
    private var discordReady: Boolean = false

    @Volatile
    private var pendingDiscordTrack: Track? = null

    @Volatile
    private var pendingDiscordPlaying: Boolean = false

    @Volatile
    private var pendingDiscordPositionMs: Long = 0L

    @Volatile
    private var pendingDiscordDurationMs: Long = 0L

    fun updateSettings(settings: OndaUserSettings) {
        scrobbleEnabled = settings.scrobbleEnabled
        scrobbleService = settings.scrobbleService
        scrobbleSessionKey = settings.scrobbleSessionKey
        scrobbleApiKey = settings.scrobbleApiKey
        scrobbleApiSecret = settings.scrobbleApiSecret

        val prevDiscordActive = discordRpcEnabled && !discordUserToken.isNullOrBlank()
        val prevToken = discordUserToken
        discordRpcEnabled = settings.discordRpcEnabled
        discordUserToken = settings.discordUserToken?.trim()?.takeIf { it.isNotEmpty() }
        val nextDiscordActive = discordRpcEnabled && !discordUserToken.isNullOrBlank()

        if (!nextDiscordActive) {
            disconnectDiscord()
        } else if (!prevDiscordActive || prevToken != discordUserToken) {
            disconnectDiscord()
            connectDiscord()
        }
    }

    fun onPlaybackStateChanged(
        track: Track?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        pendingDiscordTrack = track
        pendingDiscordPlaying = isPlaying
        pendingDiscordPositionMs = positionMs
        pendingDiscordDurationMs = durationMs

        if (track != null && isPlaying && track.id != lastNowPlayingTrackId) {
            lastNowPlayingTrackId = track.id
            if (scrobbleEnabled) {
                scope.launch {
                    sendNowPlaying(track)
                }
            }
        }

        if (discordRpcEnabled && !discordUserToken.isNullOrBlank()) {
            if (discordSocket == null) {
                connectDiscord()
            } else if (discordReady) {
                val nowMs = System.currentTimeMillis()
                val expectedPos = if (lastDiscordPlaying == true) {
                    lastDiscordAnchorPosMs + (nowMs - lastDiscordSentAtMs)
                } else {
                    lastDiscordAnchorPosMs
                }
                val stateChanged = lastDiscordTrackId != track?.id || lastDiscordPlaying != isPlaying
                val seeked = isPlaying && kotlin.math.abs(positionMs - expectedPos) > 4_000L
                if (stateChanged || seeked) {
                    lastDiscordTrackId = track?.id
                    lastDiscordPlaying = isPlaying
                    lastDiscordSentAtMs = nowMs
                    lastDiscordAnchorPosMs = positionMs
                    sendDiscordPresence(track, isPlaying, positionMs, durationMs)
                }
            }
        }
    }

    fun onTrackPlayed(track: Track, startedAtMs: Long, playedMs: Long) {
        if (!scrobbleEnabled) return
        if (!shouldScrobble(track.durationMs, playedMs)) return
        scope.launch {
            sendScrobble(track, startedAtMs / 1000L)
        }
    }

    fun shutdown() {
        disconnectDiscord()
        scope.cancel()
    }

    private fun sendNowPlaying(track: Track) {
        val creds = resolveScrobbleCredentials() ?: return
        val params = linkedMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to track.artist,
            "track" to track.title,
            "api_key" to creds.apiKey,
            "sk" to creds.sessionKey
        )
        track.album?.takeIf { it.isNotBlank() }?.let { params["album"] = it }
        if (track.durationMs > 0L) {
            params["duration"] = (track.durationMs / 1000L).toString()
        }
        postAudioscrobbler(creds, params)
    }

    private fun sendScrobble(track: Track, timestampSec: Long) {
        val creds = resolveScrobbleCredentials() ?: return
        val params = linkedMapOf(
            "method" to "track.scrobble",
            "artist" to track.artist,
            "track" to track.title,
            "timestamp" to timestampSec.toString(),
            "api_key" to creds.apiKey,
            "sk" to creds.sessionKey
        )
        track.album?.takeIf { it.isNotBlank() }?.let { params["album"] = it }
        postAudioscrobbler(creds, params)
    }

    private fun postAudioscrobbler(creds: ScrobbleCredentials, params: Map<String, String>) {
        val signature = signAudioscrobblerParams(params, creds.apiSecret)
        val form = FormBody.Builder()
        params.forEach { (k, v) -> form.add(k, v) }
        form.add("api_sig", signature)
        form.add("format", "json")
        val request = Request.Builder()
            .url(creds.endpoint)
            .header("User-Agent", "OndaMusic/1.2.8")
            .post(form.build())
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { }
        }
    }

    private fun resolveScrobbleCredentials(): ScrobbleCredentials? {
        val sk = scrobbleSessionKey.trim()
        if (sk.isEmpty()) return null
        return if (scrobbleService.equals("lastfm", ignoreCase = true)) {
            val key = scrobbleApiKey.trim()
            val secret = scrobbleApiSecret.trim()
            if (key.isEmpty() || secret.isEmpty()) return null
            ScrobbleCredentials(
                endpoint = LASTFM_ENDPOINT,
                apiKey = key,
                apiSecret = secret,
                sessionKey = sk
            )
        } else {
            ScrobbleCredentials(
                endpoint = LIBREFM_ENDPOINT,
                apiKey = scrobbleApiKey.trim().ifEmpty { LIBREFM_OPEN_KEY },
                apiSecret = scrobbleApiSecret.trim().ifEmpty { LIBREFM_OPEN_SECRET },
                sessionKey = sk
            )
        }
    }

    private fun connectDiscord() {
        val token = discordUserToken?.takeIf { it.isNotBlank() } ?: return
        if (discordSocket != null) return
        val request = Request.Builder()
            .url(DISCORD_GATEWAY_URL)
            .build()
        discordReady = false
        discordSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleDiscordMessage(webSocket, token, text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (discordSocket === webSocket) {
                        discordSocket = null
                        discordReady = false
                        discordHeartbeatJob?.cancel()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (discordSocket === webSocket) {
                        discordSocket = null
                        discordReady = false
                        discordHeartbeatJob?.cancel()
                    }
                }
            }
        )
    }

    private fun disconnectDiscord() {
        discordHeartbeatJob?.cancel()
        discordHeartbeatJob = null
        discordReady = false
        val socket = discordSocket
        discordSocket = null
        if (socket != null) {
            runCatching {
                socket.send(buildDiscordClearPresencePayload())
                socket.close(1000, "Onda disconnect")
            }
        }
    }

    private fun handleDiscordMessage(webSocket: WebSocket, token: String, payload: String) {
        val op = extractJsonInt(payload, "op") ?: return
        extractJsonLong(payload, "s")?.let { discordLastSeq = it }
        when (op) {
            10 -> {
                val intervalMs = extractJsonLong(payload, "heartbeat_interval") ?: 41_250L
                startDiscordHeartbeat(webSocket, intervalMs.coerceIn(5_000L, 120_000L))
                webSocket.send(buildDiscordIdentifyPayload(token))
            }
            0 -> {
                if (payload.contains("\"t\":\"READY\"")) {
                    discordReady = true
                    sendDiscordPresence(
                        pendingDiscordTrack,
                        pendingDiscordPlaying,
                        pendingDiscordPositionMs,
                        pendingDiscordDurationMs
                    )
                }
            }
        }
    }

    private fun startDiscordHeartbeat(webSocket: WebSocket, intervalMs: Long) {
        discordHeartbeatJob?.cancel()
        discordHeartbeatJob = scope.launch {
            while (isActive && discordSocket === webSocket) {
                delay(intervalMs)
                val seqText = discordLastSeq?.toString() ?: "null"
                runCatching { webSocket.send("""{"op":1,"d":$seqText}""") }
            }
        }
    }

    private fun sendDiscordPresence(
        track: Track?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        val socket = discordSocket ?: return
        val jsonPayload = if (track != null && isPlaying) {
            buildDiscordListeningPresencePayload(
                title = track.title,
                artist = track.artist,
                album = track.album,
                positionMs = positionMs,
                durationMs = durationMs
            )
        } else {
            buildDiscordClearPresencePayload()
        }
        runCatching { socket.send(jsonPayload) }
    }

    private data class ScrobbleCredentials(
        val endpoint: String,
        val apiKey: String,
        val apiSecret: String,
        val sessionKey: String
    )

    companion object {
        private const val LIBREFM_ENDPOINT = "https://libre.fm/2.0/"
        private const val LASTFM_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
        private const val LIBREFM_OPEN_KEY = "onda-music-open-client-000000001"
        private const val LIBREFM_OPEN_SECRET = "onda-music-open-secret-000000001"
        private const val DISCORD_GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

        fun shouldScrobble(trackDurationMs: Long, playedMs: Long): Boolean {
            if (playedMs < 30_000L) return false
            if (trackDurationMs <= 0L) return playedMs >= 30_000L
            return playedMs >= trackDurationMs / 2L || playedMs >= 240_000L
        }

        fun signAudioscrobblerParams(params: Map<String, String>, apiSecret: String): String {
            val concatenated = buildString {
                params.entries
                    .filter { it.key != "format" && it.key != "callback" }
                    .sortedBy { it.key }
                    .forEach { (k, v) ->
                        append(k)
                        append(v)
                    }
                append(apiSecret)
            }
            val digest = MessageDigest.getInstance("MD5").digest(concatenated.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        internal fun buildDiscordIdentifyPayload(token: String): String {
            val safeToken = escapeJson(token)
            return """{"op":2,"d":{"token":"$safeToken","intents":0,"properties":{"os":"Android","browser":"Onda Music","device":"Android"}}}"""
        }

        internal fun buildDiscordListeningPresencePayload(
            title: String,
            artist: String,
            album: String?,
            positionMs: Long,
            durationMs: Long,
            nowMs: Long = System.currentTimeMillis()
        ): String {
            val safeDetails = escapeJson(title.take(128))
            val safeState = escapeJson(artist.take(128))
            val startEpoch = (nowMs - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)
            val endSegment = if (durationMs > 0L) {
                val endEpoch = startEpoch + durationMs
                ""","end":$endEpoch"""
            } else {
                ""
            }
            val assetsSegment = if (!album.isNullOrBlank()) {
                ""","assets":{"large_text":"${escapeJson(album.take(128))}"}"""
            } else {
                ""
            }
            return """{"op":3,"d":{"since":null,"activities":[{"name":"Onda Music","type":2,"details":"$safeDetails","state":"$safeState","timestamps":{"start":$startEpoch$endSegment}$assetsSegment}],"status":"online","afk":false}}"""
        }

        internal fun buildDiscordClearPresencePayload(): String {
            return """{"op":3,"d":{"since":null,"activities":[],"status":"online","afk":false}}"""
        }

        private fun escapeJson(raw: String): String {
            val sb = StringBuilder(raw.length + 8)
            for (ch in raw) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (ch.code < 0x20) sb.append(' ') else sb.append(ch)
                }
            }
            return sb.toString()
        }

        private fun extractJsonInt(json: String, key: String): Int? {
            val match = Regex(""""$key"\s*:\s*(-?\d+)""").find(json) ?: return null
            return match.groupValues[1].toIntOrNull()
        }

        private fun extractJsonLong(json: String, key: String): Long? {
            val match = Regex(""""$key"\s*:\s*(-?\d+)""").find(json) ?: return null
            return match.groupValues[1].toLongOrNull()
        }
    }
}
