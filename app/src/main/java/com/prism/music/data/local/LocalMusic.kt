package com.prism.music.data.local

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.prism.music.data.model.AlbumRef
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Library → Local music: every music file on the phone (MP3, M4A, FLAC, OGG, WAV…), as Prism songs that play from
 * the file. What a song is comes from the file's own tags (title, artist, album, track number) and its album's
 * artwork; nothing is looked up online for it, and Prism never moves or deletes the files. Local songs' ids are
 * `local:<MediaStore id>` ([Song.isLocal]).
 */
class LocalMusic(private val context: Context, private val scope: CoroutineScope) {
    data class Track(
        val song: Song,
        val uri: Uri,
        val albumId: Long,
        val trackNumber: Int,
        val year: Int,
        val addedAt: Long,
        val mime: String,
        val size: Long,
    )

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    /** Every music file found in the last scan, newest first. */
    val tracks: StateFlow<List<Track>> = _tracks
    private val _scanned = MutableStateFlow(false)
    /** True once a scan has run with the permission (so an empty list means there's no music, not no access). */
    val scanned: StateFlow<Boolean> = _scanned
    @Volatile private var byId: Map<String, Track> = emptyMap()
    private var pending: Job? = null
    private var watching = false

    fun hasPermission() = context.checkSelfPermission(LocalLossless.permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** The file a local song plays from. */
    fun uriFor(id: String): Uri? = byId[id]?.uri
        ?: id.removePrefix(Song.LOCAL_PREFIX).toLongOrNull()?.let { ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it) }

    fun track(id: String): Track? = byId[id]

    /** Scans now, then again whenever Android's music files change. */
    fun start() {
        scope.launch { scan() }
        if (watching) return
        watching = true
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Copying an album fires a burst of changes: wait for it to settle, then scan once.
                pending?.cancel()
                pending = scope.launch { delay(5_000); scan() }
            }
        }
        runCatching { context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer) }
    }

    suspend fun scan() = withContext(Dispatchers.IO) {
        if (!hasPermission()) { _scanned.value = false; return@withContext }
        val out = ArrayList<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.SIZE,
        )
        // Music only (not ringtones, recordings or podcasts' downloads), and long enough to be a song.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        // Whether each album has artwork, looked at once per album (asking for art that isn't there fails every time the
        // notification or a list asks, so songs without any get none).
        val art = HashMap<Long, String?>()
        fun artFor(albumId: Long): String? = if (albumId <= 0) null else art.getOrPut(albumId) {
            val uri = "content://media/external/audio/albumart/$albumId"
            uri.takeIf { runCatching { context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.close() != null }.getOrDefault(false) }
        }
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(MIN_DURATION_MS.toString()),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val albumId = c.getLong(4)
                    val song = Song(
                        id = Song.LOCAL_PREFIX + id,
                        title = title,
                        artists = artistsOf(c.getString(2)),
                        album = c.getString(3)?.takeIf { it.isNotBlank() && it != UNKNOWN }?.let { AlbumRef(it) },
                        durationSec = (c.getLong(5) / 1000).toInt(),
                        // The album's artwork as Android serves it (from the files' embedded covers): it opens as an
                        // image, so Coil, the media notification and the car can all show it.
                        thumbnail = artFor(albumId),
                    )
                    out += Track(
                        song, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), albumId,
                        // MediaStore's TRACK is disc × 1000 + track.
                        c.getInt(6) % 1000, c.getInt(7), c.getLong(8) * 1000, c.getString(9) ?: "", c.getLong(10),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "scan failed", it) }
        byId = out.associateBy { it.song.id }
        _tracks.value = out
        _scanned.value = true
        Log.i(TAG, "${out.size} music files")
    }

    companion object {
        private const val TAG = "PrismLocal"
        private const val MIN_DURATION_MS = 20_000L
        private const val UNKNOWN = "<unknown>"

        /** "A; B" or "A / B" in a tag are several artists; "<unknown>" is none. */
        fun artistsOf(tag: String?): List<ArtistRef> {
            if (tag.isNullOrBlank() || tag == UNKNOWN) return emptyList()
            return tag.split(";", " / ", "\u0000").map { it.trim() }.filter { it.isNotEmpty() }.distinct().map { ArtistRef(it) }
        }
    }
}
