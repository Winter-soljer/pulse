package com.techfisher.pulsestream

import com.techfisher.pulsestream.data.ShareLinks
import com.techfisher.pulsestream.data.model.AlbumItem
import com.techfisher.pulsestream.data.model.ArtistItem
import com.techfisher.pulsestream.data.model.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Links Pulse shares, and the ones shared to it from other apps. */
class ShareLinksTest {
    @Test
    fun linksForWhatPulseShares() {
        assertEquals("https://winter-soljer.github.io/pulse/watch?v=abc", ShareLinks.song("abc"))
        assertEquals("https://winter-soljer.github.io/pulse/browse/MPREb_1", ShareLinks.of(AlbumItem("MPREb_1", "A", "", null)))
        assertEquals("https://winter-soljer.github.io/pulse/playlist?list=PL1", ShareLinks.of(PlaylistItem("VLPL1", "P", "", null)))
        assertEquals("https://winter-soljer.github.io/pulse/channel/UC1", ShareLinks.of(ArtistItem("UC1", "Artist", "", null)))
        // Mixes are made for whoever opens them: nothing to share.
        assertNull(ShareLinks.of(PlaylistItem("RDAMVMabc", "Mix", "", null, isMix = true)))
    }

    @Test
    fun linksSharedToPulse() {
        // YouTube links shared to Pulse are resolved to Pulse proprietary links
        assertEquals("https://winter-soljer.github.io/pulse/watch?v=dQw4w9WgXcQ", ShareLinks.findIn("Check this out https://youtu.be/dQw4w9WgXcQ?si=xyz"))
        assertEquals("https://winter-soljer.github.io/pulse/watch?v=dQw4w9WgXcQ", ShareLinks.findIn("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42"))
        assertEquals("https://winter-soljer.github.io/pulse/watch?v=abc123", ShareLinks.findIn("https://m.youtube.com/shorts/abc123"))
        assertEquals("https://winter-soljer.github.io/pulse/playlist?list=PLx", ShareLinks.findIn("(https://youtube.com/playlist?list=PLx)"))
        assertEquals("https://winter-soljer.github.io/pulse/channel/UCabc", ShareLinks.findIn("https://www.youtube.com/channel/UCabc"))
        // Pulse proprietary links shared to Pulse
        assertEquals("https://winter-soljer.github.io/pulse/watch?v=xyz789", ShareLinks.findIn("Listen on Pulse: https://winter-soljer.github.io/pulse/watch?v=xyz789"))
        assertEquals("pulse://watch?v=xyz789", ShareLinks.findIn("pulse://watch?v=xyz789"))
        // YouTube Music links
        assertEquals("https://winter-soljer.github.io/pulse/browse/MPREb_1", ShareLinks.findIn("Listen: https://music.youtube.com/browse/MPREb_1."))
        assertNull(ShareLinks.findIn("https://example.com/watch?v=abc"))
        assertNull(ShareLinks.findIn("no links here"))
        assertNull(ShareLinks.findIn(null))
    }
}
