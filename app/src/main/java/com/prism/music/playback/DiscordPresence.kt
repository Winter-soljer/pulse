package com.prism.music.playback

import android.content.Context
import android.util.Log
import com.prism.music.data.innertube.InnerTube
import com.prism.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Discord Rich Presence: "Listening to Prism" with the song, artist and a progress bar on the listener's Discord
 * profile. Discord has no Android SDK for this, so (as other Android music players do) Prism connects to Discord's
 * gateway as the listener, with the session from signing in to Discord inside Prism, and only ever updates the
 * presence. Off until the listener turns it on; the session stays on this phone (`discord` prefs, never in backups)
 * and nothing is sent anywhere but Discord. While nothing plays, the status is cleared.
 */
class DiscordPresence(context: Context, http: OkHttpClient, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("discord", Context.MODE_PRIVATE)
    private val http = http.newBuilder().pingInterval(java.time.Duration.ZERO).build()

    enum class State { OFF, CONNECTING, CONNECTED, SIGNED_OUT }

    private val _state = MutableStateFlow(State.OFF)
    val state: StateFlow<State> = _state

    /** The signed-in Discord user's name, for Settings. */
    val userName = MutableStateFlow(prefs.getString("user", null))

    val signedIn: Boolean get() = !prefs.getString("token", null).isNullOrBlank()

    fun signIn(token: String) {
        prefs.edit().putString("token", token).apply()
        reconnect()
    }

    fun signOut() {
        prefs.edit().clear().apply()
        userName.value = null
        stop()
        _state.value = State.SIGNED_OUT
    }

    private var enabled = false
    private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var sequence: Int? = null
    private var ready = false
    private var retries = 0

    /** What's playing: the last [show]n song and its timing, sent again after a reconnect. */
    private var pending: JsonObject? = null

    /** Turns presence on or off (Settings → Discord). */
    fun setEnabled(on: Boolean) {
        if (on == enabled) return
        enabled = on
        lastKey = null
        if (on) reconnect() else stop()
    }

    /** The playing song ([positionMs] into [durationMs]); null, or paused, clears the status. */
    fun show(song: Song?, playing: Boolean, positionMs: Long, durationMs: Long) {
        if (!enabled) return
        // Called often (every change and every 15 s while playing): only a new song, a pause or play, or a seek that
        // moved the progress bar by more than a few seconds goes to Discord.
        val start = System.currentTimeMillis() - positionMs
        val key = if (song == null || !playing) "" else "${song.id}|${durationMs / 1000}"
        if (key == lastKey && (key.isEmpty() || kotlin.math.abs(start - lastStart) < 3_000)) return
        lastKey = key
        lastStart = start
        pending = if (key.isEmpty()) null else activity(song!!, positionMs, durationMs)
        if (ready) sendPresence()
    }

    private var lastKey: String? = null
    private var lastStart = 0L

    private fun activity(song: Song, positionMs: Long, durationMs: Long): JsonObject = buildJsonObject {
        put("name", "Prism")
        // 2: "Listening to", as music apps show.
        put("type", 2)
        put("details", song.title.take(128))
        put("state", (if (song.isEpisode) song.primaryArtist else "by ${song.artists.joinToString(", ") { it.name }}").take(128).ifBlank { "Prism" })
        if (!song.isLive && durationMs > 0) {
            val start = System.currentTimeMillis() - positionMs.coerceAtLeast(0)
            putJsonObject("timestamps") {
                put("start", start)
                put("end", start + durationMs)
            }
        }
    }

    private fun sendPresence() {
        val activities = buildJsonArray { pending?.let { add(it) } }
        send(buildJsonObject {
            put("op", 3)
            putJsonObject("d") {
                put("since", 0)
                put("activities", activities)
                put("status", "online")
                put("afk", false)
            }
        })
    }

    private fun send(msg: JsonObject) { socket?.send(msg.toString()) }

    private fun stop() {
        heartbeat?.cancel()
        // Leaving takes the status with it.
        socket?.close(1000, null)
        socket = null
        ready = false
        if (_state.value != State.SIGNED_OUT) _state.value = State.OFF
    }

    private fun reconnect() {
        stop()
        val token = prefs.getString("token", null)
        if (!enabled || token.isNullOrBlank()) return
        _state.value = State.CONNECTING
        val req = Request.Builder().url("wss://gateway.discord.gg/?v=10&encoding=json").build()
        socket = http.newWebSocket(req, Listener(token))
    }

    private inner class Listener(private val token: String) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== socket) return
            val msg = runCatching { InnerTube.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            msg["s"]?.jsonPrimitive?.intOrNull?.let { sequence = it }
            when (msg["op"]?.jsonPrimitive?.int) {
                10 -> { // Hello: start the heartbeat and say who we are.
                    val interval = msg["d"]?.jsonObject?.get("heartbeat_interval")?.jsonPrimitive?.intOrNull?.toLong() ?: 41_250L
                    heartbeat?.cancel()
                    heartbeat = scope.launch {
                        delay((interval * Math.random()).toLong())
                        while (isActive) {
                            webSocket.send(buildJsonObject { put("op", 1); put("d", sequence) }.toString())
                            delay(interval)
                        }
                    }
                    webSocket.send(buildJsonObject {
                        put("op", 2)
                        putJsonObject("d") {
                            put("token", token)
                            put("intents", 0)
                            putJsonObject("properties") {
                                put("os", "Android")
                                put("browser", "Discord Android")
                                put("device", android.os.Build.MODEL)
                            }
                        }
                    }.toString())
                }
                0 -> if (msg["t"]?.jsonPrimitive?.contentOrNull == "READY") {
                    ready = true
                    retries = 0
                    _state.value = State.CONNECTED
                    val user = msg["d"]?.jsonObject?.get("user")?.jsonObject
                    (user?.get("global_name")?.jsonPrimitive?.contentOrNull ?: user?.get("username")?.jsonPrimitive?.contentOrNull)?.let {
                        userName.value = it
                        prefs.edit().putString("user", it).apply()
                    }
                    sendPresence()
                }
                7, 9 -> retry() // Discord asks for a reconnect / the session is no longer valid.
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = closed(webSocket, code)
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w("PrismDiscord", "gateway failed: $t")
            closed(webSocket, -1)
        }

        private fun closed(webSocket: WebSocket, code: Int) {
            if (webSocket !== socket) return
            heartbeat?.cancel()
            ready = false
            // 4004: the session isn't valid any more (password changed, signed out elsewhere).
            if (code == 4004) { signOut(); return }
            retry()
        }
    }

    /** Back off (5 s, 10 s … up to 5 min) and connect again, while presence is on. */
    private fun retry() {
        if (!enabled) return
        val wait = (5_000L shl retries.coerceAtMost(6)).coerceAtMost(300_000L)
        retries++
        scope.launch { delay(wait); if (enabled) reconnect() }
    }
}
