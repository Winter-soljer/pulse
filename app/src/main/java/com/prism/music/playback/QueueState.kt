package com.prism.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

enum class QueueKind { SINGLE, ALBUM, PLAYLIST, RADIO, LIBRARY, REPLAY, OTHER }

data class QueueSource(
    val kind: QueueKind,
    val title: String,
    /** Seed for autoplay / radio continuation. */
    val seedId: String? = null,
    /** The album, playlist or artist the queue was started from, so a play button for it can show pause. */
    val originId: String? = null,
) {
    /** A radio started from one song: it keeps going with similar music, Autoplay or not. */
    val isSongRadio: Boolean get() = kind == QueueKind.RADIO && seedId != null
}

/** Shared queue bookkeeping between the UI-facing [PlayerConnection] and [PlaybackService]. */
class QueueState {
    val source = MutableStateFlow(QueueSource(QueueKind.OTHER, ""))
    /** audio track id -> official music video id */
    val counterparts = ConcurrentHashMap<String, String>()
    /** ids with no music video available */
    val noVideo = ConcurrentHashMap.newKeySet<String>()
    @Volatile var autoplayContinuation: String? = null
    @Volatile var autoplayLoading = false
    /** Index in the queue where autoplay suggestions begin (-1 when none). */
    val autoplayStart = MutableStateFlow(-1)
    /** Songs the listener added themselves (Play next, Add to queue) and hasn't reached yet: "Next in queue". */
    val userQueued = MutableStateFlow<Set<String>>(emptySet())
    /** Playing along with a Listen Together host: their queue decides what's next (no autoplay or skipping here). */
    @Volatile var followingHost = false
}
