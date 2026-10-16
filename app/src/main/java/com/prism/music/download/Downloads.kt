package com.prism.music.download

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.prism.music.AppContainer
import com.prism.music.R
import com.prism.music.container
import com.prism.music.data.db.toEntity
import com.prism.music.data.innertube.InnerTube
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import com.prism.music.data.prefs.AudioQuality
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

const val DOWNLOAD_CHANNEL = "downloads"

/** [lossless]: a lossless file on the phone that lossless sync matched to this song (see [LosslessSync]). */
data class DownloadInfo(val song: Song?, val state: Int, val percent: Float, val bytes: Long, val addedAt: Long = 0, val lossless: Boolean = false)

/** A song's music video saved for offline: its picture, plus the video's own audio when that isn't the song's. */
data class VideoDownload(val videoId: String, val state: Int, val percent: Float, val bytes: Long)

/** What each part of a video download carries: the song it belongs to and the video's id. */
@kotlinx.serialization.Serializable
private data class VideoPart(val song: Song, val videoId: String)

/** Request ids of a video download's parts (kept apart from song downloads, so they don't list as songs). */
private const val VIDEO_PICTURE = "vpic:"
private const val VIDEO_AUDIO = "vaud:"
private fun isVideoPart(id: String) = id.startsWith(VIDEO_PICTURE) || id.startsWith(VIDEO_AUDIO)
private fun partSongId(id: String) = id.substringAfter(':')

@OptIn(UnstableApi::class)
class DownloadRepository(private val context: Context, private val c: AppContainer, scope: CoroutineScope) {

    val manager: DownloadManager = DownloadManager(
        context,
        c.databaseProvider,
        c.downloadCache,
        c.downloadDataSourceFactory(),
        Executors.newFixedThreadPool(4),
    ).apply {
        maxParallelDownloads = 4
        requirements = Requirements(if (c.settings.current.downloadWifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
    }

    /** Downloads Prism made itself, and lossless files on the phone ([LosslessSync]); both show as downloads. */
    private val _media = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    private val _lossless = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    private val _downloads = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadInfo>> = _downloads
    private fun publish() {
        _downloads.value = _lossless.value + _media.value
        c.smartPickBytes = _smartIds.value.sumOf { _media.value[it]?.bytes ?: 0L }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Songs Smart Downloads picked (the songs table's `smartDownload` flag), so their space and network rule are known. */
    private val _smartIds = MutableStateFlow<Set<String>>(emptySet())
    val smartIds: StateFlow<Set<String>> = _smartIds

    /** Re-reads which downloads are Smart Downloads' picks (after it adds or drops some). */
    fun refreshSmartIds() {
        c.scope.launch(Dispatchers.IO) {
            _smartIds.value = runCatching { c.db.songs().smartDownloads().map { it.id }.toSet() }.getOrDefault(_smartIds.value)
            publish()
            applyRequirements()
            c.trimSongCache()
        }
    }

    /**
     * Downloads wait for Wi-Fi when they're set to; Smart Downloads' picks may use mobile data when it's allowed to,
     * so while only picks are waiting, any network will do (a song you download yourself still waits for Wi-Fi).
     */
    private fun applyRequirements() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { mainHandler.post { applyRequirements() }; return }
        val s = c.settings.current
        val waiting = { d: DownloadInfo -> d.state == Download.STATE_QUEUED || d.state == Download.STATE_DOWNLOADING }
        val smart = _smartIds.value
        val picksOnly = s.smartDownloads && s.smartDownloadsCellular &&
            _media.value.any { (id, d) -> id in smart && waiting(d) } &&
            _media.value.none { (id, d) -> id !in smart && waiting(d) } &&
            _videoParts.value.values.none { it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING }
        val req = Requirements(if (s.downloadWifiOnly && !picksOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
        if (manager.requirements != req) manager.requirements = req
    }

    /** Video download parts by request id, and the videos they add up to, by song id. */
    private val _videoParts = MutableStateFlow<Map<String, Download>>(emptyMap())
    private val _videos = MutableStateFlow<Map<String, VideoDownload>>(emptyMap())
    val videos: StateFlow<Map<String, VideoDownload>> = _videos

    private fun publishVideos() {
        _videos.value = _videoParts.value.values.groupBy { partSongId(it.request.id) }.mapNotNull { (songId, parts) ->
            val videoId = parts.firstNotNullOfOrNull { it.part()?.videoId } ?: return@mapNotNull null
            val state = when {
                parts.all { it.state == Download.STATE_COMPLETED } -> Download.STATE_COMPLETED
                parts.any { it.state == Download.STATE_FAILED } -> Download.STATE_FAILED
                parts.any { it.state == Download.STATE_DOWNLOADING } -> Download.STATE_DOWNLOADING
                else -> Download.STATE_QUEUED
            }
            songId to VideoDownload(videoId, state, parts.map { it.percentDownloaded.coerceAtLeast(0f) }.average().toFloat(), parts.sumOf { it.bytesDownloaded })
        }.toMap()
    }

    private fun Download.part(): VideoPart? = runCatching { InnerTube.json.decodeFromString(VideoPart.serializer(), String(request.data)) }.getOrNull()

    private val lyricTries = context.getSharedPreferences("download_lyrics", Context.MODE_PRIVATE)
    private val lyricGate = kotlinx.coroutines.sync.Semaphore(2)
    private val _lyricsWorking = MutableStateFlow(0)
    /** How many downloaded songs are having their lyrics fetched right now. */
    val lyricsWorking: StateFlow<Int> = _lyricsWorking
    private val _lyricLevels = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Saved lyrics per downloaded song, as [com.prism.music.data.lyrics.LyricsRepository.level]. */
    val lyricLevels: StateFlow<Map<String, Int>> = _lyricLevels
    init {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(DOWNLOAD_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.downloads)).build()
        )
        scope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, DownloadInfo>()
            val parts = mutableMapOf<String, Download>()
            manager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    val d = cursor.download
                    if (isVideoPart(d.request.id)) parts[d.request.id] = d else map[d.request.id] = d.info()
                }
            }
            _media.value = map
            _videoParts.value = parts
            publish()
            publishVideos()
            refreshSmartIds()
            // Songs downloaded before lyrics came with them (or while offline) get theirs now.
            backfillLyrics()
            // ...and their animated covers, if they waited for Wi-Fi.
            saveCanvases()
            // ...and their covers, so they show offline (#54).
            saveCovers()
            // Downloads that failed (a stream link that ran out, a dropped connection) try again by themselves (#55).
            retryFailed()
            _waiting.value = manager.notMetRequirements != 0
        }
        c.network.onReconnect { retryFailed(); saveCovers() }
        watchStalls(scope)
        manager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                if (isVideoPart(download.request.id)) {
                    _videoParts.update { it + (download.request.id to download) }
                    publishVideos()
                    applyRequirements()
                    return
                }
                _media.update { it + (download.request.id to download.info()) }
                publish()
                applyRequirements()
                // A pick that arrived takes its space from the song cache.
                if (download.state == Download.STATE_COMPLETED && download.request.id in _smartIds.value) c.trimSongCache()
                if (download.state == Download.STATE_COMPLETED) download.info().song?.let { s ->
                    c.scope.launch(Dispatchers.IO) { ensureLyrics(s) }
                    c.scope.launch(Dispatchers.IO) { saveCover(s) }
                    // Its animated cover is kept with it (on Wi-Fi, unless allowed on mobile data; [saveCanvases] catches up).
                    saveCanvas(s)
                }
            }

            override fun onRequirementsStateChanged(manager: DownloadManager, requirements: Requirements, notMetRequirements: Int) {
                _waiting.value = notMetRequirements != 0
            }

            override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                if (isVideoPart(download.request.id)) {
                    _videoParts.update { it - download.request.id }
                    publishVideos()
                    return
                }
                _media.update { it - download.request.id }
                publish()
                applyRequirements()
                c.canvasStore.forgetDownloads(listOf(download.request.id))
            }
        })
        scope.launch {
            c.losslessSync.songs.collect { synced ->
                val known = _lossless.value
                _lossless.value = synced.mapValues { (id, v) ->
                    DownloadInfo(v.first, Download.STATE_COMPLETED, 100f, v.second.size, known[id]?.addedAt ?: System.currentTimeMillis(), lossless = true)
                }
                publish()
                // Lyrics come with synced songs too, like with any download (same Wi-Fi-only rule).
                val metered = context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered != false
                if (!metered || !c.settings.current.downloadWifiOnly) {
                    synced.keys.filter { it !in known }.forEach { id -> synced[id]?.first?.let { s -> c.scope.launch(Dispatchers.IO) { ensureLyrics(s) } } }
                }
            }
        }
        // Back on Wi-Fi: covers that waited for it are saved.
        runCatching {
            context.getSystemService(android.net.ConnectivityManager::class.java)?.registerNetworkCallback(
                android.net.NetworkRequest.Builder()
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED).build(),
                object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) = saveCanvases()
                },
            )
        }
        scope.launch {
            c.settings.flow.collect { applyRequirements() }
        }
    }

    private val _waiting = MutableStateFlow(false)
    /** Downloads are held back by their network rule (waiting for Wi-Fi). */
    val waitingForNetwork: StateFlow<Boolean> = _waiting

    /**
     * Starts failed downloads again (#55), with fresh stream links. Before, a download that failed stayed failed for
     * good, still showing how far it had got, and an app restart didn't help.
     */
    fun retryFailed() {
        if (!c.network.isOnline()) return
        val failed = runCatching {
            buildList {
                manager.downloadIndex.getDownloads(Download.STATE_FAILED).use { cursor -> while (cursor.moveToNext()) add(cursor.download) }
            }
        }.getOrDefault(emptyList())
        if (failed.isEmpty()) return
        mainHandler.post {
            for (d in failed) {
                streamOf(d)?.let(c.streams::invalidate)
                runCatching { DownloadService.sendAddDownload(context, PrismDownloadService::class.java, d.request, false) }
            }
        }
    }

    /** The YouTube id a download's bytes come from (a video part's is its video's). */
    private fun streamOf(d: Download): String? = if (isVideoPart(d.request.id)) d.part()?.videoId else d.request.id

    /** Bytes each running download had at the last look, and how many looks in a row it hasn't moved. */
    private val progressSeen = HashMap<String, Pair<Long, Int>>()

    /**
     * Every minute: a download that hasn't moved for three minutes while online (a connection that hung, a stream link
     * that ran out mid-way) is stopped and started again from where it got to, with a fresh link.
     */
    private fun watchStalls(scope: CoroutineScope) = scope.launch(Dispatchers.Main) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            val running = manager.currentDownloads.filter { it.state == Download.STATE_DOWNLOADING }
            progressSeen.keys.retainAll(running.map { it.request.id }.toSet())
            if (!c.network.online.value) continue
            for (d in running) {
                val id = d.request.id
                val (bytes, still) = progressSeen[id] ?: (-1L to 0)
                val now = if (d.bytesDownloaded == bytes) still + 1 else 0
                progressSeen[id] = d.bytesDownloaded to now
                if (now >= 3) {
                    progressSeen.remove(id)
                    streamOf(d)?.let(c.streams::invalidate)
                    manager.setStopReason(id, STALL_STOP)
                    manager.setStopReason(id, Download.STOP_REASON_NONE)
                }
            }
        }
    }

    /**
     * Keeps a downloaded song's cover on the phone, for offline (#54): the cover in its lossless file when it's one of
     * those (your own artwork), else the one YouTube Music shows, at full size.
     */
    private suspend fun saveCover(song: Song) {
        if (song.isLocal || c.artwork.has(song.thumbnail)) return
        val file = if (isLosslessOnly(song.id)) c.localLossless.match(song)?.uri else null
        if (file != null) {
            val embedded = runCatching {
                val r = android.media.MediaMetadataRetriever()
                try { r.setDataSource(context, file); r.embeddedPicture } finally { r.release() }
            }.getOrNull()
            if (embedded != null && c.artwork.saveBytes(song.thumbnail, embedded)) return
        }
        if (c.network.isOnline()) c.artwork.save(song.thumbnail)
    }

    /** Covers for every download that doesn't have one kept yet; covers nothing uses any more go. */
    fun saveCovers() {
        c.scope.launch(Dispatchers.IO) {
            val songs = completedSongs()
            for (s in songs) runCatching { saveCover(s) }
            if (songs.isNotEmpty()) runCatching { c.artwork.prune(songs.map { it.thumbnail } + c.offline.covers()) }
        }
    }

    private fun Download.info() = DownloadInfo(
        song = runCatching { InnerTube.json.decodeFromString(Song.serializer(), String(request.data)) }.getOrNull(),
        state = state,
        percent = percentDownloaded,
        bytes = bytesDownloaded,
        addedAt = startTimeMs,
    )

    fun isDownloaded(id: String) = _downloads.value[id]?.state == Download.STATE_COMPLETED

    /** True when this song is only here as a lossless file on the phone (not downloaded by Prism). */
    fun isLosslessOnly(id: String) = id in _lossless.value && id !in _media.value

    fun download(song: Song) {
        // A file on the phone is already offline.
        if (song.isLocal) return
        // A lossless file that was taken out of Downloads just comes back.
        if (c.losslessSync.unhide(song.id)) return
        if (_downloads.value[song.id]?.state.let { it == Download.STATE_COMPLETED || it == Download.STATE_DOWNLOADING || it == Download.STATE_QUEUED }) return
        val request = DownloadRequest.Builder(song.id, android.net.Uri.parse("prism://audio/${song.id}"))
            .setCustomCacheKey(song.id)
            .setData(InnerTube.json.encodeToString(Song.serializer(), song).toByteArray())
            .build()
        c.scope.launch(Dispatchers.IO) {
            c.db.songs().upsert(song.toEntity(c.db.songs().get(song.id)))
            // Lyrics come down with the song (karaoke if any source has it), so they're there offline too.
            ensureLyrics(song, force = true)
            // ...and its loudness, so normalization works offline.
            if (!c.loudness.contains(song.id)) runCatching { c.ytm.playerExtras(song.id, c.streams.signatureTimestamp()).loudnessDb?.let { c.loudness.edit().putFloat(song.id, it.toFloat()).apply() } }
        }
        DownloadService.sendAddDownload(context, PrismDownloadService::class.java, request, false)
    }

    fun downloadAll(songs: List<Song>) = songs.forEach(::download)

    // ------------------------------------------------------------ Music videos

    /** The video saved for [songId], once all of it is here (what video mode plays offline). */
    fun downloadedVideoId(songId: String): String? = _videos.value[songId]?.takeIf { it.state == Download.STATE_COMPLETED }?.videoId

    /**
     * Saves the song's music video for offline: the song itself (so it's in Downloads), the video's picture
     * and, when the video isn't the song, the video's own audio, which its picture is timed to. The player's
     * video mode reads them from the download cache under the same keys it streams with.
     */
    fun downloadVideo(song: Song) {
        if (song.isLive) return
        download(song)
        if (_videos.value[song.id]?.state.let { it == Download.STATE_COMPLETED || it == Download.STATE_DOWNLOADING || it == Download.STATE_QUEUED }) return
        c.scope.launch {
            val videoId = if (song.isVideo) song.id else c.queue.counterparts[song.id]
                ?: runCatching { kotlinx.coroutines.withContext(Dispatchers.IO) { c.ytm.findMusicVideo(song) } }.getOrNull()?.also { c.queue.counterparts[song.id] = it }
            if (videoId == null) { c.player.reportError("No music video to download for this song"); return@launch }
            val data = InnerTube.json.encodeToString(VideoPart.serializer(), VideoPart(song, videoId)).toByteArray()
            val picture = DownloadRequest.Builder(VIDEO_PICTURE + song.id, android.net.Uri.parse("prism://video/$videoId"))
                .setCustomCacheKey("video:$videoId").setData(data).build()
            DownloadService.sendAddDownload(context, PrismDownloadService::class.java, picture, false)
            if (videoId != song.id) {
                val audio = DownloadRequest.Builder(VIDEO_AUDIO + song.id, android.net.Uri.parse("prism://audio/$videoId"))
                    .setCustomCacheKey(videoId).setData(data).build()
                DownloadService.sendAddDownload(context, PrismDownloadService::class.java, audio, false)
            }
        }
    }

    /** Takes back a saved video (the song stays downloaded). */
    fun removeVideo(songId: String) {
        _videoParts.value.keys.filter { partSongId(it) == songId }.forEach { manager.removeDownload(it) }
    }

    fun remove(id: String) {
        if (isLosslessOnly(id)) { c.losslessSync.hide(listOf(id)); return }
        DownloadService.sendRemoveDownload(context, PrismDownloadService::class.java, id, false)
        removeVideo(id)
        c.scope.launch(Dispatchers.IO) { c.db.songs().setSmart(id, false); refreshSmartIds() }
    }

    /** Deletes many downloads at once, straight through the manager (no intent per song). */
    fun removeMany(ids: Collection<String>) {
        c.losslessSync.hide(ids.filter(::isLosslessOnly))
        ids.forEach { manager.removeDownload(it); removeVideo(it) }
        c.scope.launch(Dispatchers.IO) { ids.forEach { c.db.songs().setSmart(it, false) }; refreshSmartIds() }
    }

    fun removeAll() {
        val ids = _media.value.keys.toList()
        c.losslessSync.hide(_lossless.value.keys)
        manager.removeAllDownloads()
        c.scope.launch(Dispatchers.IO) { ids.forEach { c.db.songs().setSmart(it, false) }; refreshSmartIds() }
    }

    /** Smart Downloads' picks and the song cache, emptied (the songs you downloaded yourself stay). */
    fun clearSmartDownloads() {
        removeMany(_smartIds.value.filter { id -> _media.value[id] != null })
        c.clearSongCache()
    }

    // ------------------------------------------------------------ Lyrics for downloads


    private fun online(): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Makes sure a downloaded song has the best lyrics going. Songs without karaoke lyrics are
     * looked at again every few days (sources keep adding them); [force] skips that wait.
     */
    private suspend fun ensureLyrics(song: Song, force: Boolean = false) {
        val have = runCatching { c.lyrics.savedLevel(song) }.getOrDefault(0)
        _lyricLevels.update { it + (song.id to have) }
        if (have >= 3 || !online()) return
        val last = lyricTries.getLong(song.id, 0L)
        if (!force && System.currentTimeMillis() - last < 3 * 86_400_000L) return
        lyricGate.acquire()
        _lyricsWorking.update { it + 1 }
        try {
            val got = runCatching { c.lyrics.download(song) }.getOrNull()
            lyricTries.edit().putLong(song.id, System.currentTimeMillis()).apply()
            _lyricLevels.update { it + (song.id to maxOf(have, c.lyrics.level(got))) }
        } finally {
            _lyricsWorking.update { it - 1 }
            lyricGate.release()
        }
    }

    /** Fills in lyrics for every downloaded song that's missing them (or only has them line by line). */
    fun backfillLyrics(force: Boolean = false) {
        c.scope.launch(Dispatchers.IO) {
            // Same rule as the downloads themselves: no mobile data when they're set to Wi-Fi only.
            val metered = context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered != false
            if (metered && c.settings.current.downloadWifiOnly && !force) {
                completedSongs().forEach { s -> _lyricLevels.update { it + (s.id to runCatching { c.lyrics.savedLevel(s) }.getOrDefault(0)) } }
                return@launch
            }
            for (song in completedSongs()) ensureLyrics(song, force)
        }
    }

    // ------------------------------------------------------------ Animated covers for downloads

    /**
     * Saves a downloaded song's animated cover, when covers are on and saved with downloads, and the network allows
     * it. True when it was saved or asked for (or nothing's needed); false when it waits for Wi-Fi.
     */
    private fun saveCanvas(song: Song): Boolean {
        val s = c.settings.current
        if (!s.canvasEnabled || !s.canvasWithDownloads || song.isLocal || c.canvas.saved.value.has(song.id, null)) return true
        val metered = context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered != false
        if (metered && !s.canvasWithDownloadsCellular) return false
        c.canvasStore.saveWithDownload(song)
        return true
    }

    /** Saves the covers downloaded songs don't have yet (at launch, after the setting's turned on, back on Wi-Fi). */
    fun saveCanvases() {
        c.scope.launch(Dispatchers.Main) { for (song in completedSongs()) if (!saveCanvas(song)) break }
    }

    fun completedSongs(): List<Song> = _downloads.value.values
        .filter { it.state == Download.STATE_COMPLETED }.mapNotNull { it.song }

    val totalBytes: Long get() = _downloads.value.values.sumOf { it.bytes } + _videos.value.values.sumOf { it.bytes }
}

@OptIn(UnstableApi::class)
class PrismDownloadService : DownloadService(
    1001, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, DOWNLOAD_CHANNEL, R.string.downloads, 0,
) {
    override fun getDownloadManager(): DownloadManager = container.downloads.manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        DownloadNotificationHelper(this, DOWNLOAD_CHANNEL)
            .buildProgressNotification(this, R.drawable.ic_notification, null, null, downloads, notMetRequirements)
}

/** The stop reason [DownloadRepository] uses for a moment to restart a stalled download. */
private const val STALL_STOP = 7

/** Sizes offered for Smart Downloads' space (its picks and the songs you've streamed), in GB. */
val SMART_DOWNLOAD_SIZES = listOf(1f, 2f, 3f, 5f, 7.5f, 10f, 15f, 20f, 30f, 50f)

fun formatGb(gb: Float): String = when {
    gb < 1f -> "${(gb * 1024).roundToInt()} MB"
    gb % 1f == 0f -> "${gb.toInt()} GB"
    else -> "$gb GB"
}

private fun kbps(q: AudioQuality) = when (q) { AudioQuality.HIGH -> 150; AudioQuality.NORMAL -> 128; AudioQuality.LOW -> 55 }

/** Rough size of a download at a quality; long mixes and missing durations count as a typical song. */
fun estimatedSongBytes(durationSec: Int, q: AudioQuality): Long = (durationSec.takeIf { it in 30..1200 } ?: 210) * kbps(q) * 125L

fun smartSongEstimate(gb: Float, q: AudioQuality): String =
    "%,d".format((gb * 1_073_741_824.0 / estimatedSongBytes(210, q)).toLong())

