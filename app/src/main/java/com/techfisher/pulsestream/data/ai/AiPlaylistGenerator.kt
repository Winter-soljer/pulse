package com.techfisher.pulsestream.data.ai

import com.techfisher.pulsestream.AppContainer
import com.techfisher.pulsestream.data.innertube.SearchFilter
import com.techfisher.pulsestream.data.model.Song
import com.techfisher.pulsestream.data.model.SongItem
import com.techfisher.pulsestream.data.nlp.NaturalSearchEngine
import com.techfisher.pulsestream.data.nlp.ParsedIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class GeneratedPlaylist(
    val title: String,
    val description: String,
    val prompt: String,
    val songs: List<Song>,
    val intent: ParsedIntent,
)

class AiPlaylistGenerator(private val container: AppContainer) {

    /**
     * Generates a cohesive playlist based on a natural language prompt, mood, or vibe.
     */
    suspend fun generate(prompt: String, count: Int = 25): GeneratedPlaylist = withContext(Dispatchers.IO) {
        val intent = NaturalSearchEngine.parse(prompt)

        // Formulate target search terms
        val searchTerms = mutableListOf<String>()
        val primaryQuery = intent.cleanKeywords.ifBlank { prompt }
        searchTerms += primaryQuery

        val genre = intent.genre
        val mood = intent.mood
        val activity = intent.activity
        val era = intent.era

        if (genre != null && mood != null) {
            searchTerms += "$mood $genre"
        }
        if (activity != null) {
            searchTerms += "$activity music"
        }
        if (era != null) {
            searchTerms += "$era hits"
        }

        val collectedSongs = mutableListOf<Song>()
        val seenIds = mutableSetOf<String>()

        // 1. Gather songs from YTM search for the primary and enriched terms
        coroutineScope {
            val searchJobs = searchTerms.distinct().take(3).map { term ->
                async {
                    runCatching {
                        container.ytm.search(term, SearchFilter.SONGS).items
                            .filterIsInstance<SongItem>()
                            .map { it.song }
                    }.getOrDefault(emptyList())
                }
            }

            searchJobs.forEach { job ->
                val songs = job.await()
                for (s in songs) {
                    if (s.id !in seenIds) {
                        seenIds += s.id
                        collectedSongs += s
                    }
                }
            }
        }

        // 2. Expand with radio tracks from the top 2 seed songs for natural flow and depth
        val seedSongs = collectedSongs.take(2)
        if (seedSongs.isNotEmpty() && collectedSongs.size < count * 2) {
            coroutineScope {
                val radioJobs = seedSongs.map { seed ->
                    async {
                        runCatching {
                            container.ytm.next(videoId = seed.id).songs
                        }.getOrDefault(emptyList())
                    }
                }
                radioJobs.forEach { job ->
                    val radioTracks = job.await()
                    for (s in radioTracks) {
                        if (s.id !in seenIds && !s.isLive && !s.isEpisode) {
                            seenIds += s.id
                            collectedSongs += s
                        }
                    }
                }
            }
        }

        // 3. Blend in matching tracks from user's liked library for personal touch
        runCatching {
            val liked = container.db.songs().liked()
            val queryLower = prompt.lowercase()
            val intentGenre = intent.genre?.lowercase()
            val intentMood = intent.mood?.lowercase()
            val matchedLiked = liked.filter { entity ->
                val title = entity.title.lowercase()
                val artist = entity.artistName.lowercase()
                (intentGenre != null && (title.contains(intentGenre) || artist.contains(intentGenre))) ||
                (intentMood != null && (title.contains(intentMood) || artist.contains(intentMood))) ||
                queryLower.split(" ").any { w -> w.length > 3 && (title.contains(w) || artist.contains(w)) }
            }.map { it.toSong() }

            for (s in matchedLiked.shuffled().take(5)) {
                if (s.id !in seenIds) {
                    seenIds += s.id
                    collectedSongs.add(0, s) // prioritize familiar favorite tracks
                }
            }
        }

        // 4. Filter and cap
        val finalTracks = collectedSongs
            .distinctBy { it.id }
            .take(count)

        // 5. Generate title & description
        val title = generateTitle(prompt, intent)
        val description = generateDescription(prompt, intent, finalTracks)

        GeneratedPlaylist(
            title = title,
            description = description,
            prompt = prompt,
            songs = finalTracks,
            intent = intent,
        )
    }

    /**
     * Saves the generated playlist to YouTube Music.
     */
    suspend fun savePlaylist(
        playlist: GeneratedPlaylist,
        privacy: String = "PRIVATE",
    ): String? = withContext(Dispatchers.IO) {
        val videoIds = playlist.songs.map { it.id }.filter { !it.startsWith(Song.LOCAL_PREFIX) }
        val id = container.ytm.createPlaylist(
            title = playlist.title,
            videoIds = videoIds,
            description = playlist.description,
            privacy = privacy,
        )
        if (id != null) {
            container.library.refreshCollections()
        }
        id
    }

    private fun generateTitle(prompt: String, intent: ParsedIntent): String {
        val parts = mutableListOf<String>()
        intent.mood?.let { parts += it.replaceFirstChar { c -> c.uppercase() } }
        intent.genre?.let { parts += it.replaceFirstChar { c -> c.uppercase() } }
        if (parts.size < 2) intent.activity?.let { parts += it.replaceFirstChar { c -> c.uppercase() } }
        if (parts.size < 2) intent.era?.let { parts += it }

        return if (parts.isNotEmpty()) {
            val base = parts.joinToString(" ")
            if (base.contains("Mix", ignoreCase = true) || base.contains("Vibe", ignoreCase = true)) {
                base
            } else {
                "$base Mix"
            }
        } else {
            prompt.trim().take(30).replaceFirstChar { it.uppercase() } + " Mix"
        }
    }

    private fun generateDescription(prompt: String, intent: ParsedIntent, songs: List<Song>): String {
        val keyArtists = songs.map { it.primaryArtist }.filter { it.isNotBlank() }.distinct().take(3).joinToString(", ")
        val tags = mutableListOf<String>()
        intent.mood?.let { tags += "#${it.replace(" ", "")}" }
        intent.genre?.let { tags += "#${it.replace(" ", "")}" }
        intent.activity?.let { tags += "#${it.replace(" ", "")}" }

        val tagLine = if (tags.isNotEmpty()) " " + tags.joinToString(" ") else ""
        return if (keyArtists.isNotBlank()) {
            "AI crafted for \"$prompt\" • Featuring $keyArtists and more.$tagLine"
        } else {
            "AI generated playlist for \"$prompt\".$tagLine"
        }
    }
}
