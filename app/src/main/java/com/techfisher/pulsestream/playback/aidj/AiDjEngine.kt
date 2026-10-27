package com.techfisher.pulsestream.playback.aidj

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.exoplayer.ExoPlayer
import com.techfisher.pulsestream.data.context.ContextEngine
import com.techfisher.pulsestream.data.model.AlbumRef
import com.techfisher.pulsestream.data.model.ArtistRef
import com.techfisher.pulsestream.data.model.Song
import com.techfisher.pulsestream.data.prefs.AppSettings
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

/**
 * Intelligent AI DJ Voice Assistant (Level 29).
 * Dynamically provides voiceovers, transitions, and contextual banter between songs,
 * automatically ducking ExoPlayer volume during utterances.
 */
class AiDjEngine(
    private val context: Context,
    private val getSettings: () -> AppSettings
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var player: ExoPlayer? = null
    private var songCounter = 0
    private var isDucked = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            // TTS engine initialization failed or not available on device
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { t ->
                val result = t.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t.language = Locale.US
                }
                t.setPitch(1.0f)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                t.setAudioAttributes(audioAttributes)
                setupProgressListener(t)
                isTtsReady = true
            }
        }
    }

    private fun setupProgressListener(textToSpeech: TextToSpeech) {
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                duckVolume(true)
            }

            override fun onDone(utteranceId: String?) {
                duckVolume(false)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                duckVolume(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                duckVolume(false)
            }
        })
    }

    fun setPlayer(exoPlayer: ExoPlayer?) {
        this.player = exoPlayer
        if (exoPlayer == null && isDucked) {
            duckVolume(false)
        }
    }

    private fun duckVolume(duck: Boolean) {
        mainHandler.post {
            val p = player ?: return@post
            if (duck) {
                isDucked = true
                p.volume = 0.22f
            } else {
                isDucked = false
                p.volume = 1.0f
            }
        }
    }

    /**
     * Called when the active track changes in PlayerConnection.
     */
    fun onSongChanged(currentSong: Song, upcomingSong: Song?) {
        val settings = getSettings()
        if (!settings.aiDj || !isTtsReady) return

        songCounter++
        val frequency = settings.aiDjFrequency.coerceAtLeast(1)

        if (songCounter >= frequency) {
            songCounter = 0
            // Slight delay so the intro of the track is playing when DJ speaks
            mainHandler.postDelayed({
                if (getSettings().aiDj && player?.isPlaying == true) {
                    val script = generateDjScript(currentSong, upcomingSong)
                    speak(script)
                }
            }, 1200L)
        }
    }

    /**
     * Generates an energetic, context-aware radio DJ voiceover.
     */
    fun generateDjScript(currentSong: Song, upcomingSong: Song?): String {
        val contextVibe = ContextEngine.getCurrentContext()
        val timeGreeting = when (contextVibe.timeSlot) {
            com.techfisher.pulsestream.data.context.TimeSlot.EARLY_MORNING -> "Rise and shine."
            com.techfisher.pulsestream.data.context.TimeSlot.MORNING -> "Good morning, music lovers."
            com.techfisher.pulsestream.data.context.TimeSlot.AFTERNOON -> "Hope your afternoon is treating you right."
            com.techfisher.pulsestream.data.context.TimeSlot.EVENING -> "Good evening."
            com.techfisher.pulsestream.data.context.TimeSlot.LATE_NIGHT -> "Late night frequencies on Pulse."
        }

        val templatesWithUpcoming = listOf(
            "$timeGreeting You're locked into Pulse AI DJ. We just queued up ${currentSong.title} by ${currentSong.artistText}. Coming up down the road, look out for ${upcomingSong?.title}.",
            "This is Pulse DJ in the mix. Enjoying ${currentSong.title}? Up next, we've got ${upcomingSong?.title} by ${upcomingSong?.artistText} lined up for you.",
            "$timeGreeting Keep the volume right here. Spinning ${currentSong.title} by ${currentSong.artistText}. Up next, get ready for ${upcomingSong?.title}.",
            "Pulse AI DJ spinning the vibes. Right now it's ${currentSong.title}. Don't touch that dial, ${upcomingSong?.title} is right around the corner."
        )

        val templatesSolo = listOf(
            "$timeGreeting You're listening to Pulse AI DJ. Here is ${currentSong.title} by ${currentSong.artistText}. Enjoy the sound.",
            "Pulse DJ keeping the vibe alive. Tuning into ${currentSong.title} by ${currentSong.artistText}. Turn it up!",
            "$timeGreeting Pulse AI DJ here with you. Turn up ${currentSong.title} by ${currentSong.artistText}."
        )

        return if (upcomingSong != null) {
            templatesWithUpcoming[Random.nextInt(templatesWithUpcoming.size)]
        } else {
            templatesSolo[Random.nextInt(templatesSolo.size)]
        }
    }

    /**
     * Preview button handler in Settings.
     */
    fun preview(sampleSong: Song? = null) {
        val song = sampleSong ?: Song(
            id = "preview_track",
            title = "Midnight Horizon",
            artists = listOf(ArtistRef(name = "Solar Drift")),
            album = AlbumRef(title = "Neon Skies"),
            durationSec = 210
        )
        val sampleUpcoming = Song(
            id = "preview_upcoming",
            title = "Electric Wave",
            artists = listOf(ArtistRef(name = "Cyber Echo")),
            album = AlbumRef(title = "Synthetica"),
            durationSec = 195
        )
        val script = generateDjScript(song, sampleUpcoming)
        speak(script)
    }

    private fun speak(text: String) {
        if (!isTtsReady) return
        val settings = getSettings()
        tts?.setSpeechRate(settings.aiDjVoiceRate.coerceIn(0.5f, 2.0f))
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        if (isDucked) {
            duckVolume(false)
        }
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        player = null
    }
}
