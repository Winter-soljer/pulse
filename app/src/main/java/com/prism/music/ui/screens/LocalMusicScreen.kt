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
import com.prism.music.data.prefs.LocalSection
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

/** The Songs section's orders: Album plays each album through in track order. */
private enum class LocalSort(val label: String) { TITLE("Title"), ADDED("Recently added"), ARTIST("Artist"), ALBUM("Album") }

/** One album among the files (by Android's album id), its songs in track order. */
private class LocalAlbum(val id: Long, val title: String, val artist: String, val year: Int, val tracks: List<LocalMusic.Track>) {
    val art: String? get() = tracks.firstNotNullOfOrNull { it.song.thumbnail }
    /** When its newest file arrived on the phone. */
    val addedAt: Long get() = tracks.maxOf { it.addedAt }
}

private fun sorted(tracks: List<LocalMusic.Track>, sort: LocalSort): List<LocalMusic.Track> = when (sort) {
    LocalSort.ADDED -> tracks.sortedByDescending { it.addedAt }
    LocalSort.TITLE -> tracks.sortedBy { it.song.title.lowercase() }
    LocalSort.ARTIST -> tracks.sortedWith(compareBy<LocalMusic.Track> { artistKey(it.song.primaryArtist) }
        .thenBy { it.song.album?.title?.lowercase() ?: "" }.thenBy { it.trackNumber })
    LocalSort.ALBUM -> tracks.sortedWith(compareBy<LocalMusic.Track> { it.song.album?.title?.lowercase() ?: "￿" }
        .thenBy { it.albumId }.thenBy { it.trackNumber }.thenBy { it.song.title.lowercase() })
}

/** How many of the newest files and albums "Recently added" and "Added albums" show. */
private const val ADDED_SONGS = 100
private const val ADDED_ALBUMS = 40

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
    val settings = com.prism.music.ui.theme.LocalAppSettings.current
    val sort = LocalSort.entries.firstOrNull { it.name == settings.localSort } ?: LocalSort.TITLE
    val sections = settings.localSections
    val pager = rememberPagerState { sections.size }
    var openArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var openAlbum by rememberSaveable { mutableStateOf<Long?>(null) }
    var openRecent by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    BackHandler(openArtist != null || openAlbum != null || openRecent) {
        // An album opened from an artist goes back to the artist.
        when {
            openAlbum != null -> openAlbum = null
            openArtist != null -> openArtist = null
            else -> openRecent = false
        }
    }
    val liked by c.library.likedIds.collectAsState()
    val recentIds by c.library.recentLocalIds.collectAsState()
    val dls by c.downloads.downloads.collectAsState()

    val albums = remember(tracks) { albumsOf(tracks) }
    val artists = remember(tracks, albums) {
        tracks.groupBy { artistKey(it.song.primaryArtist) }.map { (key, t) ->
            val ids = t.map { it.albumId }.toSet()
            LocalArtist(key, t.first().song.primaryArtist.ifBlank { "Unknown artist" }, t, albums.filter { it.id in ids })
        }.sortedWith(compareBy<LocalArtist> { it.key == "￿" }.thenBy { it.name.lowercase().removePrefix("the ") })
    }
    val album = openAlbum?.let { id -> albums.firstOrNull { it.id == id } }
    val artist = openArtist?.let { k -> artists.firstOrNull { it.key == k } }

    // What each section lists (and what the Play / Shuffle buttons at the top play).
    val songsSorted = remember(tracks, sort) { sorted(tracks, sort).map { it.song } }
    val favourites = remember(tracks, liked, sort) { sorted(tracks.filter { it.song.id in liked }, sort).map { it.song } }
    val addedSongs = remember(tracks) { tracks.sortedByDescending { it.addedAt }.take(ADDED_SONGS).map { it.song } }
    val addedAlbums = remember(albums) { albums.sortedByDescending { it.addedAt }.take(ADDED_ALBUMS) }
    val downloads = remember(dls) { c.downloads.completedSongs() }
    val recent = remember(tracks, recentIds) {
        val byId = tracks.associateBy { it.song.id }
        recentIds.mapNotNull { byId[it]?.song }
    }
    val section = sections.getOrNull(pager.currentPage)
    fun playable(): Pair<List<Song>, String> = when {
        album != null -> album.tracks.map { it.song } to album.title
        artist != null -> artist.albums.flatMap { a -> a.tracks.filter { t -> t in artist.tracks } }.map { it.song } to artist.name
        openRecent -> recent to "Recently played"
        else -> when (section) {
            LocalSection.FAVOURITES -> favourites to "Favourites"
            LocalSection.ADDED_SONGS -> addedSongs to "Recently added"
            LocalSection.ADDED_ALBUMS -> addedAlbums.flatMap { a -> a.tracks.map { it.song } } to "Added albums"
            LocalSection.DOWNLOADS -> downloads to "Downloads"
            else -> songsSorted to "Local files"
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // As a tab of its own (Settings → Layout & navigation) the top level has nothing to go back to.
            val isTab = com.prism.music.data.prefs.NavTab.LOCAL in settings.navTabs
            val inside = openAlbum != null || openArtist != null || openRecent
            if (!isTab || inside) {
                RoundAction(PrismIcons.ArrowBack, "Back") {
                    when {
                        openAlbum != null -> openAlbum = null
                        openArtist != null -> openArtist = null
                        openRecent -> openRecent = false
                        else -> nav.back()
                    }
                }
                Spacer(Modifier.width(14.dp))
            } else Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(album?.title ?: artist?.name ?: (if (openRecent) "Recently played" else "Local files"), style = pageTitleStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // The count sits under the title (#29), where "Music files on this phone" was.
                val sub = when {
                    album != null -> listOfNotNull(album.artist.ifBlank { null }, album.year.takeIf { it > 0 }?.toString(), songCount(album.tracks.size)).joinToString(" · ")
                    artist != null -> listOf(songCount(artist.tracks.size), if (artist.albums.size == 1) "1 album" else "${artist.albums.size} albums").joinToString(" · ")
                    openRecent -> songCount(recent.size)
                    allowed && tracks.isNotEmpty() -> listOf(songCount(tracks.size), if (albums.size == 1) "1 album" else "${albums.size} albums").joinToString(" · ")
                    else -> null
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Recently played, Shuffle and Play at the top (#29), for whatever the page is showing.
            if (allowed && tracks.isNotEmpty()) {
                if (!inside) {
                    RoundAction(PrismIcons.History, "Recently played") { openRecent = true }
                    Spacer(Modifier.width(8.dp))
                }
                RoundAction(PrismIcons.Shuffle, "Shuffle") {
                    val (list, title) = playable()
                    if (list.isNotEmpty()) c.player.playQueue(list, 0, source(title), shuffle = true)
                }
                Spacer(Modifier.width(8.dp))
                RoundAction(PrismIcons.Play, "Play") {
                    val (list, title) = playable()
                    if (list.isNotEmpty()) c.player.playQueue(list, 0, source(title))
                }
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
            openRecent -> if (recent.isEmpty()) EmptyState(PrismIcons.History, "Nothing played yet", "Songs from your phone that you play show up here.")
                else TrackList(recent, "Recently played", bottomPadding)
            else -> {
                SectionChips(sections, pager) { editing = true }
                // Sideways swipes on a song queue it (SwipeToQueue), so the sections change from the chips.
                HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = false) { page ->
                    when (sections.getOrNull(page)) {
                        LocalSection.SONGS -> SongsPage(songsSorted, sort, { c.settings.setLocalSort(it.name) }, "Local files", bottomPadding)
                        LocalSection.FAVOURITES -> if (favourites.isEmpty()) EmptyState(PrismIcons.Heart, "No favourites yet", "Like songs from your phone (♥ on the player or in a song's menu) and they gather here.")
                            else SongsPage(favourites, sort, { c.settings.setLocalSort(it.name) }, "Favourites", bottomPadding)
                        LocalSection.ARTISTS -> ArtistsPage(artists, bottomPadding) { openArtist = it.key }
                        LocalSection.ALBUMS -> AlbumsPage(albums, bottomPadding) { openAlbum = it.id }
                        LocalSection.ADDED_SONGS -> TrackList(addedSongs, "Recently added", bottomPadding)
                        LocalSection.ADDED_ALBUMS -> AlbumsPage(addedAlbums, bottomPadding) { openAlbum = it.id }
                        LocalSection.DOWNLOADS -> if (downloads.isEmpty()) EmptyState(PrismIcons.DownloadDone, "No downloads yet", "Songs you download from YouTube Music show up here too.")
                            else TrackList(downloads, "Downloads", bottomPadding)
                        null -> {}
                    }
                }
            }
        }
    }
    if (editing) SectionsEditor(sections) { editing = false }
}

/** The section chips, then an Edit chip that opens [SectionsEditor]. */
@Composable
private fun SectionChips(sections: List<LocalSection>, pager: androidx.compose.foundation.pager.PagerState, onEdit: () -> Unit) {
    val scope = rememberCoroutineScope()
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sections.size) { i ->
            PrismChip(pager.currentPage == i, { scope.launch { pager.animateScrollToPage(i) } }, sections[i].label)
        }
        item { PrismChip(false, onEdit, "Edit", icon = PrismIcons.Customize) }
    }
}

/** Which sections Local files shows, and in what order (#29). At least one stays. */
@Composable
private fun SectionsEditor(sections: List<LocalSection>, onDismiss: () -> Unit) {
    val prefs = LocalContainer.current.settings
    com.prism.music.ui.components.PrismSheet(onDismiss = onDismiss) {
        Text("Sections", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            "Tick the sections you want and use the arrows to order them.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        )
        val ordered = sections + LocalSection.entries.filter { it !in sections }
        ordered.forEach { s ->
            val on = s in sections
            val i = sections.indexOf(s)
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(on, { v -> prefs.setLocalSections(if (v) sections + s else sections - s) }, enabled = !on || sections.size > 1)
                Text(s.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (on) {
                    androidx.compose.material3.IconButton(onClick = { prefs.setLocalSections(sections.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                        androidx.compose.material3.Icon(PrismIcons.ChevronUp, "Move up")
                    }
                    androidx.compose.material3.IconButton(onClick = { prefs.setLocalSections(sections.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < sections.lastIndex) {
                        androidx.compose.material3.Icon(PrismIcons.ChevronDown, "Move down")
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun source(title: String) = QueueSource(QueueKind.LIBRARY, title)

/** Songs that play as a list from the one tapped; swipe one to queue it. Play and Shuffle are at the top of the page. */
@Composable
private fun TrackList(songs: List<Song>, title: String, bottomPadding: Dp, numbered: Boolean = false) {
    val c = LocalContainer.current
    val current by c.player.currentSong.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 16.dp)) {
        itemsIndexed(songs, key = { i, s -> "t" + s.id + ":" + i }) { i, s ->
            SwipeToQueue(s) {
                SongRow(s, index = if (numbered) i + 1 else null, playing = current?.id == s.id) { c.player.playQueue(songs, i, source(title)) }
            }
        }
    }
}

/** Songs (or Favourites) in the chosen order; the sort is kept (Title unless changed). */
@Composable
private fun SongsPage(songs: List<Song>, sort: LocalSort, onSort: (LocalSort) -> Unit, title: String, bottomPadding: Dp) {
    val c = LocalContainer.current
    val current by c.player.currentSong.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 0.dp, bottom = bottomPadding + 16.dp)) {
        item {
            // The count is under the page title now; this row only holds the order.
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                com.prism.music.ui.components.SortButton(LocalSort.entries, sort, { it.label }) { onSort(it) }
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
