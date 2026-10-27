package com.techfisher.pulsestream.ui.player

import com.techfisher.pulsestream.ui.theme.PulseIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.techfisher.pulsestream.data.model.Song
import com.techfisher.pulsestream.ui.LocalNavigator
import com.techfisher.pulsestream.ui.Routes
import com.techfisher.pulsestream.ui.components.ActionGroup
import com.techfisher.pulsestream.ui.components.ActionHeader
import com.techfisher.pulsestream.ui.components.ActionItem
import com.techfisher.pulsestream.ui.components.ActionSheet
import com.techfisher.pulsestream.ui.components.PlaylistPicker
import com.techfisher.pulsestream.ui.components.QuickActions
import com.techfisher.pulsestream.ui.components.SheetIconButton
import com.techfisher.pulsestream.ui.components.runAction
import com.techfisher.pulsestream.ui.components.shareTarget
import com.techfisher.pulsestream.ui.theme.LocalContainer

/**
 * The player's ⋯ menu, in the app's frosted look (the song's cover as frosted glass): like and
 * share up top, the everyday actions as tiles, then lyrics, player and "go to" groups.
 */
@Composable
fun PlayerOptionsSheet(
    song: Song,
    hasCanvas: Boolean,
    statsShown: Boolean,
    hasNext: Boolean,
    onLyricsSource: () -> Unit,
    onLyricsTiming: () -> Unit,
    onRedownloadLyrics: () -> Unit,
    onToggleStats: () -> Unit,
    onSleepTimer: () -> Unit,
    onCollapse: () -> Unit,
    onAudioAnalysis: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val liked by c.library.likedIds.collectAsState()
    val downloads by c.downloads.downloads.collectAsState()
    val videos by c.downloads.videos.collectAsState()
    val hidden by c.canvas.hidden.collectAsState()
    val saved by c.canvas.saved.collectAsState()
    val sleepAt by c.player.sleepAt.collectAsState()
    val sleepEnd by c.player.sleepEndOfSong.collectAsState()
    val together by c.together.state.collectAsState()
    val disliked by c.library.dislikedIds.collectAsState()
    var pickPlaylist by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    if (pickPlaylist) { PlaylistPicker(song) { pickPlaylist = false; onDismiss() }; return }
    if (sharing) { com.techfisher.pulsestream.ui.components.ShareSheet(song.shareTarget()) { sharing = false; onDismiss() }; return }

    val isLiked = song.id in liked
    val dl = downloads[song.id]
    val downloaded = dl?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
    fun toast(text: String) = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    val sleepMin = if (sleepAt > 0) ((sleepAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) + 1 else 0
    val sleeping = sleepAt > 0 || sleepEnd
    val inSession = together !is com.techfisher.pulsestream.playback.together.TogetherState.Idle

    val quick = listOfNotNull(
        ActionItem(PulseIcons.Radio, "Radio") { c.player.playRadio(song) },
        ActionItem(
            if (downloaded) PulseIcons.DownloadDone else PulseIcons.Download, "Download",
            value = when { downloaded -> "Downloaded"; dl != null -> "${dl.percent.toInt().coerceAtLeast(0)}%"; else -> null },
            active = downloaded, keepOpen = true,
        ) { if (dl != null) c.downloads.remove(song.id) else c.downloads.download(song) },
        if (c.settings.current.isLoggedIn) ActionItem(PulseIcons.PlaylistAdd, "Playlist", keepOpen = true) { pickPlaylist = true } else null,
        ActionItem(
            PulseIcons.Moon, "Sleep",
            value = when { sleepEnd -> "Song end"; sleepAt > 0 -> "${sleepMin} min"; else -> null },
            active = sleeping, onClick = onSleepTimer,
        ),
    )
    val speed by c.player.episodeSpeed.collectAsState()
    if (song.isEpisode) {
        val speeds = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)
        val episode = listOfNotNull(
            ActionItem(
                PulseIcons.Speed, "Playback speed", value = "${speed.toString().removeSuffix(".0")}×", active = speed != 1f, keepOpen = true,
            ) { c.player.setEpisodeSpeed(speeds[(speeds.indexOf(speed) + 1) % speeds.size]) },
            song.podcastId?.let { id -> ActionItem(PulseIcons.Podcasts, "Go to podcast") { onCollapse(); nav.go(Routes.podcast(id)) } },
            ActionItem(PulseIcons.Speed, "Audio analysis & Camelot", onClick = onAudioAnalysis),
            ActionItem(PulseIcons.Stats, "Stats for nerds", active = statsShown, onClick = onToggleStats),
        )
        ActionSheet(song.thumbnail, onDismiss) { close ->
            ActionHeader(song.thumbnail, song.title, song.artistText) {
                SheetIconButton(PulseIcons.Share, "Share") { sharing = true }
            }
            QuickActions(quick.filter { it.label != "Radio" && it.label != "Playlist" }) { runAction(it, close) }
            ActionGroup("Episode", episode) { runAction(it, close) }
        }
        return
    }
    val translating = c.settings.flow.collectAsState().value.lyricsTranslate
    val lyrics = listOf(
        ActionItem(PulseIcons.Lyrics, "Lyrics source", onClick = onLyricsSource),
        ActionItem(PulseIcons.Timer, "Lyrics timing", onClick = onLyricsTiming),
        ActionItem(
            PulseIcons.Translate, "Translate lyrics", value = if (translating) "On" else "Off", active = translating, keepOpen = true,
        ) { c.settings.setLyricsTranslate(!translating) },
        ActionItem(PulseIcons.Refresh, "Download lyrics again") { onRedownloadLyrics(); toast("Downloading lyrics again") },
    ) + listOfNotNull(
        // Into your own FLAC / MP3 file, for other players (#16).
        if (song.isLocal || c.downloads.downloads.value[song.id]?.lossless == true)
            ActionItem(PulseIcons.Download, "Save lyrics into the file") { c.lyricsToFiles.request(listOf(song)) } else null,
    )
    val cover = com.techfisher.pulsestream.data.canvas.CanvasRepository.albumKey(song)
    val immersive = c.settings.flow.collectAsState().value.immersivePlayer
    val player = listOfNotNull(
        // The cover fills the screen with the lyrics over it; a tap there brings this back.
        ActionItem(PulseIcons.Fullscreen, "Immersive mode", value = if (immersive) "On" else null, active = immersive) { c.settings.setImmersivePlayer(!immersive) },
        com.techfisher.pulsestream.ui.components.videoDownloadAction(song, videos[song.id]),
        ActionItem(PulseIcons.Groups, "Listen together", value = if (inSession) "On" else null, active = inSession) { onCollapse(); nav.go(Routes.TOGETHER) },
        ActionItem(PulseIcons.Speed, "Audio analysis & Camelot", onClick = onAudioAnalysis),
        ActionItem(PulseIcons.Stats, "Stats for nerds", active = statsShown, onClick = onToggleStats),
        if (hasCanvas) ActionItem(PulseIcons.AnimatedCover, "Animated cover", active = song.id !in hidden, keepOpen = true) { c.canvas.setHidden(song.id, song.id !in hidden) } else null,
        if (!hasCanvas) null else if (saved.has(song.id, cover)) ActionItem(PulseIcons.Download, "Animated cover saved", active = true, keepOpen = true) { c.canvasStore.unsave(song) }
        else ActionItem(PulseIcons.Download, "Save animated cover", keepOpen = true) {
            c.canvasStore.save(song)
            toast("Saving animated covers for this album")
        },
    )
    val go = listOfNotNull(
        song.album?.id?.let { id -> ActionItem(PulseIcons.Album, "Go to album") { onCollapse(); nav.go(Routes.album(id)) } },
    ) + com.techfisher.pulsestream.ui.components.goToArtists(song) { id -> onCollapse(); nav.go(Routes.artist(id)) } + listOfNotNull(
        if (song.id in disliked) ActionItem(PulseIcons.ThumbDownFilled, "Not for me", active = true, keepOpen = true) { c.library.setDisliked(song, false) }
        else ActionItem(PulseIcons.ThumbDown, "Not for me") {
            c.library.setDisliked(song, true)
            toast("Got it — Pulse will steer clear of this song")
            if (hasNext) c.player.next()
        },
    )

    ActionSheet(song.thumbnail, onDismiss) { close ->
        ActionHeader(song.thumbnail, song.title, song.artistText) {
            SheetIconButton(if (isLiked) PulseIcons.HeartFilled else PulseIcons.Heart, if (isLiked) "Remove from liked" else "Like", active = isLiked) {
                c.library.toggleLike(song)
            }
            if (!song.isLocal) SheetIconButton(PulseIcons.Share, "Share") { sharing = true }
        }
        // A file on the phone: no radio, download, YouTube playlist or music video.
        QuickActions(if (song.isLocal) quick.filter { it.label == "Sleep" } else quick) { runAction(it, close) }
        ActionGroup("Lyrics", lyrics) { runAction(it, close) }
        ActionGroup("Player", if (song.isLocal) player.filter { it.label == "Stats for nerds" || it.label == "Immersive mode" || it.label == "Audio analysis & Camelot" } else player) { runAction(it, close) }
        ActionGroup("More", go) { runAction(it, close) }
    }
}
