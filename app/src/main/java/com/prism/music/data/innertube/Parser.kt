package com.prism.music.data.innertube

import com.prism.music.data.model.AlbumItem
import com.prism.music.data.model.AlbumRef
import com.prism.music.data.model.ArtistItem
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.BrowseItem
import com.prism.music.data.model.Episode
import com.prism.music.data.model.MoodItem
import com.prism.music.data.model.PlaylistItem
import com.prism.music.data.model.PodcastItem
import com.prism.music.data.model.Shelf
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import com.prism.music.data.model.parseDuration
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Converts YouTube Music renderer objects into app models. */
object Parser {
    private const val ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    private const val CHANNEL = "MUSIC_PAGE_TYPE_USER_CHANNEL"
    private const val ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    private const val PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
    /** Artists in the signed-in library: browse id "MPLA" + the channel id. */
    private const val LIBRARY_ARTIST = "MUSIC_PAGE_TYPE_LIBRARY_ARTIST"
    /** A podcast show: browse id "MPSP" + its playlist id. */
    private const val PODCAST = "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"
    private const val EPISODE_VIDEO = "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"
    private val typeLabels = setOf(
        "Song", "Video", "Album", "Single", "EP", "Artist", "Playlist", "Episode", "Podcast", "Profile", "Station",
    )
    private val durationRegex = Regex("^\\d{1,2}(:\\d{2}){1,2}$")
    private val playsRegex = Regex("^([\\d.,]+)\\s*([KMB])?\\s+plays?$", RegexOption.IGNORE_CASE)

    /** "54M plays" -> 54,000,000; null if [text] isn't a play count. */
    fun parsePlays(text: String): Long? {
        val m = playsRegex.matchEntire(text.trim()) ?: return null
        val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val mult = when (m.groupValues[2].uppercase()) { "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
        return (n * mult).toLong()
    }

    /** A list row's play count (album tracks and top songs show one), if it has one. */
    fun playCount(r: JsonObject): Long? =
        r.arr("flexColumns")?.drop(1)?.firstNotNullOfOrNull { col ->
            parsePlays(col.obj("musicResponsiveListItemFlexColumnRenderer", "text").runs().joinToString("") { it.str("text") ?: "" })
        }

    private fun pageType(nav: JsonElement?): String? =
        nav.str("browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")

    private fun videoType(endpoint: JsonElement?): String? =
        endpoint.str("watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig", "musicVideoType")

    private fun isSeparator(t: String) = t.isBlank() || t.trim() == "•" || t.trim() == "&" || t.trim() == ","

    fun artistsFromRuns(runs: List<JsonObject>): List<ArtistRef> {
        val linked = runs.filter { pageType(it["navigationEndpoint"]) in setOf(ARTIST, CHANNEL) }.map {
            ArtistRef(it.str("text") ?: "", it.str("navigationEndpoint", "browseEndpoint", "browseId"))
        }
        if (linked.isNotEmpty()) return linked
        // Unlinked: "Artist • Album • 3:10" or "Song • Artist • 3:10"
        val segments = runs.joinToString("") { it.str("text") ?: "" }.split(" • ").map { it.trim() }
        val name = segments.firstOrNull { it.isNotBlank() && it !in typeLabels && !durationRegex.matches(it) && !it.contains(" views") && !playsRegex.matches(it) }
        return if (name != null) name.split(", ", " & ").map { ArtistRef(it) } else emptyList()
    }

    private fun albumFromRuns(runs: List<JsonObject>): AlbumRef? =
        runs.firstOrNull { pageType(it["navigationEndpoint"]) == ALBUM }?.let {
            AlbumRef(it.str("text") ?: "", it.str("navigationEndpoint", "browseEndpoint", "browseId"))
        }

    fun parseItem(wrapper: JsonElement?): BrowseItem? {
        wrapper.obj("musicResponsiveListItemRenderer")?.let { return responsive(it) }
        wrapper.obj("musicTwoRowItemRenderer")?.let { return twoRow(it) }
        wrapper.obj("musicNavigationButtonRenderer")?.let { return mood(it) }
        wrapper.obj("musicMultiRowListItemRenderer")?.let { return episode(it)?.song?.let(::SongItem) }
        wrapper.obj("playlistPanelVideoRenderer")?.let { return panelVideo(it)?.let(::SongItem) }
        return null
    }

    private fun mood(r: JsonObject): MoodItem? {
        val title = r.obj("buttonText").text() ?: return null
        val id = r.str("clickCommand", "browseEndpoint", "browseId") ?: return null
        val color = r.str("solid", "leftStripeColor")?.toLongOrNull() ?: 0xFF7C5CFF
        return MoodItem(id, title, r.str("clickCommand", "browseEndpoint", "params"), color)
    }

    fun responsive(r: JsonObject): BrowseItem? {
        val cols = r.arr("flexColumns")?.map { it.obj("musicResponsiveListItemFlexColumnRenderer", "text").runs() }
            ?: return null
        val title = cols.getOrNull(0)?.joinToString("") { it.str("text") ?: "" }?.takeIf { it.isNotBlank() } ?: return null
        val thumb = r.obj("thumbnail").bestThumbnail()
        val watch = r.obj("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint")
            ?: cols[0].firstOrNull()?.obj("navigationEndpoint", "watchEndpoint")
        val videoId = r.str("playlistItemData", "videoId") ?: watch.str("videoId")
        val restRuns = cols.drop(1).flatten()
        val browse = r.obj("navigationEndpoint")

        // A podcast episode (search's Episodes): "Sep 30 • The Show", the show linked.
        if (videoId != null && videoType(watch) == EPISODE_VIDEO) {
            val show = restRuns.firstOrNull { pageType(it["navigationEndpoint"]) == PODCAST }
            return SongItem(
                Song(
                    id = videoId, title = title,
                    artists = listOfNotNull(show?.str("text")?.let { ArtistRef(it) }),
                    thumbnail = thumb, isEpisode = true,
                    podcastId = show?.str("navigationEndpoint", "browseEndpoint", "browseId"),
                )
            )
        }
        if (videoId != null && pageType(browse) == null) {
            val duration = r.arr("fixedColumns")?.firstOrNull()
                ?.obj("musicResponsiveListItemFixedColumnRenderer", "text").text()
                ?: restRuns.map { it.str("text") ?: "" }.lastOrNull { durationRegex.matches(it.trim()) }
            val vt = videoType(watch)
            val explicit = r.arr("badges")?.any {
                it.str("musicInlineBadgeRenderer", "icon", "iconType") == "MUSIC_EXPLICIT_BADGE"
            } == true
            return SongItem(
                Song(
                    id = videoId,
                    title = title,
                    artists = artistsFromRuns(cols.getOrNull(1) ?: emptyList()),
                    album = albumFromRuns(restRuns),
                    durationSec = parseDuration(duration),
                    thumbnail = thumb,
                    isVideo = vt != null && vt != "MUSIC_VIDEO_TYPE_ATV",
                    explicit = explicit,
                )
            )
        }
        val browseId = browse.str("browseEndpoint", "browseId") ?: return null
        val subtitle = restRuns.joinToString("") { it.str("text") ?: "" }
        return when (pageType(browse)) {
            ALBUM -> AlbumItem(browseId, title, subtitle, thumb)
            ARTIST, CHANNEL -> ArtistItem(browseId, title, subtitle, thumb)
            LIBRARY_ARTIST -> ArtistItem(browseId.removePrefix("MPLA"), title, subtitle, thumb)
            PLAYLIST -> PlaylistItem(browseId.removePrefix("VL"), title, subtitle, thumb)
            PODCAST -> PodcastItem(browseId, title, subtitle, thumb)
            else -> null
        }
    }

    /**
     * A podcast episode row (a show's page, "Popular episodes"): the title, the show it's from
     * ("secondTitle", linked), "663K views • 1d ago", its length ("2 hr 54 min") and description.
     */
    fun episode(r: JsonObject, show: PodcastItem? = null): Episode? {
        val watch = r.obj("onTap", "watchEndpoint")
            ?: r.obj("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint")
        val id = watch.str("videoId") ?: return null
        val title = r.obj("title").text() ?: return null
        val showRun = r.obj("secondTitle").runs().firstOrNull { pageType(it["navigationEndpoint"]) == PODCAST }
        val progress = r.obj("playbackProgress", "musicPlaybackProgressRenderer")
        val subtitle = r.obj("subtitle").runs().map { it.str("text") ?: "" }.filter { !isSeparator(it) }
        return Episode(
            song = Song(
                id = id,
                title = title,
                artists = listOfNotNull((showRun?.str("text") ?: show?.title)?.let { ArtistRef(it) }),
                durationSec = parseDuration(progress.obj("durationText").runs().lastOrNull().str("text")),
                thumbnail = r.obj("thumbnail").bestThumbnail() ?: show?.thumbnail,
                isEpisode = true,
                podcastId = showRun?.str("navigationEndpoint", "browseEndpoint", "browseId") ?: show?.id,
            ),
            // The date, without the view count.
            date = subtitle.lastOrNull { !it.contains("view", ignoreCase = true) },
            description = r.obj("description").runs().joinToString("") { it.str("text") ?: "" }.takeIf { it.isNotBlank() },
            progress = (progress.str("playbackProgressPercentage")?.toFloatOrNull() ?: 0f) / 100f,
            played = progress.obj("playedText") != null && (progress.str("playbackProgressPercentage")?.toFloatOrNull() ?: 0f) >= 99f,
        )
    }

    /**
     * The "Top result" card of YouTube Music's unfiltered search (`musicCardShelfRenderer`): an artist, album,
     * playlist or song. It's YouTube's own best guess, good at abbreviations and typos ("adtr", "taylor swfit").
     */
    fun card(r: JsonObject): BrowseItem? {
        val titleRun = r.obj("title").runs().firstOrNull() ?: return null
        val title = r.obj("title").text()?.takeIf { it.isNotBlank() } ?: return null
        val subtitleRuns = r.obj("subtitle").runs()
        val subtitle = subtitleRuns.joinToString("") { it.str("text") ?: "" }
        val thumb = r.obj("thumbnail").bestThumbnail()
        val nav = titleRun.obj("navigationEndpoint") ?: r.obj("onTap")
        nav.obj("watchEndpoint")?.let { watch ->
            val id = watch.str("videoId") ?: return null
            val vt = videoType(watch)
            if (vt == EPISODE_VIDEO) return null
            return SongItem(
                Song(
                    id = id, title = title,
                    artists = artistsFromRuns(subtitleRuns),
                    album = albumFromRuns(subtitleRuns),
                    durationSec = parseDuration(subtitleRuns.map { it.str("text") ?: "" }.lastOrNull { durationRegex.matches(it.trim()) }),
                    thumbnail = thumb,
                    isVideo = vt != null && vt != "MUSIC_VIDEO_TYPE_ATV",
                )
            )
        }
        val browseId = nav.str("browseEndpoint", "browseId") ?: return null
        return when (pageType(nav)) {
            ALBUM -> AlbumItem(browseId, title, subtitle, thumb)
            ARTIST, CHANNEL -> ArtistItem(browseId, title, subtitle, thumb)
            PLAYLIST -> PlaylistItem(browseId.removePrefix("VL"), title, subtitle, thumb)
            else -> null
        }
    }

    fun twoRow(r: JsonObject): BrowseItem? {
        val title = r.obj("title").text() ?: return null
        val subtitleRuns = r.obj("subtitle").runs()
        val subtitle = subtitleRuns.joinToString("") { it.str("text") ?: "" }
        val thumb = r.obj("thumbnailRenderer").bestThumbnail()
        val nav = r.obj("navigationEndpoint")
        nav.obj("watchEndpoint")?.let { w ->
            val id = w.str("videoId")
            val pid = w.str("playlistId")
            // A station ("Station • Bring Me The Horizon, Oasis…"): a radio seeded by a song, not the song itself.
            // Song cards carry their own radio ("RDAMVM" + the song) or album; anything else that's a radio is the station.
            val kind = subtitle.substringBefore(" • ").trim()
            val station = pid != null && (
                id == null || kind.equals("Station", true) || kind.equals("Mix", true) ||
                    (pid.startsWith("RD") && !pid.startsWith("RDAMVM"))
                )
            if (pid != null && station) {
                return PlaylistItem(pid, title, subtitle, thumb, isMix = true, seedVideoId = id, params = w.str("params"))
            }
            id ?: return null
            val vt = videoType(w)
            if (vt == EPISODE_VIDEO) {
                val show = subtitleRuns.firstOrNull { pageType(it["navigationEndpoint"]) == PODCAST }
                return SongItem(
                    Song(
                        id = id, title = title, artists = listOfNotNull(show?.str("text")?.let { ArtistRef(it) }),
                        thumbnail = thumb, isEpisode = true, podcastId = show?.str("navigationEndpoint", "browseEndpoint", "browseId"),
                    )
                )
            }
            return SongItem(
                Song(
                    id = id, title = title, artists = artistsFromRuns(subtitleRuns),
                    album = albumFromRuns(subtitleRuns), thumbnail = thumb,
                    isVideo = vt != null && vt != "MUSIC_VIDEO_TYPE_ATV",
                )
            )
        }
        nav.obj("watchPlaylistEndpoint")?.let { w ->
            val pid = w.str("playlistId") ?: return null
            return PlaylistItem(pid, title, subtitle, thumb, isMix = true, params = w.str("params"))
        }
        val browseId = nav.str("browseEndpoint", "browseId") ?: return null
        return when (pageType(nav)) {
            ALBUM -> AlbumItem(
                browseId, title, subtitle, thumb,
                playlistId = r.str("thumbnailOverlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchPlaylistEndpoint", "playlistId"),
            )
            ARTIST, CHANNEL -> ArtistItem(browseId, title, subtitle, thumb)
            LIBRARY_ARTIST -> ArtistItem(browseId.removePrefix("MPLA"), title, subtitle, thumb)
            PODCAST -> PodcastItem(browseId, title, subtitle, thumb)
            else -> if (browseId.startsWith("VL") || browseId.startsWith("RD") || browseId.startsWith("PL")) {
                PlaylistItem(browseId.removePrefix("VL"), title, subtitle, thumb)
            } else null
        }
    }

    fun panelVideo(r: JsonObject): Song? {
        val id = r.str("videoId") ?: return null
        val byline = r.obj("longBylineText").runs()
        val vt = videoType(r.obj("navigationEndpoint", "watchEndpoint"))
        return Song(
            id = id,
            title = r.obj("title").text() ?: return null,
            artists = artistsFromRuns(byline),
            album = albumFromRuns(byline),
            durationSec = parseDuration(r.obj("lengthText").text()),
            thumbnail = r.obj("thumbnail").bestThumbnail(),
            isVideo = vt != null && vt != "MUSIC_VIDEO_TYPE_ATV",
        )
    }

    /** All carousel / list / grid shelves found in a browse response. */
    fun shelves(root: JsonElement): List<Shelf> {
        val out = mutableListOf<Shelf>()
        val sections = root.findAll("sectionListRenderer").flatMap { it.arr("contents") ?: emptyList() } +
            root.findAll("sectionListContinuation").flatMap { it.arr("contents") ?: emptyList() }
        val nodes = if (sections.isNotEmpty()) sections else listOf(root)
        for (node in nodes) {
            node.obj("musicCarouselShelfRenderer")?.let { s ->
                val header = s.obj("header", "musicCarouselShelfBasicHeaderRenderer")
                val title = header.obj("title").text() ?: return@let
                val items = s.arr("contents")?.mapNotNull { parseItem(it) } ?: emptyList()
                if (items.isNotEmpty()) out += Shelf(
                    title = title,
                    strapline = header.obj("strapline").text(),
                    items = items,
                    moreBrowseId = header.str("moreContentButton", "buttonRenderer", "navigationEndpoint", "browseEndpoint", "browseId")
                        ?: header.obj("title").runs().firstOrNull().str("navigationEndpoint", "browseEndpoint", "browseId"),
                    moreParams = header.str("moreContentButton", "buttonRenderer", "navigationEndpoint", "browseEndpoint", "params"),
                    rows = s.str("numItemsPerColumn") != null || s.arr("contents")?.firstOrNull().obj("musicResponsiveListItemRenderer") != null,
                )
            }
            node.obj("musicShelfRenderer")?.let { s ->
                val items = s.arr("contents")?.mapNotNull { parseItem(it) } ?: emptyList()
                if (items.isNotEmpty()) out += Shelf(
                    title = s.obj("title").text() ?: "",
                    items = items,
                    moreBrowseId = s.str("bottomEndpoint", "browseEndpoint", "browseId")
                        ?: s.obj("title").runs().firstOrNull().str("navigationEndpoint", "browseEndpoint", "browseId"),
                    moreParams = s.str("bottomEndpoint", "browseEndpoint", "params"),
                )
            }
            node.obj("gridRenderer")?.let { g ->
                val items = g.arr("items")?.mapNotNull { parseItem(it) } ?: emptyList()
                if (items.isNotEmpty()) out += Shelf(
                    title = g.obj("header", "gridHeaderRenderer", "title").text() ?: "",
                    items = items,
                    grid = true,
                )
            }
        }
        return out
    }

    fun continuation(root: JsonElement?): String? =
        root.findFirst("nextContinuationData").str("continuation")
            ?: root.findFirst("nextRadioContinuationData").str("continuation")
            ?: root.findFirst("continuationCommand").str("token")
}
