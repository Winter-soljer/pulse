package com.techfisher.pulsestream.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.techfisher.pulsestream.data.innertube.InnerTube
import com.techfisher.pulsestream.data.model.Song
import com.techfisher.pulsestream.data.model.hiRes

const val EXTRA_SONG = "pulse.song"
const val EXTRA_VIDEO = "pulse.video"

/**
 * pulse://audio/<id>[?video=<videoId>] – resolved to a real stream URL at load time.
 * Live streams are pulse://live/<id>, resolved to their HLS manifest, which carries the picture too: their video mode
 * keeps the same URI (so switching doesn't reload the stream) and only turns the video track on.
 */
fun Song.toMediaItem(videoId: String? = null): MediaItem {
    val uri = if (isLive) Uri.Builder().scheme("pulse").authority("live").appendPath(id).build()
    else Uri.Builder().scheme("pulse").authority("audio").appendPath(id).apply {
        if (videoId != null) appendQueryParameter("video", videoId)
    }.build()
    val extras = Bundle().apply {
        putString(EXTRA_SONG, InnerTube.json.encodeToString(Song.serializer(), this@toMediaItem))
        if (videoId != null) putString(EXTRA_VIDEO, videoId)
    }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setCustomCacheKey(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistText)
                .setAlbumTitle(album?.title)
                .setArtworkUri(hiRes(thumbnail, 544)?.let(Uri::parse))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(if (isEpisode) MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE else MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(extras)
                .build()
        )
        .build()
}

fun MediaItem.toSong(): Song? = mediaMetadata.extras?.getString(EXTRA_SONG)?.let {
    runCatching { InnerTube.json.decodeFromString(Song.serializer(), it) }.getOrNull()
}

val MediaItem.videoModeId: String? get() = mediaMetadata.extras?.getString(EXTRA_VIDEO)
