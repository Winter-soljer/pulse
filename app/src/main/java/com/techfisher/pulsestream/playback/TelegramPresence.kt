package com.techfisher.pulsestream.playback

import android.content.Context
import android.util.Log
import com.techfisher.pulsestream.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Telegram Live Presence: updates a live "Now Playing" message in a designated Telegram chat, channel,
 * or direct message with the user's bot using the official Telegram Bot API.
 *
 * Safe and transparent:
 * - Uses the official Telegram Bot API (no user-account session scraping, 0 ban risk).
 * - All bot tokens and chat IDs are stored locally on the listener's device.
 * - Edits the existing message on song change or playback pause rather than spamming the chat.
 */
class TelegramPresence(
    context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("telegram_presence", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    enum class Status { OFF, READY, UPDATING, ERROR }

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var enabled: Boolean = false
    private var botToken: String = ""
    private var chatId: String = ""

    private var updateJob: Job? = null
    private var lastTrackKey: String? = null
    private var lastPlayingState: Boolean? = null

    private var lastMessageId: Long?
        get() = prefs.getLong("last_message_id", -1L).takeIf { it > 0 }
        set(value) {
            if (value != null && value > 0) {
                prefs.edit().putLong("last_message_id", value).apply()
            } else {
                prefs.edit().remove("last_message_id").apply()
            }
        }

    fun updateConfig(enabled: Boolean, botToken: String, chatId: String) {
        val changed = this.enabled != enabled || this.botToken != botToken || this.chatId != chatId
        this.enabled = enabled
        this.botToken = botToken.trim()
        this.chatId = chatId.trim()

        if (!enabled || this.botToken.isBlank() || this.chatId.isBlank()) {
            _status.value = Status.OFF
            _lastError.value = null
            lastTrackKey = null
            lastPlayingState = null
        } else if (changed) {
            _status.value = Status.READY
            _lastError.value = null
        }
    }

    /**
     * Updates the Telegram presence when song or play/pause state changes.
     */
    fun show(song: Song?, isPlaying: Boolean) {
        if (!enabled || botToken.isBlank() || chatId.isBlank()) return

        val trackKey = if (song != null) "${song.id}|${song.title}" else ""
        if (trackKey == lastTrackKey && isPlaying == lastPlayingState) {
            return
        }

        lastTrackKey = trackKey
        lastPlayingState = isPlaying

        updateJob?.cancel()
        updateJob = scope.launch(Dispatchers.IO) {
            // Small debounce to avoid hitting Telegram API rate limits during rapid skips
            delay(500)
            publishUpdate(song, isPlaying)
        }
    }

    private suspend fun publishUpdate(song: Song?, isPlaying: Boolean) {
        val currentToken = botToken
        val currentChat = chatId
        if (currentToken.isBlank() || currentChat.isBlank()) return

        val messageText = formatMessage(song, isPlaying)
        _status.value = Status.UPDATING

        val msgId = lastMessageId
        if (msgId != null) {
            val edited = editMessage(currentToken, currentChat, msgId, messageText)
            if (edited) {
                _status.value = Status.READY
                _lastError.value = null
                return
            }
            // If editing failed (message deleted or expired), clear and fall through to send fresh
            lastMessageId = null
        }

        // Send a fresh message
        val newId = sendMessage(currentToken, currentChat, messageText)
        if (newId != null) {
            lastMessageId = newId
            _status.value = Status.READY
            _lastError.value = null
        } else {
            _status.value = Status.ERROR
        }
    }

    private fun formatMessage(song: Song?, isPlaying: Boolean): String {
        if (song == null) {
            return "⏹️ <b>Pulse:</b> <i>Nothing playing</i>"
        }

        val stateIcon = if (isPlaying) "🎧" else "⏸️"
        val stateLabel = if (isPlaying) "Listening to" else "Paused"

        val titleEscaped = escapeHtml(song.title)
        val artistEscaped = escapeHtml(song.artistText.ifBlank { "Unknown Artist" })
        val albumEscaped = song.album?.title?.let { escapeHtml(it) }

        val titleDisplay = if (song.isLocal) {
            "<b>$titleEscaped</b>"
        } else {
            "<a href=\"https://music.youtube.com/watch?v=${song.id}\"><b>$titleEscaped</b></a>"
        }

        val sb = StringBuilder()
        sb.append("$stateIcon <b>$stateLabel:</b> $titleDisplay\n")
        sb.append("👤 <b>Artist:</b> $artistEscaped\n")
        if (!albumEscaped.isNullOrBlank()) {
            sb.append("📀 <b>Album:</b> $albumEscaped\n")
        }
        sb.append("✨ <i>via Pulse Music</i>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private suspend fun sendMessage(token: String, chat: String, text: String): Long? = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("chat_id", chat)
                put("text", text)
                put("parse_mode", "HTML")
                put("disable_web_page_preview", false)
            }.toString()

            val req = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            http.newCall(req).execute().use { res ->
                val bodyStr = res.body.string()
                if (res.isSuccessful) {
                    val root = json.parseToJsonElement(bodyStr).jsonObject
                    val messageId = root["result"]?.jsonObject?.get("message_id")?.jsonPrimitive?.longOrNull
                    return@withContext messageId
                } else {
                    Log.w("TelegramPresence", "sendMessage failed [${res.code}]: $bodyStr")
                    _lastError.value = parseErrorDescription(bodyStr) ?: "Telegram HTTP ${res.code}"
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramPresence", "sendMessage exception", e)
            _lastError.value = e.localizedMessage ?: "Network error"
            null
        }
    }

    private suspend fun editMessage(token: String, chat: String, messageId: Long, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("chat_id", chat)
                put("message_id", messageId)
                put("text", text)
                put("parse_mode", "HTML")
                put("disable_web_page_preview", false)
            }.toString()

            val req = Request.Builder()
                .url("https://api.telegram.org/bot$token/editMessageText")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            http.newCall(req).execute().use { res ->
                val bodyStr = res.body.string()
                if (res.isSuccessful) {
                    return@withContext true
                }
                // If the message wasn't modified, Telegram returns 400 with "message is not modified"
                if (bodyStr.contains("message is not modified", ignoreCase = true)) {
                    return@withContext true
                }
                Log.w("TelegramPresence", "editMessage failed [${res.code}]: $bodyStr")
                false
            }
        } catch (e: Exception) {
            Log.e("TelegramPresence", "editMessage exception", e)
            false
        }
    }

    private fun parseErrorDescription(body: String): String? {
        return runCatching {
            json.parseToJsonElement(body).jsonObject["description"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    /**
     * Tests the bot credentials by sending a confirmation test message to the configured chat.
     */
    suspend fun testConnection(token: String, chat: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedToken = token.trim()
        val trimmedChat = chat.trim()
        if (trimmedToken.isBlank() || trimmedChat.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot Token and Chat ID cannot be empty."))
        }

        try {
            val testPayload = buildJsonObject {
                put("chat_id", trimmedChat)
                put("text", "🎵 <b>Pulse Music</b>: Telegram presence connected successfully!\n✨ Live track updates are ready.")
                put("parse_mode", "HTML")
            }.toString()

            val req = Request.Builder()
                .url("https://api.telegram.org/bot$trimmedToken/sendMessage")
                .post(testPayload.toRequestBody(jsonMediaType))
                .build()

            http.newCall(req).execute().use { res ->
                val bodyStr = res.body.string()
                if (res.isSuccessful) {
                    Result.success("Success! Test message delivered.")
                } else {
                    val desc = parseErrorDescription(bodyStr) ?: "HTTP ${res.code}"
                    Result.failure(Exception("Telegram error: $desc"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }
}
