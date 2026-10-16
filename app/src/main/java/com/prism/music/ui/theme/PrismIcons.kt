package com.prism.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Prism's own icons, used everywhere in the app (issue #2): simple rounded shapes on a 24-unit grid, 1.8-unit
 * strokes with round ends. Each idle (outline) icon and its picked (filled) twin are drawn from the same shape, so
 * switching tabs only fills it in. Drawn here rather than taken from an icon library, so there's no extra dependency
 * or license. `IconRenderTest` draws them all onto app/build/icon-previews/icons.png for a look.
 */
/** How heavy Prism's line icons are drawn (#19). */
enum class IconWeight(val label: String, val scale: Float) { THIN("Thin", 0.64f), REGULAR("Regular", 1f), BOLD("Bold", 1.45f) }

/**
 * The icon style picked in Settings → Appearance → Icons (#19): line weight, and rounded or square ends and corners.
 * The icons are built once, as Prism starts, so this is read then ([load], from plain shared preferences so it's there
 * before anything draws) and a change shows after Prism restarts.
 */
/** Sets of icons to pick from (Settings → Appearance → Icons): Prism's own, or Cupertino, drawn after Apple's style. */
enum class IconPack(val label: String, val detail: String) {
    PRISM("Prism", "Prism's own icons"),
    CUPERTINO("Cupertino", "In the style of Apple's apps: filled tabs, a chevron for Back, two-triangle skips, circled menus"),
}

object IconStyle {
    private const val PREFS = "icon_style"
    @Volatile var weight = IconWeight.REGULAR
        private set
    @Volatile var rounded = true
        private set
    @Volatile var pack = IconPack.PRISM
        private set

    fun load(context: android.content.Context) {
        val (w, r) = saved(context)
        weight = w; rounded = r
        pack = savedPack(context)
    }

    fun savedPack(context: android.content.Context): IconPack {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return IconPack.entries.firstOrNull { it.name == p.getString("pack", null) } ?: IconPack.PRISM
    }

    fun savePack(context: android.content.Context, pack: IconPack) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString("pack", pack.name).apply()
    }

    /** What's saved (it may differ from what's drawn until a restart). */
    fun saved(context: android.content.Context): Pair<IconWeight, Boolean> {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val w = IconWeight.entries.firstOrNull { it.name == p.getString("weight", null) } ?: IconWeight.REGULAR
        return w to p.getBoolean("rounded", true)
    }

    fun save(context: android.content.Context, weight: IconWeight, rounded: Boolean) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString("weight", weight.name).putBoolean("rounded", rounded).apply()
    }
}

object PrismIcons {
    private const val STROKE = 1.8f

    /** [mirror]: flipped in right-to-left languages (arrows, lists). */
    private fun icon(name: String, mirror: Boolean = false, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply(block).build()

    private fun ImageVector.Builder.line(width: Float = STROKE, d: PathBuilder.() -> Unit) = path(
        fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width * IconStyle.weight.scale,
        strokeLineCap = if (IconStyle.rounded) StrokeCap.Round else StrokeCap.Butt,
        strokeLineJoin = if (IconStyle.rounded) StrokeJoin.Round else StrokeJoin.Miter, pathBuilder = d,
    )

    /** This icon, or its twin from the icon pack picked in Settings (only drawn when that pack is the one in use). */
    private inline fun ImageVector.or(pack: () -> ImageVector): ImageVector = if (IconStyle.pack == IconPack.CUPERTINO) pack() else this

    private fun ImageVector.Builder.solid(evenOdd: Boolean = false, d: PathBuilder.() -> Unit) = path(
        fill = SolidColor(Color.Black), pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero, pathBuilder = d,
    )

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        close()
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float) {
        // Square style: corners mostly squared off.
        val rad = if (IconStyle.rounded) radius else radius * 0.3f
        moveTo(l + rad, t)
        lineTo(r - rad, t); quadTo(r, t, r, t + rad)
        lineTo(r, b - rad); quadTo(r, b, r - rad, b)
        lineTo(l + rad, b); quadTo(l, b, l, b - rad)
        lineTo(l, t + rad); quadTo(l, t, l + rad, t)
        close()
    }

    // ---------------------------------------------------------------- Tabs

    private val house: PathBuilder.() -> Unit = {
        moveTo(10.7f, 3.9f); quadTo(12f, 2.9f, 13.3f, 3.9f)
        lineTo(19.6f, 9.1f); quadTo(20.5f, 9.8f, 20.5f, 11f)
        lineTo(20.5f, 18.5f); quadTo(20.5f, 21f, 18f, 21f)
        lineTo(15f, 21f); lineTo(15f, 16f); quadTo(15f, 14.5f, 13.5f, 14.5f)
        lineTo(10.5f, 14.5f); quadTo(9f, 14.5f, 9f, 16f); lineTo(9f, 21f)
        lineTo(6f, 21f); quadTo(3.5f, 21f, 3.5f, 18.5f)
        lineTo(3.5f, 11f); quadTo(3.5f, 9.8f, 4.4f, 9.1f)
        close()
    }
    val Home = icon("Home") { line(d = house) }.or { Cupertino.Home }
    val HomeFilled = icon("HomeFilled") { solid(d = house) }.or { Cupertino.HomeFilled }

    private fun search(width: Float) = icon("Search$width") {
        line(width) { circle(10.5f, 10.5f, 6.5f) }
        line(width) { moveTo(15.4f, 15.4f); lineTo(20f, 20f) }
    }
    val Search = search(STROKE).or { Cupertino.Search }
    val SearchBold = search(2.5f)

    /** A stack of cards, the library's records. */
    val Library = icon("Library") {
        line { roundRect(3.5f, 9f, 20.5f, 21f, 2.5f) }
        line { moveTo(5.5f, 5.8f); lineTo(18.5f, 5.8f) }
        line { moveTo(7.5f, 2.8f); lineTo(16.5f, 2.8f) }
    }.or { Cupertino.Library }
    val LibraryFilled = icon("LibraryFilled") {
        solid { roundRect(3.5f, 9f, 20.5f, 21f, 2.5f) }
        line { moveTo(5.5f, 5.8f); lineTo(18.5f, 5.8f) }
        line { moveTo(7.5f, 2.8f); lineTo(16.5f, 2.8f) }
    }.or { Cupertino.LibraryFilled }

    /** A four-point sparkle, its sides curving in. */
    private fun PathBuilder.sparkle(cx: Float, cy: Float, r: Float) {
        val k = r * 0.16f
        moveTo(cx, cy - r)
        quadTo(cx + k, cy - k, cx + r, cy)
        quadTo(cx + k, cy + k, cx, cy + r)
        quadTo(cx - k, cy + k, cx - r, cy)
        quadTo(cx - k, cy - k, cx, cy - r)
        close()
    }
    val Replay = icon("Replay") {
        line { sparkle(10f, 13.5f, 7.5f) }
        line { sparkle(18.5f, 5.5f, 3f) }
    }
    val ReplayFilled = icon("ReplayFilled") {
        solid { sparkle(10f, 13.5f, 7.8f) }
        solid { sparkle(18.5f, 5.5f, 3.3f) }
    }

    /** Eight teeth round a ring. */
    private val gear: PathBuilder.() -> Unit = {
        val teeth = 8
        fun pt(deg: Double, r: Double) = (12 + r * cos(deg * PI / 180)).toFloat() to (12 + r * sin(deg * PI / 180)).toFloat()
        for (k in 0 until teeth) {
            val a = k * 360.0 / teeth
            val points = listOf(pt(a - 17, 7.2), pt(a - 9, 9.6), pt(a + 9, 9.6), pt(a + 17, 7.2))
            points.forEachIndexed { i, (x, y) -> if (k == 0 && i == 0) moveTo(x, y) else lineTo(x, y) }
        }
        close()
    }
    val Settings = icon("Settings") {
        line(d = gear)
        line { circle(12f, 12f, 3f) }
    }.or { Cupertino.Settings }
    val SettingsFilled = icon("SettingsFilled") {
        solid(evenOdd = true) { gear(); circle(12f, 12f, 3f) }
        // The filled gear keeps the soft corners the stroked one gets from its round joins.
        line(width = 1.2f, d = gear)
    }.or { Cupertino.SettingsFilled }

    // ---------------------------------------------------------------- Home's top bar

    /** Four rounded tiles: customize Home. */
    val Customize = icon("Customize") {
        solid {
            roundRect(3.5f, 3.5f, 10.5f, 10.5f, 2.2f); roundRect(13.5f, 3.5f, 20.5f, 10.5f, 2.2f)
            roundRect(3.5f, 13.5f, 10.5f, 20.5f, 2.2f); roundRect(13.5f, 13.5f, 20.5f, 20.5f, 2.2f)
        }
    }

    /** A dot broadcasting in two arcs, on a short stand. */
    val Podcasts = icon("Podcasts") {
        solid { circle(12f, 10.5f, 2.2f) }
        line { moveTo(12f, 14.2f); lineTo(12f, 20.5f) }
        line { moveTo(8.46f, 14.04f); arcTo(5f, 5f, 0f, true, true, 15.54f, 14.04f) }
        line { moveTo(5.99f, 16.51f); arcTo(8.5f, 8.5f, 0f, true, true, 18.01f, 16.51f) }
    }

    val Person = icon("Person") {
        solid { circle(12f, 8f, 4f) }
        solid {
            moveTo(4.5f, 19f); quadTo(4.5f, 14.5f, 12f, 14.5f); quadTo(19.5f, 14.5f, 19.5f, 19f)
            quadTo(19.5f, 20.5f, 18f, 20.5f); lineTo(6f, 20.5f); quadTo(4.5f, 20.5f, 4.5f, 19f); close()
        }
    }.or { Cupertino.Person }

    // ---------------------------------------------------------------- Player

    val Play = icon("Play") {
        solid {
            moveTo(7.5f, 5.3f); quadTo(7.5f, 3.8f, 8.8f, 4.6f)
            lineTo(19.3f, 11f); quadTo(20.5f, 12f, 19.3f, 13f)
            lineTo(8.8f, 19.4f); quadTo(7.5f, 20.2f, 7.5f, 18.7f); close()
        }
    }.or { Cupertino.Play }

    val Pause = icon("Pause") {
        solid { roundRect(5.8f, 4.5f, 10f, 19.5f, 1.6f); roundRect(14f, 4.5f, 18.2f, 19.5f, 1.6f) }
    }.or { Cupertino.Pause }

    private fun PathBuilder.triangleRight(x: Float) {
        moveTo(x, 7.3f); quadTo(x, 6f, x + 1.1f, 6.6f)
        lineTo(x + 8.7f, 11.1f); quadTo(x + 9.9f, 12f, x + 8.7f, 12.9f)
        lineTo(x + 1.1f, 17.4f); quadTo(x, 18f, x, 16.7f); close()
    }
    private fun PathBuilder.triangleLeft(x: Float) {
        moveTo(x, 7.3f); quadTo(x, 6f, x - 1.1f, 6.6f)
        lineTo(x - 8.7f, 11.1f); quadTo(x - 9.9f, 12f, x - 8.7f, 12.9f)
        lineTo(x - 1.1f, 17.4f); quadTo(x, 18f, x, 16.7f); close()
    }

    /** Two triangles: on to the next song (the player's "Glyphs" style). */
    val Forward = icon("Forward") { solid { triangleRight(2.3f); triangleRight(12f) } }
    val Backward = icon("Backward") { solid { triangleLeft(21.7f); triangleLeft(12f) } }
    /** Triangle and bar. */
    val Next = icon("Next") { solid { triangleRight(4f); roundRect(16.2f, 5.5f, 18.8f, 18.5f, 1.3f) } }.or { Cupertino.Next }
    val Previous = icon("Previous") { solid { triangleLeft(20f); roundRect(5.2f, 5.5f, 7.8f, 18.5f, 1.3f) } }.or { Cupertino.Previous }

    private val heart: PathBuilder.() -> Unit = {
        moveTo(12f, 20.3f)
        curveTo(11.6f, 20.3f, 3f, 15.5f, 3f, 9.3f)
        curveTo(3f, 6.4f, 5.2f, 4.2f, 8f, 4.2f)
        curveTo(9.7f, 4.2f, 11.1f, 5.1f, 12f, 6.4f)
        curveTo(12.9f, 5.1f, 14.3f, 4.2f, 16f, 4.2f)
        curveTo(18.8f, 4.2f, 21f, 6.4f, 21f, 9.3f)
        curveTo(21f, 15.5f, 12.4f, 20.3f, 12f, 20.3f)
        close()
    }
    val Heart = icon("Heart") { line(d = heart) }.or { Cupertino.Heart }
    val HeartFilled = icon("HeartFilled") { solid(d = heart) }.or { Cupertino.HeartFilled }

    val More = icon("More") { solid { circle(5.5f, 12f, 1.7f); circle(12f, 12f, 1.7f); circle(18.5f, 12f, 1.7f) } }.or { Cupertino.More }

    private val bubble: PathBuilder.() -> Unit = {
        moveTo(7.5f, 4f); lineTo(16.5f, 4f); quadTo(21f, 4f, 21f, 8.5f)
        lineTo(21f, 12.3f); quadTo(21f, 16.8f, 16.5f, 16.8f)
        lineTo(11.6f, 16.8f); lineTo(7.4f, 20f); quadTo(6.6f, 20.6f, 6.6f, 19.6f); lineTo(6.6f, 16.7f)
        quadTo(3f, 16.2f, 3f, 12.3f); lineTo(3f, 8.5f); quadTo(3f, 4f, 7.5f, 4f); close()
    }
    /** A speech bubble with two quote dots: lyrics. */
    val Lyrics = icon("Lyrics") {
        line(d = bubble)
        solid { circle(9.4f, 10.4f, 1.4f); circle(14.6f, 10.4f, 1.4f) }
    }.or { Cupertino.Lyrics }
    val LyricsFilled = icon("LyricsFilled") {
        solid(evenOdd = true) { bubble(); circle(9.4f, 10.4f, 1.4f); circle(14.6f, 10.4f, 1.4f) }
    }.or { Cupertino.LyricsFilled }

    /** Up next: three dotted rows. */
    val Queue = icon("Queue") {
        solid { circle(4.6f, 6f, 1.4f); circle(4.6f, 12f, 1.4f); circle(4.6f, 18f, 1.4f) }
        line { moveTo(9f, 6f); lineTo(20f, 6f); moveTo(9f, 12f); lineTo(20f, 12f); moveTo(9f, 18f); lineTo(20f, 18f) }
    }.or { Cupertino.Queue }

    /** One note: the song itself. */
    val Song = icon("Song") {
        solid { circle(9f, 17.2f, 3.2f) }
        line(2f) { moveTo(12.2f, 17.2f); lineTo(12.2f, 4.2f); quadTo(17.6f, 4.8f, 18.6f, 8.8f) }
    }

    /** A screen with a play triangle: the music video. */
    val Video = icon("Video") {
        line { roundRect(2.8f, 5f, 21.2f, 19f, 3.5f) }
        solid { moveTo(10f, 9f); lineTo(15.4f, 12f); lineTo(10f, 15f); close() }
    }

    val Shuffle = icon("Shuffle") {
        line {
            moveTo(3f, 7f); lineTo(6f, 7f); curveTo(10.5f, 7f, 11f, 17f, 15.5f, 17f); lineTo(20.5f, 17f)
            moveTo(3f, 17f); lineTo(6f, 17f); curveTo(7.9f, 17f, 9.1f, 15.3f, 10f, 13.4f)
            moveTo(13.4f, 10.4f); curveTo(14.2f, 8.6f, 15.4f, 7f, 17.5f, 7f); lineTo(20.5f, 7f)
            moveTo(18f, 4.5f); lineTo(20.5f, 7f); lineTo(18f, 9.5f)
            moveTo(18f, 14.5f); lineTo(20.5f, 17f); lineTo(18f, 19.5f)
        }
    }.or { Cupertino.Shuffle }

    val Repeat = icon("Repeat") {
        line {
            moveTo(4f, 11.5f); lineTo(4f, 9.6f); quadTo(4f, 7f, 6.6f, 7f); lineTo(19.5f, 7f)
            moveTo(17f, 4.5f); lineTo(19.5f, 7f); lineTo(17f, 9.5f)
            moveTo(20f, 12.5f); lineTo(20f, 14.4f); quadTo(20f, 17f, 17.4f, 17f); lineTo(4.5f, 17f)
            moveTo(7f, 14.5f); lineTo(4.5f, 17f); lineTo(7f, 19.5f)
        }
    }.or { Cupertino.Repeat }

    /** Endless: autoplay keeps going. */
    val Autoplay = icon("Autoplay") {
        line {
            moveTo(12f, 12f)
            curveTo(10f, 9f, 8.5f, 8f, 6.75f, 8f); curveTo(4.6f, 8f, 3f, 9.8f, 3f, 12f)
            curveTo(3f, 14.2f, 4.6f, 16f, 6.75f, 16f); curveTo(8.5f, 16f, 10f, 15f, 12f, 12f)
            curveTo(14f, 9f, 15.5f, 8f, 17.25f, 8f); curveTo(19.4f, 8f, 21f, 9.8f, 21f, 12f)
            curveTo(21f, 14.2f, 19.4f, 16f, 17.25f, 16f); curveTo(15.5f, 16f, 14f, 15f, 12f, 12f)
            close()
        }
    }

    // ---------------------------------------------------------------- Arrows

    val ArrowBack = icon("ArrowBack", mirror = true) { line { moveTo(19.5f, 12f); lineTo(4.8f, 12f); moveTo(10.5f, 6f); lineTo(4.5f, 12f); lineTo(10.5f, 18f) } }.or { Cupertino.ArrowBack }
    val ArrowForward = icon("ArrowForward", mirror = true) { line { moveTo(4.5f, 12f); lineTo(19.2f, 12f); moveTo(13.5f, 6f); lineTo(19.5f, 12f); lineTo(13.5f, 18f) } }
    val ChevronRight = icon("ChevronRight", mirror = true) { line(2f) { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) } }
    val ChevronDown = icon("ChevronDown") { line(2f) { moveTo(5.5f, 9f); lineTo(12f, 15.5f); lineTo(18.5f, 9f) } }
    val ChevronUp = icon("ChevronUp") { line(2f) { moveTo(5.5f, 15f); lineTo(12f, 8.5f); lineTo(18.5f, 15f) } }
    /** Up and to the left: "fill the search box with this". */
    val FillIn = icon("FillIn", mirror = true) { line { moveTo(17.5f, 17.5f); lineTo(7f, 7f); moveTo(7f, 15f); lineTo(7f, 7f); lineTo(15f, 7f) } }
    val TrendingUp = icon("TrendingUp", mirror = true) { line { moveTo(3f, 17f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(21f, 7f); moveTo(15f, 7f); lineTo(21f, 7f); lineTo(21f, 13f) } }
    val TrendingDown = icon("TrendingDown", mirror = true) { line { moveTo(3f, 7f); lineTo(9f, 13f); lineTo(13f, 9f); lineTo(21f, 17f); moveTo(15f, 17f); lineTo(21f, 17f); lineTo(21f, 11f) } }
    val OpenInNew = icon("OpenInNew", mirror = true) {
        line {
            moveTo(13.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 10.5f); moveTo(19.5f, 4.5f); lineTo(11f, 13f)
            moveTo(18.5f, 14f); lineTo(18.5f, 17.5f); quadTo(18.5f, 19.5f, 16.5f, 19.5f); lineTo(6.5f, 19.5f)
            quadTo(4.5f, 19.5f, 4.5f, 17.5f); lineTo(4.5f, 7.5f); quadTo(4.5f, 5.5f, 6.5f, 5.5f); lineTo(10f, 5.5f)
        }
    }
    val SwapVertical = icon("SwapVertical") {
        line {
            moveTo(8f, 19.5f); lineTo(8f, 4.8f); moveTo(4.5f, 8.5f); lineTo(8f, 5f); lineTo(11.5f, 8.5f)
            moveTo(16f, 4.5f); lineTo(16f, 19.2f); moveTo(12.5f, 15.5f); lineTo(16f, 19f); lineTo(19.5f, 15.5f)
        }
    }
    val Login = icon("Login", mirror = true) {
        line {
            moveTo(14f, 4f); lineTo(17.5f, 4f); quadTo(20f, 4f, 20f, 6.5f); lineTo(20f, 17.5f); quadTo(20f, 20f, 17.5f, 20f); lineTo(14f, 20f)
            moveTo(4f, 12f); lineTo(14f, 12f); moveTo(10.5f, 8.5f); lineTo(14f, 12f); lineTo(10.5f, 15.5f)
        }
    }
    val Logout = icon("Logout", mirror = true) {
        line {
            moveTo(10f, 4f); lineTo(6.5f, 4f); quadTo(4f, 4f, 4f, 6.5f); lineTo(4f, 17.5f); quadTo(4f, 20f, 6.5f, 20f); lineTo(10f, 20f)
            moveTo(10f, 12f); lineTo(20f, 12f); moveTo(16.5f, 8.5f); lineTo(20f, 12f); lineTo(16.5f, 15.5f)
        }
    }

    // ---------------------------------------------------------------- Everyday actions

    val Check = icon("Check") { line(2.1f) { moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(19.5f, 6.5f) } }
    val CheckCircle = icon("CheckCircle") { line { circle(12f, 12f, 8.8f) }; line(2f) { moveTo(8f, 12.3f); lineTo(10.9f, 15.2f); lineTo(16.2f, 9.2f) } }
    val DoneAll = icon("DoneAll") {
        line(2f) { moveTo(2.5f, 12.5f); lineTo(7f, 17f); lineTo(16f, 7f); moveTo(11.5f, 15.5f); lineTo(13f, 17f); lineTo(22f, 7f) }
    }
    val Close = icon("Close") { line(2f) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } }
    val Add = icon("Add") { line(2f) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } }
    val Remove = icon("Remove") { line(2f) { moveTo(5f, 12f); lineTo(19f, 12f) } }
    val MoreVertical = icon("MoreVertical") { solid { circle(12f, 5.5f, 1.7f); circle(12f, 12f, 1.7f); circle(12f, 18.5f, 1.7f) } }
    val DragHandle = icon("DragHandle") { line(2f) { moveTo(5f, 9.5f); lineTo(19f, 9.5f); moveTo(5f, 14.5f); lineTo(19f, 14.5f) } }
    val Edit = icon("Edit") {
        line {
            moveTo(4.5f, 19.5f); lineTo(5.3f, 15.6f); lineTo(15.6f, 5.3f); quadTo(17f, 3.9f, 18.4f, 5.3f); lineTo(18.7f, 5.6f)
            quadTo(20.1f, 7f, 18.7f, 8.4f); lineTo(8.4f, 18.7f); close()
            moveTo(13.8f, 7.1f); lineTo(16.9f, 10.2f)
        }
    }
    /** A signature scribble. */
    val Signature = icon("Signature") {
        line {
            moveTo(3.5f, 16.5f); curveTo(6f, 11.5f, 8.2f, 7.6f, 10f, 8.6f); curveTo(12f, 9.8f, 7.8f, 16.8f, 10.8f, 16.8f)
            curveTo(12.8f, 16.8f, 13.8f, 12.8f, 15.9f, 12.8f); curveTo(17.8f, 12.8f, 17.2f, 16.6f, 20.5f, 16.2f)
            moveTo(4f, 20f); lineTo(20f, 20f)
        }
    }
    val Delete = icon("Delete") {
        line {
            moveTo(4f, 6.5f); lineTo(20f, 6.5f)
            moveTo(9.5f, 6.5f); lineTo(9.5f, 5f); quadTo(9.5f, 3.8f, 10.7f, 3.8f); lineTo(13.3f, 3.8f); quadTo(14.5f, 3.8f, 14.5f, 5f); lineTo(14.5f, 6.5f)
            moveTo(6f, 6.5f); lineTo(7f, 18.6f); quadTo(7.2f, 20.5f, 9f, 20.5f); lineTo(15f, 20.5f); quadTo(16.8f, 20.5f, 17f, 18.6f); lineTo(18f, 6.5f)
            moveTo(10f, 10.5f); lineTo(10f, 16.5f); moveTo(14f, 10.5f); lineTo(14f, 16.5f)
        }
    }
    val Refresh = icon("Refresh") {
        line {
            moveTo(20f, 12f); arcTo(8f, 8f, 0f, true, true, 17.66f, 6.34f)
            moveTo(14.3f, 6.34f); lineTo(17.66f, 6.34f); lineTo(17.66f, 2.94f)
        }
    }
    /** A clock with an arrow running back round it. */
    val History = icon("History") {
        line {
            moveTo(4.5f, 12f); arcTo(7.5f, 7.5f, 0f, true, false, 6.7f, 6.7f)
            moveTo(10.1f, 6.7f); lineTo(6.7f, 6.7f); lineTo(6.7f, 3.3f)
            moveTo(12f, 8f); lineTo(12f, 12f); lineTo(14.8f, 13.8f)
        }
    }
    private val syncArcs: PathBuilder.() -> Unit = {
        moveTo(4.5f, 12f); arcTo(7.5f, 7.5f, 0f, false, true, 17.8f, 7.2f)
        moveTo(14.5f, 7.4f); lineTo(17.8f, 7.2f); lineTo(18f, 3.9f)
        moveTo(19.5f, 12f); arcTo(7.5f, 7.5f, 0f, false, true, 6.2f, 16.8f)
        moveTo(9.5f, 16.6f); lineTo(6.2f, 16.8f); lineTo(6f, 20.1f)
    }
    val Sync = icon("Sync") { line(d = syncArcs) }
    val SyncOff = icon("SyncOff") { line(d = syncArcs); line { moveTo(4f, 4f); lineTo(20f, 20f) } }
    val Share = icon("Share") {
        line {
            moveTo(8.5f, 9f); lineTo(7f, 9f); quadTo(5f, 9f, 5f, 11f); lineTo(5f, 18.5f); quadTo(5f, 20.5f, 7f, 20.5f)
            lineTo(17f, 20.5f); quadTo(19f, 20.5f, 19f, 18.5f); lineTo(19f, 11f); quadTo(19f, 9f, 17f, 9f); lineTo(15.5f, 9f)
            moveTo(12f, 14f); lineTo(12f, 3.8f); moveTo(8.5f, 7f); lineTo(12f, 3.5f); lineTo(15.5f, 7f)
        }
    }.or { Cupertino.Share }
    val Link = icon("Link") {
        line {
            moveTo(10f, 14f); lineTo(14f, 10f)
            moveTo(8.8f, 11.2f); lineTo(6.9f, 13.1f); quadTo(4.4f, 15.6f, 6.9f, 18.1f); quadTo(9.4f, 20.6f, 11.9f, 18.1f); lineTo(12.8f, 17.2f)
            moveTo(15.2f, 12.8f); lineTo(17.1f, 10.9f); quadTo(19.6f, 8.4f, 17.1f, 5.9f); quadTo(14.6f, 3.4f, 12.1f, 5.9f); lineTo(11.2f, 6.8f)
        }
    }
    val Copy = icon("Copy") {
        line { roundRect(8.5f, 8.5f, 20f, 20f, 2.5f) }
        line { moveTo(5.5f, 15.5f); quadTo(4f, 15.5f, 4f, 14f); lineTo(4f, 6.5f); quadTo(4f, 4f, 6.5f, 4f); lineTo(14f, 4f); quadTo(15.5f, 4f, 15.5f, 5.5f) }
    }
    val Lock = icon("Lock") {
        line { roundRect(5f, 10.5f, 19f, 20.5f, 2.5f) }
        line { moveTo(8f, 10.5f); lineTo(8f, 8f); arcTo(4f, 4f, 0f, false, true, 16f, 8f); lineTo(16f, 10.5f) }
        solid { circle(12f, 15.5f, 1.4f) }
    }
    val Globe = icon("Globe") {
        line { circle(12f, 12f, 8.5f) }
        line { moveTo(12f, 3.5f); curveTo(8.6f, 7f, 8.6f, 17f, 12f, 20.5f); curveTo(15.4f, 17f, 15.4f, 7f, 12f, 3.5f); moveTo(3.5f, 12f); lineTo(20.5f, 12f) }
    }
    private val eye: PathBuilder.() -> Unit = {
        moveTo(2.5f, 12f); curveTo(5f, 7.2f, 8.5f, 5.5f, 12f, 5.5f); curveTo(15.5f, 5.5f, 19f, 7.2f, 21.5f, 12f)
        curveTo(19f, 16.8f, 15.5f, 18.5f, 12f, 18.5f); curveTo(8.5f, 18.5f, 5f, 16.8f, 2.5f, 12f); close()
    }
    val Visible = icon("Visible") { line(d = eye); line { circle(12f, 12f, 3f) } }
    val Hidden = icon("Hidden") { line(d = eye); line { circle(12f, 12f, 3f) }; line { moveTo(4f, 4f); lineTo(20f, 20f) } }
    val Info = icon("Info") {
        line { circle(12f, 12f, 8.8f) }
        line(2f) { moveTo(12f, 11f); lineTo(12f, 16.5f) }
        solid { circle(12f, 7.8f, 1.2f) }
    }

    // ---------------------------------------------------------------- Stars, hearts, thumbs, bookmarks

    private val star: PathBuilder.() -> Unit = {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 8.8 else 4.1
            val a = -PI / 2 + i * PI / 5
            val x = (12 + r * cos(a)).toFloat(); val y = (12.7 + r * sin(a)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    val Star = icon("Star") { line(d = star) }
    val StarFilled = icon("StarFilled") { solid(d = star); line(1.2f, d = star) }

    private val thumb: PathBuilder.() -> Unit = {
        moveTo(7.5f, 10.5f); lineTo(7.5f, 20f); lineTo(5f, 20f); quadTo(3.5f, 20f, 3.5f, 18.5f); lineTo(3.5f, 12f); quadTo(3.5f, 10.5f, 5f, 10.5f); close()
        moveTo(7.5f, 10.5f); lineTo(11.2f, 4.3f); quadTo(11.8f, 3.3f, 12.9f, 3.6f); quadTo(14.6f, 4.1f, 14.3f, 5.9f); lineTo(13.8f, 9f)
        lineTo(18.3f, 9f); quadTo(20.7f, 9f, 20.3f, 11.4f); lineTo(19.1f, 18f); quadTo(18.8f, 20f, 16.8f, 20f); lineTo(7.5f, 20f)
    }
    val ThumbDown = icon("ThumbDown") { group(rotate = 180f, pivotX = 12f, pivotY = 12f) { line(d = thumb) } }
    val ThumbDownFilled = icon("ThumbDownFilled") { group(rotate = 180f, pivotX = 12f, pivotY = 12f) { solid(d = thumb); line(1.2f, d = thumb) } }

    private val bookmark: PathBuilder.() -> Unit = {
        moveTo(6f, 5.5f); quadTo(6f, 3.5f, 8f, 3.5f); lineTo(16f, 3.5f); quadTo(18f, 3.5f, 18f, 5.5f); lineTo(18f, 20f); lineTo(12f, 16f); lineTo(6f, 20f); close()
    }
    val Bookmark = icon("Bookmark") { line(d = bookmark) }
    val BookmarkFilled = icon("BookmarkFilled") { solid(d = bookmark); line(1.2f, d = bookmark) }

    // ---------------------------------------------------------------- Downloads and the cloud

    private val tray: PathBuilder.() -> Unit = {
        moveTo(4.5f, 15.5f); lineTo(4.5f, 18f); quadTo(4.5f, 20f, 6.5f, 20f); lineTo(17.5f, 20f); quadTo(19.5f, 20f, 19.5f, 18f); lineTo(19.5f, 15.5f)
    }
    val Download = icon("Download") { line { tray(); moveTo(12f, 4f); lineTo(12f, 15f); moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f) } }.or { Cupertino.Download }
    val DownloadDone = icon("DownloadDone") { line { tray() }; line(2f) { moveTo(7f, 10.5f); lineTo(10.5f, 14f); lineTo(17.5f, 6f) } }.or { Cupertino.DownloadDone }
    val DownloadCircle = icon("DownloadCircle") {
        line { circle(12f, 12f, 8.8f) }
        line { moveTo(12f, 7.5f); lineTo(12f, 15.5f); moveTo(8.5f, 12f); lineTo(12f, 15.5f); lineTo(15.5f, 12f) }
    }
    val Update = icon("Update") {
        line { roundRect(6.5f, 2.5f, 17.5f, 21.5f, 2.5f) }
        line { moveTo(12f, 7f); lineTo(12f, 14f); moveTo(9.5f, 11.5f); lineTo(12f, 14f); lineTo(14.5f, 11.5f) }
    }
    private val cloud: PathBuilder.() -> Unit = {
        moveTo(7f, 18.5f); quadTo(3.5f, 18.5f, 3.5f, 15f); quadTo(3.5f, 11.8f, 6.7f, 11.6f); quadTo(7.4f, 7f, 12f, 7f)
        quadTo(16f, 7f, 17f, 10.8f); quadTo(20.5f, 11.2f, 20.5f, 14.7f); quadTo(20.5f, 18.5f, 16.7f, 18.5f); close()
    }
    val CloudOff = icon("CloudOff") { line(d = cloud); line { moveTo(4f, 4f); lineTo(20f, 20f) } }

    // ---------------------------------------------------------------- Music

    val PlayCircle = icon("PlayCircle") {
        line { circle(12f, 12f, 8.8f) }
        solid { moveTo(10f, 8.6f); lineTo(15.6f, 12f); lineTo(10f, 15.4f); close() }
    }.or { Cupertino.PlayCircle }
    val Album = icon("Album") { line { circle(12f, 12f, 8.8f) }; line { circle(12f, 12f, 2.6f) } }
    val PlaylistPlay = icon("PlaylistPlay", mirror = true) {
        line { moveTo(4f, 6f); lineTo(17f, 6f); moveTo(4f, 11f); lineTo(17f, 11f); moveTo(4f, 16f); lineTo(10f, 16f) }
        solid { moveTo(14f, 13.6f); quadTo(14f, 12.6f, 14.9f, 13.1f); lineTo(19.6f, 16.1f); quadTo(20.4f, 16.6f, 19.6f, 17.1f); lineTo(14.9f, 20.1f); quadTo(14f, 20.6f, 14f, 19.6f); close() }
    }
    val QueueMusic = icon("QueueMusic", mirror = true) {
        line { moveTo(4f, 6f); lineTo(16f, 6f); moveTo(4f, 11f); lineTo(16f, 11f); moveTo(4f, 16f); lineTo(11f, 16f) }
        solid { circle(15.6f, 18.2f, 2.3f) }
        line { moveTo(17.9f, 18.2f); lineTo(17.9f, 9.2f); quadTo(20f, 9.6f, 20.6f, 11.6f) }
    }
    val PlaylistAdd = icon("PlaylistAdd", mirror = true) {
        line { moveTo(4f, 6f); lineTo(17f, 6f); moveTo(4f, 11f); lineTo(17f, 11f); moveTo(4f, 16f); lineTo(10f, 16f) }
        line { moveTo(17f, 13.5f); lineTo(17f, 20.5f); moveTo(13.5f, 17f); lineTo(20.5f, 17f) }
    }
    val PlaylistRemove = icon("PlaylistRemove", mirror = true) {
        line { moveTo(4f, 6f); lineTo(17f, 6f); moveTo(4f, 11f); lineTo(17f, 11f); moveTo(4f, 16f); lineTo(10f, 16f) }
        line { moveTo(13.5f, 17f); lineTo(20.5f, 17f) }
    }
    val Sort = icon("Sort", mirror = true) { line { moveTo(4f, 6.5f); lineTo(20f, 6.5f); moveTo(4f, 12f); lineTo(15f, 12f); moveTo(4f, 17.5f); lineTo(10f, 17.5f) } }
    val LibraryAdd = icon("LibraryAdd") {
        line { roundRect(3.5f, 9f, 20.5f, 21f, 2.5f) }
        line { moveTo(5.5f, 5.8f); lineTo(18.5f, 5.8f); moveTo(7.5f, 2.8f); lineTo(16.5f, 2.8f) }
        line { moveTo(12f, 12f); lineTo(12f, 18f); moveTo(9f, 15f); lineTo(15f, 15f) }
    }
    val LibraryAdded = icon("LibraryAdded") {
        line { roundRect(3.5f, 9f, 20.5f, 21f, 2.5f) }
        line { moveTo(5.5f, 5.8f); lineTo(18.5f, 5.8f); moveTo(7.5f, 2.8f); lineTo(16.5f, 2.8f) }
        line { moveTo(8.8f, 15.2f); lineTo(11f, 17.4f); lineTo(15.4f, 12.6f) }
    }
    /** A dot sending waves both ways: radio, broadcasting. */
    val Radio = icon("Radio") {
        solid { circle(12f, 12f, 2f) }
        line {
            moveTo(8.5f, 8.5f); arcTo(5f, 5f, 0f, false, false, 8.5f, 15.5f); moveTo(15.5f, 8.5f); arcTo(5f, 5f, 0f, false, true, 15.5f, 15.5f)
            moveTo(5.6f, 5.6f); arcTo(9f, 9f, 0f, false, false, 5.6f, 18.4f); moveTo(18.4f, 5.6f); arcTo(9f, 9f, 0f, false, true, 18.4f, 18.4f)
        }
    }.or { Cupertino.Radio }
    /** Sound all round: spatial audio. */
    val Spatial = icon("Spatial") { solid { circle(12f, 12f, 2.2f) }; line { circle(12f, 12f, 5.6f) }; line(1.5f) { circle(12f, 12f, 9.2f) } }
    val Equalizer = icon("Equalizer") {
        line(2f) {
            moveTo(5f, 10f); lineTo(5f, 14f); moveTo(8.5f, 7f); lineTo(8.5f, 17f); moveTo(12f, 4f); lineTo(12f, 20f)
            moveTo(15.5f, 8f); lineTo(15.5f, 16f); moveTo(19f, 10.5f); lineTo(19f, 13.5f)
        }
    }
    /** An animated cover: a picture with a sparkle. */
    val AnimatedCover = icon("AnimatedCover") {
        line { roundRect(3.5f, 4.5f, 20.5f, 19.5f, 3f) }
        solid { sparkle(12f, 12f, 4.6f) }
    }
    val Explicit = icon("Explicit") {
        line { roundRect(4f, 4f, 20f, 20f, 3.5f) }
        line(2f) { moveTo(14.5f, 8f); lineTo(9.5f, 8f); lineTo(9.5f, 16f); lineTo(14.5f, 16f); moveTo(9.5f, 12f); lineTo(13.5f, 12f) }
    }

    // ---------------------------------------------------------------- Sound and devices

    private val speakerCone: PathBuilder.() -> Unit = {
        moveTo(4f, 9.5f); lineTo(7.3f, 9.5f); lineTo(11.4f, 5.9f); quadTo(12.4f, 5.1f, 12.4f, 6.4f); lineTo(12.4f, 17.6f)
        quadTo(12.4f, 18.9f, 11.4f, 18.1f); lineTo(7.3f, 14.5f); lineTo(4f, 14.5f); quadTo(3f, 14.5f, 3f, 13.5f); lineTo(3f, 10.5f); quadTo(3f, 9.5f, 4f, 9.5f); close()
    }
    val VolumeDown = icon("VolumeDown", mirror = true) { solid(d = speakerCone); line { moveTo(15.5f, 9f); arcTo(4f, 4f, 0f, false, true, 15.5f, 15f) } }
    val VolumeUp = icon("VolumeUp", mirror = true) {
        solid(d = speakerCone)
        line { moveTo(15.5f, 9f); arcTo(4f, 4f, 0f, false, true, 15.5f, 15f); moveTo(18f, 6.2f); arcTo(8f, 8f, 0f, false, true, 18f, 17.8f) }
    }
    val VolumeOff = icon("VolumeOff", mirror = true) { solid(d = speakerCone); line { moveTo(15.5f, 9.5f); lineTo(20.5f, 14.5f); moveTo(20.5f, 9.5f); lineTo(15.5f, 14.5f) } }
    val Headphones = icon("Headphones") {
        line { moveTo(4.5f, 15f); lineTo(4.5f, 12.5f); arcTo(7.5f, 7.5f, 0f, false, true, 19.5f, 12.5f); lineTo(19.5f, 15f) }
        solid { roundRect(3.5f, 13.5f, 8.2f, 20.5f, 2f); roundRect(15.8f, 13.5f, 20.5f, 20.5f, 2f) }
    }
    val Speaker = icon("Speaker") {
        line { roundRect(6f, 3f, 18f, 21f, 3f) }
        line { circle(12f, 14.5f, 3f) }
        solid { circle(12f, 7.5f, 1.2f) }
    }
    val Phone = icon("Phone") { line { roundRect(6.5f, 2.5f, 17.5f, 21.5f, 2.8f) }; line { moveTo(10.5f, 18.5f); lineTo(13.5f, 18.5f) } }
    val Usb = icon("Usb") {
        line { roundRect(7.5f, 8.5f, 16.5f, 15.5f, 2f) }
        line { moveTo(10.3f, 8.5f); lineTo(10.3f, 4.5f); moveTo(13.7f, 8.5f); lineTo(13.7f, 4.5f); moveTo(12f, 15.5f); lineTo(12f, 20.5f) }
    }
    val Cast = icon("Cast") {
        line {
            moveTo(3.5f, 8f); lineTo(3.5f, 6.5f); quadTo(3.5f, 4.5f, 5.5f, 4.5f); lineTo(18.5f, 4.5f); quadTo(20.5f, 4.5f, 20.5f, 6.5f)
            lineTo(20.5f, 17.5f); quadTo(20.5f, 19.5f, 18.5f, 19.5f); lineTo(14f, 19.5f)
            moveTo(3.5f, 11.5f); arcTo(8f, 8f, 0f, false, true, 11.5f, 19.5f); moveTo(3.5f, 15f); arcTo(4.5f, 4.5f, 0f, false, true, 8f, 19.5f)
        }
        solid { circle(4.2f, 18.8f, 1.2f) }
    }
    val Speed = icon("Speed") {
        line { moveTo(4.6f, 17f); arcTo(8.5f, 8.5f, 0f, true, true, 19.4f, 17f) }
        line(2f) { moveTo(12f, 14f); lineTo(15.8f, 9.4f) }
        solid { circle(12f, 14f, 1.6f) }
    }
    /** A stopwatch: timing. */
    val Timer = icon("Timer") {
        line { circle(12f, 13.5f, 7.5f) }
        line { moveTo(10f, 3f); lineTo(14f, 3f); moveTo(12f, 13.5f); lineTo(12f, 9.5f); moveTo(18.2f, 6.8f); lineTo(19.4f, 5.6f) }
    }
    /** A crescent moon: the sleep timer. */
    val Moon = icon("Moon") {
        line { moveTo(19.5f, 14.5f); arcTo(8f, 8f, 0f, true, true, 9.5f, 4.5f); arcTo(6.3f, 6.3f, 0f, false, false, 19.5f, 14.5f); close() }
    }
    /** Three bars: stats for nerds. */
    val Stats = icon("Stats") { solid { roundRect(4f, 12.5f, 7.6f, 20f, 1.5f); roundRect(10.2f, 7.5f, 13.8f, 20f, 1.5f); roundRect(16.4f, 4f, 20f, 20f, 1.5f) } }
    val Translate = icon("Translate") {
        line {
            moveTo(3.5f, 6.5f); lineTo(13f, 6.5f); moveTo(8.2f, 4f); lineTo(8.2f, 6.5f)
            moveTo(11.4f, 6.5f); curveTo(10.8f, 10.5f, 8f, 13.8f, 4.5f, 15.4f); moveTo(6.4f, 9.6f); curveTo(7.8f, 12f, 9.8f, 13.6f, 12f, 14.5f)
            moveTo(13f, 20.5f); lineTo(16.8f, 11f); lineTo(20.6f, 20.5f); moveTo(14.4f, 17.3f); lineTo(19.2f, 17.3f)
        }
    }

    // ---------------------------------------------------------------- People, places, looks

    val Groups = icon("Groups") {
        solid { circle(9.2f, 8f, 3.2f) }
        solid { moveTo(3.5f, 18.8f); quadTo(3.5f, 13.8f, 9.2f, 13.8f); quadTo(14.9f, 13.8f, 14.9f, 18.8f); quadTo(14.9f, 20.2f, 13.5f, 20.2f); lineTo(4.9f, 20.2f); quadTo(3.5f, 20.2f, 3.5f, 18.8f); close() }
        solid { circle(16.8f, 8.6f, 2.6f) }
        solid { moveTo(16.3f, 13.4f); quadTo(20.8f, 13.4f, 20.8f, 17.8f); quadTo(20.8f, 19.2f, 19.4f, 19.2f); lineTo(16.6f, 19.2f); quadTo(16.4f, 15.6f, 14.5f, 13.9f); quadTo(15.2f, 13.4f, 16.3f, 13.4f); close() }
    }
    val PersonAdd = icon("PersonAdd") {
        solid { circle(10f, 8f, 3.6f) }
        solid { moveTo(3.5f, 19f); quadTo(3.5f, 14.2f, 10f, 14.2f); quadTo(16.5f, 14.2f, 16.5f, 19f); quadTo(16.5f, 20.4f, 15.1f, 20.4f); lineTo(4.9f, 20.4f); quadTo(3.5f, 20.4f, 3.5f, 19f); close() }
        line { moveTo(19f, 6.5f); lineTo(19f, 12.5f); moveTo(16f, 9.5f); lineTo(22f, 9.5f) }
    }
    val Mood = icon("Mood") {
        line { circle(12f, 12f, 8.8f) }
        solid { circle(9f, 10f, 1.2f); circle(15f, 10f, 1.2f) }
        line { moveTo(8.5f, 14.2f); quadTo(12f, 17.6f, 15.5f, 14.2f) }
    }
    /** A triangle, a circle and a square: genres. */
    val Category = icon("Category") {
        line { moveTo(12f, 3.5f); lineTo(16.5f, 10.5f); lineTo(7.5f, 10.5f); close() }
        line { circle(7.5f, 16.8f, 3.5f) }
        line { roundRect(13.3f, 13.3f, 20.3f, 20.3f, 1.4f) }
    }
    val Palette = icon("Palette") {
        line {
            moveTo(12f, 3.5f); arcTo(8.5f, 8.5f, 0f, false, false, 12f, 20.5f); quadTo(13.5f, 20.5f, 13.5f, 19f)
            quadTo(13.5f, 18f, 12.8f, 17.3f); quadTo(12.2f, 16.6f, 12.2f, 15.8f); quadTo(12.2f, 14.3f, 13.7f, 14.3f); lineTo(15.5f, 14.3f)
            arcTo(5f, 5f, 0f, false, false, 20.5f, 9.3f); curveTo(20.5f, 6f, 16.7f, 3.5f, 12f, 3.5f); close()
        }
        solid { circle(7.5f, 11.2f, 1.25f); circle(9.6f, 7.2f, 1.25f); circle(14.4f, 7.2f, 1.25f); circle(17.2f, 10.6f, 1.25f) }
    }
    val Photo = icon("Photo") {
        line { roundRect(3.5f, 4.5f, 20.5f, 19.5f, 3f) }
        line { moveTo(3.8f, 16.5f); lineTo(8.5f, 11.8f); lineTo(13f, 16.2f); lineTo(15.5f, 13.7f); lineTo(20.2f, 18.2f) }
        solid { circle(15.5f, 9f, 1.6f) }
    }
    /** Home laid out as stacked rows. */
    val Rows = icon("Rows") { line { moveTo(4f, 5f); lineTo(20f, 5f); roundRect(4f, 8.5f, 20f, 15.5f, 2f); moveTo(4f, 19f); lineTo(20f, 19f) } }
    /** Home laid out as a carousel. */
    val Carousel = icon("Carousel") { line { roundRect(7.5f, 5f, 16.5f, 19f, 2f); moveTo(4f, 7.5f); lineTo(4f, 16.5f); moveTo(20f, 7.5f); lineTo(20f, 16.5f) } }

    // ---------------------------------------------------------------- Video

    val Fullscreen = icon("Fullscreen") {
        line {
            moveTo(4f, 9f); lineTo(4f, 5.5f); quadTo(4f, 4f, 5.5f, 4f); lineTo(9f, 4f)
            moveTo(15f, 4f); lineTo(18.5f, 4f); quadTo(20f, 4f, 20f, 5.5f); lineTo(20f, 9f)
            moveTo(20f, 15f); lineTo(20f, 18.5f); quadTo(20f, 20f, 18.5f, 20f); lineTo(15f, 20f)
            moveTo(9f, 20f); lineTo(5.5f, 20f); quadTo(4f, 20f, 4f, 18.5f); lineTo(4f, 15f)
        }
    }
    val FullscreenExit = icon("FullscreenExit") {
        line {
            moveTo(9f, 4f); lineTo(9f, 7.5f); quadTo(9f, 9f, 7.5f, 9f); lineTo(4f, 9f)
            moveTo(20f, 9f); lineTo(16.5f, 9f); quadTo(15f, 9f, 15f, 7.5f); lineTo(15f, 4f)
            moveTo(15f, 20f); lineTo(15f, 16.5f); quadTo(15f, 15f, 16.5f, 15f); lineTo(20f, 15f)
            moveTo(4f, 15f); lineTo(7.5f, 15f); quadTo(9f, 15f, 9f, 16.5f); lineTo(9f, 20f)
        }
    }
    val AspectRatio = icon("AspectRatio") {
        line { roundRect(3f, 5f, 21f, 19f, 2.8f) }
        line { moveTo(6.5f, 11f); lineTo(6.5f, 8.5f); lineTo(9f, 8.5f); moveTo(17.5f, 13f); lineTo(17.5f, 15.5f); lineTo(15f, 15.5f) }
    }
    val PictureInPicture = icon("PictureInPicture") {
        line { roundRect(3f, 5f, 21f, 19f, 2.8f) }
        solid { roundRect(12f, 12f, 18f, 16.2f, 1.2f) }
    }

    /**
     * The Cupertino pack: the most-seen icons redrawn in the manner of Apple's apps (Prism's own drawings, not Apple's
     * symbols): solid tab glyphs with cut-outs, a chevron for Back, skips as two touching triangles, arrows with solid
     * heads, menus and downloads in circles. Icons it doesn't redraw stay Prism's. Built only when it's picked.
     */
    private object Cupertino {
        private val house: PathBuilder.() -> Unit = {
            moveTo(10.8f, 3.9f); quadTo(12f, 2.9f, 13.2f, 3.9f)
            lineTo(20.2f, 9.9f); quadTo(20.9f, 10.5f, 20.9f, 11.5f)
            lineTo(20.9f, 18.9f); quadTo(20.9f, 20.9f, 18.9f, 20.9f)
            lineTo(5.1f, 20.9f); quadTo(3.1f, 20.9f, 3.1f, 18.9f)
            lineTo(3.1f, 11.5f); quadTo(3.1f, 10.5f, 3.8f, 9.9f); close()
        }
        val Home = icon("CupertinoHome") {
            line(d = house)
            line { moveTo(9.8f, 20.9f); lineTo(9.8f, 15.6f); quadTo(9.8f, 14.4f, 11f, 14.4f); lineTo(13f, 14.4f); quadTo(14.2f, 14.4f, 14.2f, 15.6f); lineTo(14.2f, 20.9f) }
        }
        val HomeFilled = icon("CupertinoHomeFilled") {
            solid(evenOdd = true) { house(); roundRect(9.8f, 14.4f, 14.2f, 20.9f, 1.2f) }
        }

        val Search = icon("CupertinoSearch") {
            line(2f) { circle(10.4f, 10.4f, 6.6f) }
            line(2.8f) { moveTo(15.4f, 15.4f); lineTo(20.2f, 20.2f) }
        }

        /** A stack with a note on its front card. */
        private fun PathBuilder.note() {
            circle(10.3f, 16.9f, 1.9f)
            // Stem and flag touch the head without overlapping it, so the filled card can cut the whole note out.
            moveTo(12.2f, 10.4f); lineTo(13.5f, 10.4f); lineTo(13.5f, 16.9f); lineTo(12.2f, 16.9f); close()
            moveTo(13.5f, 10.4f); quadTo(15.8f, 10.6f, 16.4f, 12.6f); quadTo(15f, 11.9f, 13.5f, 12.2f); close()
        }
        val Library = icon("CupertinoLibrary") {
            line { roundRect(3.6f, 8f, 20.4f, 21f, 3.2f) }
            line { moveTo(6.2f, 4.6f); lineTo(17.8f, 4.6f) }
            solid { note() }
        }
        val LibraryFilled = icon("CupertinoLibraryFilled") {
            solid(evenOdd = true) { roundRect(3.6f, 8f, 20.4f, 21f, 3.2f); note() }
            line { moveTo(6.2f, 4.6f); lineTo(17.8f, 4.6f) }
        }

        /** A rounder gear with twelve short teeth. */
        private val gear: PathBuilder.() -> Unit = {
            val teeth = 12
            fun pt(deg: Double, r: Double) = (12 + r * cos(deg * PI / 180)).toFloat() to (12 + r * sin(deg * PI / 180)).toFloat()
            for (k in 0 until teeth) {
                val a = k * 360.0 / teeth
                listOf(pt(a - 10, 7.9), pt(a - 6, 9.5), pt(a + 6, 9.5), pt(a + 10, 7.9))
                    .forEachIndexed { i, (x, y) -> if (k == 0 && i == 0) moveTo(x, y) else lineTo(x, y) }
            }
            close()
        }
        val Settings = icon("CupertinoSettings") {
            line(d = gear)
            line { circle(12f, 12f, 3.2f) }
        }
        val SettingsFilled = icon("CupertinoSettingsFilled") {
            solid(evenOdd = true) { gear(); circle(12f, 12f, 3.2f) }
            line(width = 1.2f, d = gear)
        }

        /** A head and shoulders in a circle. */
        val Person = icon("CupertinoPerson") {
            line { circle(12f, 12f, 9.2f) }
            solid { circle(12f, 9.6f, 3.3f) }
            solid {
                moveTo(6.1f, 18.2f); quadTo(7.6f, 14.6f, 12f, 14.6f); quadTo(16.4f, 14.6f, 17.9f, 18.2f)
                quadTo(15.4f, 20.6f, 12f, 20.6f); quadTo(8.6f, 20.6f, 6.1f, 18.2f); close()
            }
        }

        val Play = icon("CupertinoPlay") {
            solid {
                moveTo(6.8f, 5f); quadTo(6.8f, 3.4f, 8.2f, 4.2f)
                lineTo(19.6f, 10.8f); quadTo(21f, 12f, 19.6f, 13.2f)
                lineTo(8.2f, 19.8f); quadTo(6.8f, 20.6f, 6.8f, 19f); close()
            }
        }
        val Pause = icon("CupertinoPause") {
            solid { roundRect(6f, 4.2f, 9.9f, 19.8f, 1.9f); roundRect(14.1f, 4.2f, 18f, 19.8f, 1.9f) }
        }

        /** One triangle of a skip, [width] wide, pointing right from [x] (left from it when [left]). */
        private fun PathBuilder.wedge(x: Float, left: Boolean = false, width: Float = 9.6f) {
            val d = if (left) -1f else 1f
            moveTo(x, 7f); quadTo(x, 5.8f, x + d * 1f, 6.4f)
            lineTo(x + d * (width - 1.1f), 11.1f); quadTo(x + d * width, 12f, x + d * (width - 1.1f), 12.9f)
            lineTo(x + d * 1f, 17.6f); quadTo(x, 18.2f, x, 17f); close()
        }
        /** Two triangles nose to tail, as Apple's players skip. */
        val Next = icon("CupertinoNext") { solid { wedge(2.4f); wedge(11.6f) } }
        val Previous = icon("CupertinoPrevious") { solid { wedge(21.6f, left = true); wedge(12.4f, left = true) } }

        private val heart: PathBuilder.() -> Unit = {
            moveTo(12f, 20.6f)
            curveTo(11.6f, 20.6f, 2.5f, 15.4f, 2.5f, 8.9f)
            curveTo(2.5f, 5.9f, 4.8f, 3.6f, 7.7f, 3.6f)
            curveTo(9.6f, 3.6f, 11.1f, 4.6f, 12f, 6.2f)
            curveTo(12.9f, 4.6f, 14.4f, 3.6f, 16.3f, 3.6f)
            curveTo(19.2f, 3.6f, 21.5f, 5.9f, 21.5f, 8.9f)
            curveTo(21.5f, 15.4f, 12.4f, 20.6f, 12f, 20.6f)
            close()
        }
        val Heart = icon("CupertinoHeart") { line(d = heart) }
        val HeartFilled = icon("CupertinoHeartFilled") { solid(d = heart) }

        /** Three dots in a circle. */
        val More = icon("CupertinoMore") {
            line { circle(12f, 12f, 9.2f) }
            solid { circle(7.6f, 12f, 1.35f); circle(12f, 12f, 1.35f); circle(16.4f, 12f, 1.35f) }
        }

        private val bubble: PathBuilder.() -> Unit = {
            moveTo(12f, 3.6f)
            curveTo(17.2f, 3.6f, 21f, 6.9f, 21f, 11f)
            curveTo(21f, 15.1f, 17.2f, 18.4f, 12f, 18.4f)
            curveTo(11f, 18.4f, 10.1f, 18.3f, 9.2f, 18f)
            lineTo(5.6f, 20.2f); quadTo(4.8f, 20.6f, 5f, 19.7f)
            lineTo(5.6f, 16.6f)
            curveTo(3.9f, 15.2f, 3f, 13.2f, 3f, 11f)
            curveTo(3f, 6.9f, 6.8f, 3.6f, 12f, 3.6f); close()
        }
        /** Two quotation marks: a dot with a tail. */
        private fun PathBuilder.quotes() {
            for (x in listOf(9.1f, 14.3f)) {
                circle(x, 10f, 1.5f)
                moveTo(x + 1.5f, 10f); quadTo(x + 1.6f, 12.6f, x - 0.6f, 13.6f); lineTo(x - 0.9f, 13f); quadTo(x + 0.5f, 12.2f, x, 11.4f); close()
            }
        }
        val Lyrics = icon("CupertinoLyrics") { line(d = bubble); solid { quotes() } }
        val LyricsFilled = icon("CupertinoLyricsFilled") { solid(evenOdd = true) { bubble(); quotes() } }

        /** Rows, the first led by a small play triangle: what plays next. */
        val Queue = icon("CupertinoQueue") {
            solid { moveTo(3.4f, 4f); lineTo(7.6f, 6.4f); lineTo(3.4f, 8.8f); close() }
            line { moveTo(10.5f, 6.4f); lineTo(20.5f, 6.4f); moveTo(3.8f, 12.2f); lineTo(20.5f, 12.2f); moveTo(3.8f, 18f); lineTo(20.5f, 18f) }
        }

        /** A solid arrowhead with its tip at ([x], [y]), pointing right (left when [left]). */
        private fun PathBuilder.head(x: Float, y: Float, left: Boolean = false) {
            val d = if (left) 1f else -1f
            moveTo(x, y); lineTo(x + d * 3.6f, y - 2.6f); lineTo(x + d * 3.6f, y + 2.6f); close()
        }
        val Shuffle = icon("CupertinoShuffle") {
            line {
                moveTo(3f, 7f); lineTo(5.8f, 7f); curveTo(10.4f, 7f, 10.9f, 17f, 15.4f, 17f); lineTo(18f, 17f)
                moveTo(3f, 17f); lineTo(5.8f, 17f); curveTo(7.7f, 17f, 8.9f, 15.4f, 9.8f, 13.5f)
                moveTo(13.6f, 10.4f); curveTo(14.4f, 8.6f, 15.6f, 7f, 17.6f, 7f); lineTo(18f, 7f)
            }
            solid { head(21.2f, 7f); head(21.2f, 17f) }
        }
        val Repeat = icon("CupertinoRepeat") {
            line {
                moveTo(4f, 12f); lineTo(4f, 10f); quadTo(4f, 7f, 7f, 7f); lineTo(17.4f, 7f)
                moveTo(20f, 12f); lineTo(20f, 14f); quadTo(20f, 17f, 17f, 17f); lineTo(6.6f, 17f)
            }
            solid { head(20.8f, 7f); head(3.2f, 17f, left = true) }
        }

        /** Back is a chevron, without a shaft. */
        val ArrowBack = icon("CupertinoBack", mirror = true) { line(2.2f) { moveTo(15f, 4.6f); lineTo(7.6f, 12f); lineTo(15f, 19.4f) } }

        /** An open box with an arrow rising out of it. */
        val Share = icon("CupertinoShare") {
            line {
                moveTo(8.6f, 9.4f); lineTo(7f, 9.4f); quadTo(5f, 9.4f, 5f, 11.4f); lineTo(5f, 19f); quadTo(5f, 21f, 7f, 21f)
                lineTo(17f, 21f); quadTo(19f, 21f, 19f, 19f); lineTo(19f, 11.4f); quadTo(19f, 9.4f, 17f, 9.4f); lineTo(15.4f, 9.4f)
                moveTo(12f, 3.4f); lineTo(12f, 14.4f); moveTo(8.7f, 6.7f); lineTo(12f, 3.4f); lineTo(15.3f, 6.7f)
            }
        }

        val Download = icon("CupertinoDownload") {
            line { circle(12f, 12f, 9.2f) }
            line { moveTo(12f, 7f); lineTo(12f, 16.4f); moveTo(8.4f, 12.8f); lineTo(12f, 16.4f); lineTo(15.6f, 12.8f) }
        }
        /** Downloaded: a solid circle with a check cut out of it. */
        val DownloadDone = icon("CupertinoDownloadDone") {
            solid(evenOdd = true) {
                circle(12f, 12f, 9.6f)
                moveTo(7.2f, 12.3f); lineTo(8.6f, 10.9f); lineTo(10.8f, 13.1f); lineTo(15.4f, 8.5f)
                lineTo(16.8f, 9.9f); lineTo(10.8f, 15.9f); close()
            }
        }

        /** A dot sending out waves to both sides. */
        val Radio = icon("CupertinoRadio") {
            solid { circle(12f, 12f, 2f) }
            line {
                moveTo(8.46f, 8.46f); arcTo(5f, 5f, 0f, false, false, 8.46f, 15.54f)
                moveTo(15.54f, 8.46f); arcTo(5f, 5f, 0f, false, true, 15.54f, 15.54f)
                moveTo(5.64f, 5.64f); arcTo(9f, 9f, 0f, false, false, 5.64f, 18.36f)
                moveTo(18.36f, 5.64f); arcTo(9f, 9f, 0f, false, true, 18.36f, 18.36f)
            }
        }

        val PlayCircle = icon("CupertinoPlayCircle") {
            line { circle(12f, 12f, 9.2f) }
            solid { moveTo(10f, 8.4f); quadTo(10f, 7.6f, 10.7f, 8f); lineTo(15.8f, 11.3f); quadTo(16.4f, 12f, 15.8f, 12.7f); lineTo(10.7f, 16f); quadTo(10f, 16.4f, 10f, 15.6f); close() }
        }
    }
}
