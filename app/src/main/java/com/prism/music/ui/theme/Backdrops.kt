package com.prism.music.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.prism.music.ui.components.AmbientFrost
import com.prism.music.ui.components.LocalFrosted
import com.prism.music.ui.components.LocalPageBackdrop
import com.prism.music.ui.components.PageBackdrop
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.prism.music.data.model.hiRes
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The screens whose background can be changed. */
enum class BackdropScreen(val label: String) { HOME("Home"), SEARCH("Search"), LIBRARY("Library"), REPLAY("Replay"), SETTINGS("Settings") }

/** How a ready-made background is drawn. */
enum class BackdropKind {
    /** Default, Now playing and Your photo: drawn specially. */
    SPECIAL,
    /** A base colour with three slow-drifting light fields. */
    FIELDS,
    /** A quiet top-to-bottom blend of two colours. */
    BLEND,
    /** The theme's accent, softly, over the theme's background (follows the accent colour). */
    TINT,
    /** Simple line patterns in the accent colour over the theme's background. */
    DOTS, PAPER, PINSTRIPE, WAVES, RIPPLES, DIAMONDS, ZIGZAG, HALFTONE, CROSSES, SCALES, CONFETTI,
    /** Detailed illustrations in their own palettes ([Design]). */
    CONSTELLATION, CONTOURS, MOSAIC, BOKEH, SILK, HORIZON,
}

/** Which row of the picker a background sits in. */
enum class BackdropGroup(val label: String) { YOURS("Yours"), COLOUR("Colour"), SIMPLE("Simple"), DESIGNS("Designs") }

/**
 * Ready-made backgrounds. Each has a dark and a light version (base colour first, then its light
 * fields), so the same pick looks at home in either theme: deep and glowing in dark mode, airy
 * pastels in light mode. Pattern designs take their ink from the theme's accent instead.
 * Names are stored in settings, so keep them stable.
 */
enum class Backdrop(
    val label: String,
    val kind: BackdropKind,
    val group: BackdropGroup,
    val dark: List<Long> = emptyList(),
    val light: List<Long> = emptyList(),
) {
    DEFAULT("Default", BackdropKind.SPECIAL, BackdropGroup.YOURS),
    ARTWORK("Now playing", BackdropKind.SPECIAL, BackdropGroup.YOURS),
    PHOTO("Your photo", BackdropKind.SPECIAL, BackdropGroup.YOURS),

    AURORA("Aurora", BackdropKind.FIELDS, BackdropGroup.COLOUR,
        listOf(0xFF07122A, 0xFF2EE59D, 0xFF7C5CFF, 0xFF00C2FF), listOf(0xFFEEF6F5, 0xFF7FE6BE, 0xFFB3A3FF, 0xFF8ADDFF)),
    PRISM("Prism", BackdropKind.FIELDS, BackdropGroup.COLOUR,
        listOf(0xFF0E0E12, 0xFFFF7A9C, 0xFFFFD36B, 0xFF6BE3FF), listOf(0xFFFBF8F4, 0xFFFFA9BE, 0xFFFFDE94, 0xFF9AE8FF)),
    SUNSET("Sunset", BackdropKind.FIELDS, BackdropGroup.COLOUR,
        listOf(0xFF2A0E1A, 0xFFFF6B3D, 0xFFFF3B7F, 0xFFFFC145), listOf(0xFFFFF4EE, 0xFFFFA57D, 0xFFFF8FB3, 0xFFFFD685)),
    OCEAN("Ocean", BackdropKind.FIELDS, BackdropGroup.COLOUR,
        listOf(0xFF031827, 0xFF0077B6, 0xFF00B4D8, 0xFF48CAE4), listOf(0xFFEEF7FC, 0xFF6DBDEA, 0xFF7ADBEE, 0xFFAAE7F3)),
    FOREST("Forest", BackdropKind.FIELDS, BackdropGroup.COLOUR,
        listOf(0xFF07150E, 0xFF2D6A4F, 0xFF52B788, 0xFFB7E4C7), listOf(0xFFF2F7F1, 0xFF8EC3A4, 0xFFA8D9B9, 0xFFDDF0DA)),
    NEON("Neon", BackdropKind.FIELDS, BackdropGroup.COLOUR,
        listOf(0xFF0A0014, 0xFFFF00E5, 0xFF00F0FF, 0xFF7B2FFF), listOf(0xFFF8F3FF, 0xFFFF8CF2, 0xFF7FF3FF, 0xFFB394FF)),
    ROSE("Rosé", BackdropKind.FIELDS, BackdropGroup.COLOUR,
        listOf(0xFF1C0B12, 0xFFF4A3B4, 0xFFE76F8A, 0xFFFFD6A5), listOf(0xFFFFF5F6, 0xFFF8BECB, 0xFFF0A3B6, 0xFFFFE1C0)),
    MIDNIGHT("Midnight", BackdropKind.FIELDS, BackdropGroup.COLOUR,
        listOf(0xFF04040A, 0xFF1B1F3B, 0xFF3A2E6E, 0xFF0F3460), listOf(0xFFEFF1F8, 0xFFB4BDE6, 0xFFC2B5EC, 0xFFA8C2E0)),
    EMBER("Ember", BackdropKind.FIELDS, BackdropGroup.COLOUR,
        listOf(0xFF140604, 0xFFB5361B, 0xFFFF7B00, 0xFF5A0F0F), listOf(0xFFFFF5EF, 0xFFEE9A80, 0xFFFFBC7A, 0xFFE3A0A0)),
    GRAPHITE("Graphite", BackdropKind.FIELDS, BackdropGroup.COLOUR,
        listOf(0xFF111113, 0xFF2C2C31, 0xFF44444C, 0xFF1C1C20), listOf(0xFFF5F5F7, 0xFFDDDDE3, 0xFFD2D2D9, 0xFFE8E8EC)),

    TINT("Soft tint", BackdropKind.TINT, BackdropGroup.SIMPLE),
    DUSK("Dusk", BackdropKind.BLEND, BackdropGroup.SIMPLE,
        listOf(0xFF221338, 0xFF0B1B2E), listOf(0xFFFCE8F1, 0xFFE2ECFF)),
    SAND("Sand", BackdropKind.BLEND, BackdropGroup.SIMPLE,
        listOf(0xFF221D18, 0xFF141210), listOf(0xFFF7F0E6, 0xFFEDE3D3)),
    MIST("Mist", BackdropKind.BLEND, BackdropGroup.SIMPLE,
        listOf(0xFF0F1E22, 0xFF0D1218), listOf(0xFFEAF5F3, 0xFFE6ECF4)),
    DOTS("Dots", BackdropKind.DOTS, BackdropGroup.SIMPLE),
    PAPER("Paper", BackdropKind.PAPER, BackdropGroup.SIMPLE),
    PINSTRIPE("Pinstripe", BackdropKind.PINSTRIPE, BackdropGroup.SIMPLE),
    WAVES("Waves", BackdropKind.WAVES, BackdropGroup.SIMPLE),
    RIPPLES("Ripples", BackdropKind.RIPPLES, BackdropGroup.SIMPLE),
    DIAMONDS("Diamonds", BackdropKind.DIAMONDS, BackdropGroup.SIMPLE),
    ZIGZAG("Zigzag", BackdropKind.ZIGZAG, BackdropGroup.SIMPLE),
    HALFTONE("Halftone", BackdropKind.HALFTONE, BackdropGroup.SIMPLE),
    CROSSES("Crosses", BackdropKind.CROSSES, BackdropGroup.SIMPLE),
    SCALES("Scales", BackdropKind.SCALES, BackdropGroup.SIMPLE),
    CONFETTI("Confetti", BackdropKind.CONFETTI, BackdropGroup.SIMPLE),

    CONSTELLATION("Constellation", BackdropKind.CONSTELLATION, BackdropGroup.DESIGNS,
        listOf(0xFF070B1A, 0xFFA9C6FF, 0xFF6B4CFF, 0xFF00C2D1), listOf(0xFFF3F5FC, 0xFF3D4F8A, 0xFFB8A8FF, 0xFF8EE3EA)),
    CONTOURS("Contours", BackdropKind.CONTOURS, BackdropGroup.DESIGNS,
        listOf(0xFF0B1414, 0xFF3FD0A0, 0xFF3F8FB0, 0xFFE0B354), listOf(0xFFF4F8F3, 0xFF2E8B6E, 0xFF4F8FA8, 0xFFD9A441)),
    MOSAIC("Mosaic", BackdropKind.MOSAIC, BackdropGroup.DESIGNS,
        listOf(0xFF120F1E, 0xFF5B3FD9, 0xFFE0457B, 0xFF1FA2C9), listOf(0xFFFAF7F2, 0xFFC4B5FF, 0xFFFFB3C7, 0xFFA8E0F0)),
    BOKEH("Bokeh", BackdropKind.BOKEH, BackdropGroup.DESIGNS,
        listOf(0xFF0E0A14, 0xFFFFB347, 0xFFFF5E8A, 0xFF7A6BFF), listOf(0xFFFFF8F1, 0xFFFFB866, 0xFFFF8FB0, 0xFF9C90FF)),
    SILK("Silk", BackdropKind.SILK, BackdropGroup.DESIGNS,
        listOf(0xFF0A0E18, 0xFF9C84FF, 0xFF27C4E8, 0xFFFF6B9D), listOf(0xFFF6F4FB, 0xFF7C5CFF, 0xFF1AA6CC, 0xFFE8558A)),
    HORIZON("Horizon", BackdropKind.HORIZON, BackdropGroup.DESIGNS,
        listOf(0xFF0D0420, 0xFFFFB03D, 0xFFFF2E88, 0xFFB08CFF), listOf(0xFFFFF3EA, 0xFFFFB27A, 0xFFFF86B8, 0xFF9B7BFF)),
    ;

    fun colors(dark: Boolean): List<Long> = if (dark) this.dark else light

    companion object {
        /** Stored as "NAME" or "PHOTO|/path/to/image". */
        fun parse(v: String?): Pair<Backdrop, String?> {
            if (v.isNullOrBlank()) return DEFAULT to null
            val name = v.substringBefore('|')
            val style = entries.firstOrNull { it.name == name } ?: DEFAULT
            return style to v.substringAfter('|', "").ifBlank { null }
        }
    }
}

/**
 * Draws [screen]'s chosen background behind [content]. With frosted pages on, the default is the
 * frost made from what's playing (as on artist and album pages), and the page's panels, chips and
 * buttons turn to frosted glass over it, whichever background it has.
 */
@Composable
fun ScreenBackdrop(screen: BackdropScreen, content: @Composable BoxScope.() -> Unit) {
    val c = LocalContainer.current
    val s by c.settings.flow.collectAsState()
    val (style, photo) = Backdrop.parse(s.backdrops[screen.name])
    val motion = s.backdropMotion && !s.reduceMotion
    val frosted = s.frostedPages
    val density = LocalDensity.current
    var height by remember { mutableStateOf(0.dp) }
    val draw: @Composable (Modifier) -> Unit = { m ->
        when {
            style != Backdrop.DEFAULT -> BackdropLayer(style, photo, s.backdropDim, motion, m)
            frosted -> AmbientFrost(m)
            else -> Box(m.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
    }
    val page = remember(style, photo, frosted, height, s.backdropDim, motion) { PageBackdrop(height, draw) }
    Box(Modifier.fillMaxSize().onSizeChanged { height = with(density) { it.height.toDp() } }) {
        if (style != Backdrop.DEFAULT || frosted) draw(Modifier.fillMaxSize())
        CompositionLocalProvider(LocalFrosted provides frosted, LocalPageBackdrop provides page) { content() }
    }
}

/** One background, at any size (the settings previews use it too). */
@Composable
fun BackdropLayer(style: Backdrop, photo: String?, dim: Float, motion: Boolean, modifier: Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val dark = LocalIsDark.current
    Box(modifier) {
        when (style) {
            // With frosted pages on, the default is the frost from what's playing (the previews show it so).
            Backdrop.DEFAULT -> if (LocalAppSettings.current.frostedPages) AmbientFrost(Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize().background(bg))
            Backdrop.ARTWORK -> {
                val song by LocalContainer.current.player.currentSong.collectAsState()
                Box(Modifier.fillMaxSize().background(bg))
                AsyncImage(
                    hiRes(song?.thumbnail, 300), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(70.dp).graphicsLayer { alpha = 0.85f },
                )
            }
            Backdrop.PHOTO -> {
                Box(Modifier.fillMaxSize().background(bg))
                if (photo != null) AsyncImage(File(photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            else -> when (style.kind) {
                BackdropKind.FIELDS -> Fields(style.colors(dark).map { Color(it) }, dark, motion)
                BackdropKind.BLEND -> style.colors(dark).map { Color(it) }.let { (top, bottom) ->
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom))))
                }
                else -> if (style.group == BackdropGroup.DESIGNS) Design(style.kind, style.colors(dark).map { Color(it) }, dark, motion)
                    else Pattern(style.kind, dark, motion)
            }
        }
        // Keeps text readable: a wash of the theme's background, stronger toward the bottom. The light
        // palettes are pale already, so they take a lighter wash; the patterns are quiet and take less still.
        val base = if (dark) dim else dim * 0.85f
        val wash = if (style.group == BackdropGroup.SIMPLE) base * 0.5f else base
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(bg.copy(alpha = wash * 0.7f), bg.copy(alpha = wash), bg.copy(alpha = (wash + 0.25f).coerceAtMost(0.95f))))
            ),
        )
    }
}

@Composable
internal fun drift(motion: Boolean, periodMs: Int, rest: Float): Float = if (motion) {
    rememberInfiniteTransition(label = "backdrop").animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "t").value
} else rest

@Composable
private fun Fields(colors: List<Color>, dark: Boolean, motion: Boolean) {
    val t = drift(motion, 28_000, 0.15f)
    // Pastels need a touch less strength to stay airy; the dark palettes glow at full.
    val strength = if (dark) 0.7f else 0.62f
    Canvas(Modifier.fillMaxSize().background(colors[0])) {
        val a = t * 2 * PI.toFloat()
        val r = size.maxDimension * 0.6f
        fun field(color: Color, cx: Float, cy: Float) =
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = strength), color.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
        field(colors[1], size.width * (0.15f + 0.25f * sin(a)), size.height * (0.12f + 0.08f * cos(a)))
        field(colors[2], size.width * (0.9f + 0.12f * cos(a * 2)), size.height * (0.45f + 0.12f * sin(a)))
        field(colors[3], size.width * (0.35f + 0.2f * cos(a)), size.height * (0.92f + 0.06f * sin(a * 2)))
    }
}

/**
 * The simple designs: the theme's background, a soft glow of the accent from the top, and (except
 * Soft tint) a fine pattern in the accent that fades out down the page. Inks are stronger in light
 * mode, where pale lines on a pale page would otherwise vanish.
 */
@Composable
private fun Pattern(kind: BackdropKind, dark: Boolean, motion: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val bg = scheme.background
    val accent = scheme.primary
    val second = scheme.tertiary
    val t = drift(motion && (kind == BackdropKind.WAVES || kind == BackdropKind.RIPPLES), 24_000, 0.2f)
    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize().background(bg)) {
        val w = size.width
        val h = size.height
        // The glow every simple design shares: the accent, faintly, from the top.
        drawRect(Brush.verticalGradient(
            0f to accent.copy(alpha = if (dark) 0.22f else 0.16f), 0.55f to accent.copy(alpha = if (dark) 0.06f else 0.04f), 1f to Color.Transparent,
        ))
        if (kind == BackdropKind.TINT) {
            drawCircle(
                Brush.radialGradient(listOf(second.copy(alpha = if (dark) 0.16f else 0.12f), Color.Transparent), Offset(w, h * 0.85f), w * 0.9f),
                w * 0.9f, Offset(w, h * 0.85f),
            )
            return@Canvas
        }
        val ink = accent.copy(alpha = if (dark) 0.20f else 0.26f)
        // Patterns fade out toward the bottom, where lists sit thickest.
        val fade = { y: Float -> (1f - (y / h) * 0.85f).coerceIn(0.15f, 1f) }
        val stroke = 1f * density
        when (kind) {
            BackdropKind.DOTS -> {
                val step = 18f * density
                val r = 1.4f * density
                var y = step / 2
                while (y < h) {
                    var x = step / 2
                    while (x < w) { drawCircle(ink.copy(alpha = ink.alpha * fade(y)), r, Offset(x, y)); x += step }
                    y += step
                }
            }
            BackdropKind.PAPER -> {
                val step = 24f * density
                var x = step
                while (x < w) { drawLine(Brush.verticalGradient(listOf(ink, ink.copy(alpha = ink.alpha * 0.15f))), Offset(x, 0f), Offset(x, h), stroke * 0.8f); x += step }
                var y = step
                while (y < h) { drawLine(ink.copy(alpha = ink.alpha * fade(y)), Offset(0f, y), Offset(w, y), stroke * 0.8f); y += step }
            }
            BackdropKind.PINSTRIPE -> {
                val step = 14f * density
                var x = -h
                while (x < w) {
                    drawLine(Brush.verticalGradient(listOf(ink, ink.copy(alpha = ink.alpha * 0.15f))), Offset(x, h), Offset(x + h, 0f), stroke)
                    x += step
                }
            }
            BackdropKind.WAVES -> {
                // Three soft bands rolling along the foot of the page, plus thin echoes at the top.
                val phase = t * 2 * PI.toFloat()
                listOf(Triple(accent, 0.70f, 0f), Triple(second, 0.78f, 1.7f), Triple(accent, 0.86f, 3.4f)).forEachIndexed { i, (color, at, shift) ->
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, h)
                        var x = 0f
                        while (x <= w + 4f) {
                            lineTo(x, h * at + sin(x / w * 2 * PI.toFloat() * 1.2f + phase + shift) * h * 0.03f)
                            x += 4f
                        }
                        lineTo(w, h)
                        close()
                    }
                    drawPath(path, color.copy(alpha = (if (dark) 0.14f else 0.12f) + i * 0.03f))
                }
                for (k in 0 until 4) {
                    val path = androidx.compose.ui.graphics.Path()
                    var x = 0f
                    val y0 = h * (0.08f + k * 0.035f)
                    path.moveTo(0f, y0)
                    while (x <= w + 4f) { path.lineTo(x, y0 + sin(x / w * 2 * PI.toFloat() + phase + k * 0.6f) * h * 0.012f); x += 4f }
                    drawPath(path, ink.copy(alpha = ink.alpha * (1f - k * 0.2f)), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                }
            }
            BackdropKind.RIPPLES -> {
                // Rings spreading from the top corner; with motion on they slowly travel outward.
                val origin = Offset(w * 0.92f, h * 0.06f)
                val step = 26f * density
                val maxR = size.maxDimension * 1.2f
                var r = step * t
                while (r < maxR) {
                    if (r > 0f) drawCircle(ink.copy(alpha = ink.alpha * (1f - r / maxR)), r, origin, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    r += step
                }
            }
            BackdropKind.DIAMONDS -> {
                // A fine lattice of diagonals both ways.
                val step = 22f * density
                val brush = Brush.verticalGradient(listOf(ink, ink.copy(alpha = ink.alpha * 0.12f)))
                var x = -h
                while (x < w + h) {
                    drawLine(brush, Offset(x, h), Offset(x + h, 0f), stroke * 0.8f)
                    drawLine(brush, Offset(x, 0f), Offset(x + h, h), stroke * 0.8f)
                    x += step
                }
            }
            BackdropKind.ZIGZAG -> {
                val rowStep = 24f * density
                val run = 9f * density
                val amp = 4.5f * density
                var y = rowStep / 2
                while (y < h) {
                    val path = androidx.compose.ui.graphics.Path().apply { moveTo(0f, y) }
                    var x = 0f
                    var up = true
                    while (x < w + run) { x += run; path.lineTo(x, y + if (up) -amp else amp); up = !up }
                    drawPath(path, ink.copy(alpha = ink.alpha * fade(y)), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    y += rowStep
                }
            }
            BackdropKind.HALFTONE -> {
                // Dots that swell toward the top corner, like a print's halftone.
                val step = 13f * density
                val origin = Offset(w, 0f)
                val reach = size.maxDimension * 0.9f
                var y = step / 2
                var row = 0
                while (y < h) {
                    var x = if (row % 2 == 0) step / 2 else step
                    while (x < w) {
                        val k = (1f - (Offset(x, y) - origin).getDistance() / reach).coerceIn(0f, 1f)
                        if (k > 0.04f) drawCircle(ink.copy(alpha = ink.alpha * 1.1f), step * 0.42f * k, Offset(x, y))
                        x += step
                    }
                    y += step * 0.87f; row++
                }
            }
            BackdropKind.CROSSES -> {
                val step = 30f * density
                val arm = 3.5f * density
                var y = step / 2
                var row = 0
                while (y < h) {
                    var x = if (row % 2 == 0) step / 2 else step
                    val c = ink.copy(alpha = ink.alpha * fade(y))
                    while (x < w) {
                        drawLine(c, Offset(x - arm, y), Offset(x + arm, y), stroke * 1.2f)
                        drawLine(c, Offset(x, y - arm), Offset(x, y + arm), stroke * 1.2f)
                        x += step
                    }
                    y += step / 2; row++
                }
            }
            BackdropKind.SCALES -> {
                // Overlapping arcs in offset rows, like fish scales or roof tiles.
                val rad = 13f * density
                var y = 0f
                var row = 0
                while (y < h + rad) {
                    var x = if (row % 2 == 0) 0f else rad
                    val c = ink.copy(alpha = ink.alpha * fade(y))
                    while (x < w + rad) {
                        drawArc(c, 0f, 180f, false, Offset(x - rad, y - rad), androidx.compose.ui.geometry.Size(rad * 2, rad * 2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                        x += rad * 2
                    }
                    y += rad * 0.9f; row++
                }
            }
            BackdropKind.CONFETTI -> {
                // Short dashes scattered at all angles, in the accent and its partner colour (same scatter every time).
                val rnd = kotlin.random.Random(42)
                val len = 7f * density
                repeat((w * h / (34f * 34f * density * density)).toInt().coerceAtMost(900)) {
                    val x = rnd.nextFloat() * w
                    val y = rnd.nextFloat() * h
                    val a = rnd.nextFloat() * PI.toFloat()
                    val col = (if (rnd.nextBoolean()) accent else second).copy(alpha = ink.alpha * 1.15f * fade(y))
                    drawLine(col, Offset(x - cos(a) * len / 2, y - sin(a) * len / 2), Offset(x + cos(a) * len / 2, y + sin(a) * len / 2),
                        stroke * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            else -> {}
        }
    }
}
