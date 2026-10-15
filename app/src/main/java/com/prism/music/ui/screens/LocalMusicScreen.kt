package com.prism.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.music.data.local.LocalLossless
import com.prism.music.data.local.LocalMusic
import com.prism.music.data.model.Song
import com.prism.music.data.model.songCount
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.ui.LocalNavigator
import com.prism.music.ui.components.Artwork
import com.prism.music.ui.components.EmptyState
import com.prism.music.ui.components.PagerChips
import com.prism.music.ui.components.PrismChip
import com.prism.music.ui.components.RoundAction
import com.prism.music.ui.components.SongRow
import com.prism.music.ui.components.SortBar
import com.prism.music.ui.components.SwipeToQueue
import com.prism.music.ui.components.pageTitleStyle
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.LocalUi
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.launch

/** Local files' sections. */
private enum class LocalTab(val label: String) { SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums") }

/** The Songs section's orders: Album plays each album through in track order. */
private enum class LocalSort(val label: String) { ADDED("Recently added"), TITLE("Title"), ARTIST("Artist"), ALBUM("Album") }

/** One album among the files (by Android's album id), its songs in track order. */
private class LocalAlbum(val id: Long, val title: String, val artist: String, val year: Int, val tracks: List<LocalMusic.Track>) {
    val art: String? get() = tracks.firstNotNullOfOrNull { it.song.thumbnail }
}

/** One artist among the files, with their albums. */
private class LocalArtist(val key: String, val name: String, val tracks: List<LocalMusic.Track>, val albums: List<LocalAlbum>) {
    val art: String? get() = albums.firstNotNullOfOrNull { it.art }
}

private fun artistKey(name: String) = name.trim().lowercase().ifBlank { "￿" }
private fun inTrackOrder(tracks: List<LocalMusic.Track>) =
    tracks.sortedWith(compareBy<LocalMusic.Track> { it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.song.title.lowercase() })

private fun albumsOf(tracks: List<LocalMusic.Track>): List<LocalAlbum> = tracks.groupBy { it.albumId }.map { (id, t) ->
    val artist = t.groupingBy { it.song.primaryArtist }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
    LocalAlbum(id, t.firstNotNullOfOrNull { it.song.album?.title?.takeIf(String::isNotBlank) } ?: "Unknown album", artist, t.maxOf { it.year }, inTrackOrder(t))
}.sortedWith(compareBy<LocalAlbum> { it.title == "Unknown album" }.thenBy { it.title.lowercase() })

/**
 * Library → Local files: the phone's own music files ([LocalMusic]), playing from the files, by song, artist or album.
 * An artist or album opens in place, and Back closes it. Asks for access to the files first.
 */
@Composable
fun LocalMusicScreen(bottomPadding: Dp) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val tracks by c.localMusic.tracks.collectAsState()
    val scanned by c.localMusic.scanned.collectAsState()
    var allowed by remember { mutableStateOf(c.localMusic.hasPermission()) }
    val allow = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted) {
            c.localMusic.start()
            // Lossless sync reads the same files.
            scope.launch { c.losslessSync.sync() }
        }
    }
    var sort by rememberSaveable { mutableStateOf(LocalSort.ADDED) }
    val pager = rememberPagerState { LocalTab.entries.size }
    var openArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var openAlbum by rememberSaveable { mutableStateOf<Long?>(null) }
    BackHandler(openArtist != null || openAlbum != null) {
        // An album opened from an artist goes back to the artist.
        if (openAlbum != null) openAlbum = null else openArtist = null
    }

    val albums = remember(tracks) { albumsOf(tracks) }
    val artists = remember(tracks, albums) {
        tracks.groupBy { artistKey(it.song.primaryArtist) }.map { (key, t) ->
            val ids = t.map { it.albumId }.toSet()
            LocalArtist(key, t.first().song.primaryArtist.ifBlank { "Unknown artist" }, t, albums.filter { it.id in ids })
        }.sortedWith(compareBy<LocalArtist> { it.key == "￿" }.thenBy { it.name.lowercase().removePrefix("the ") })
    }
    val album = openAlbum?.let { id -> albums.firstOrNull { it.id == id } }
    val artist = openArtist?.let { k -> artists.firstOrNull { it.key == k } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(PrismIcons.ArrowBack, "Back") {
                when {
                    openAlbum != null -> openAlbum = null
                    openArtist != null -> openArtist = null
                    else -> nav.back()
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(album?.title ?: artist?.name ?: "Local files", style = pageTitleStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    album != null -> listOfNotNull(album.artist.ifBlank { null }, album.year.takeIf { it > 0 }?.toString(), songCount(album.tracks.size)).joinToString(" · ")
                    artist != null -> listOf(songCount(artist.tracks.size), if (artist.albums.size == 1) "1 album" else "${artist.albums.size} albums").joinToString(" · ")
                    allowed && tracks.isNotEmpty() -> "Music files on this phone"
                    else -> null
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        when {
            !allowed -> Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(PrismIcons.Phone, "Your music files", "Play the songs on your phone (MP3, M4A, FLAC, OGG, WAV…) in Prism. They stay where they are; Prism only reads them.")
                Button(onClick = { allow.launch(LocalLossless.permission) }) { Text("Allow access to music files") }
            }
            tracks.isEmpty() -> Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (scanned) EmptyState(PrismIcons.Phone, "No music files found", "Songs you copy onto your phone, or download with another app, show up here.")
                else CircularProgressIndicator(Modifier.padding(32.dp))
            }
            album != null -> TrackList(album.tracks.map { it.song }, album.title, bottomPadding, numbered = true)
            artist != null -> ArtistPage(artist, bottomPadding) { openAlbum = it.id }
            else -> {
                PagerChips(LocalTab.entries.map { it.label }, pager)
                // Sideways swipes on a song queue it (SwipeToQueue), so the sections change from the chips.
                HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = false) { page ->
                    when (LocalTab.entries[page]) {
                        LocalTab.SONGS -> SongsPage(tracks, sort, { sort = it }, bottomPadding)
                        LocalTab.ARTISTS -> ArtistsPage(artists, bottomPadding) { openArtist = it.key }
                        LocalTab.ALBUMS -> AlbumsPage(albums, bottomPadding) { openAlbum = it.id }
                    }
                }
            }
        }
    }
}

private fun source(title: String) = QueueSource(QueueKind.LIBRARY, title)

/** Play and Shuffle, for a list of songs. */
@Composable
private fun PlayChips(songs: List<Song>, title: String) {
    val c = LocalContainer.current
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrismChip(false, { c.player.playQueue(songs, 0, source(title)) }, "Play", icon = PrismIcons.Play)
        PrismChip(false, { c.player.playQueue(songs, 0, source(title), shuffle = true) }, "Shuffle", icon = PrismIcons.Shuffle)
    }
}

/** Songs that play as a list from the one tapped; swipe one to queue it. */
@Composable
private fun TrackList(songs: List<Song>, title: String, bottomPadding: Dp, numbered: Boolean = false) {
    val c = LocalContainer.current
    val current by c.player.currentSong.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 16.dp)) {
        item { PlayChips(songs, title) }
        itemsIndexed(songs, key = { i, s -> "t" + s.id + ":" + i }) { i, s ->
            SwipeToQueue(s) {
                SongRow(s, index = if (numbered) i + 1 else null, playing = current?.id == s.id) { c.player.playQueue(songs, i, source(title)) }
            }
        }
    }
}

@Composable
private fun SongsPage(tracks: List<LocalMusic.Track>, sort: LocalSort, onSort: (LocalSort) -> Unit, bottomPadding: Dp) {
    val c = LocalContainer.current
    val current by c.player.currentSong.collectAsState()
    val songs = remember(tracks, sort) {
        when (sort) {
            LocalSort.ADDED -> tracks
            LocalSort.TITLE -> tracks.sortedBy { it.song.title.lowercase() }
            LocalSort.ARTIST -> tracks.sortedWith(compareBy<LocalMusic.Track> { artistKey(it.song.primaryArtist) }
                .thenBy { it.song.album?.title?.lowercase() ?: "" }.thenBy { it.trackNumber })
            LocalSort.ALBUM -> tracks.sortedWith(compareBy<LocalMusic.Track> { it.song.album?.title?.lowercase() ?: "￿" }
                .thenBy { it.albumId }.thenBy { it.trackNumber }.thenBy { it.song.title.lowercase() })
        }.map { it.song }
    }
    val title = "Local files"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 16.dp)) {
        item {
            Column {
                SortBar(songCount(songs.size), LocalSort.entries, sort, { it.label }) { onSort(it) }
                PlayChips(songs, title)
            }
        }
        itemsIndexed(songs, key = { _, s -> "l" + s.id }) { i, s ->
            SwipeToQueue(s) {
                SongRow(s, playing = current?.id == s.id) { c.player.playQueue(songs, i, source(title)) }
            }
        }
    }
}

@Composable
private fun ArtistsPage(artists: List<LocalArtist>, bottomPadding: Dp, onOpen: (LocalArtist) -> Unit) {
    val ui = LocalUi.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 16.dp)) {
        item {
            Text(
                if (artists.size == 1) "1 artist" else "${artists.size} artists", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        items(artists, key = { "a" + it.key }) { a ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(a) }.padding(horizontal = 16.dp, vertical = ui.gap(7.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(a.art, Modifier.size(56.dp), CircleShape, size = 226, placeholderIcon = PrismIcons.Person)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(songCount(a.tracks.size), if (a.albums.size == 1) "1 album" else "${a.albums.size} albums").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsPage(albums: List<LocalAlbum>, bottomPadding: Dp, onOpen: (LocalAlbum) -> Unit) {
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomPadding + 16.dp),
    ) {
        items(albums, key = { "b" + it.id }) { a -> AlbumTile(a) { onOpen(a) } }
    }
}

@Composable
private fun AlbumTile(a: LocalAlbum, onClick: () -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.padding(4.dp).clip(ui.tile).clickable(onClick = onClick).padding(4.dp)) {
        Artwork(a.art, Modifier.fillMaxWidth().aspectRatio(1f), ui.art, size = 544, placeholderIcon = PrismIcons.Album)
        Spacer(Modifier.height(8.dp))
        Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(a.artist.ifBlank { null }, a.year.takeIf { it > 0 }?.toString()).joinToString(" · ").ifBlank { songCount(a.tracks.size) },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An artist's songs, album by album, each album opening on its own. */
@Composable
private fun ArtistPage(artist: LocalArtist, bottomPadding: Dp, onOpenAlbum: (LocalAlbum) -> Unit) {
    val c = LocalContainer.current
    val current by c.player.currentSong.collectAsState()
    // Their songs on each album (an album can be shared with other artists).
    val byAlbum = remember(artist) {
        val mine = artist.tracks.map { it.song.id }.toSet()
        artist.albums.map { a -> a to a.tracks.filter { it.song.id in mine }.map { it.song } }.filter { it.second.isNotEmpty() }
    }
    val all = remember(byAlbum) { byAlbum.flatMap { it.second } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 16.dp)) {
        item { PlayChips(all, artist.name) }
        byAlbum.forEach { (album, songs) ->
            item(key = "h" + album.id) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenAlbum(album) }.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(album.art, Modifier.size(44.dp), LocalUi.current.smallArt, size = 226, placeholderIcon = PrismIcons.Album)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(album.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        album.year.takeIf { it > 0 }?.let { Text("$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    androidx.compose.material3.Icon(PrismIcons.ChevronRight, "Open album", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(songs, key = { i, s -> "s" + album.id + ":" + s.id + ":" + i }) { _, s ->
                SwipeToQueue(s) {
                    SongRow(s, playing = current?.id == s.id) { c.player.playQueue(all, all.indexOf(s), source(artist.name)) }
                }
            }
        }
    }
}
