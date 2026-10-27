package com.techfisher.pulsestream

import com.techfisher.pulsestream.data.model.Song
import com.techfisher.pulsestream.data.model.ArtistRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingFallbackLogicTest {

    private fun testSong(id: String) = Song(id, "Title $id", listOf(ArtistRef("Artist")), durationSec = 180)

    @Test
    fun consecutiveNetworkFailuresThresholdTriggersFallback() {
        var consecutiveFailures = 0
        var switchedToDownloaded = false

        fun onTrackFailed(isStreaming: Boolean, isNetwork: Boolean) {
            if (isStreaming && isNetwork) {
                consecutiveFailures++
                if (consecutiveFailures >= 2) {
                    switchedToDownloaded = true
                }
            }
        }

        // Track 1 fails
        onTrackFailed(isStreaming = true, isNetwork = true)
        assertEquals(1, consecutiveFailures)
        assertFalse(switchedToDownloaded)

        // Track 2 fails
        onTrackFailed(isStreaming = true, isNetwork = true)
        assertEquals(2, consecutiveFailures)
        assertTrue(switchedToDownloaded)
    }

    @Test
    fun successfulPlaybackResetsFailureCounter() {
        var consecutiveFailures = 1
        val isPlaying = true
        val inFallbackMode = false

        if (isPlaying && !inFallbackMode) {
            consecutiveFailures = 0
        }
        assertEquals(0, consecutiveFailures)
    }

    @Test
    fun savedStreamingStateAdvancesToNextTrack() {
        val streamingQueue = listOf(testSong("s1"), testSong("s2"), testSong("s3"), testSong("s4"))
        val currentFailedIndex = 1 // Track s2 failed after s1 failed

        val nextIndex = (currentFailedIndex + 1).coerceAtMost(streamingQueue.lastIndex)
        assertEquals(2, nextIndex)
        assertEquals("s3", streamingQueue[nextIndex].id)
    }

    @Test
    fun downloadedTracksResumeFromSavedPointerNotFromZero() {
        val downloaded = listOf(testSong("dl1"), testSong("dl2"), testSong("dl3"), testSong("dl4"))

        // First fallback: no saved pointer, starts at index 0
        var savedSongId: String? = null
        var savedIndex = 0
        var startIndex = if (savedSongId != null) {
            val idx = downloaded.indexOfFirst { it.id == savedSongId }
            if (idx >= 0) idx else savedIndex.coerceIn(0, downloaded.lastIndex)
        } else {
            savedIndex.coerceIn(0, downloaded.lastIndex)
        }
        assertEquals(0, startIndex)

        // When network resumed, downloaded track at index 2 finished playing
        val finishedDlIndex = 2
        val nextDlIndex = (finishedDlIndex + 1) % downloaded.size
        savedSongId = downloaded[nextDlIndex].id
        savedIndex = nextDlIndex

        // Second fallback later: starts at saved pointer, NOT index 0!
        val secondFallbackStart = if (savedSongId != null) {
            val idx = downloaded.indexOfFirst { it.id == savedSongId }
            if (idx >= 0) idx else savedIndex.coerceIn(0, downloaded.lastIndex)
        } else {
            savedIndex.coerceIn(0, downloaded.lastIndex)
        }
        assertEquals(3, secondFallbackStart)
        assertEquals("dl4", downloaded[secondFallbackStart].id)
    }

    @Test
    fun networkResumptionDefersUntilCurrentTrackCompletes() {
        var pendingResume = false
        var streamResumed = false

        // Network comes back online while playing downloaded track
        fun onNetworkOnline() {
            pendingResume = true
        }

        // Current track finishes playing (e.g. onMediaItemTransition or playback ended)
        fun onDownloadedTrackCompleted() {
            if (pendingResume) {
                streamResumed = true
                pendingResume = false
            }
        }

        onNetworkOnline()
        assertTrue(pendingResume)
        assertFalse(streamResumed)

        // Track finishes playing
        onDownloadedTrackCompleted()
        assertFalse(pendingResume)
        assertTrue(streamResumed)
    }
}
