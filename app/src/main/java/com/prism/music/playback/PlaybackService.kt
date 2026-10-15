package com.prism.music.playback

import com.prism.music.data.friendlyMessage
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withTimeoutOrNull
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.guava.future
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.prism.music.MainActivity
import com.prism.music.R
import com.prism.music.container
import com.prism.music.data.db.PlayEventEntity
import com.prism.music.data.innertube.InnerTube
import com.prism.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import kotlin.math.roundToInt

data class VideoLookup(val id: String?, val lookupFailed: Boolean = false)

@Serializable
private data class SavedQueue(val songs: List<Song>, val index: Int, val positionMs: Long, val title: String)

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService(), Player.Listener {
    private val c by lazy { container }
    lateinit var player: ExoPlayer
        private set
    private lateinit var session: MediaLibrarySession
    private val tree by lazy { MediaTree(c) }
    lateinit var effects: AudioEffects
        private set
    private val spatial = SpatialAudioProcessor()
    private val normalizer = NormalizerProcessor()
    private var songLoudness: Float? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val likeCommand = SessionCommand("prism.LIKE", Bundle.EMPTY)
    private val shuffleCommand = SessionCommand("prism.SHUFFLE", Bundle.EMPTY)
    private val repeatCommand = SessionCommand("prism.REPEAT", Bundle.EMPTY)
    private val lyricsCommand = SessionCommand("prism.CAR_LYRICS", Bundle.EMPTY)
    private val radioCommand = SessionCommand("prism.RADIO", Bundle.EMPTY)
    private val dislikeCommand = SessionCommand("prism.DISLIKE", Bundle.EMPTY)
    private val customCommands get() = listOf(likeCommand, shuffleCommand, repeatCommand, lyricsCommand, radioCommand, dislikeCommand)
    /** What the media session (Android Auto, the notification) sees: the player, with lyrics laid over the title and cover. */
    private lateinit var sessionPlayer: LyricsOverlayPlayer
    private lateinit var carLyrics: CarLyrics
    /** Android Auto's connections to Prism. */
    private val carControllers = HashSet<MediaSession.ControllerInfo>()
    private var playStartedAt = 0L
    private var accumulatedMs = 0L
    private var trackedSong: Song? = null
    private val retries = HashMap<String, Int>()
    private var saveJob: Job? = null
    private var extrasJob: Job? = null


    override fun onCreate() {
        super.onCreate()
        effects = AudioEffects(this)
        // Lossless mode keeps hi-res files (24-bit FLAC) at full resolution instead of rounding to 16-bit.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput || c.settings.current.lossless)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(spatial, normalizer))
                    .build()
        }
        player = ExoPlayer.Builder(this, renderers)
            // Start (and resume after a switch to video) once 1 s is buffered rather than 2.5 s.
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 60_000, 1_000, 2_000)
                    .build()
            )
            .setMediaSourceFactory(
                PrismMediaSourceFactory(
                    c.playbackDataSourceFactory(), c.videoDataSourceFactory(),
                    androidx.media3.datasource.DefaultDataSource.Factory(this),
                    localAudio = { item -> losslessFileFor(item) },
                    liveDsf = c.liveDataSourceFactory(),
                )
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        // Matching the display to the video's frame rate also throttled the whole
        // app (lyrics included) to 60 Hz; the UI should stay at the panel's full rate.
        player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        // Shuffled, Previous goes back to the song played last (see HistoryShuffleOrder).
        player.setShuffleOrder(HistoryShuffleOrder(IntArray(0)) { player.currentMediaItemIndex })
        player.addListener(this)
        player.addAnalyticsListener(StatsListener())
        player.skipSilenceEnabled = c.settings.current.skipSilence

        sessionPlayer = LyricsOverlayPlayer(player)
        carLyrics = CarLyrics(this, c, player, sessionPlayer, scope)
        carLyrics.start()
        session = MediaLibrarySession.Builder(this, sessionPlayer, LibraryCallback())
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
        // Wear OS (a watch's media controls) has its own keys for keeping the skip
        // buttons: without them it puts the first custom buttons (like, shuffle) where
        // previous/next go. Media3 only sets the phone/Auto equivalents. Set after build():
        // only a change of session extras reaches the platform session's extras.
        session.setSessionExtras(Bundle().apply {
            putBoolean("android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_PREVIOUS", true)
            putBoolean("android.support.wearable.media.extra.RESERVE_SLOT_SKIP_TO_NEXT", true)
        })
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.ic_notification)
        })
        // Keep the media notification (and the system media controls) up for a
        // paused or restored queue, not only while audio is actively playing.
        setShowNotificationForIdlePlayer(MediaSessionService.SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS)

        effects.attach(player.audioSessionId)
        c.onStreamResolved = { id -> scope.launch { applyStreamStats(id) } }
        scope.launch { c.settings.flow.map { it.eq }.distinctUntilChanged().collect { effects.apply(it) } }
        scope.launch { c.settings.flow.map { it.skipSilence }.distinctUntilChanged().collect { player.skipSilenceEnabled = it } }
        scope.launch { c.settings.flow.map { it.autoplay }.distinctUntilChanged().drop(1).collect { onAutoplayChanged(it) } }
        // "Keep playing videos" switched off: songs already lined up as their videos go back to songs.
        scope.launch { c.settings.flow.map { it.videoContinues }.distinctUntilChanged().drop(1).collect { if (it) armNextVideo() else disarmVideos() } }
        scope.launch { c.settings.flow.map { it.spatial to it.spatialAmount }.distinctUntilChanged().collect { (on, amount) -> spatial.configure(on, amount) } }
        scope.launch { c.settings.flow.map { it.normalize }.distinctUntilChanged().collect { applyNormalization() } }
        scope.launch { c.library.likedIds.collect { updateButtons() } }
        scope.launch {
            c.settings.flow.map { Triple(it.carLyrics, it.carLyricsStyle, it.carLyricsOnPhone) }.distinctUntilChanged().collect {
                carLyrics.refresh(); updateButtons()
            }
        }
        scope.launch {
            var tick = 0
            while (isActive) {
                delay(1000)
                // An episode's place is saved every few seconds while it plays.
                if (++tick % 5 == 0 && player.isPlaying) saveEpisodePosition()
                // Widgets' progress bars move on every 15 s (more often would cost battery for little).
                if (tick % 15 == 0 && player.isPlaying) pushWidgets()
                if (player.mediaItemCount > 0) c.nerdStats.value = c.nerdStats.value.copy(
                    bufferMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0),
                    effects = effects.activeSummary,
                    audioSessionId = player.audioSessionId,
                )
            }
        }
        mix = MixEngine(
            main = player,
            scope = scope,
            tempo = c.mixTempo,
            mixMs = { c.settings.current.let { s -> if (s.mix) s.mixSeconds * 1000L else null } },
            auto = { c.settings.current.mixAuto },
            beatMatch = { c.settings.current.mixBeatMatch },
            allowed = ::mayBlend,
            scanFromPhone = { id -> scope.launch { c.mixTempo.measure(id, network = false) } },
            buildTail = ::buildTailPlayer,
        ).also { it.start() }
        restoreQueue()
        c.player.attach(this)
    }

    private lateinit var mix: MixEngine

    /**
     * Whether Mix may blend these two: not an album running into its own next track (albums are made to flow on
     * as they are), not live streams or music videos, and not while playing along with others (Listen Together
     * keeps everyone on the same song).
     */
    private fun mayBlend(from: MediaItem, to: MediaItem): Boolean {
        val a = from.toSong() ?: return false
        val b = to.toSong() ?: return false
        if (a.isLive || b.isLive || from.videoModeId != null || to.videoModeId != null) return false
        // Talk doesn't blend.
        if (a.isEpisode || b.isEpisode) return false
        if (c.queue.followingHost || c.together.state.value !is com.prism.music.playback.together.TogetherState.Idle) return false
        // Only while an album plays through (a radio or playlist that puts two of an album's songs side by side blends).
        val album = a.album?.id
        return !(album != null && album == b.album?.id && c.queue.source.value.kind == QueueKind.ALBUM)
    }

    /**
     * The second player Mix plays a song's ending on while the next one fades in: the same sources, the same
     * effects (spatial audio, the ending song's normalization, and the equalizer through the shared audio
     * session), and no audio focus of its own.
     */
    private fun buildTailPlayer(shift: ShiftProcessor): ExoPlayer {
        val s = c.settings.current
        val tailSpatial = SpatialAudioProcessor().apply { configure(s.spatial, s.spatialAmount) }
        val tailNormalizer = NormalizerProcessor().apply { setGainDb(normalizeGainDb) }
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput || s.lossless)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(shift, tailSpatial, tailNormalizer))
                    .build()
        }
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(
                PrismMediaSourceFactory(
                    c.playbackDataSourceFactory(), c.videoDataSourceFactory(),
                    androidx.media3.datasource.DefaultDataSource.Factory(this),
                    localAudio = { item -> losslessFileFor(item) },
                    liveDsf = c.liveDataSourceFactory(),
                )
            )
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(4_000, 15_000, 500, 500).build())
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                false,
            )
            .build()
            .also {
                it.skipSilenceEnabled = false
                it.setAudioSessionId(player.audioSessionId)
            }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueue()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        flushPlayTime()
        saveEpisodePosition()
        saveQueue()
        player.currentMediaItem?.toSong()?.let { song ->
            com.prism.music.widget.Widgets.update(this, song, false, song.id in c.library.likedIds.value, player.currentPosition, player.duration.takeIf { it > 0 })
        }
        mix.release()
        c.player.detach()
        carLyrics.stop()
        effects.release()
        session.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ Player events

    /** Id of the song being swapped in place (song ↔ video, audio ↔ live): the same listen goes on. */
    private var swappingInPlace: String? = null

    /** Replaces the item at [idx] with another form of the same song, without counting a new play. */
    private fun swapInPlace(idx: Int, item: MediaItem) {
        swappingInPlace = item.mediaId
        try { player.replaceMediaItem(idx, item) } finally { swappingInPlace = null }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Song ↔ video and the like swap the item but it's still the same listen: the play time
        // keeps adding up, and nothing that starts a new song (history, lyrics, prefetch) runs again.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null &&
            mediaItem.mediaId == swappingInPlace && mediaItem.mediaId == trackedSong?.id
        ) {
            applyLiveVideo()
            updateButtons()
            scheduleSave()
            return
        }
        flushPlayTime()
        applyLiveVideo()
        val song = mediaItem?.toSong() ?: return
        // Disliked songs are passed over when the queue reaches them on its own (tapping one still plays it).
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !c.queue.followingHost && c.library.isDisliked(song.id) && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            return
        }
        trackedSong = song
        // Podcast episodes pick up where they were left, and play at the speed picked for episodes.
        if (song.isEpisode) {
            val resume = c.episodes.positionOf(song.id)
            if (resume > 0 && player.currentPosition < 5_000) player.seekTo(resume)
            if (player.playbackParameters.speed != episodeSpeed) player.setPlaybackSpeed(episodeSpeed)
        } else if (lastWasEpisode) {
            if (player.playbackParameters.speed != 1f) player.setPlaybackSpeed(1f)
            // Watching a podcast isn't a wish for music videos: music after it starts as the song.
            videoWanted = false
        }
        lastWasEpisode = song.isEpisode
        // Already playing: a seamless move on (gapless, or a Mix blend); nothing to time.
        startedLoading = if (player.isPlaying) null else song.id to SystemClock.elapsedRealtime()
        retries.remove(song.id)
        // A queued song that's playing now isn't "next in queue" any more.
        if (song.id in c.queue.userQueued.value) c.queue.userQueued.value = c.queue.userQueued.value - song.id
        if (player.isPlaying) playStartedAt = SystemClock.elapsedRealtime()
        c.nerdStats.value = NerdStats(videoId = song.id, outputDevice = AudioEffects.outputDevice(this))
        c.streamDetails[song.id]?.let { applyStreamStats(song.id) }
        scope.launch(Dispatchers.IO) { c.library.recordPlayed(song) }
        loadExtras(song)
        // Every new song starts as the song; the track that just finished goes back to audio too,
        // so returning to it doesn't land in its video.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            for (i in 0 until player.mediaItemCount) {
                if (i == player.currentMediaItemIndex) continue
                val other = player.getMediaItemAt(i)
                if (other.videoModeId != null) other.toSong()?.let { player.replaceMediaItem(i, it.toMediaItem(null)) }
            }
        }
        continueVideo()
        maybeAutoplay()
        prefetchNext()
        updateButtons()
        carLyrics.refresh()
        scheduleSave()
    }

    /** Podcast episodes' playback speed (the player's ⋯ sets it); music always plays at 1×. */
    var episodeSpeed = 1f
        set(v) {
            field = v
            if (player.currentMediaItem?.toSong()?.isEpisode == true) player.setPlaybackSpeed(v)
        }
    private var lastWasEpisode = false

    /** Remembers how far into the playing episode the listener is (see [com.prism.music.data.EpisodeProgress]). */
    private fun saveEpisodePosition() {
        val song = player.currentMediaItem?.toSong()?.takeIf { it.isEpisode } ?: return
        c.episodes.save(song, player.currentPosition, player.duration.takeIf { it > 0 } ?: song.durationSec * 1000L)
    }

    /** The song that just came up and when, until it's heard: how long starting a song takes (logcat tag PrismStream). */
    private var startedLoading: Pair<String, Long>? = null

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (isPlaying) startedLoading?.let { (id, at) ->
            if (id == player.currentMediaItem?.mediaId) android.util.Log.i("PrismStream", "$id playing ${now - at} ms after it came up (${c.streamDetails[id]?.first ?: "?"})")
            startedLoading = null
        }
        if (!isPlaying) saveEpisodePosition()
        pushWidgets()
        if (isPlaying) playStartedAt = now
        else if (playStartedAt > 0) {
            accumulatedMs += now - playStartedAt
            playStartedAt = 0
            scheduleSave()
        }
    }

    /** The song [prefetchNext] last got ready. */
    private var warmedNext: String? = null

    /**
     * The queue changed under the current song (autoplay's suggestions landed, something was queued or moved):
     * if a different song is next now, get that one ready instead. Playing a single song used to leave its first
     * radio song cold, since the suggestions arrive after the song has started.
     */
    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
        val i = player.nextMediaItemIndex
        val next = if (i == C.INDEX_UNSET) null else player.getMediaItemAt(i).mediaId
        if (next != null && next != warmedNext && player.currentMediaItem != null) prefetchNext()
    }

    /** When playback stalled waiting for data (logged with how long, tag PrismStream). */
    private var stalledAt = 0L

    override fun onPlaybackStateChanged(playbackState: Int) {
        val now = SystemClock.elapsedRealtime()
        if (playbackState == Player.STATE_BUFFERING && player.playWhenReady && startedLoading == null) stalledAt = now
        else if (playbackState == Player.STATE_READY && stalledAt > 0) {
            android.util.Log.i("PrismStream", "${player.currentMediaItem?.mediaId} stalled ${now - stalledAt} ms at ${player.currentPosition} ms")
            stalledAt = 0
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateButtons()
        // Shuffling picks a different next song.
        prefetchNext()
    }

    override fun onRepeatModeChanged(repeatMode: Int) = updateButtons()

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        effects.attach(audioSessionId)
    }

    override fun onPlayerError(error: PlaybackException) {
        val id = player.currentMediaItem?.mediaId ?: return
        // It turned out to be a live stream (a "Listen together" station): play it again as one, from its HLS manifest.
        val live = generateSequence(error as Throwable) { it.cause }.filterIsInstance<com.prism.music.data.stream.LiveStreamException>().firstOrNull()
        if (live != null) {
            val idx = player.currentMediaItemIndex
            val song = player.currentMediaItem?.toSong()
            if (song != null && !song.isLive) {
                swapInPlace(idx, song.copy(isLive = true).toMediaItem())
                applyLiveVideo()
                player.seekToDefaultPosition(idx)
                player.prepare()
                player.play()
                return
            }
        }
        // A music video that won't load falls back to the song itself — never skip ahead.
        if (player.currentMediaItem?.videoModeId != null) {
            scope.launch { revertVideo(id) }
            return
        }
        val httpCode = generateSequence(error as Throwable) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
        val attempts = retries[id] ?: 0
        if (attempts < 2 && (httpCode == 403 || httpCode == 410 || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        ) {
            retries[id] = attempts + 1
            c.streams.invalidate(id)
            val pos = player.currentPosition
            player.prepare()
            player.seekTo(pos)
            player.play()
            return
        }
        c.player.reportError((error.cause ?: error).friendlyMessage("Playback failed"))
        if (player.hasNextMediaItem()) {
            scope.launch {
                delay(1500)
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    // ------------------------------------------------------------ Features

    /**
     * Normalization gain for the current song: YouTube's loudness figure says how far the track
     * sits from its reference level, so loud songs come down and quiet ones go up (by up to 6 dB;
     * the limiter in [NormalizerProcessor] keeps those from clipping).
     */
    /** The normalization gain now applied (the ending song's, when a Mix blend's tail takes it over). */
    private var normalizeGainDb = 0f

    /**
     * The playing song is still just starting (loading, or in its first [SETTLE_MS] of sound): a better loudness figure
     * may still set its volume. After that the volume stays put for the rest of the song.
     */
    private fun settling() = player.currentPosition < SETTLE_MS

    private fun applyNormalization() {
        // The song's own measured loudness (integrated LUFS, from [MixTempo]'s scan) is the real figure. YouTube's
        // loudnessDb hardly differs between a loud master and a quiet one, so it only stands in until the song is
        // measured.
        val measured = trackedSong?.id?.let { c.mixTempo.loudness(it) }
        val yt = songLoudness
        val gain = when {
            !c.settings.current.normalize -> 0f
            measured != null -> (TARGET_LUFS - measured).coerceIn(-12f, 6f)
            yt != null -> (-yt).coerceIn(-12f, 6f)
            else -> 0f
        }
        normalizer.setGainDb(gain)
        normalizeGainDb = gain
        c.nerdStats.value = c.nerdStats.value.copy(
            loudnessDb = (measured ?: yt)?.toDouble(), loudnessMeasured = measured != null, normalizationGainDb = gain,
        )
        android.util.Log.i(
            "PrismNormalize",
            (if (measured != null) "measured %.1f LUFS".format(measured) else "YouTube's figure ${yt ?: "unknown"} dB") +
                " -> gain %.1f dB (normalize ${c.settings.current.normalize})".format(gain),
        )
    }

    /**
     * Scans the playing and next songs ([MixTempo]): how their music starts and ends and their beat, for Crossfade, and
     * their loudness, for normalization. From the phone when the song's there (downloaded, cached, a local file),
     * else a small download, on Wi-Fi unless mobile data is allowed. Each song is scanned once.
     *
     * Not until the playing song is under way: a scan fetches a whole song (and asks YouTube for its streams) and
     * decodes it, and run as a song changed it held up that song's own start, which buffered for longer.
     */
    private fun scanAhead(vararg items: MediaItem?) {
        val s = c.settings.current
        if (!s.mix && !s.normalize) return
        val network = s.mixTempoOnCellular || getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered == false
        val songs = items.mapNotNull { it?.toSong() }.filter { !it.isLive && !it.isEpisode }.distinctBy { it.id }
        if (songs.isEmpty()) return
        // Songs scanned before fade-outs and intros were measured are scanned again only for Crossfade's automatic length.
        val edges = s.mix && s.mixAuto
        val playing = player.currentMediaItem?.mediaId
        scanJob?.cancel()
        scanJob = scope.launch {
            if (playing != null) untilUnderWay(playing, UNDER_WAY_MS)
            // YouTube's loudness figure for each queued song not known yet: quick, and enough to start it at the right
            // volume if it comes up before it's been measured (a skip).
            if (s.normalize) for (song in songs) {
                if (song.id == playing || song.isLocal || c.loudness.contains(song.id) || c.mixTempo.loudness(song.id) != null) continue
                withContext(Dispatchers.IO) { runCatching { c.ytm.playerExtras(song.id, c.streams.signatureTimestamp()).loudnessDb } }
                    .getOrNull()?.let { c.loudness.edit().putFloat(song.id, it.toFloat()).apply() }
            }
            // Then measured, the queued songs first (their volume is set as they start); the playing one last (only
            // Crossfade needs it, near its end).
            for (song in songs.sortedBy { it.id == playing }) {
                c.mixTempo.measure(song.id, network, edges)
                // Measured while it's just begun: its own figure takes over (later in the song, a jump would be heard).
                if (trackedSong?.id == song.id && settling()) applyNormalization()
            }
        }
    }

    private fun loadExtras(song: Song) {
        extrasJob?.cancel()
        // Remembered loudness applies straight away (and offline); the network refreshes it.
        songLoudness = c.loudness.getFloat(song.id, Float.NaN).takeIf { !it.isNaN() }
        applyNormalization()
        // A file on the phone has nothing on YouTube to fetch or report.
        if (song.isLocal) return
        extrasJob = scope.launch {
            val ex = runCatching { withContext(Dispatchers.IO) { c.ytm.playerExtras(song.id, c.streams.signatureTimestamp()) } }.getOrNull()
            ex?.loudnessDb?.toFloat()?.let { loud ->
                c.loudness.edit().putFloat(song.id, loud).apply()
                // Only while the song is just starting: later, the volume jumping mid-song is worse than a figure
                // that's a little off (it's kept, so the song plays at it from the start next time).
                if (trackedSong?.id == song.id && settling()) { songLoudness = loud; applyNormalization() }
            }
            if (ex != null && c.settings.current.isLoggedIn) {
                withContext(Dispatchers.IO) { runCatching { c.ytm.registerPlayback(ex) } }
            }
        }
    }

    /**
     * With Autoplay on, keeps the queue topped up with YouTube Music radio
     * suggestions as it nears its end: seeded by the song for a single-song
     * play, or by the last track of an album / playlist.
     */
    fun maybeAutoplay() {
        val src = c.queue.source.value
        if (!c.settings.current.autoplay || player.mediaItemCount == 0 || c.queue.followingHost) return
        // No music radio after a podcast episode.
        if (player.currentMediaItem?.toSong()?.isEpisode == true) return
        if (src.kind != QueueKind.SINGLE && player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (player.mediaItemCount - player.currentMediaItemIndex > 3 || c.queue.autoplayLoading) return
        val seed = src.seedId ?: player.getMediaItemAt(player.mediaItemCount - 1).mediaId.takeIf { it.isNotBlank() } ?: return
        // YouTube Music's radio can't start from a file on the phone.
        if (seed.startsWith(Song.LOCAL_PREFIX)) return
        c.queue.autoplayLoading = true
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) { c.ytm.next(seed, "RDAMVM$seed", continuation = c.queue.autoplayContinuation) }
            }.getOrNull()
            c.queue.autoplayLoading = false
            // Dropped if the queue was replaced, or Autoplay was switched off, while loading.
            if (r == null || c.queue.source.value !== src || !c.settings.current.autoplay) return@launch
            c.queue.counterparts.putAll(r.counterparts)
            c.queue.autoplayContinuation = r.continuation
            val existing = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            val add = r.songs.filter { it.id !in existing && !c.library.isDisliked(it.id) }
            if (add.isNotEmpty()) {
                if (c.queue.autoplayStart.value < 0) c.queue.autoplayStart.value = player.mediaItemCount
                player.addMediaItems(add.map { it.toMediaItem() })
            }
        }
    }

    /** Switching Autoplay off takes its suggestions back out of the queue; on, fetches them. */
    private fun onAutoplayChanged(enabled: Boolean) {
        if (enabled) { maybeAutoplay(); return }
        val start = c.queue.autoplayStart.value
        if (start >= 0 && start < player.mediaItemCount) {
            val from = maxOf(start, player.currentMediaItemIndex + 1)
            if (from < player.mediaItemCount) player.removeMediaItems(from, player.mediaItemCount)
        }
        c.queue.autoplayStart.value = -1
        c.queue.autoplayContinuation = null
    }

    /**
     * The song's music video id. Null with [lookupFailed] = true means YouTube
     * couldn't be reached (worth trying again); otherwise there simply isn't one.
     */
    suspend fun videoIdFor(song: Song): VideoLookup {
        // An episode is a YouTube video already: its video is itself.
        if (song.isVideo || song.isEpisode) return VideoLookup(song.id)
        // A file on the phone has no music video.
        if (song.isLocal) return VideoLookup(null)
        // A saved video plays offline, so it's known without asking YouTube.
        c.downloads.downloadedVideoId(song.id)?.let { return VideoLookup(it) }
        c.queue.counterparts[song.id]?.let { return VideoLookup(it) }
        if (song.id in c.queue.noVideo) return VideoLookup(null)
        val found = runCatching { withContext(Dispatchers.IO) { c.ytm.findMusicVideo(song) } }
        found.getOrNull()?.let { c.queue.counterparts[song.id] = it; return VideoLookup(it) }
        if (found.isSuccess) c.queue.noVideo.add(song.id)
        return VideoLookup(null, lookupFailed = found.isFailure)
    }

    /**
     * Gets a song's video ready before it's asked for: the video's streams are
     * resolved (the slow part of switching), and on Wi-Fi the lyric/position
     * offset is measured, so the video button switches straight away.
     */
    fun prefetchVideo(song: Song, videoId: String) {
        if (song.isLocal || videoId in prefetched) return
        prefetched += videoId
        scope.launch(Dispatchers.IO) {
            runCatching { c.streams.resolve(videoId, withVideo = true) }.onFailure { prefetched -= videoId }
            val metered = getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered != false
            if (metered || videoId == song.id || c.videoSync.cached(song.id, videoId) != null) return@launch
            // Measured once the song is under way: a long video ("Alright" runs three minutes past its song) means
            // minutes of audio to fetch and analyse, which slowed the song's own start when it ran at once.
            withContext(Dispatchers.Main) { untilUnderWay(song.id) }
            runCatching { c.videoSync.lagMs(song.id, videoId) }
        }
    }

    /** Returns once [songId] has played [afterMs] (~10 s; or something else is playing: it was lined up as the next song). */
    private suspend fun untilUnderWay(songId: String, afterMs: Long = 10_000) {
        delay(minOf(3_000, afterMs))
        repeat(60) {
            if (player.currentMediaItem?.mediaId != songId || (player.isPlaying && player.currentPosition > afterMs)) return
            delay(1_000)
        }
    }

    /** The next [n] songs in the order they'll play (shuffled or not). */
    private fun upcoming(n: Int): List<MediaItem> {
        val tl = player.currentTimeline
        val out = ArrayList<MediaItem>(n)
        var i = player.currentMediaItemIndex
        while (out.size < n) {
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (i == C.INDEX_UNSET) break
            out += player.getMediaItemAt(i)
        }
        return out
    }

    /** The queued songs' scans and loudness figures, under way (a newer queue replaces it). */
    private var scanJob: kotlinx.coroutines.Job? = null
    /** Whole queued songs being loaded ahead (Settings → Load ahead; a newer queue replaces it). */
    private var preloadJob: kotlinx.coroutines.Job? = null

    /**
     * Resolves the next song's stream while this one plays (the slow part of starting a song), so
     * skipping ahead or moving on starts straight away. Downloaded songs don't need it.
     */
    private fun prefetchNext() {
        val i = player.nextMediaItemIndex
        if (i == C.INDEX_UNSET) return
        val next = player.getMediaItemAt(i).mediaId.takeIf { it.isNotBlank() } ?: return
        warmedNext = next
        // The next few queued songs' loudness (for normalization) and edges (for Crossfade), ready well before they
        // play, so each starts at its own volume from its first second. Once this song is under way ([scanAhead]).
        scanAhead(player.currentMediaItem, *upcoming(QUEUE_AHEAD).toTypedArray())
        // Settings → Load ahead: the next song or three loaded whole, once this one is under way.
        val preload = c.settings.current.preload
        preloadJob?.cancel()
        if (preload.songs > 0) {
            val playing = player.currentMediaItem?.mediaId
            val ids = upcoming(preload.songs).map { it.mediaId }.filter { it.isNotBlank() && !c.downloads.isDownloaded(it) }
            preloadJob = scope.launch {
                if (playing != null) untilUnderWay(playing, UNDER_WAY_MS)
                for (id in ids) withContext(Dispatchers.IO) { c.warmUp(id, whole = true, cellular = c.settings.current.preloadCellular) }
            }
        }
        if (next.startsWith(Song.LOCAL_PREFIX)) return
        if (c.downloads.isDownloaded(next)) { armNextVideo(); return }
        scope.launch(Dispatchers.IO) {
            c.warmUp(next, cellular = c.settings.current.preloadCellular)
            // A live stream coming up is lined up as one, so it starts without a failed attempt first.
            if (runCatching { c.streams.isLive(next) }.getOrDefault(false)) withContext(Dispatchers.Main) {
                val at = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == next && it != player.currentMediaItemIndex }
                val song = at?.let { player.getMediaItemAt(it).toSong() }
                if (at != null && song != null && !song.isLive) {
                    player.replaceMediaItem(at, song.copy(isLive = true).toMediaItem(player.getMediaItemAt(at).videoModeId?.let { song.id }))
                }
            }
        }
        armNextVideo()
    }

    /**
     * The listener picked the video (and hasn't gone back to the song since). With "Keep playing videos"
     * on, each new song then plays as its video too.
     */
    var videoWanted = false
        set(v) {
            field = v
            if (v) armNextVideo() else disarmVideos()
        }

    private fun keepVideos() = videoWanted && c.settings.current.videoContinues

    /**
     * A new song started while videos are wanted. The next song is usually lined up as its video
     * already ([armNextVideo]); if it wasn't ready (a quick skip), it switches here. Songs without a
     * video just play as songs, quietly.
     */
    private fun continueVideo() {
        if (!keepVideos()) return
        val item = player.currentMediaItem ?: return
        val song = item.toSong() ?: return
        if (item.videoModeId != null) { watchVideo(song.id); return }
        scope.launch { setVideoMode(true) }
    }

    /** Lines the next song up as its video, so moving on to it is seamless. */
    private fun armNextVideo() {
        if (!keepVideos()) return
        val i = player.nextMediaItemIndex
        if (i == C.INDEX_UNSET) return
        val item = player.getMediaItemAt(i)
        if (item.videoModeId != null) return
        val song = item.toSong() ?: return
        scope.launch {
            val vid = if (song.isLive) song.id else videoIdFor(song).id ?: return@launch
            val at = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return@launch
            val next = player.getMediaItemAt(at)
            if (!keepVideos() || next.mediaId != song.id || next.videoModeId != null) return@launch
            val nextSong = next.toSong() ?: return@launch
            if (failedVideos.remove(vid)) c.streams.invalidate(vid)
            player.replaceMediaItem(at, nextSong.toMediaItem(if (nextSong.isLive) nextSong.id else vid))
            if (!nextSong.isLive) prefetchVideo(nextSong, vid)
        }
    }

    /** Back to songs: anything lined up as a video (other than what's playing) goes back to audio. */
    private fun disarmVideos() {
        for (i in 0 until player.mediaItemCount) {
            if (i == player.currentMediaItemIndex) continue
            val other = player.getMediaItemAt(i)
            if (other.videoModeId != null) other.toSong()?.let { player.replaceMediaItem(i, it.toMediaItem(null)) }
        }
    }

    private val prefetched = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    /** Videos that failed to play; their streams are re-resolved on the next try. */
    private val failedVideos = HashSet<String>()

    /** Swaps the current item between its audio track and its music video. Returns an error message, if any. */
    suspend fun setVideoMode(enabled: Boolean): String? {
        val idx = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return null
        val song = item.toSong() ?: return null
        if (enabled == (item.videoModeId != null)) return null
        if (song.isLive) {
            // The picture is already in the stream: same item, video track on (or off), no seeking about.
            swapInPlace(idx, song.toMediaItem(if (enabled) song.id else null))
            applyLiveVideo()
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            videoWatchdog?.cancel()
            if (enabled) watchVideo(song.id)
            return null
        }
        val vid = if (enabled) {
            val found = videoIdFor(song)
            found.id ?: return if (found.lookupFailed) "Couldn't reach YouTube for the music video. Tap to try again." else "No music video for this song"
        } else null
        if (player.currentMediaItemIndex != idx) return null
        if (vid != null && failedVideos.remove(vid)) c.streams.invalidate(vid)
        // Videos often have an intro, so the same moment falls at a different time: carry the
        // listener to the matching spot. Uses the measured offset if it's ready (it's prefetched);
        // otherwise switch now and correct the position as soon as it's known.
        val known = when {
            vid != null -> c.videoSync.cached(song.id, vid)
            else -> item.videoModeId?.let { c.videoSync.cached(song.id, it) }?.let { -it }
        }
        val now = player.currentPosition
        val pos = (now + (known ?: 0L)).coerceAtLeast(0)
        val playWhenReady = player.playWhenReady
        swapInPlace(idx, song.toMediaItem(vid))
        player.seekTo(idx, pos)
        player.playWhenReady = playWhenReady
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        videoWatchdog?.cancel()
        if (vid != null) {
            if (known == null && vid != song.id && now > 1_500) scope.launch {
                val lag = c.videoSync.lagMs(song.id, vid) ?: return@launch
                val current = player.currentMediaItem
                // Only if the listener is still on this video and hasn't moved far since switching.
                if (kotlin.math.abs(lag) >= 1_000 && current?.mediaId == song.id && current.videoModeId == vid &&
                    kotlin.math.abs(player.currentPosition - pos) < 20_000
                ) player.seekTo(player.currentPosition + lag)
            }
            watchVideo(song.id)
        }
        return null
    }

    private var videoWatchdog: Job? = null

    /** No picture after a fair wait: fall back to the song (the button stays usable). */
    private fun watchVideo(songId: String) {
        videoWatchdog?.cancel()
        videoWatchdog = scope.launch {
            delay(20_000)
            val cur = player.currentMediaItem
            if (cur?.mediaId == songId && cur.videoModeId != null && player.videoSize.width <= 0) revertVideo(songId)
        }
    }

    /**
     * A live stream's HLS variants carry video and audio together, so in audio mode its video track is turned off
     * (nothing to decode, and the player can take a lighter variant); other items leave video on for music videos.
     */
    private fun applyLiveVideo() {
        val item = player.currentMediaItem
        val off = item != null && item.toSong()?.isLive == true && item.videoModeId == null
        val params = player.trackSelectionParameters
        if ((C.TRACK_TYPE_VIDEO in params.disabledTrackTypes) != off) {
            player.trackSelectionParameters = params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, off).build()
        }
    }

    /** Switches back to the audio version after a video failure; the video can be tried again. */
    private suspend fun revertVideo(songId: String) {
        videoWatchdog?.cancel()
        val idx = player.currentMediaItemIndex
        val current = player.currentMediaItem ?: return
        val song = current.toSong() ?: return
        if (song.isLive) {
            swapInPlace(idx, song.toMediaItem(null))
            applyLiveVideo()
            if (player.playbackState == Player.STATE_IDLE) { player.prepare(); player.play() }
            c.player.onVideoFailed(songId)
            c.player.reportError("The live video didn't load, so it's playing as audio. Tap the video button to try again.")
            return
        }
        current.videoModeId?.let { failedVideos += it; prefetched -= it }
        val lag = current.videoModeId?.takeIf { it != song.id }?.let { c.videoSync.cached(song.id, it) } ?: 0L
        val pos = (player.currentPosition - lag).coerceAtLeast(0)
        swapInPlace(idx, song.toMediaItem(null))
        player.seekTo(idx, pos)
        player.prepare()
        player.play()
        c.player.onVideoFailed(songId)
        c.player.reportError("The music video didn't load, so the song is playing. Tap the video button to try again.")
    }

    private fun flushPlayTime() {
        val now = SystemClock.elapsedRealtime()
        if (playStartedAt > 0) accumulatedMs += now - playStartedAt
        playStartedAt = if (::player.isInitialized && player.isPlaying) now else 0
        val song = trackedSong
        val ms = accumulatedMs
        accumulatedMs = 0
        // Replay, smart downloads and search taste are about music: episodes aren't counted.
        if (song != null && ms >= 10_000 && !song.isEpisode) {
            scope.launch(Dispatchers.IO) {
                c.db.plays().insert(PlayEventEntity(songId = song.id, timestamp = System.currentTimeMillis(), playedMs = ms))
            }
        }
    }

    /**
     * Buttons beside play/pause/skip. The phone's media controls show the first two (like, shuffle);
     * the watch keeps previous/next beside play (see the session extras); Android Auto shows them all: like, shuffle, repeat, lyrics, start radio and "not for me".
     */
    private fun updateButtons() {
        if (!::session.isInitialized) return
        val id = player.currentMediaItem?.mediaId
        val liked = id != null && id in c.library.likedIds.value
        fun button(icon: Int, name: String, command: SessionCommand) =
            CommandButton.Builder(icon).setDisplayName(name).setSessionCommand(command).setSlots(CommandButton.SLOT_OVERFLOW).build()
        // Previous/next are named for their slots outright: with only custom buttons, a controller that lays out by
        // slots (the Pixel Watch's media controls) filled the back/forward places with like and shuffle.
        val buttons = listOf(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS).setDisplayName("Previous")
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS).setSlots(CommandButton.SLOT_BACK).build(),
            CommandButton.Builder(CommandButton.ICON_NEXT).setDisplayName("Next")
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT).setSlots(CommandButton.SLOT_FORWARD).build(),
            button(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED, if (liked) "Remove from liked" else "Like", likeCommand),
            button(if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF, if (player.shuffleModeEnabled) "Shuffle off" else "Shuffle", shuffleCommand),
            when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> button(CommandButton.ICON_REPEAT_ALL, "Repeat one", repeatCommand)
                Player.REPEAT_MODE_ONE -> button(CommandButton.ICON_REPEAT_ONE, "Repeat off", repeatCommand)
                else -> button(CommandButton.ICON_REPEAT_OFF, "Repeat", repeatCommand)
            },
            if (c.settings.current.carLyrics) button(CommandButton.ICON_SUBTITLES, "Hide lyrics", lyricsCommand)
            else button(CommandButton.ICON_SUBTITLES_OFF, "Show lyrics", lyricsCommand),
            button(CommandButton.ICON_RADIO, "Start radio", radioCommand),
            button(CommandButton.ICON_THUMB_DOWN_UNFILLED, "Not for me", dislikeCommand),
        )
        session.setMediaButtonPreferences(buttons)
        pushWidgets()
    }

    /** Home screen widgets follow what's playing (nothing happens when none is placed). */
    private fun pushWidgets() {
        if (!::player.isInitialized) return
        val song = player.currentMediaItem?.toSong()
        com.prism.music.widget.Widgets.update(
            this, song, player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE,
            song != null && song.id in c.library.likedIds.value,
            player.currentPosition, player.duration.takeIf { it > 0 },
        )
    }

    /** A fresh shuffle order each time shuffle goes on, current song first. */
    private fun reshuffle() {
        val n = player.mediaItemCount
        if (n < 2) return
        val cur = player.currentMediaItemIndex.coerceIn(0, n - 1)
        player.setShuffleOrder(HistoryShuffleOrder.shuffled(n, cur) { player.currentMediaItemIndex })
    }

    /** Swaps everything after the current song for a radio station seeded by it. */
    private fun startRadio() {
        val song = player.currentMediaItem?.toSong() ?: return
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { c.ytm.next(song.id, "RDAMVM${song.id}") } }.getOrNull() ?: return@launch
            if (player.currentMediaItem?.mediaId != song.id) return@launch
            c.queue.counterparts.putAll(r.counterparts)
            val add = r.songs.filter { it.id != song.id && !c.library.isDisliked(it.id) }
            if (add.isEmpty()) return@launch
            val idx = player.currentMediaItemIndex
            if (idx + 1 < player.mediaItemCount) player.removeMediaItems(idx + 1, player.mediaItemCount)
            if (idx > 0) player.removeMediaItems(0, idx)
            player.shuffleModeEnabled = false
            player.addMediaItems(add.map { it.toMediaItem() })
            c.queue.source.value = QueueSource(QueueKind.RADIO, "${song.title} radio", seedId = song.id)
            c.queue.autoplayStart.value = -1
            c.queue.autoplayContinuation = r.continuation
        }
    }

    private fun isCar(session: MediaSession, controller: MediaSession.ControllerInfo) =
        controller.packageName == "com.google.android.projection.gearhead" ||
            runCatching { session.isAutoCompanionController(controller) || session.isAutomotiveController(controller) }.getOrDefault(false)

    fun applyStreamStats(id: String) {
        val (source, s) = c.streamDetails[id] ?: return
        if (player.currentMediaItem?.mediaId != id && player.currentMediaItem?.videoModeId != id) return
        // Downloaded and cached songs have no stream info this session: work the bitrate out
        // from the saved file's size over the song's length.
        val savedBytes = if (s == null) savedLength(id) else 0L
        val durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            ?: player.currentMediaItem?.toSong()?.durationSec?.takeIf { it > 0 }?.times(1000L) ?: 0L
        val savedKbps = if (savedBytes > 0 && durationMs > 0) (savedBytes * 8 / durationMs).toInt() else 0
        c.nerdStats.value = c.nerdStats.value.copy(
            source = source,
            itag = s?.itag ?: 0,
            container = s?.mimeType ?: c.nerdStats.value.container,
            codec = s?.codec ?: c.nerdStats.value.codec,
            streamBitrateKbps = s?.bitrate ?: savedKbps.takeIf { it > 0 } ?: c.nerdStats.value.streamBitrateKbps,
            contentLength = s?.contentLength ?: savedBytes,
            client = s?.client ?: "",
        )
    }

    /** The lossless add-on: a matching FLAC / WAV / AIFF on the phone, when lossless mode and the add-on are on. */
    private fun losslessFileFor(item: MediaItem): android.net.Uri? {
        // Local music always plays from its file.
        if (item.mediaId.startsWith(Song.LOCAL_PREFIX)) {
            val uri = c.localMusic.uriFor(item.mediaId) ?: return null
            val format = c.localMusic.track(item.mediaId)?.mime?.substringAfter('/')?.removePrefix("x-")?.uppercase()
            c.streamDetails[item.mediaId] = "On this phone" + (format?.let { " · $it" } ?: "") to null
            scope.launch { applyStreamStats(item.mediaId) }
            return uri
        }
        val s = c.settings.current
        if (!s.lossless || !s.losslessLocal) return null
        val song = item.toSong() ?: return null
        val track = c.localLossless.match(song) ?: return null
        c.streamDetails[song.id] = "Lossless · ${track.format} on this phone" to null
        scope.launch { applyStreamStats(song.id) }
        return track.uri
    }

    private fun savedLength(id: String): Long = c.localLossless.matched[id]?.size ?: runCatching {
        listOf(c.downloadCache, c.playerCache).firstNotNullOfOrNull { cache ->
            androidx.media3.datasource.cache.ContentMetadata.getContentLength(cache.getContentMetadata(id)).takeIf { it > 0 }
        } ?: 0L
    }.getOrDefault(0L)

    // ------------------------------------------------------------ Queue persistence

    private val queueFile get() = File(filesDir, "queue.json")

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch { delay(2000); saveQueue() }
    }

    private fun saveQueue() {
        if (!::player.isInitialized) return
        val songs = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toSong() }
        val q = SavedQueue(songs, player.currentMediaItemIndex, player.currentPosition, c.queue.source.value.title)
        val text = InnerTube.json.encodeToString(SavedQueue.serializer(), q)
        scope.launch(Dispatchers.IO) { runCatching { queueFile.writeText(text) } }
    }

    private fun restoreQueue() {
        val q = runCatching { InnerTube.json.decodeFromString(SavedQueue.serializer(), queueFile.readText()) }.getOrNull() ?: return
        if (q.songs.isEmpty()) return
        c.queue.source.value = QueueSource(QueueKind.OTHER, q.title)
        player.setMediaItems(q.songs.map { it.toMediaItem() }, q.index.coerceIn(0, q.songs.lastIndex), q.positionMs)
        // The mini player shows this song: get it ready, so Play starts it straight away.
        player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }?.let { id -> scope.launch(Dispatchers.IO) { c.warmUp(id) } }
        // And the one after it (otherwise only a song coming up warms its successor).
        prefetchNext()
    }

    // ------------------------------------------------------------ Session

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (isCar(session, controller)) {
                carControllers += controller
                carLyrics.carConnected = true
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().apply { customCommands.forEach { add(it) } }.build()
                )
                .build()
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            if (carControllers.remove(controller)) carLyrics.carConnected = carControllers.isNotEmpty()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                likeCommand.customAction -> player.currentMediaItem?.toSong()?.let { c.library.toggleLike(it) }
                shuffleCommand.customAction -> {
                    if (!player.shuffleModeEnabled) reshuffle()
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                }
                repeatCommand.customAction -> player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                lyricsCommand.customAction -> c.settings.setCarLyrics(!c.settings.current.carLyrics)
                radioCommand.customAction -> startRadio()
                dislikeCommand.customAction -> player.currentMediaItem?.toSong()?.let { song ->
                    c.library.setDisliked(song, true)
                    if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        // ---- Browsing (Android Auto, Assistant, other media browsers)

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            // Android Auto / the system ask for a "recent" root to offer a resume card.
            Futures.immediateFuture(LibraryResult.ofItem(if (params?.isRecent == true) tree.recentRoot() else tree.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val all = tree.children(parentId)
            val from = (page * pageSize).coerceAtMost(all.size)
            LibraryResult.ofItemList(all.subList(from, (from + pageSize).coerceAtMost(all.size)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            tree.item(mediaId)?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            val count = tree.search(query)
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val all = tree.searchResults()
            val from = (page * pageSize).coerceAtMost(all.size)
            LibraryResult.ofItemList(all.subList(from, (from + pageSize).coerceAtMost(all.size)), params)
        }

        // ---- Playing what a controller picked

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = scope.future {
            mediaItems.flatMap { item ->
                if (item.mediaId.startsWith(MediaTree.SHUFFLE)) tree.shuffleSongs(item.mediaId)?.second.orEmpty().shuffled().map { it.toMediaItem() }
                else listOfNotNull((item.toSong() ?: tree.song(item.mediaId, item))?.toMediaItem())
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val single = mediaItems.singleOrNull()
            // "Hey Google, play … on Prism": a search query instead of an id.
            val query = single?.requestMetadata?.searchQuery
            if (single != null && single.mediaId.isBlank() && !query.isNullOrBlank()) {
                val top = tree.searchTop(query)
                if (top != null) {
                    c.queue.source.value = QueueSource(QueueKind.SINGLE, "Autoplay", seedId = top.id)
                    c.queue.autoplayStart.value = -1
                    c.queue.autoplayContinuation = null
                    return@future MediaSession.MediaItemsWithStartPosition(listOf(top.toMediaItem()), 0, C.TIME_UNSET)
                }
            }
            // "Shuffle play" at the top of a list.
            if (single != null && single.mediaId.startsWith(MediaTree.SHUFFLE)) {
                tree.shuffleSongs(single.mediaId)?.takeIf { it.second.isNotEmpty() }?.let { (title, list) ->
                    c.queue.source.value = QueueSource(QueueKind.PLAYLIST, title)
                    c.queue.autoplayStart.value = -1
                    player.shuffleModeEnabled = true
                    // Shuffled here too, so each "Shuffle play" is a new order rather than the player's last one.
                    return@future MediaSession.MediaItemsWithStartPosition(list.shuffled().map { it.toMediaItem() }, 0, C.TIME_UNSET)
                }
            }
            // One song tapped in a list: play the list from that song.
            if (single != null && single.toSong() == null) {
                tree.folderOf(single.mediaId)?.let { (title, list) ->
                    val index = list.indexOfFirst { it.id == single.mediaId }.coerceAtLeast(0)
                    c.queue.source.value = QueueSource(QueueKind.PLAYLIST, title)
                    c.queue.autoplayStart.value = -1
                    return@future MediaSession.MediaItemsWithStartPosition(list.map { it.toMediaItem() }, index, startPositionMs)
                }
                tree.song(single.mediaId, single)?.let { song ->
                    c.queue.source.value = QueueSource(QueueKind.SINGLE, "Autoplay", seedId = song.id)
                    c.queue.autoplayStart.value = -1
                    c.queue.autoplayContinuation = null
                    return@future MediaSession.MediaItemsWithStartPosition(listOf(song.toMediaItem()), 0, startPositionMs)
                }
            }
            val resolved = mediaItems.mapNotNull { item -> (item.toSong() ?: tree.song(item.mediaId, item))?.toMediaItem() }
            MediaSession.MediaItemsWithStartPosition(resolved, startIndex.coerceIn(0, (resolved.size - 1).coerceAtLeast(0)), startPositionMs)
        }

        /** Lets the system media controls (and the car) resume the last queue after Prism was closed. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val q = withContext(Dispatchers.IO) {
                runCatching { InnerTube.json.decodeFromString(SavedQueue.serializer(), queueFile.readText()) }.getOrNull()
            }
            if (q == null || q.songs.isEmpty()) {
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)
            } else {
                c.queue.source.value = QueueSource(QueueKind.OTHER, q.title)
                MediaSession.MediaItemsWithStartPosition(q.songs.map { it.toMediaItem() }, q.index.coerceIn(0, q.songs.lastIndex), q.positionMs)
            }
        }
    }
    // ------------------------------------------------------------ Stats for nerds

    private inner class StatsListener : AnalyticsListener {
        private fun update(block: NerdStats.() -> NerdStats) {
            c.nerdStats.value = c.nerdStats.value.block()
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) = update {
            copy(
                codec = format.codecs ?: format.sampleMimeType ?: codec,
                sampleRate = format.sampleRate.takeIf { it > 0 } ?: sampleRate,
                channels = format.channelCount.takeIf { it > 0 } ?: channels,
                container = format.containerMimeType ?: container,
                streamBitrateKbps = streamBitrateKbps.takeIf { it > 0 }
                    ?: (format.averageBitrate.takeIf { it > 0 } ?: format.bitrate.takeIf { it > 0 })?.let { it / 1000 } ?: 0,
            )
        }

        // The length isn't always known when a downloaded song starts; fill its bitrate in once it is.
        override fun onTimelineChanged(eventTime: AnalyticsListener.EventTime, reason: Int) {
            val id = player.currentMediaItem?.mediaId ?: return
            if (c.nerdStats.value.streamBitrateKbps <= 0) applyStreamStats(id)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) = update { copy(decoder = decoderName) }

        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) =
            update {
                copy(
                    outputEncoding = AudioEffects.encodingName(audioTrackConfig.encoding),
                    outputSampleRate = audioTrackConfig.sampleRate,
                    offload = audioTrackConfig.offload,
                    spatializer = c.settings.current.let { s ->
                        if (s.spatial) "Prism Spatial · ${(s.spatialAmount * 100).roundToInt()}%" else "Off"
                    },
                )
            }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) = update { copy(bandwidthKbps = bitrateEstimate / 1000) }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) = update {
            copy(
                videoResolution = "${format.width}×${format.height}" + if (format.frameRate > 0) " @ ${format.frameRate.toInt()}fps" else "",
                videoCodec = format.codecs ?: format.sampleMimeType ?: "",
            )
        }

        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) =
            update { copy(droppedFrames = this.droppedFrames + droppedFrames) }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long,
        ) = update { copy(underruns = underruns + 1) }
    }
}

/** Normalization's level: songs are brought to this loudness (integrated LUFS, as Spotify and YouTube use). */
private const val TARGET_LUFS = -14f

/** A song's volume (normalization) can still be set this far into it; after that it stays put for the song. */
private const val SETTLE_MS = 2_000L
/** Queued songs are got ready once the playing one has played this long (not as it starts: that slowed its loading). */
private const val UNDER_WAY_MS = 5_000L
/** How many queued songs ahead are got ready (their loudness and edges). */
private const val QUEUE_AHEAD = 3
