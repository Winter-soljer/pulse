package com.prism.music.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The "Designs" backgrounds: detailed illustrations (a starfield, contour lines, a low-poly mosaic,
 * bokeh, silk ribbons, a retro horizon) that stay calm enough to read over. Detail is strongest at
 * the top and thins out down the page, where lists sit thickest, and [BackdropLayer] lays the usual
 * wash over them. Every random choice is seeded, so a design looks the same each time.
 */
@Composable
internal fun Design(kind: BackdropKind, colors: List<Color>, dark: Boolean, motion: Boolean) {
    val (base, a, b, c) = colors
    val periodMs = when (kind) {
        BackdropKind.CONSTELLATION -> 9_000
        BackdropKind.BOKEH -> 60_000
        BackdropKind.HORIZON -> 6_000
        else -> 30_000
    }
    val animated = motion && kind != BackdropKind.MOSAIC
    val t = drift(animated, periodMs, 0.3f)
    val density = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize().background(base)) {
        // A fresh generator with the same seed every frame: stars and facets stay put.
        val r = Random(kind.ordinal * 7919 + 17)
        when (kind) {
            BackdropKind.CONSTELLATION -> constellation(r, a, b, c, dark, t, density)
            BackdropKind.CONTOURS -> contours(a, b, c, dark, t, density)
            BackdropKind.MOSAIC -> mosaic(r, base, a, b, c, dark)
            BackdropKind.BOKEH -> bokeh(r, a, b, c, dark, t, density)
            BackdropKind.SILK -> silk(a, b, c, dark, t, density)
            BackdropKind.HORIZON -> horizon(r, base, a, b, c, dark, t, density)
            else -> {}
        }
    }
}

/** Fades detail out toward the bottom of the page. */
private fun DrawScope.fade(y: Float, floor: Float = 0.2f) = (1f - (y / size.height) * 0.8f).coerceIn(floor, 1f)

private fun DrawScope.glow(color: Color, alpha: Float, cx: Float, cy: Float, radius: Float) =
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), Offset(cx, cy), radius), radius, Offset(cx, cy))

private fun DrawScope.constellation(r: Random, star: Color, nebula: Color, nebula2: Color, dark: Boolean, t: Float, d: Float) {
    val w = size.width; val h = size.height
    glow(nebula, if (dark) 0.34f else 0.42f, w * 0.18f, h * 0.22f, w * 0.75f)
    glow(nebula2, if (dark) 0.22f else 0.32f, w * 0.88f, h * 0.55f, w * 0.65f)
    // A thin band of fainter stars, like a galaxy seen edge on.
    val band = Path().apply { moveTo(-w * 0.1f, h * 0.05f); lineTo(w * 1.1f, h * 0.42f) }
    drawPath(band, Brush.linearGradient(listOf(Color.Transparent, star.copy(alpha = if (dark) 0.07f else 0.06f), Color.Transparent), Offset(0f, 0f), Offset(w, h * 0.5f)), style = Stroke(w * 0.22f))
    val twinkle = t * 2 * PI.toFloat()
    repeat(150) {
        val x = r.nextFloat() * w
        val y = r.nextFloat() * h
        val size = (0.5f + r.nextFloat() * r.nextFloat() * 1.6f) * d
        val phase = r.nextFloat() * 6.28f
        val a = (if (dark) 0.75f else 0.5f) * (0.55f + 0.45f * sin(twinkle + phase)) * fade(y)
        drawCircle(star.copy(alpha = a), size, Offset(x, y))
    }
    // A few constellations in the upper part of the page: bright stars joined by fine lines.
    repeat(5) { g ->
        val cx = w * (0.12f + r.nextFloat() * 0.76f)
        val cy = h * (0.06f + r.nextFloat() * 0.5f)
        var p = Offset(cx, cy)
        val path = Path().apply { moveTo(p.x, p.y) }
        val stars = mutableListOf(p)
        repeat(3 + r.nextInt(3)) {
            val ang = r.nextFloat() * 6.28f
            val len = (28f + r.nextFloat() * 46f) * d
            p = Offset((p.x + cos(ang) * len).coerceIn(0f, w), (p.y + sin(ang) * len).coerceIn(0f, h))
            path.lineTo(p.x, p.y)
            stars += p
        }
        val f = fade(cy)
        drawPath(path, star.copy(alpha = (if (dark) 0.28f else 0.32f) * f), style = Stroke(0.8f * d))
        stars.forEachIndexed { i, s ->
            val pulse = 0.75f + 0.25f * sin(twinkle + g + i)
            glow(star, (if (dark) 0.35f else 0.25f) * f * pulse, s.x, s.y, 7f * d)
            drawCircle(star.copy(alpha = (if (dark) 0.95f else 0.75f) * f), 1.7f * d, s)
        }
    }
}

private fun DrawScope.contours(line: Color, line2: Color, mark: Color, dark: Boolean, t: Float, d: Float) {
    val w = size.width; val h = size.height
    val phase = t * 2 * PI.toFloat()
    glow(line2, if (dark) 0.22f else 0.3f, w * 0.85f, h * 0.12f, w * 0.7f)
    // Gently winding parallel lines, like a map's contours; every fifth one is an index line.
    val step = 15f * d
    var k = 0
    var y0 = -step * 3
    while (y0 < h + step * 3) {
        val path = Path()
        var x = 0f
        fun yAt(x: Float) = y0 + h * (0.045f * sin(2 * PI.toFloat() * x / w * 1.2f + k * 0.13f + phase) + 0.028f * sin(2 * PI.toFloat() * x / w * 2.6f - k * 0.21f - phase))
        path.moveTo(0f, yAt(0f))
        while (x <= w + 6f) { path.lineTo(x, yAt(x)); x += 6f }
        val index = k % 5 == 0
        val color = lerp(line, line2, (y0 / h).coerceIn(0f, 1f))
        val alpha = (if (dark) 0.16f else 0.22f) * (if (index) 1.8f else 1f) * fade(y0, 0.25f)
        drawPath(path, color.copy(alpha = alpha), style = Stroke((if (index) 1.3f else 0.8f) * d))
        y0 += step; k++
    }
    // A summit in the top corner: closed rings drawn over the flowing lines.
    val cx = w * 0.78f; val cy = h * 0.16f
    for (ring in 1..9) {
        val rr = ring * 13f * d
        val path = Path()
        var a = 0f
        while (a <= 6.30f) {
            val rad = rr * (1f + 0.12f * sin(3 * a + ring * 0.5f + phase) + 0.06f * sin(5 * a - ring * 0.3f))
            val px = cx + cos(a) * rad; val py = cy + sin(a) * rad * 0.8f
            if (a == 0f) path.moveTo(px, py) else path.lineTo(px, py)
            a += 0.08f
        }
        path.close()
        drawPath(path, line.copy(alpha = (if (dark) 0.3f else 0.34f) * (1f - ring / 12f)), style = Stroke((if (ring % 3 == 0) 1.3f else 0.8f) * d))
    }
    drawCircle(mark.copy(alpha = if (dark) 0.8f else 0.7f), 2.4f * d, Offset(cx, cy))
}

private fun DrawScope.mosaic(r: Random, base: Color, a: Color, b: Color, c: Color, dark: Boolean) {
    val w = size.width; val h = size.height
    val cols = 6
    val cell = w / cols
    val rows = (h / cell).toInt() + 2
    // Jittered grid points, shared by neighbouring triangles so the facets meet.
    val pts = Array(rows + 1) { row -> Array(cols + 1) { col ->
        val jx = if (col == 0 || col == cols) 0f else (r.nextFloat() - 0.5f) * cell * 0.7f
        val jy = (r.nextFloat() - 0.5f) * cell * 0.7f
        Offset(col * cell + jx, row * cell + jy - cell * 0.5f)
    } }
    val strength = if (dark) 0.62f else 0.55f
    fun shade(center: Offset): Color {
        // A diagonal sweep through the three colours, each facet a little lighter or darker.
        val u = ((center.x / w) * 0.45f + (center.y / h) * 0.75f).coerceIn(0f, 1f)
        val tone = if (u < 0.5f) lerp(a, b, u * 2) else lerp(b, c, (u - 0.5f) * 2)
        val jitter = (r.nextFloat() - 0.5f) * 0.22f
        val shaded = if (jitter > 0) lerp(tone, Color.White, jitter) else lerp(tone, if (dark) Color.Black else base, -jitter)
        return lerp(base, shaded, strength * fade(center.y, 0.12f))
    }
    for (row in 0 until rows) for (col in 0 until cols) {
        val p00 = pts[row][col]; val p10 = pts[row][col + 1]; val p01 = pts[row + 1][col]; val p11 = pts[row + 1][col + 1]
        val flip = (row + col) % 2 == 0
        val tris = if (flip) listOf(listOf(p00, p10, p11), listOf(p00, p11, p01)) else listOf(listOf(p00, p10, p01), listOf(p10, p11, p01))
        for (tri in tris) {
            val path = Path().apply { moveTo(tri[0].x, tri[0].y); lineTo(tri[1].x, tri[1].y); lineTo(tri[2].x, tri[2].y); close() }
            val center = Offset((tri[0].x + tri[1].x + tri[2].x) / 3, (tri[0].y + tri[1].y + tri[2].y) / 3)
            drawPath(path, shade(center))
            drawPath(path, base.copy(alpha = if (dark) 0.35f else 0.45f), style = Stroke(1f))
        }
    }
}

private fun DrawScope.bokeh(r: Random, a: Color, b: Color, c: Color, dark: Boolean, t: Float, d: Float) {
    val w = size.width; val h = size.height
    glow(a, if (dark) 0.25f else 0.3f, w * 0.2f, h * 0.15f, w * 0.8f)
    glow(c, if (dark) 0.2f else 0.26f, w * 0.95f, h * 0.7f, w * 0.7f)
    val palette = listOf(a, b, c)
    repeat(28) {
        val rad = (16f + r.nextFloat() * r.nextFloat() * 80f) * d
        val x0 = r.nextFloat() * w
        val y0 = r.nextFloat() * (h + rad * 2)
        // Whole laps per loop, so nothing jumps when the animation starts over.
        val speed = 1f + r.nextInt(2)
        val sway = r.nextFloat() * 6.28f
        // Slowly rising, wrapping round at the top.
        val span = h + rad * 2
        val y = ((y0 - t * span * speed) % span + span) % span - rad
        val x = x0 + sin(t * 6.28f + sway) * 14f * d
        val color = palette[r.nextInt(3)]
        val alpha = (if (dark) 0.07f else 0.10f) + r.nextFloat() * (if (dark) 0.12f else 0.12f)
        val f = fade(y, 0.35f)
        drawCircle(color.copy(alpha = alpha * f), rad, Offset(x, y))
        drawCircle(color.copy(alpha = alpha * 1.6f * f), rad, Offset(x, y), style = Stroke(1.2f * d))
    }
}

private fun DrawScope.silk(a: Color, b: Color, c: Color, dark: Boolean, t: Float, d: Float) {
    val w = size.width; val h = size.height
    val phase = t * 2 * PI.toFloat()
    glow(a, if (dark) 0.22f else 0.28f, w * 0.1f, h * 0.1f, w * 0.7f)
    // Bundles of fine threads, each bundle a ribbon that folds as it crosses the page.
    data class Ribbon(val y: Float, val amp: Float, val freq: Float, val shift: Float, val colors: List<Color>, val strength: Float)
    val ribbons = listOf(
        Ribbon(0.16f, 0.07f, 1.1f, 0f, listOf(a, b), 1f),
        Ribbon(0.36f, 0.05f, 0.8f, 2.1f, listOf(b, c), 0.8f),
        Ribbon(0.78f, 0.06f, 1.3f, 4.0f, listOf(c, a), 0.6f),
    )
    for (rb in ribbons) {
        val threads = 26
        for (i in 0 until threads) {
            val off = i * 2.6f * d
            val twist = sin(i / threads.toFloat() * PI.toFloat())
            val path = Path()
            var x = -10f
            fun yAt(x: Float): Float {
                val u = x / w * 2 * PI.toFloat()
                return h * rb.y + h * rb.amp * sin(u * rb.freq + rb.shift + phase) + off * (0.6f + 0.4f * cos(u * rb.freq * 1.5f + rb.shift + phase))
            }
            path.moveTo(x, yAt(x))
            while (x <= w + 10f) { path.lineTo(x, yAt(x)); x += 8f }
            val alpha = (if (dark) 0.10f else 0.13f) * rb.strength * (0.4f + 0.6f * twist)
            drawPath(path, Brush.horizontalGradient(rb.colors.map { it.copy(alpha = alpha) }), style = Stroke(0.9f * d))
        }
    }
}

private fun DrawScope.horizon(r: Random, base: Color, sun: Color, sun2: Color, grid: Color, dark: Boolean, t: Float, d: Float) {
    val w = size.width; val h = size.height
    val horizonY = h * 0.5f
    // Sky: a soft glow behind the sun and a sprinkle of stars.
    glow(sun2, if (dark) 0.34f else 0.36f, w * 0.5f, horizonY, w * 0.9f)
    repeat(50) {
        val y = r.nextFloat() * horizonY * 0.85f
        drawCircle(grid.copy(alpha = (if (dark) 0.5f else 0.35f) * r.nextFloat()), (0.5f + r.nextFloat()) * d, Offset(r.nextFloat() * w, y))
    }
    // The sun, banded across its lower half.
    val sr = w * 0.27f
    val sc = Offset(w * 0.5f, horizonY - sr * 0.35f)
    drawCircle(Brush.verticalGradient(listOf(sun.copy(alpha = if (dark) 0.85f else 0.75f), sun2.copy(alpha = if (dark) 0.8f else 0.7f)), sc.y - sr, sc.y + sr), sr, sc)
    // The bands are cut out of the sun only, not the glow around it.
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(sc, sr)) }
    clipPath(disc) {
        for (i in 0 until 6) {
            val bandH = (2f + i * 1.6f) * d
            val y = sc.y + sr * (0.12f + i * 0.15f)
            if (y < horizonY) drawRect(base, Offset(sc.x - sr, y), androidx.compose.ui.geometry.Size(sr * 2, bandH))
        }
    }
    // Ground: hide the sun below the horizon, then a grid running off toward it.
    drawRect(base, Offset(0f, horizonY), androidx.compose.ui.geometry.Size(w, h - horizonY))
    drawRect(Brush.verticalGradient(listOf(sun2.copy(alpha = if (dark) 0.16f else 0.14f), Color.Transparent), horizonY, h), Offset(0f, horizonY), androidx.compose.ui.geometry.Size(w, h - horizonY))
    val gAlpha = if (dark) 0.34f else 0.3f
    drawLine(sun2.copy(alpha = gAlpha * 1.5f), Offset(0f, horizonY), Offset(w, horizonY), 1.2f * d)
    val lines = 14
    for (i in 0 until lines) {
        val u = ((i + t) / lines).coerceIn(0f, 1f)
        val y = horizonY + (h - horizonY) * u.pow(2.2f)
        drawLine(grid.copy(alpha = gAlpha * (0.25f + 0.75f * u) * fade(y, 0.35f)), Offset(0f, y), Offset(w, y), 1f * d)
    }
    val spokes = 16
    for (i in 0..spokes) {
        val bx = -w + (3 * w) * i / spokes
        drawLine(
            Brush.verticalGradient(listOf(grid.copy(alpha = gAlpha * 0.2f), grid.copy(alpha = gAlpha * 0.5f)), horizonY, h),
            Offset(w * 0.5f, horizonY), Offset(bx, h), 1f * d,
        )
    }
}
