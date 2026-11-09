package com.techfisher.pulsestream.data

import com.techfisher.pulsestream.data.model.AlbumItem
import com.techfisher.pulsestream.data.model.ArtistItem
import com.techfisher.pulsestream.data.model.BrowseItem
import com.techfisher.pulsestream.data.model.MoodItem
import com.techfisher.pulsestream.data.model.PlaylistItem
import com.techfisher.pulsestream.data.model.SongItem

/**
 * Pulse proprietary links for what Pulse shares. They open directly in Pulse for listeners who
 * have it installed, or prompt to download Pulse (.apk) with fallback web playback for those who don't.
 */
object ShareLinks {
    const val PULSE_HOST = "winter-soljer.github.io"
    const val PULSE_PREFIX = "/pulse"
    const val PULSE_BASE = "https://$PULSE_HOST$PULSE_PREFIX"
    const val APK_DOWNLOAD_URL = "https://github.com/Winter-soljer/pulse/releases/latest/download/Pulse.apk"
    const val YTM_BASE = "https://music.youtube.com"

    fun song(videoId: String) = "$PULSE_BASE/watch?v=$videoId"
    fun album(browseId: String) = "$PULSE_BASE/browse/$browseId"
    fun playlist(id: String) = "$PULSE_BASE/playlist?list=${id.removePrefix("VL")}"
    fun artist(channelId: String) = "$PULSE_BASE/channel/$channelId"

    /** Direct custom URI scheme for intra-app or explicit deep links. */
    fun pulseSchemeSong(videoId: String) = "pulse://watch?v=$videoId"

    /** YouTube Music link for direct web listening. */
    fun ytmSong(videoId: String) = "$YTM_BASE/watch?v=$videoId"

    /** The link for any card; null for Pulse's own lists and for moods (pages of shelves). */
    fun of(item: BrowseItem): String? = when (item) {
        is SongItem -> song(item.id)
        is AlbumItem -> album(item.id)
        // Radios and mixes are made for whoever opens them, so there's nothing stable to send.
        is PlaylistItem -> if (item.isMix || item.id.startsWith("RD")) null else playlist(item.id)
        is ArtistItem -> artist(item.id)
        // A show's address on YouTube Music is its episodes' playlist.
        is com.techfisher.pulsestream.data.model.PodcastItem -> playlist(item.playlistId)
        is MoodItem -> null
    }

    /**
     * The first Pulse, YouTube or YouTube Music link in [text] (what other apps put in a share),
     * as the proprietary Pulse address Pulse opens. youtu.be, youtube.com, and pulse:// links count too.
     */
    fun findIn(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val raw = Regex("(?:https?://|pulse://)\\S+").findAll(text)
            .map { it.value.trimEnd('.', ',', ')', '"', '\'', ';') }
            .firstOrNull { u ->
                listOf("winter-soljer.github.io", "youtube.com", "youtu.be", "pulse://").any {
                    u.contains(it, ignoreCase = true)
                }
            } ?: return null

        if (raw.startsWith("pulse://", ignoreCase = true)) {
            return raw
        }

        val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val segments = if (path.firstOrNull() == "pulse") path.drop(1) else path
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { p ->
            val k = p.substringBefore('=', "")
            if (k.isBlank()) null else k to java.net.URLDecoder.decode(p.substringAfter('='), "UTF-8")
        }.toMap()
        val v = query["v"] ?: query["id"]?.takeIf { it.isNotBlank() }
        val list = query["list"]?.takeIf { it.isNotBlank() }

        return when {
            host == PULSE_HOST -> {
                when {
                    v != null -> song(v)
                    list != null -> playlist(list)
                    segments.firstOrNull() == "browse" && segments.size > 1 -> album(segments[1])
                    segments.firstOrNull() == "channel" && segments.size > 1 -> artist(segments[1])
                    segments.firstOrNull() in setOf("watch", "song", "play") && segments.size > 1 -> song(segments[1])
                    else -> raw
                }
            }
            host == "youtu.be" -> path.firstOrNull()?.let(::song)
            host == "music.youtube.com" -> {
                when {
                    v != null -> song(v)
                    list != null -> playlist(list)
                    segments.firstOrNull() == "browse" && segments.size > 1 -> album(segments[1])
                    segments.firstOrNull() == "channel" && segments.size > 1 -> artist(segments[1])
                    else -> raw
                }
            }
            !host.endsWith("youtube.com") -> null
            v != null -> song(v)
            segments.firstOrNull() == "shorts" && segments.size > 1 -> song(segments[1])
            list != null -> playlist(list)
            segments.firstOrNull() == "channel" && segments.size > 1 -> artist(segments[1])
            else -> null
        }
    }
}
