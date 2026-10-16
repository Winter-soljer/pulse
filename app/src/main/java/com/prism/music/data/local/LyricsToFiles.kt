package com.prism.music.data.local

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.prism.music.AppContainer
import com.prism.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves synced lyrics into your own music files (#16): Local files, and the lossless files Prism plays in place of
 * songs. Each song's best lyrics (saved, or found now) go into the file's tag ([LyricsTags]), so any player shows them.
 * Android asks once, for all of them, before files that other apps made can be changed. Prism's own downloads live
 * in its storage rather than as files, so there's nothing there to tag.
 */
class LyricsToFiles(private val context: Context, private val c: AppContainer) {
    class Target(val song: Song, val uri: Uri, val kind: LyricsTags.Kind)
    class Item(val target: Target, val text: String)

    private val _requests = MutableSharedFlow<List<Song>?>(extraBufferCapacity = 1)
    /** Asks for lyrics to be saved into these songs' files (null: every music file). The app's root carries it out. */
    val requests = _requests.asSharedFlow()

    fun request(songs: List<Song>?) { _requests.tryEmit(songs) }

    /** The files [songs] play from that can take lyrics (FLAC and MP3); null: all of them. */
    fun targets(songs: List<Song>?): List<Target> {
        val local = c.localMusic.tracks.value.associateBy { it.song.id }
        fun of(s: Song): Target? {
            if (s.isLocal) {
                val t = local[s.id] ?: return null
                return LyricsTags.kindOf(t.mime)?.let { Target(s, t.uri, it) }
            }
            val t = c.localLossless.match(s) ?: return null
            return LyricsTags.kindOf(t.mime)?.let { Target(s, t.uri, it) }
        }
        val all = songs ?: (local.values.map { it.song } + c.downloads.downloads.value.values.filter { it.lossless }.mapNotNull { it.song })
        return all.mapNotNull(::of).distinctBy { it.uri }
    }

    /** Each file's lyrics text: saved ones, or looked up now when online (a few at a time). Files without any are left out. */
    suspend fun prepare(targets: List<Target>): List<Item> = coroutineScope {
        val online = c.network.isOnline()
        val gate = Semaphore(4)
        targets.map { t ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val l = runCatching { c.lyrics.best(t.song, fetch = online) }.getOrNull()
                    l?.takeIf { it.lines.any { line -> !line.isGap && line.text.isNotBlank() } }?.let { Item(t, LyricsTags.text(it)) }
                }
            }
        }.awaitAll().filterNotNull()
    }

    /** Android's own "allow Prism to modify these files?" (Android 11+), for every file at once. */
    fun writeRequest(items: List<Item>): IntentSender? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createWriteRequest(context.contentResolver, items.map { it.target.uri }).intentSender else null

    /** Writes each file's lyrics in. How many files were changed. */
    suspend fun write(items: List<Item>): Int = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "lyrics-tags").apply { mkdirs() }
        var done = 0
        for (item in items) {
            val src = File(dir, "in"); val out = File(dir, "out")
            try {
                context.contentResolver.openInputStream(item.target.uri)?.use { input -> src.outputStream().use { input.copyTo(it) } } ?: continue
                if (!LyricsTags.write(src, out, item.target.kind, item.text)) continue
                // Only a whole new file goes back over the old one.
                if (out.length() < src.length()) continue
                context.contentResolver.openOutputStream(item.target.uri, "wt")?.use { o -> out.inputStream().use { it.copyTo(o) } } ?: continue
                done++
            } catch (e: Exception) {
                Log.w("PrismLyricsTags", "Couldn't save lyrics into ${item.target.uri}: $e")
            } finally {
                src.delete(); out.delete()
            }
        }
        done
    }
}
