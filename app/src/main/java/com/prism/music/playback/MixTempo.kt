package com.prism.music.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.prism.music.data.stream.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * What Mix knows about a song's rhythm and edges, all in ms from the song's start.
 *
 * @property bpm tempo, as heard (an octave can be off: 85 for a 170 BPM song); Mix compares tempos octave-free.
 * @property confidence how clearly the beat stood out (a z-score of the tempo's autocorrelation peak); ambient
 *   songs and spoken intros score low, and Mix then blends without lining beats up.
 * @property firstSoundMs where the music starts (leading silence ends).
 * @property lastSoundMs where the music ends (trailing silence or a fade-out's tail begins).
 * @property firstBeatMs the first beat at or after [firstSoundMs].
 * @property outroBeatMs a beat close to [lastSoundMs]; the song's last bars lie on `outroBeatMs + k × periodMs`.
 */
data class Tempo(
    val bpm: Double,
    val confidence: Double,
    val firstSoundMs: Long,
    val lastSoundMs: Long,
    val firstBeatMs: Long,
    val outroBeatMs: Long,
) {
    val periodMs: Double get() = 60_000.0 / bpm
    /** Clear enough to line beats up on. */
    val steady: Boolean get() = confidence >= MIN_CONFIDENCE

    fun encode() = "$VERSION|$bpm|$confidence|$firstSoundMs|$lastSoundMs|$firstBeatMs|$outroBeatMs"

    companion object {
        const val MIN_CONFIDENCE = 3.0
        /** Bumped when [analyze] changes, so songs measured the old way are measured again. */
        private const val VERSION = 2

        fun decode(s: String): Tempo? = runCatching {
            val p = s.split('|')
            if (p[0].toInt() != VERSION) return null
            Tempo(p[1].toDouble(), p[2].toDouble(), p[3].toLong(), p[4].toLong(), p[5].toLong(), p[6].toLong())
        }.getOrNull()

        /**
         * Reads tempo and edges from a song's band energies ([VideoSync.bandFeatures]: log energy per band, one frame
         * every [frameMs]). Onsets are rises in band energy; the tempo is the autocorrelation lag (60–200 BPM) that
         * stands out most, weighted toward 120 BPM so a song's half or double time isn't picked over it; beats are
         * placed by sliding that period over the onsets near each end of the song. [offsetMs] is added to every time
         * read: a frame's energy is its whole window's, so half a window puts each frame at its window's middle.
         */
        fun analyze(bands: Array<FloatArray>, frameMs: Double, offsetMs: Double = 0.0): Tempo? {
            val frames = bands.firstOrNull()?.size ?: return null
            if (frames < 3000 / frameMs) return null
            val fps = 1000.0 / frameMs

            // Loudness per frame (all bands together), for the music's start and end.
            val loud = FloatArray(frames) { t ->
                var m = Float.NEGATIVE_INFINITY
                for (b in bands) m = max(m, b[t])
                var s = 0.0
                for (b in bands) s += exp((b[t] - m).toDouble())
                (m + ln(s)).toFloat()
            }
            val sorted = loud.sortedArray()
            val peak = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.lastIndex)]
            val floor = peak - SILENCE_BELOW_PEAK
            val smooth = 5
            fun audible(t: Int): Boolean {
                var n = 0
                for (i in t until min(frames, t + smooth)) if (loud[i] > floor) n++
                return n >= smooth - 1
            }
            val first = (0 until frames).firstOrNull(::audible) ?: return null
            val last = (frames - 1 downTo 0).firstOrNull(::audible) ?: return null
            if (last - first < 10 * fps) return null

            // Onset strength: summed rises in band energy, minus its local average, kept positive.
            val flux = FloatArray(frames)
            for (t in 1 until frames) {
                var s = 0f
                for (b in bands) { val d = b[t] - b[t - 1]; if (d > 0) s += d }
                flux[t] = s
            }
            val half = (0.2 * fps).toInt().coerceAtLeast(1)
            val pre = DoubleArray(frames + 1).also { for (i in 0 until frames) it[i + 1] = it[i] + flux[i] }
            val od = FloatArray(frames) { t ->
                val lo = max(0, t - half); val hi = min(frames, t + half + 1)
                max(0.0, flux[t] - (pre[hi] - pre[lo]) / (hi - lo)).toFloat()
            }

            // Tempo: autocorrelation over the music (up to two minutes of it, from its middle).
            val span = min(last - first, (120 * fps).toInt())
            val from = first + ((last - first) - span) / 2
            val to = from + span
            val lagMin = (fps * 60 / 200).toInt()
            val lagMax = (fps * 60 / 60).toInt() + 1
            // Autocorrelation out to four beats of the slowest tempo, so every candidate gets its multiples' support.
            val acfMax = lagMax * 4
            if (to - from <= acfMax * 2) return null
            val acf = DoubleArray(acfMax + 1)
            for (lag in lagMin..acfMax) {
                var s = 0.0
                for (t in from until to - lag) s += od[t].toDouble() * od[t + lag]
                acf[lag] = s / (to - from - lag)
            }
            fun weighted(lag: Int): Double {
                val bpm = 60 * fps / lag
                val prior = exp(-0.5 * sq(ln(bpm / 120.0) / ln(2.0) / 0.9))
                // A beat also lines up with itself two and four beats on (a bar), which a 3:2 or 4:3 cousin of
                // the tempo (a triplet feel, a syncopated hook) doesn't: those multiples back the true period.
                return (acf[lag] + 0.5 * acf[2 * lag] + 0.25 * acf[4 * lag]) * prior
            }
            val scores = (lagMin..lagMax).map { weighted(it) }
            val bestIdx = scores.indices.maxBy { scores[it] }
            val best = lagMin + bestIdx
            val mean = scores.average()
            val sd = sqrt(scores.sumOf { sq(it - mean) } / scores.size).takeIf { it > 1e-12 } ?: return null
            val confidence = (scores[bestIdx] - mean) / sd
            // Sub-frame period from a parabola through the peak and its neighbours.
            var lag = if (best in lagMin + 1 until lagMax) {
                val a = acf[best - 1]; val b = acf[best]; val c = acf[best + 1]
                val d = a - 2 * b + c
                best + (if (d < 0) (0.5 * (a - c) / d).coerceIn(-0.5, 0.5) else 0.0)
            } else best.toDouble()

            /** Onset strength landing on a comb of beats [period] apart, starting at [start], up to frame [b]. */
            fun comb(start: Double, b: Int, period: Double): Double {
                var s = 0.0
                var t = start
                while (t < b - 1) { s += interp(od, t); t += period }
                return s
            }

            /** The first beat in [a, b) of the comb that lands on the most onset strength. */
            fun phase(a: Int, b: Int, period: Double = lag): Double {
                var bestPhase = 0.0; var bestScore = -1.0
                var ph = 0.0
                while (ph < period) {
                    val s = comb(a + ph, b, period)
                    if (s > bestScore) { bestScore = s; bestPhase = ph }
                    ph += 0.5
                }
                return a + bestPhase
            }

            // Fast tempos are often a song's eighth notes (kick on the beat, hi-hat between). If every other "beat"
            // is clearly stronger, the song is at half that tempo and the strong ones are its beats; otherwise
            // (drum & bass: kick and snare both hit hard) the fast tempo stands.
            if (60 * fps / lag > 140 && 60 * fps / (2 * lag) >= 70) {
                val strong = phase(from, to, 2 * lag)
                val a = comb(strong, to, 2 * lag)
                val b = comb(strong + lag, to, 2 * lag)
                if (a > 1.25 * b) lag *= 2
            }
            // Fine tempo: the autocorrelation's peak is good to about a frame; a beat comb laid over the whole
            // stretch drifts off the onsets with even a tiny error, so the period whose best comb scores highest
            // is the exact one. (A 1% error is a beat's worth of drift over a minute, and a blend leans on the
            // ending song's beats being where its last bars put them.)
            run {
                var bestP = lag; var bestS = -1.0
                var p = lag * 0.985
                while (p <= lag * 1.015) {
                    var ph = 0.0
                    while (ph < p) {
                        val s = comb(from + ph, to, p)
                        if (s > bestS) { bestS = s; bestP = p }
                        ph += 1.0
                    }
                    p += lag * 0.0005
                }
                lag = bestP
            }
            val bpm = 60 * fps / lag

            val window = (20 * fps).toInt()
            val introAnchor = phase(first, min(last, first + window))
            // First beat at or after the music starts (a hair early still counts: onsets lead the peak slightly).
            var firstBeat = introAnchor
            while (firstBeat - lag >= first - 3) firstBeat -= lag
            while (firstBeat < first - 3) firstBeat += lag
            val outroFrom = max(first, last - window)
            val outroAnchor = phase(outroFrom, last + 1)
            var outroBeat = outroAnchor
            while (outroBeat + lag <= last) outroBeat += lag

            return Tempo(
                bpm = bpm,
                confidence = confidence,
                firstSoundMs = (first * frameMs + offsetMs).toLong(),
                lastSoundMs = (last * frameMs + offsetMs).toLong(),
                firstBeatMs = (firstBeat * frameMs + offsetMs).toLong(),
                outroBeatMs = (outroBeat * frameMs + offsetMs).toLong(),
            )
        }

        /** Below the song's loud parts by this much (natural log of power: 40 dB), it counts as silence. */
        private const val SILENCE_BELOW_PEAK = 9.2f

        private fun sq(x: Double) = x * x

        private fun interp(x: FloatArray, t: Double): Double {
            val i = floor(t).toInt()
            if (i < 0 || i + 1 >= x.size) return 0.0
            val f = t - i
            return x[i] * (1 - f) + x[i + 1] * f
        }
    }
}

/**
 * Measures songs' tempo for Mix: the song's smallest audio stream is fetched whole (1–4 MB, only on Wi-Fi), decoded
 * to mono ~11 kHz, and read by [Tempo.analyze]. Results are kept per song (`mix_tempo` prefs), so each song is
 * measured once.
 */
class MixTempo(private val context: Context, private val http: OkHttpClient, private val streams: StreamResolver) {
    private val prefs = context.getSharedPreferences("mix_tempo", Context.MODE_PRIVATE)
    private val failed = ConcurrentHashMap.newKeySet<String>()
    /** One song at a time: decoding a whole song is the heaviest thing Prism does in the background. */
    private val lock = Mutex()

    fun cached(id: String): Tempo? = prefs.getString(id, null)?.let(Tempo::decode)

    /** The song's tempo, measured now if it isn't known yet; null when it can't be (offline, no clear audio). */
    suspend fun measure(id: String): Tempo? {
        cached(id)?.let { return it }
        if (id in failed) return null
        return lock.withLock {
            cached(id) ?: withContext(Dispatchers.IO) {
                Log.d(TAG, "measuring the tempo of $id")
                val started = System.nanoTime()
                val t = runCatching { analyzeSong(id) }.onFailure { Log.w(TAG, "tempo of $id failed: $it") }.getOrNull()
                if (t == null) failed += id else {
                    prefs.edit().putString(id, t.encode()).apply()
                    Log.i(TAG, "tempo $id: %.1f BPM (confidence %.1f), music %d–%d ms, first beat %d ms, in %d ms"
                        .format(t.bpm, t.confidence, t.firstSoundMs, t.lastSoundMs, t.firstBeatMs, (System.nanoTime() - started) / 1_000_000))
                }
                t
            }
        }
    }

    private fun analyzeSong(id: String): Tempo? {
        val a = streams.analysisAudio(id)
        val file = File(context.cacheDir, "mix_$id.part")
        try {
            http.newCall(Request.Builder().url(a.url).apply {
                if (a.contentLength > 0) header("Range", "bytes=0-${a.contentLength - 1}")
            }.build()).execute().use { r ->
                if (!r.isSuccessful) { Log.w(TAG, "tempo of $id: audio returned HTTP ${r.code}"); return null }
                file.outputStream().use { out -> r.body.byteStream().copyTo(out) }
            }
            val (pcm, rate) = decodeAll(file) ?: run { Log.w(TAG, "tempo of $id: couldn't decode ${file.length()} bytes"); return null }
            // Debug builds keep the decoded audio when asked (adb shell touch …/files/mix_dump), for tuning [Tempo.analyze].
            if (com.prism.music.BuildConfig.DEBUG) context.getExternalFilesDir(null)?.let { dir ->
                if (File(dir, "mix_dump").exists()) runCatching {
                    val bb = java.nio.ByteBuffer.allocate(pcm.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                    for (x in pcm) bb.putFloat(x)
                    File(dir, "mix_${id}_$rate.f32").writeBytes(bb.array())
                }
            }
            val bands = VideoSync.bandFeatures(pcm, rate)
            val frameMs = (rate / 100) * 1000.0 / rate
            return Tempo.analyze(bands, frameMs, offsetMs = VideoSync.WINDOW / 2 * 1000.0 / rate)
                ?: run { Log.w(TAG, "tempo of $id: no clear music in ${pcm.size / rate} s of audio"); null }
        } finally {
            file.delete()
        }
    }

    /** The whole file as mono PCM, decimated to roughly 11 kHz. */
    private fun decodeAll(file: File): Pair<FloatArray, Int>? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(file.path)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!).also { codec = it }
            dec.configure(fmt, null, null, 0)
            dec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false
            var factor = max(1, rate / 11025)
            var out = FloatArray(1 shl 20)
            var count = 0
            var acc = 0f
            var n = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val i = dec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val size = runCatching { ex.readSampleData(dec.getInputBuffer(i)!!, 0) }.getOrDefault(-1)
                        if (size < 0) {
                            dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(i, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 5_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = dec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    if (count == 0) factor = max(1, rate / 11025)
                } else if (o >= 0) {
                    val buf = dec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val frames = info.size / (channels * (if (isFloat) 4 else 2))
                    for (f in 0 until frames) {
                        var m = 0f
                        for (c in 0 until channels) m += if (isFloat) buf.float else buf.short / 32768f
                        acc += m / channels
                        if (++n == factor) {
                            if (count == out.size) out = out.copyOf(out.size * 2)
                            out[count++] = acc / factor
                            acc = 0f; n = 0
                        }
                    }
                    dec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    if (count > MAX_SECONDS * (rate / factor)) break
                } else if (inputDone && o == MediaCodec.INFO_TRY_AGAIN_LATER && count > 0) {
                    break
                }
            }
            val sr = rate / factor
            if (count < sr * 20) return null
            return out.copyOf(count) to sr
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }

    companion object {
        private const val TAG = "PrismMix"
        /** Longer than this (a DJ set, an hour-long mix) isn't measured past this point. */
        private const val MAX_SECONDS = 15 * 60
    }
}
