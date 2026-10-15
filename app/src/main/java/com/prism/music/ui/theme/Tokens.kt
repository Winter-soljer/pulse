package com.prism.music.ui.theme

import android.graphics.Typeface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.FontChoice
import com.prism.music.data.prefs.TitleSize
import java.io.File

/**
 * The look & layout knobs every screen reads: how round things are, how much room they get,
 * how wide cards are and how big page titles are. Built from [AppSettings] by [PrismTheme].
 */
@Immutable
data class PrismUi(
    val corner: Float = 1f,
    val density: Float = 1f,
    val cardWidth: Dp = 152.dp,
    val titleSize: TitleSize = TitleSize.LARGE,
    val reduceMotion: Boolean = false,
) {
    /** A corner radius designed at [base] for the default "Round" setting. */
    fun r(base: Dp): Dp = base * corner
    fun shape(base: Dp): RoundedCornerShape = RoundedCornerShape(r(base))

    /** Vertical room designed at [base] for "Comfortable". */
    fun gap(base: Dp): Dp = base * density

    val tile: RoundedCornerShape get() = shape(16.dp)
    val card: RoundedCornerShape get() = shape(22.dp)
    val art: RoundedCornerShape get() = shape(12.dp)
    val smallArt: RoundedCornerShape get() = shape(9.dp)

    val materialShapes: Shapes
        get() = Shapes(
            extraSmall = shape(4.dp), small = shape(8.dp), medium = shape(12.dp),
            large = shape(18.dp), extraLarge = shape(28.dp),
        )

    companion object {
        fun from(s: AppSettings) = PrismUi(
            corner = s.corners.scale,
            density = s.uiDensity.scale,
            cardWidth = s.cardSize.widthDp.dp,
            titleSize = s.titleSize,
            reduceMotion = s.reduceMotion,
        )
    }
}

val LocalUi = staticCompositionLocalOf { PrismUi() }

/** The font picked for lyrics (#12), or null to keep each lyric style's own. Apply with [withLyricsFont]. */
val LocalLyricsFont = staticCompositionLocalOf<FontFamily?> { null }

/** [this] style in the lyrics font, when one is picked. */
@androidx.compose.runtime.Composable
fun androidx.compose.ui.text.TextStyle.withLyricsFont(): androidx.compose.ui.text.TextStyle =
    LocalLyricsFont.current?.let { copy(fontFamily = it) } ?: this

/** The app-wide typefaces. Variable device fonts are set up at every weight the UI uses. */
@OptIn(ExperimentalTextApi::class)
object AppFonts {
    private val cache = HashMap<FontChoice, FontFamily>()
    private val weights = listOf(300, 400, 500, 600, 700, 800, 900)
    private val sansFlex = arrayOf("/product/fonts/GoogleSansFlex-Regular.ttf", "/system/fonts/GoogleSansFlex-Regular.ttf")
    private val googleSans = arrayOf("/product/fonts/GoogleSans-Regular.ttf", "/system/fonts/GoogleSans-Regular.ttf")
    private val robotoFlex = arrayOf("/system/fonts/RobotoFlex-Regular.ttf")

    private fun file(vararg paths: String): File? = paths.map(::File).firstOrNull { it.canRead() }

    private fun variable(f: File, vararg extra: FontVariation.Setting): FontFamily = FontFamily(
        weights.map { w -> Font(f, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(w), *extra)) }
    )

    private fun build(choice: FontChoice): FontFamily = when (choice) {
        FontChoice.SYSTEM -> FontFamily.Default
        FontChoice.GOOGLE_SANS -> file(*sansFlex)?.let { variable(it) }
            ?: file(*googleSans)?.let { FontFamily(Font(it)) }
            ?: FontFamily.SansSerif
        FontChoice.ROUNDED -> file(*sansFlex)?.let { variable(it, FontVariation.Setting("ROND", 100f)) }
            ?: FontFamily.SansSerif
        FontChoice.CONDENSED -> file(*robotoFlex)?.let { variable(it, FontVariation.width(78f)) }
            ?: FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.MONO -> FontFamily.Monospace
        FontChoice.CUSTOM -> FontFamily.Default // see [family]
    }

    /**
     * Whether this phone has the font behind [choice]. Google Sans and Rounded need Google Sans Flex, which Pixels have
     * and Samsungs (among others) don't; without it they fell back to the same face as Default, so picking them seemed
     * to do nothing.
     */
    fun available(choice: FontChoice): Boolean = when (choice) {
        FontChoice.GOOGLE_SANS -> file(*sansFlex) != null || file(*googleSans) != null
        FontChoice.ROUNDED -> file(*sansFlex) != null
        FontChoice.CUSTOM -> customFile(FontSlot.APP) != null
        else -> true
    }

    fun family(choice: FontChoice): FontFamily =
        if (choice == FontChoice.CUSTOM) custom(FontSlot.APP) ?: FontFamily.Default
        else synchronized(cache) { cache.getOrPut(choice) { runCatching { build(choice) }.getOrDefault(FontFamily.Default) } }

    // ---------------------------------------------------------------- The listener's own fonts (#12)

    /** Where an imported font goes: one for the whole app, one for the lyrics. */
    enum class FontSlot(val fileName: String) { APP("app.font"), LYRICS("lyrics.font") }

    @Volatile private var dir: File? = null
    private val customCache = HashMap<FontSlot, Pair<Long, FontFamily>>()

    /** Called as Prism starts. */
    fun init(context: android.content.Context) { dir = File(context.filesDir, "fonts") }

    fun customFile(slot: FontSlot): File? = dir?.let { File(it, slot.fileName) }?.takeIf { it.canRead() }

    /** The imported font for [slot] (rebuilt when the file changes), or null if there's none or it won't load. */
    fun custom(slot: FontSlot): FontFamily? {
        val f = customFile(slot) ?: return null
        synchronized(customCache) {
            customCache[slot]?.takeIf { it.first == f.lastModified() }?.let { return it.second }
            // A variable font (Inter, Roboto Flex…) gets every weight from its own axis; a single-weight file is one
            // face that Compose thickens for bold.
            val family = runCatching { if (hasWeightAxis(f)) variable(f) else FontFamily(Font(f)) }.getOrNull() ?: return null
            customCache[slot] = f.lastModified() to family
            return family
        }
    }

    /**
     * Copies a font the listener picked ([uri], a .ttf / .otf) into Prism for [slot], after checking Android can read
     * it. Returns its name for Settings, or throws with a message to show.
     */
    fun import(context: android.content.Context, uri: android.net.Uri, slot: FontSlot): String {
        val target = File(context.filesDir, "fonts").apply { mkdirs() }
        val tmp = File(target, slot.fileName + ".new")
        context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            ?: error("Couldn't open that file")
        val ok = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 29) { android.graphics.fonts.Font.Builder(tmp).build(); true }
            else Typeface.createFromFile(tmp) != null
        }.getOrDefault(false)
        if (!ok || tmp.length() < 1024) { tmp.delete(); error("That isn't a font file Android can use (pick a .ttf or .otf)") }
        val out = File(target, slot.fileName)
        out.delete()
        if (!tmp.renameTo(out)) { tmp.delete(); error("Couldn't save the font") }
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        return name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Your font"
    }

    fun remove(slot: FontSlot) {
        customFile(slot)?.delete()
        synchronized(customCache) { customCache.remove(slot) }
    }

    /** Whether a TrueType / OpenType file has an 'fvar' table, i.e. is a variable font. */
    private fun hasWeightAxis(f: File): Boolean = runCatching {
        java.io.RandomAccessFile(f, "r").use { raf ->
            raf.seek(4)
            val tables = raf.readUnsignedShort()
            raf.seek(12)
            val tag = ByteArray(4)
            (0 until tables.coerceAtMost(256)).any {
                raf.readFully(tag)
                raf.skipBytes(12)
                String(tag, Charsets.US_ASCII) == "fvar"
            }
        }
    }.getOrDefault(false)
}
