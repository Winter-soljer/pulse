package com.prism.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.prism.music.MainActivity
import com.prism.music.R
import com.prism.music.container
import com.prism.music.data.innertube.InnerTube
import com.prism.music.data.model.Song
import com.prism.music.data.model.hiRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The home screen widgets' looks; each is its own widget in the launcher's list. */
enum class WidgetStyle(val layout: Int, val provider: Class<out AppWidgetProvider>, val artCorner: Float) {
    /** Prism's own: the cover as frosted glass behind it. */
    PRISM(R.layout.widget_prism, PrismWidget::class.java, 0.16f),
    /** After Spotify's: a slim near-black bar with a green play button. */
    SPOTIFY(R.layout.widget_spotify, SpotifyStyleWidget::class.java, 0.08f),
    /** After Apple Music's: a big cover on a card the colour of the cover. */
    APPLE(R.layout.widget_apple, AppleStyleWidget::class.java, 0.11f),
    /** After YouTube Music's: dark grey, thumbs up, a red progress line. */
    YTMUSIC(R.layout.widget_ytmusic, YtMusicStyleWidget::class.java, 0.11f),
}

abstract class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) = Widgets.refresh(context)
}

class PrismWidget : NowPlayingWidget()
class SpotifyStyleWidget : NowPlayingWidget()
class AppleStyleWidget : NowPlayingWidget()
class YtMusicStyleWidget : NowPlayingWidget()

/** The widgets' buttons. Tapping one counts as using Prism, so playback may start from here. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.container
        when (intent.action) {
            Widgets.ACTION_PLAY -> c.player.togglePlay()
            Widgets.ACTION_NEXT -> c.player.next()
            Widgets.ACTION_PREVIOUS -> c.player.previous()
            Widgets.ACTION_LIKE -> (c.player.currentSong.value ?: Widgets.lastSong(context))?.let { song ->
                c.library.toggleLike(song)
                // The heart shows the change at once; the service confirms it when the like lands.
                Widgets.update(context, song, c.player.isPlaying.value, song.id !in c.library.likedIds.value, null, null)
            }
        }
    }
}

/**
 * Draws every placed widget from what's playing. The playback service pushes changes ([update]): a new song,
 * play/pause, a like, and the position every 15 s while playing. The last song is kept so widgets still show it
 * after a restart; tapping play then picks the saved queue back up.
 */
object Widgets {
    const val ACTION_PLAY = "com.prism.music.widget.PLAY"
    const val ACTION_NEXT = "com.prism.music.widget.NEXT"
    const val ACTION_PREVIOUS = "com.prism.music.widget.PREVIOUS"
    const val ACTION_LIKE = "com.prism.music.widget.LIKE"

    private class State(val song: Song?, val playing: Boolean, val liked: Boolean, val positionMs: Long, val durationMs: Long)

    /** The cover in each form the widgets use, for one song's picture. */
    private class Art(val url: String, val square: Map<WidgetStyle, Bitmap>, val frost: Bitmap?, val color: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    @Volatile private var state: State? = null
    @Volatile private var art: Art? = null
    private var job: Job? = null

    private fun placed(context: Context): Map<WidgetStyle, IntArray> {
        val manager = AppWidgetManager.getInstance(context) ?: return emptyMap()
        return WidgetStyle.entries.associateWith { runCatching { manager.getAppWidgetIds(ComponentName(context, it.provider)) }.getOrDefault(IntArray(0)) }
            .filterValues { it.isNotEmpty() }
    }

    /** Whether any Prism widget is on the home screen (so the service can skip the work when none is). */
    fun anyPlaced(context: Context): Boolean = placed(context).isNotEmpty()

    /** What's playing changed. [positionMs]/[durationMs] null keep the last known ones. */
    fun update(context: Context, song: Song?, playing: Boolean, liked: Boolean, positionMs: Long?, durationMs: Long?) {
        val app = context.applicationContext
        if (!anyPlaced(app)) return
        val before = state
        state = State(
            song, playing, liked,
            positionMs ?: before?.positionMs?.takeIf { before.song?.id == song?.id } ?: 0L,
            durationMs ?: before?.durationMs?.takeIf { before.song?.id == song?.id } ?: (song?.durationSec ?: 0) * 1000L,
        )
        if (song != null && song.id != before?.song?.id) saveLast(app, song)
        refresh(app)
    }

    /** Draws the widgets again from the latest state (or the last song played, after a restart). */
    fun refresh(context: Context) {
        val app = context.applicationContext
        job?.cancel()
        job = scope.launch {
            lock.withLock {
                val s = state ?: State(lastSong(app), playing = false, liked = false, 0L, 0L)
                val url = s.song?.thumbnail
                // Offline with a cover Coil hasn't kept: the widget shows Prism's mark instead.
                if (art?.url != url) art = url?.let { loadArt(app, it) }
                val manager = AppWidgetManager.getInstance(app) ?: return@withLock
                placed(app).forEach { (style, ids) ->
                    runCatching { manager.updateAppWidget(ids, views(app, style, s, art)) }
                }
            }
        }
    }

    private fun views(context: Context, style: WidgetStyle, s: State, art: Art?): RemoteViews {
        val v = RemoteViews(context.packageName, style.layout)
        val song = s.song
        v.setTextViewText(R.id.widget_title, song?.title ?: context.getString(R.string.widget_nothing))
        v.setTextViewText(R.id.widget_artist, song?.artistText ?: context.getString(R.string.widget_tap_to_play))
        val cover = art?.square?.get(style)
        if (cover != null) v.setImageViewBitmap(R.id.widget_art, cover) else v.setImageViewResource(R.id.widget_art, R.drawable.ic_notification)
        v.setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        v.setContentDescription(R.id.widget_play, context.getString(if (s.playing) R.string.widget_pause else R.string.widget_play))
        val heart = when (style) {
            WidgetStyle.YTMUSIC -> if (s.liked) R.drawable.ic_w_thumb else R.drawable.ic_w_thumb_outline
            else -> if (s.liked) R.drawable.ic_w_heart else R.drawable.ic_w_heart_outline
        }
        v.setImageViewResource(R.id.widget_like, heart)
        if (style == WidgetStyle.SPOTIFY) v.setInt(R.id.widget_like, "setColorFilter", if (s.liked) 0xFF1ED760.toInt() else Color.WHITE)
        val progress = if (s.durationMs > 0) (s.positionMs * 1000 / s.durationMs).toInt().coerceIn(0, 1000) else 0
        if (style != WidgetStyle.APPLE) v.setProgressBar(R.id.widget_progress, 1000, progress, false)

        when (style) {
            WidgetStyle.PRISM -> {
                // Frosted cover behind (Android 12+ rounds it for us); older launchers get the plain dark card.
                if (art?.frost != null && Build.VERSION.SDK_INT >= 31) v.setImageViewBitmap(R.id.widget_bg, art.frost)
                else v.setImageViewBitmap(R.id.widget_bg, null)
                v.setTextColor(R.id.widget_label, art?.color?.let { ColorUtils.blendARGB(it, Color.WHITE, 0.55f) } ?: 0xB3FFFFFF.toInt())
            }
            WidgetStyle.APPLE -> v.setInt(R.id.widget_tint, "setColorFilter", art?.color ?: 0xFF3A3A3C.toInt())
            else -> Unit
        }

        fun broadcast(action: String, code: Int) = PendingIntent.getBroadcast(
            context, code, Intent(context, WidgetActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        v.setOnClickPendingIntent(R.id.widget_play, broadcast(ACTION_PLAY, 1))
        v.setOnClickPendingIntent(R.id.widget_next, broadcast(ACTION_NEXT, 2))
        v.setOnClickPendingIntent(R.id.widget_prev, broadcast(ACTION_PREVIOUS, 3))
        v.setOnClickPendingIntent(R.id.widget_like, broadcast(ACTION_LIKE, 4))
        val open = PendingIntent.getActivity(
            context, 5,
            Intent(context, MainActivity::class.java).setData(Uri.parse("prism://player"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        v.setOnClickPendingIntent(R.id.widget_open, open)
        v.setOnClickPendingIntent(R.id.widget_art, open)
        if (style == WidgetStyle.PRISM) v.setOnClickPendingIntent(R.id.widget_logo, open)
        return v
    }

    private suspend fun loadArt(context: Context, url: String): Art? = runCatching {
        val req = ImageRequest.Builder(context).data(hiRes(url, 544)).size(320).allowHardware(false).build()
        val bmp = (SingletonImageLoader.get(context).execute(req) as? SuccessResult)?.image?.toBitmap() ?: return null
        val square = centerSquare(bmp, 300)
        val palette = Palette.from(square).maximumColorCount(16).generate()
        val base = (palette.dominantSwatch ?: palette.mutedSwatch ?: palette.vibrantSwatch)?.rgb ?: 0xFF3A3A3C.toInt()
        // Deep enough for white text on it, like Apple Music's cards.
        val card = if (ColorUtils.calculateLuminance(base) > 0.35) ColorUtils.blendARGB(base, Color.BLACK, 0.45f) else base
        Art(
            url = url,
            square = WidgetStyle.entries.associateWith { rounded(square, it.artCorner) },
            frost = frost(square),
            color = card or 0xFF000000.toInt(),
        )
    }.getOrNull()

    private fun centerSquare(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        return Bitmap.createScaledBitmap(crop, size, size, true)
    }

    /** The cover with rounded corners ([corner] of its side), so they're round on every Android version. */
    private fun rounded(src: Bitmap, corner: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val r = src.width * corner
        Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, paint)
        return out
    }

    /** A soft frosted version of the cover: shrunk right down and stretched back up, twice. */
    private fun frost(src: Bitmap): Bitmap {
        val tiny = Bitmap.createScaledBitmap(src, 10, 10, true)
        val mid = Bitmap.createScaledBitmap(tiny, 40, 40, true)
        return Bitmap.createScaledBitmap(mid, 240, 240, true)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)

    private fun saveLast(context: Context, song: Song) {
        prefs(context).edit().putString("song", InnerTube.json.encodeToString(Song.serializer(), song)).apply()
    }

    fun lastSong(context: Context): Song? = prefs(context).getString("song", null)?.let {
        runCatching { InnerTube.json.decodeFromString(Song.serializer(), it) }.getOrNull()
    }
}
