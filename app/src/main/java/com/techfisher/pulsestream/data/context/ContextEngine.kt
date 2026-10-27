package com.techfisher.pulsestream.data.context

import com.techfisher.pulsestream.AppContainer
import com.techfisher.pulsestream.data.innertube.SearchFilter
import com.techfisher.pulsestream.data.model.Song
import com.techfisher.pulsestream.data.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class TimeSlot(val label: String) {
    EARLY_MORNING("Dawn"),
    MORNING("Morning"),
    AFTERNOON("Afternoon"),
    EVENING("Evening"),
    LATE_NIGHT("Late Night"),
}

enum class DayContext(val label: String) {
    WEEKDAY("Weekday Focus"),
    FRIDAY_NIGHT("Weekend Eve"),
    WEEKEND("Weekend Vibes"),
}

data class ContextVibe(
    val timeSlot: TimeSlot,
    val dayContext: DayContext,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val defaultQuery: String,
    val vibes: List<String>,
    val gradientColors: List<Long>,
)

object ContextEngine {

    fun getCurrentContext(calendar: Calendar = Calendar.getInstance()): ContextVibe {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 7=Saturday

        val dayContext = when {
            dayOfWeek == Calendar.FRIDAY && hour >= 17 -> DayContext.FRIDAY_NIGHT
            dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY -> DayContext.WEEKEND
            else -> DayContext.WEEKDAY
        }

        val timeSlot = when (hour) {
            in 4..7 -> TimeSlot.EARLY_MORNING
            in 8..11 -> TimeSlot.MORNING
            in 12..16 -> TimeSlot.AFTERNOON
            in 17..21 -> TimeSlot.EVENING
            else -> TimeSlot.LATE_NIGHT
        }

        return when {
            dayContext == DayContext.FRIDAY_NIGHT -> ContextVibe(
                timeSlot = timeSlot,
                dayContext = dayContext,
                title = "🎉 Friday Night Energy",
                subtitle = "Kick back or turn up for the weekend",
                emoji = "⚡",
                defaultQuery = "Friday night party dance hits",
                vibes = listOf("Dance", "Party", "Upbeat", "Pop", "Electronic"),
                gradientColors = listOf(0xFF8A2387, 0xFFE94057, 0xFFF27121),
            )
            dayContext == DayContext.WEEKEND && timeSlot == TimeSlot.MORNING -> ContextVibe(
                timeSlot = timeSlot,
                dayContext = dayContext,
                title = "☕ Weekend Morning Chill",
                subtitle = "Slow sips and acoustic melodies",
                emoji = "🥞",
                defaultQuery = "Acoustic coffeehouse morning chill",
                vibes = listOf("Acoustic", "Folk", "Indie", "Coffeehouse", "Soul"),
                gradientColors = listOf(0xFF3A6073, 0xFF3A7BD5),
            )
            dayContext == DayContext.WEEKEND && (timeSlot == TimeSlot.EVENING || timeSlot == TimeSlot.AFTERNOON) -> ContextVibe(
                timeSlot = timeSlot,
                dayContext = dayContext,
                title = "☀️ Weekend Adventure",
                subtitle = "Uplifting rhythms for your free days",
                emoji = "🏖️",
                defaultQuery = "Feel good upbeat indie pop roadtrip",
                vibes = listOf("Feel Good", "Roadtrip", "Indie Rock", "Summer"),
                gradientColors = listOf(0xFF11998E, 0xFF38EF7D),
            )
            timeSlot == TimeSlot.EARLY_MORNING -> ContextVibe(
                timeSlot = timeSlot,
                dayContext = dayContext,
                title = "🌅 Peaceful Sunrise",
                subtitle = "Gentle acoustic and morning ambient",
                emoji = "🌿",
                defaultQuery = "Morning sunrise peaceful acoustic ambient",
                vibes = listOf("Gentle", "Acoustic", "Ambient", "Nature", "Piano"),
                gradientColors = listOf(0xFFF3904F, 0xFF3B4371),
            )
            timeSlot == TimeSlot.MORNING -> ContextVibe(
                timeSlot = timeSlot,
                dayContext = dayContext,
                title = "🚀 Morning Drive & Focus",
                subtitle = "Clear head, high energy, ready to conquer",
                emoji = "☀️",
                defaultQuery = "Morning motivation deep focus upbeat",
                vibes = listOf("Motivation", "Focus", "Commute", "Pop", "Lo-Fi"),
                gradientColors = listOf(0xFF4A00E0, 0xFF8E2DE2),
            )
            timeSlot == TimeSlot.AFTERNOON -> ContextVibe(
                timeSlot = timeSlot,
                dayContext = dayContext,
                title = "⚡ Midday Flow",
                subtitle = "Keep your momentum going through the day",
                emoji = "🎧",
                defaultQuery = "Deep work flow electronic lofi study",
                vibes = listOf("Deep Work", "Lofi", "Instrumental", "Electronic"),
                gradientColors = listOf(0xFF0052D4, 0xFF4364F7, 0xFF6FB1FC),
            )
            timeSlot == TimeSlot.EVENING -> ContextVibe(
                timeSlot = timeSlot,
                dayContext = dayContext,
                title = "🌆 Evening Sunset Unwind",
                subtitle = "Mellow grooves as the sun goes down",
                emoji = "🍷",
                defaultQuery = "Sunset chill indie r&b neo soul",
                vibes = listOf("Chill", "Neo Soul", "Indie", "Sunset", "R&B"),
                gradientColors = listOf(0xFF2C3E50, 0xFFFD746C),
            )
            else -> ContextVibe(
                timeSlot = timeSlot,
                dayContext = dayContext,
                title = "🌙 Midnight Lofi & Dreams",
                subtitle = "Deep nocturnal calm and ambient textures",
                emoji = "🌌",
                defaultQuery = "Late night lofi sleep ambient dream pop",
                vibes = listOf("Midnight", "Lofi Beats", "Dream Pop", "Ambient", "Sleep"),
                gradientColors = listOf(0xFF0F2027, 0xFF203A43, 0xFF2C5364),
            )
        }
    }

    suspend fun generateContextMix(context: ContextVibe, c: AppContainer): List<Song> = withContext(Dispatchers.IO) {
        val liked = c.library.liked.value
        val recent = c.library.recent.value
        val heard = (recent.take(30).map { it.id } + liked.map { it.id }).toSet()

        // 1. Search YouTube Music for query matching current context
        val searchResults: List<Song> = runCatching {
            c.ytm.search(context.defaultQuery, filter = SearchFilter.SONGS).items
                .filterIsInstance<SongItem>()
                .map { it.song }
        }.getOrDefault(emptyList())

        val candidateSongs = mutableListOf<Song>()
        candidateSongs.addAll(searchResults)

        // 2. Expand via radio on the best match seed
        val seed = searchResults.firstOrNull() ?: liked.firstOrNull()
        if (seed != null) {
            val radioTracks = runCatching {
                c.ytm.next(seed.id, playlistId = "RDAMVM${seed.id}").songs
            }.getOrDefault(emptyList())
            candidateSongs.addAll(radioTracks)
        }

        // 3. Interleave matching user favorites if appropriate
        val matchingLiked = liked.filter { song ->
            val text = "${song.title} ${song.primaryArtist} ${song.album?.title.orEmpty()}".lowercase()
            context.vibes.any { v -> text.contains(v.lowercase()) }
        }

        val result = mutableListOf<Song>()
        var likedIdx = 0
        for (song in candidateSongs.distinctBy { it.id }) {
            if (likedIdx < matchingLiked.size && result.size % 4 == 0) {
                result.add(matchingLiked[likedIdx++])
            }
            result.add(song)
            if (result.size >= 25) break
        }

        if (result.isEmpty()) {
            searchResults.take(20)
        } else {
            result.distinctBy { it.id }
        }
    }
}
