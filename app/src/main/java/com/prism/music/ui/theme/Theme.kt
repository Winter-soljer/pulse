package com.prism.music.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.prism.music.AppContainer
import com.prism.music.data.prefs.AppSettings
import com.prism.music.data.prefs.ColorSource
import com.prism.music.data.prefs.ThemeMode

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }
val LocalIsDark = staticCompositionLocalOf { true }

val PrismViolet = Color(0xFF7C5CFF)

val AccentSwatches = listOf(
    0xFF7C5CFF, 0xFFFF3B5C, 0xFFFF6B35, 0xFFFFB627, 0xFF2EC27E, 0xFF00B4D8,
    0xFF3A86FF, 0xFFE056FD, 0xFFFF5D8F, 0xFF9BE15D, 0xFFB08968, 0xFFFFFFFF,
).map { Color(it) }

/** Greys have no hue to build a scheme around (theirs reads as 0°, red), so they get a neutral one. */
fun isNeutralSeed(argb: Int): Boolean {
    val out = FloatArray(3)
    ColorUtils.colorToHSL(argb, out)
    return out[1] < 0.08f || out[2] > 0.97f || out[2] < 0.03f
}

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360) + 360) % 360, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

/**
 * White (or any grey): pure white accents in dark mode; in light mode, where white on white would
 * vanish, the same monochrome look in near-black ink. No tint anywhere.
 */
private fun neutralScheme(dark: Boolean, pureBlack: Boolean): ColorScheme {
    fun g(l: Float) = hsl(0f, 0f, l)
    return if (dark) {
        val bg = if (pureBlack) Color.Black else g(0.055f)
        darkColorScheme(
            primary = Color.White, onPrimary = g(0.08f),
            primaryContainer = g(0.26f), onPrimaryContainer = Color.White, inversePrimary = g(0.20f),
            secondary = g(0.82f), onSecondary = g(0.10f),
            secondaryContainer = g(0.22f), onSecondaryContainer = g(0.95f),
            tertiary = g(0.70f), onTertiary = g(0.10f),
            tertiaryContainer = g(0.24f), onTertiaryContainer = g(0.95f),
            background = bg, onBackground = g(0.95f), surface = bg, onSurface = g(0.95f),
            surfaceVariant = g(0.20f), onSurfaceVariant = g(0.74f), surfaceTint = Color.White,
            inverseSurface = g(0.93f), inverseOnSurface = g(0.12f),
            outline = g(0.45f), outlineVariant = g(0.26f),
            surfaceBright = g(if (pureBlack) 0.16f else 0.20f), surfaceDim = bg,
            surfaceContainerLowest = if (pureBlack) Color.Black else g(0.04f),
            surfaceContainerLow = g(if (pureBlack) 0.05f else 0.085f),
            surfaceContainer = g(if (pureBlack) 0.07f else 0.105f),
            surfaceContainerHigh = g(if (pureBlack) 0.10f else 0.135f),
            surfaceContainerHighest = g(if (pureBlack) 0.13f else 0.165f),
        )
    } else {
        val bg = g(0.985f)
        lightColorScheme(
            primary = g(0.12f), onPrimary = Color.White,
            primaryContainer = g(0.88f), onPrimaryContainer = g(0.08f), inversePrimary = Color.White,
            secondary = g(0.34f), onSecondary = Color.White,
            secondaryContainer = g(0.90f), onSecondaryContainer = g(0.10f),
            tertiary = g(0.45f), onTertiary = Color.White,
            tertiaryContainer = g(0.90f), onTertiaryContainer = g(0.10f),
            background = bg, onBackground = g(0.08f), surface = bg, onSurface = g(0.08f),
            surfaceVariant = g(0.91f), onSurfaceVariant = g(0.34f), surfaceTint = g(0.12f),
            inverseSurface = g(0.16f), inverseOnSurface = g(0.95f),
            outline = g(0.52f), outlineVariant = g(0.82f),
            surfaceBright = bg, surfaceDim = g(0.88f),
            surfaceContainerLowest = Color.White, surfaceContainerLow = g(0.965f),
            surfaceContainer = g(0.95f), surfaceContainerHigh = g(0.93f), surfaceContainerHighest = g(0.905f),
        )
    }
}

/** Builds a full Material 3 scheme from a single seed colour (tonal approximation). */
fun schemeFromSeed(seed: Color, dark: Boolean, pureBlack: Boolean): ColorScheme {
    if (isNeutralSeed(seed.toArgb())) return neutralScheme(dark, pureBlack)
    val out = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), out)
    val h = out[0]
    val s = out[1].coerceIn(0.30f, 0.92f)
    val n = 0.10f
    return if (dark) {
        val bg = if (pureBlack) Color.Black else hsl(h, n * 1.4f, 0.055f)
        darkColorScheme(
            primary = hsl(h, s, 0.76f),
            onPrimary = hsl(h, s, 0.14f),
            primaryContainer = hsl(h, s * 0.75f, 0.30f),
            onPrimaryContainer = hsl(h, s, 0.92f),
            inversePrimary = hsl(h, s, 0.42f),
            secondary = hsl(h, s * 0.35f, 0.76f),
            onSecondary = hsl(h, s * 0.35f, 0.16f),
            secondaryContainer = hsl(h, s * 0.30f, 0.26f),
            onSecondaryContainer = hsl(h, s * 0.35f, 0.92f),
            tertiary = hsl(h + 45f, s * 0.6f, 0.78f),
            onTertiary = hsl(h + 45f, s * 0.6f, 0.16f),
            tertiaryContainer = hsl(h + 45f, s * 0.5f, 0.28f),
            onTertiaryContainer = hsl(h + 45f, s * 0.6f, 0.92f),
            background = bg,
            onBackground = hsl(h, n, 0.93f),
            surface = bg,
            onSurface = hsl(h, n, 0.93f),
            surfaceVariant = hsl(h, n * 1.5f, 0.20f),
            onSurfaceVariant = hsl(h, n * 1.2f, 0.74f),
            surfaceTint = hsl(h, s, 0.76f),
            inverseSurface = hsl(h, n, 0.92f),
            inverseOnSurface = hsl(h, n, 0.12f),
            outline = hsl(h, n, 0.45f),
            outlineVariant = hsl(h, n, 0.26f),
            surfaceBright = hsl(h, n * 1.4f, if (pureBlack) 0.16f else 0.20f),
            surfaceDim = bg,
            surfaceContainerLowest = if (pureBlack) Color.Black else hsl(h, n * 1.4f, 0.04f),
            surfaceContainerLow = hsl(h, n * 1.4f, if (pureBlack) 0.05f else 0.085f),
            surfaceContainer = hsl(h, n * 1.4f, if (pureBlack) 0.07f else 0.105f),
            surfaceContainerHigh = hsl(h, n * 1.4f, if (pureBlack) 0.10f else 0.135f),
            surfaceContainerHighest = hsl(h, n * 1.4f, if (pureBlack) 0.13f else 0.165f),
        )
    } else {
        val bg = hsl(h, 0.30f, 0.985f)
        lightColorScheme(
            primary = hsl(h, s, 0.42f),
            onPrimary = Color.White,
            primaryContainer = hsl(h, s, 0.90f),
            onPrimaryContainer = hsl(h, s, 0.14f),
            inversePrimary = hsl(h, s, 0.78f),
            secondary = hsl(h, s * 0.30f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = hsl(h, s * 0.40f, 0.90f),
            onSecondaryContainer = hsl(h, s * 0.35f, 0.14f),
            tertiary = hsl(h + 45f, s * 0.6f, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(h + 45f, s * 0.6f, 0.90f),
            onTertiaryContainer = hsl(h + 45f, s * 0.6f, 0.14f),
            background = bg,
            onBackground = hsl(h, n, 0.10f),
            surface = bg,
            onSurface = hsl(h, n, 0.10f),
            surfaceVariant = hsl(h, 0.18f, 0.91f),
            onSurfaceVariant = hsl(h, n, 0.34f),
            surfaceTint = hsl(h, s, 0.42f),
            inverseSurface = hsl(h, n, 0.18f),
            inverseOnSurface = hsl(h, n, 0.95f),
            outline = hsl(h, n, 0.52f),
            outlineVariant = hsl(h, n, 0.82f),
            surfaceBright = bg,
            surfaceDim = hsl(h, 0.15f, 0.88f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, 0.30f, 0.965f),
            surfaceContainer = hsl(h, 0.28f, 0.95f),
            surfaceContainerHigh = hsl(h, 0.25f, 0.93f),
            surfaceContainerHighest = hsl(h, 0.22f, 0.905f),
        )
    }
}

private val base = Typography()

/** Tight, confident headings over a calm body; [family] is the typeface picked in Appearance. */
fun prismTypography(family: FontFamily = FontFamily.Default): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.035).em).f(),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.03).em).f(),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.025).em).f(),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.025).em).f(),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em).f(),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em).f(),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.012).em).f(),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.005).em).f(),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold).f(),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold).f(),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium).f(),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium).f(),
        bodyLarge = base.bodyLarge.copy(letterSpacing = 0.1.sp).f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
    )
}

val PrismTypography = prismTypography()

val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)

@Composable
fun isAppDark(settings: AppSettings = LocalAppSettings.current): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PrismTheme(settings: AppSettings, artworkColor: Color?, content: @Composable () -> Unit) {
    val dark = isAppDark(settings)
    val context = LocalContext.current
    val seedTarget = when (settings.colorSource) {
        ColorSource.ARTWORK -> artworkColor ?: Color(settings.accent)
        ColorSource.CUSTOM -> Color(settings.accent)
        ColorSource.DYNAMIC -> Color(settings.accent)
    }
    val seed by animateColorAsState(seedTarget, tween(900), label = "seed")
    val scheme = if (settings.colorSource == ColorSource.DYNAMIC && Build.VERSION.SDK_INT >= 31) {
        val d = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (dark && settings.pureBlack) d.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black) else d
    } else remember(seed, dark, settings.pureBlack) { schemeFromSeed(seed, dark, settings.pureBlack) }

    val ui = remember(settings.corners, settings.uiDensity, settings.cardSize, settings.titleSize, settings.reduceMotion) { PrismUi.from(settings) }
    val typography = remember(settings.fontChoice, settings.fontStamp) { prismTypography(AppFonts.family(settings.fontChoice)) }
    // Settings → Appearance → Text → "Lyrics font" (#12): null keeps each lyric style's own face.
    val lyricsFont = remember(settings.lyricsFont, settings.fontStamp) {
        when (settings.lyricsFont) {
            "" -> null
            com.prism.music.data.prefs.FontChoice.CUSTOM.name -> AppFonts.custom(AppFonts.FontSlot.LYRICS)
            else -> com.prism.music.data.prefs.FontChoice.entries.firstOrNull { it.name == settings.lyricsFont }?.let(AppFonts::family)
        }
    }
    val density = LocalDensity.current
    // The app's own text size sits on top of the phone's.
    val scaled = remember(density, settings.textScale) { Density(density.density, density.fontScale * settings.textScale) }

    CompositionLocalProvider(LocalAppSettings provides settings, LocalIsDark provides dark, LocalUi provides ui, LocalDensity provides scaled, LocalLyricsFont provides lyricsFont) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = ui.materialShapes) {
            // Text and icons outside a Surface read LocalContentColor, which
            // otherwise defaults to black — unreadable in dark mode.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
