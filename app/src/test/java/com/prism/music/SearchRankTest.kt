package com.prism.music

import com.prism.music.data.innertube.SearchFilter
import com.prism.music.data.innertube.SearchRank
import com.prism.music.data.model.AlbumItem
import com.prism.music.data.model.ArtistItem
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankTest {
    private val ep = AlbumItem("MPREb_ep", "A Day", "EP • Polar Colors • 2021", null)
    private val adtr = ArtistItem("UCadtr", "A Day to Remember", "Artist • 2.1M monthly audience", null)
    private val song = SongItem(Song("vid1", "A Day", listOf(ArtistRef("Someone Else"))))
    private val results = listOf(
        SearchFilter.SONGS to listOf(song),
        SearchFilter.ALBUMS to listOf(ep),
        SearchFilter.ARTISTS to listOf(adtr),
    )

    private val fan = SearchRank.Taste(listOf(SearchRank.KnownArtist("UCadtr", "A Day to Remember", null, 0.6)), emptyMap())

    @Test fun exactMatchWinsWithoutTaste() {
        assertEquals("MPREb_ep", SearchRank.rank("A Day", results).top?.id)
    }

    @Test fun anArtistTheListenerPlaysComesFirst() {
        assertEquals("UCadtr", SearchRank.rank("A Day", results, fan).top?.id)
    }

    @Test fun aKnownArtistMissingFromResultsIsAdded() {
        val r = SearchRank.rank("a day", results.filter { it.first != SearchFilter.ARTISTS }, fan)
        assertEquals("UCadtr", r.top?.id)
    }

    @Test fun partialWordsDontBorrowTaste() {
        // "halo" is a prefix of Halocene, not a word of it: the exact song still wins.
        val halo = SongItem(Song("halo", "Halo", listOf(ArtistRef("Beyoncé"))))
        val halocene = ArtistItem("UChalocene", "Halocene", "Artist • 300K monthly audience", null)
        val taste = SearchRank.Taste(listOf(SearchRank.KnownArtist("UChalocene", "Halocene", null, 0.6)), emptyMap())
        val r = SearchRank.rank("halo", listOf(SearchFilter.SONGS to listOf(halo), SearchFilter.ARTISTS to listOf(halocene)), taste)
        assertEquals("halo", r.top?.id)
        assertTrue(SearchRank.knownArtists("halo", taste).isEmpty())
    }

    @Test fun liveStreamsAlwaysGetARowButNotTheTop() {
        val stream = SongItem(Song("lofi", "lofi hip hop radio", listOf(ArtistRef("Lofi Girl")), isVideo = true, isLive = true))
        val r = SearchRank.rank("A Day", results + (SearchFilter.LIVE to listOf(stream)))
        assertEquals("MPREb_ep", r.top?.id)
        assertEquals(SearchFilter.LIVE, r.sections[1].first)
        // With nothing else found, a stream can lead.
        assertEquals("lofi", SearchRank.rank("lofi", listOf(SearchFilter.LIVE to listOf(stream))).top?.id)
    }

    @Test fun aTitleTrackGivesWayToItsAlbum() {
        val track = Song("ah", "After Hours", listOf(ArtistRef("The Weeknd")), album = com.prism.music.data.model.AlbumRef("After Hours", "MPREb_ah"))
        assertTrue(SearchRank.namesItsAlbum("after hours", track))
        assertTrue(!SearchRank.namesItsAlbum("blinding lights", track.copy(title = "Blinding Lights")))
        val r = SearchRank.rank("after hours", listOf(SearchFilter.SONGS to listOf(SongItem(track))))
        val album = AlbumItem("MPREb_ah", "After Hours", "Album • The Weeknd • 2020", null)
        val moved = SearchRank.withAlbumOnTop(r, album)
        assertEquals("MPREb_ah", moved.top?.id)
        assertEquals("ah", moved.sections.first { it.first == SearchFilter.SONGS }.second.first().id)
    }
}
