package com.prism.music

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.prism.music.ui.theme.PrismIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws Prism's own icons (PrismIcons) large, light on dark and dark on light, onto app/build/icon-previews/icons.png. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconRenderTest {
    /** Every icon in PrismIcons, in the order they're declared. */
    private val icons: List<ImageVector> = PrismIcons::class.java.declaredFields
        .filter { it.type == ImageVector::class.java }
        .map { f -> f.isAccessible = true; f.get(PrismIcons) as ImageVector }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIcon(v: ImageVector, color: Color) {
        fun group(g: VectorGroup) {
            for (node in g) when (node) {
                is VectorPath -> {
                    val path = node.pathData.toPath().apply { fillType = node.pathFillType }
                    if (node.fill != null) drawPath(path, color)
                    if (node.stroke != null) drawPath(path, color, style = Stroke(node.strokeLineWidth, cap = node.strokeLineCap, join = node.strokeLineJoin))
                }
                is VectorGroup -> rotate(node.rotation, Offset(node.pivotX, node.pivotY)) { group(node) }
            }
        }
        group(v.root)
    }

    /**
     * A few icons in each Settings → Appearance → Icons style (#19), as `PrismIcons.line` draws them: Thin / Regular /
     * Bold, rounded and square, onto icon-previews/icon-styles.png.
     */
    @Test
    fun styles() {
        val sample = icons.take(30)
        val styles = com.prism.music.ui.theme.IconWeight.entries.flatMap { w -> listOf(w to true, w to false) }
        val cell = 96
        val bmp = Bitmap.createBitmap(sample.size * cell, styles.size * cell, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp.asImageBitmap()), Size(bmp.width.toFloat(), bmp.height.toFloat())) {
            drawRect(Color(0xFF121212))
            styles.forEachIndexed { r, (weight, rounded) ->
                sample.forEachIndexed { i, v ->
                    translate(i * cell + 12f, r * cell + 12f) {
                        scale(3f, Offset.Zero) {
                            fun group(g: VectorGroup) {
                                for (node in g) when (node) {
                                    is VectorPath -> {
                                        val path = node.pathData.toPath().apply { fillType = node.pathFillType }
                                        if (node.fill != null) drawPath(path, Color.White)
                                        // Restyle the current (Regular, rounded) icon the way IconStyle would build it.
                                        if (node.stroke != null) drawPath(
                                            path, Color.White,
                                            style = Stroke(
                                                node.strokeLineWidth / com.prism.music.ui.theme.IconStyle.weight.scale * weight.scale,
                                                cap = if (rounded) androidx.compose.ui.graphics.StrokeCap.Round else androidx.compose.ui.graphics.StrokeCap.Butt,
                                                join = if (rounded) androidx.compose.ui.graphics.StrokeJoin.Round else androidx.compose.ui.graphics.StrokeJoin.Miter,
                                            ),
                                        )
                                    }
                                    is VectorGroup -> rotate(node.rotation, Offset(node.pivotX, node.pivotY)) { group(node) }
                                }
                            }
                            group(v.root)
                        }
                    }
                }
            }
        }
        val dir = File("build/icon-previews").apply { mkdirs() }
        File(dir, "icon-styles.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * The Cupertino pack beside Prism's own icon it replaces (Prism left, Cupertino right, light on dark then dark on
     * light), onto icon-previews/cupertino.png.
     */
    @Test
    fun cupertinoPack() {
        val holder = PrismIcons::class.java.declaredClasses.first { it.simpleName == "Cupertino" }
        val instance = holder.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        val pack = holder.declaredFields.filter { it.type == ImageVector::class.java }
            .map { f -> f.isAccessible = true; f.name to (f.get(instance) as ImageVector) }
        val base = PrismIcons::class.java.declaredFields.filter { it.type == ImageVector::class.java }
            .associate { f -> f.isAccessible = true; f.name to (f.get(PrismIcons) as ImageVector) }
        val cell = 110
        val cols = 4 // pairs per row
        val rows = (pack.size + cols - 1) / cols
        val bmp = Bitmap.createBitmap(cols * cell * 2, rows * cell * 2, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp.asImageBitmap()), Size(bmp.width.toFloat(), bmp.height.toFloat())) {
            drawRect(Color(0xFF121212), Offset.Zero, Size(bmp.width.toFloat(), rows * cell.toFloat()))
            drawRect(Color(0xFFF4F4F4), Offset(0f, rows * cell.toFloat()), Size(bmp.width.toFloat(), rows * cell.toFloat()))
            for (half in 0..1) pack.forEachIndexed { i, (name, v) ->
                val ink = if (half == 0) Color.White else Color(0xFF111111)
                val x = (i % cols) * cell * 2 + 8f
                val y = (i / cols) * cell + 8f + half * rows * cell
                base[name]?.let { translate(x, y) { scale(3.8f, Offset.Zero) { drawIcon(it, ink.copy(alpha = 0.45f)) } } }
                translate(x + cell, y) { scale(3.8f, Offset.Zero) { drawIcon(v, ink) } }
            }
        }
        val dir = File("build/icon-previews").apply { mkdirs() }
        File(dir, "cupertino.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("cupertino: " + pack.joinToString { it.first })
    }

    @Test
    fun contactSheet() {
        val cell = 120
        val cols = 10
        val rows = (icons.size + cols - 1) / cols
        val bmp = Bitmap.createBitmap(cols * cell, rows * cell * 2, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp.asImageBitmap()), Size(bmp.width.toFloat(), bmp.height.toFloat())) {
            drawRect(Color(0xFF121212), Offset.Zero, Size(bmp.width.toFloat(), rows * cell.toFloat()))
            drawRect(Color(0xFFF4F4F4), Offset(0f, rows * cell.toFloat()), Size(bmp.width.toFloat(), rows * cell.toFloat()))
            for (half in 0..1) icons.forEachIndexed { i, v ->
                val x = (i % cols) * cell + 12f
                val y = (i / cols) * cell + 12f + half * rows * cell
                translate(x, y) { scale(4f, Offset.Zero) { drawIcon(v, if (half == 0) Color.White else Color(0xFF111111)) } }
            }
        }
        val dir = File("build/icon-previews").apply { mkdirs() }
        File(dir, "icons.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("wrote ${File(dir, "icons.png").absolutePath}")
    }
}
