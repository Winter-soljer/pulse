package com.techfisher.pulsestream.data.nlp

typealias ParsedIntent = NaturalQueryIntent

data class NaturalQueryIntent(
    val originalQuery: String,
    val moods: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val eras: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val tempo: String? = null,
    val instrumentalOnly: Boolean = false,
    val acousticOnly: Boolean = false,
    val distilledKeywords: String = originalQuery
) {
    val mood: String? get() = moods.firstOrNull()
    val genre: String? get() = genres.firstOrNull()
    val era: String? get() = eras.firstOrNull()
    val activity: String? get() = activities.firstOrNull()
    val cleanKeywords: String get() = distilledKeywords

    val isNaturalSearch: Boolean
        get() = moods.isNotEmpty() || genres.isNotEmpty() || eras.isNotEmpty() || activities.isNotEmpty() || tempo != null || instrumentalOnly

    val summaryBadges: List<Pair<String, String>>
        get() = buildList {
            moods.forEach { add("Mood" to it) }
            genres.forEach { add("Genre" to it) }
            eras.forEach { add("Era" to it) }
            activities.forEach { add("Vibe" to it) }
            tempo?.let { add("Tempo" to it) }
            if (instrumentalOnly) add("Type" to "Instrumental")
            if (acousticOnly) add("Type" to "Acoustic")
        }
}

data class NaturalPrompt(
    val emoji: String,
    val title: String,
    val prompt: String,
    val targetQuery: String
)

object NaturalSearchEngine {

    val curatedPrompts = listOf(
        NaturalPrompt("⚡", "Workout Energy", "High bpm aggressive workout phonk and electronic music", "high energy workout phonk electronic"),
        NaturalPrompt("☕", "Cozy Coffeehouse", "Relaxing chill jazz coffee shop acoustic morning", "cozy coffeehouse acoustic jazz chill"),
        NaturalPrompt("🌙", "Late Night Chill", "Late night melancholy r&b soul dreamy indie", "late night melancholy dreamy indie r&b"),
        NaturalPrompt("🌴", "80s Retro Drive", "80s retro synthwave night driving electronic outrun", "80s synthwave retro outrun night drive"),
        NaturalPrompt("🧘", "Deep Focus", "Instrumental ambient lofi beats for intense study focus", "lofi beats instrumental ambient study focus"),
        NaturalPrompt("🌧️", "Rainy Day", "Soft emotional acoustic guitar indie folk rainy afternoon", "rainy day soft acoustic indie folk"),
        NaturalPrompt("🎸", "90s Alternative", "90s grunge alternative rock distorted guitars", "90s alternative rock grunge classics"),
        NaturalPrompt("✨", "Euphoric EDM", "Festival uplifting progressive house electronic dance", "uplifting progressive house edm festival")
    )

    private val moodKeywords = mapOf(
        "chill" to "Chill", "relax" to "Chill", "relaxing" to "Chill", "calm" to "Calm", "peaceful" to "Calm",
        "sad" to "Melancholy", "depressed" to "Melancholy", "crying" to "Melancholy", "melancholic" to "Melancholy", "heartbreak" to "Heartbreak",
        "happy" to "Joyful", "cheerful" to "Joyful", "upbeat" to "Uplifting", "uplifting" to "Uplifting", "euphoric" to "Euphoric",
        "aggressive" to "Aggressive", "hype" to "High Energy", "energetic" to "High Energy", "intense" to "Intense",
        "dark" to "Dark", "dreamy" to "Dreamy", "atmospheric" to "Atmospheric", "nostalgic" to "Nostalgic", "romantic" to "Romantic"
    )

    private val genreKeywords = mapOf(
        "synthwave" to "Synthwave", "retrowave" to "Retrowave", "vaporwave" to "Vaporwave",
        "lofi" to "Lofi", "lo-fi" to "Lofi", "hip hop" to "Hip Hop", "hiphop" to "Hip Hop", "rap" to "Rap", "trap" to "Trap",
        "rock" to "Rock", "metal" to "Metal", "grunge" to "Grunge", "punk" to "Punk", "indie" to "Indie",
        "jazz" to "Jazz", "blues" to "Blues", "soul" to "Soul", "r&b" to "R&B", "rnb" to "R&B",
        "pop" to "Pop", "folk" to "Folk", "acoustic" to "Acoustic", "classical" to "Classical",
        "techno" to "Techno", "house" to "House", "edm" to "EDM", "ambient" to "Ambient", "phonk" to "Phonk"
    )

    private val eraKeywords = mapOf(
        "80s" to "1980s", "eighties" to "1980s", "1980s" to "1980s",
        "90s" to "1990s", "nineties" to "1990s", "1990s" to "1990s",
        "70s" to "1970s", "seventies" to "1970s", "1970s" to "1970s",
        "2000s" to "2000s", "y2k" to "2000s", "classic" to "Classics", "vintage" to "Vintage"
    )

    private val activityKeywords = mapOf(
        "workout" to "Workout", "gym" to "Workout", "running" to "Running", "fitness" to "Workout",
        "study" to "Study", "studying" to "Study", "focus" to "Deep Focus", "coding" to "Coding", "work" to "Work",
        "sleep" to "Sleep", "sleeping" to "Sleep", "bedtime" to "Bedtime",
        "drive" to "Driving", "driving" to "Driving", "road trip" to "Road Trip",
        "party" to "Party", "gaming" to "Gaming", "meditation" to "Meditation",
        "rain" to "Rainy Day", "rainy" to "Rainy Day", "morning" to "Morning Coffee", "coffee" to "Coffeehouse"
    )

    private val fillerPatterns = listOf(
        Regex("(?i)\\b(music|songs?|tracks?|playlist|tunes?)\\b"),
        Regex("(?i)\\b(play|find|search|give me|show me|listen to|soundtrack|vibe)\\b"),
        Regex("(?i)\\b(for|with|about|like|to|in|of|a|an|the|and)\\b")
    )

    fun parse(rawQuery: String): NaturalQueryIntent {
        val lower = rawQuery.lowercase()
        val detectedMoods = mutableListOf<String>()
        val detectedGenres = mutableListOf<String>()
        val detectedEras = mutableListOf<String>()
        val detectedActivities = mutableListOf<String>()
        var detectedTempo: String? = null

        moodKeywords.forEach { (kw, label) ->
            if (lower.contains("\\b$kw\\b".toRegex()) && !detectedMoods.contains(label)) {
                detectedMoods.add(label)
            }
        }

        genreKeywords.forEach { (kw, label) ->
            if (lower.contains("\\b$kw\\b".toRegex()) && !detectedGenres.contains(label)) {
                detectedGenres.add(label)
            }
        }

        eraKeywords.forEach { (kw, label) ->
            if (lower.contains("\\b$kw\\b".toRegex()) && !detectedEras.contains(label)) {
                detectedEras.add(label)
            }
        }

        activityKeywords.forEach { (kw, label) ->
            if (lower.contains("\\b$kw\\b".toRegex()) && !detectedActivities.contains(label)) {
                detectedActivities.add(label)
            }
        }

        if (lower.contains("fast") || lower.contains("high bpm") || lower.contains("energetic") || lower.contains("speed")) {
            detectedTempo = "Fast / High BPM"
        } else if (lower.contains("slow") || lower.contains("mellow") || lower.contains("gentle") || lower.contains("low bpm")) {
            detectedTempo = "Slow / Mellow"
        }

        val instrumentalOnly = lower.contains("instrumental") || lower.contains("no lyrics") || lower.contains("without vocals")
        val acousticOnly = lower.contains("acoustic") || lower.contains("unplugged")

        var distilled = rawQuery
        fillerPatterns.forEach { pattern ->
            distilled = pattern.replace(distilled, " ")
        }
        val cleanTokens = distilled.split("\\s+".toRegex()).filter { it.length > 1 }
        val distilledKeywords = if (cleanTokens.isNotEmpty()) cleanTokens.joinToString(" ") else rawQuery.trim()

        return NaturalQueryIntent(
            originalQuery = rawQuery,
            moods = detectedMoods,
            genres = detectedGenres,
            eras = detectedEras,
            activities = detectedActivities,
            tempo = detectedTempo,
            instrumentalOnly = instrumentalOnly,
            acousticOnly = acousticOnly,
            distilledKeywords = distilledKeywords
        )
    }
}
