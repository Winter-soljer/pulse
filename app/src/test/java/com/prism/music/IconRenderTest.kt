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
