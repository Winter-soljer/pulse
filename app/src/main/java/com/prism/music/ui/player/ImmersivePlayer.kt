package com.prism.music.ui.player

import com.prism.music.ui.theme.withLyricsFont
import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.prism.music.data.canvas.CanvasArtwork
import com.prism.music.data.lyrics.LyricLine
import com.prism.music.data.model.Song
import com.prism.music.data.model.hiRes
import com.prism.music.ui.components.ActionGroup
import com.prism.music.ui.components.ActionHeader
import com.prism.music.ui.components.ActionItem
import com.prism.music.ui.components.ActionSheet
import com.prism.music.ui.components.RoundAction
import com.prism.music.ui.components.runAction
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.delay
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.prism.music.data.prefs.ImmersiveLyrics
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.prism.music.ui.components.FloatingDisc
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.lerp
import com.prism.music.data.prefs.ImmersiveArt
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.rememberHaptics

/** The lines' look: big, bold and centred, like a lyric video's. */
private val lineShadow = Shadow(Color.Black.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(0f, 2f), 14f)

/**
 * Immersive mode (the player's ⋯ menu): the cover fills the screen (its animated version when it has one) and the
 * lyrics play over its lower part, a line at a time, as in a lyric video. How much of the cover shows is up to the
 * listener ([ImmersiveArt]): zoomed out, it sits at the top and dissolves into a blurred copy of itself. Nothing else
 * shows; a tap brings up a settings button for a few seconds (back to the normal player, artwork size, lyrics timing
 * and source, the song's options). Swipe left or right for the next or previous song, as on the normal player; a drag
 * down closes the player.
 */
@Composable
fun ImmersivePlayer(
    song: Song,
    /** The song's animated cover, if it has one (and it's allowed here): it plays in place of the still. */
    canvas: CanvasArtwork?,
    canvasSaved: Boolean,
    isPlaying: Boolean,
    lyricsState: LyricsState,
    /** A sheet is over the player. The clip and the lyrics keep going under it (they used to freeze, which read as a bug). */
    covered: Boolean,
    onExit: () -> Unit,
    onLyricsTiming: () -> Unit,
    onLyricsSource: () -> Unit,
    onSongOptions: () -> Unit,
    onCollapse: () -> Unit,
) {
    val view = LocalView.current
    val c = LocalContainer.current
    val settings by c.settings.flow.collectAsState()
    val art = settings.immersiveArt
    val place = settings.immersiveLyrics
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var button by remember { mutableStateOf(false) }
    // A tap shows the button for a few seconds; another tap (or the time) hides it.
    // Held down, the button stays up however long that takes.
    var pressing by remember { mutableStateOf(false) }
    LaunchedEffect(button, menu, pressing) { if (button && !menu && !pressing) { delay(4000); button = false } }

    // Edge to edge for real: the system bars hide (a swipe from the edge shows them for a moment), and the screen stays on.
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }

    var drag by remember { mutableFloatStateOf(0f) }
    // Sideways, the cover follows the finger a little; past the mark, letting go changes the song.
    var swipe by remember { mutableFloatStateOf(0f) }
    val blurred = rememberFullArtworkBlur(song.thumbnail)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { translationY = drag.coerceAtLeast(0f) }
            .pointerInput(Unit) { detectTapGestures(onTap = { button = !button }) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (drag > 220f) onCollapse(); drag = 0f },
                    onDragCancel = { drag = 0f },
                ) { change, amount -> change.consume(); drag += amount }
            }
            .pointerInput(Unit) {
                val mark = 72.dp.toPx()
                fun settle() { val from = swipe; scope.launch { animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> swipe = v } } }
                detectHorizontalDragGestures(
                    onDragEnd = {
                        // Left for the next song, right for the previous one (or the start of this one), as on the player.
                        if (swipe <= -mark) { haptics.gestureEnd(); c.player.next() }
                        else if (swipe >= mark) { haptics.gestureEnd(); c.player.previous() }
                        settle()
                    },
                    onDragCancel = { settle() },
                ) { change, d ->
                    change.consume()
                    if ((abs(swipe) < mark) != (abs(swipe + d) < mark)) haptics.threshold()
                    swipe += d
                }
            },
    ) {
        // ---- Behind a zoomed-out cover, the cover blurred, so it still fills the screen.
        if (art != ImmersiveArt.FILL) FullArtworkBlurBackdrop(blurred, scrim = 0.8f)

        // ---- The cover; the animated one over it once its first frame is in.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fill = art == ImmersiveArt.FILL
            // Zoomed out, a square from the top: as wide as the screen at most (the whole cover), as tall at least.
            val side = lerp(minOf(maxWidth, maxHeight), maxOf(maxWidth, maxHeight), art.zoom)
            val fade = if (fill) 0f else 0.3f
            var canvasCover by remember(canvas) { mutableFloatStateOf(0f) }
            Box(
                (if (fill) Modifier.fillMaxSize() else Modifier.align(Alignment.TopCenter).requiredSize(side))
                    .graphicsLayer { translationX = swipe * 0.35f },
            ) {
                AsyncImage(
                    hiRes(song.thumbnail, 1200), null, contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - canvasCover; compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (fade > 0f) drawRect(
                                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height * (1f - fade), endY = size.height),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                )
                // Filling a phone held upright, a tall version of the clip (when there is one) fits better than the square one.
                val clip = remember(canvas, fill) {
                    canvas?.let { cv -> if (fill) cv.fallbackUrl?.let { tall -> cv.copy(url = tall, fallbackUrl = cv.url) } ?: cv else cv }
                }
                clip?.let {
                    CanvasPlayer(it, canvasSaved, isPlaying, Modifier.fillMaxSize(), bottomFade = fade, onCoverChanged = { v -> canvasCover = v })
                }
            }
        }

        // ---- Shade where the lyrics are, so they (and the button, at the bottom) read over any cover.
        val black = { a: Float -> Color.Black.copy(alpha = a) }
        Crossfade(place, label = "immersiveShade") { p ->
            val stops = when (p) {
                ImmersiveLyrics.HIGH -> arrayOf(0f to black(0.62f), 0.42f to black(0.18f), 0.62f to Color.Transparent, 0.84f to black(0.2f), 1f to black(0.55f))
                ImmersiveLyrics.MIDDLE -> arrayOf(0f to black(0.25f), 0.28f to black(0.3f), 0.5f to black(0.5f), 0.72f to black(0.3f), 1f to black(0.55f))
                ImmersiveLyrics.LOW -> arrayOf(0f to black(0.28f), 0.16f to Color.Transparent, 0.5f to Color.Transparent, 0.78f to black(0.42f), 1f to black(0.78f))
                ImmersiveLyrics.FULL -> arrayOf(0f to black(0.5f), 0.5f to black(0.46f), 1f to black(0.66f))
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(*stops)))
        }

        // ---- Lyrics: a line at a time high, in the middle or low on the screen, or all of them over it.
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        when (place) {
            ImmersiveLyrics.FULL -> LyricsOverAll(song, lyricsState, isPlaying, Modifier.fillMaxSize().padding(horizontal = 28.dp))
            else -> {
                val align = when (place) { ImmersiveLyrics.HIGH -> Alignment.TopCenter; ImmersiveLyrics.MIDDLE -> Alignment.Center; else -> Alignment.BottomCenter }
                Box(
                    Modifier
                        .align(align)
                        .fillMaxWidth()
                        .then(if (place == ImmersiveLyrics.MIDDLE) Modifier else Modifier.fillMaxHeight(0.5f))
                        .padding(
                            start = 28.dp, end = 28.dp,
                            top = if (place == ImmersiveLyrics.HIGH) insets.calculateTopPadding() + 72.dp else 0.dp,
                            // Low, there's room underneath for the settings button.
                            bottom = if (place == ImmersiveLyrics.LOW) insets.calculateBottomPadding() + 132.dp else 0.dp,
                        ),
                    contentAlignment = align,
                ) {
                    LineAtATime(song, lyricsState, isPlaying, align)
                }
            }
        }

        // ---- The settings button, on a tap: low down, under the thumb. Held, it fills and leaves immersive mode.
        AnimatedVisibility(
            button || menu, Modifier.align(Alignment.BottomCenter).padding(bottom = insets.calculateBottomPadding() + 28.dp),
            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f), exit = fadeOut(tween(300)),
        ) {
            HoldToLeaveButton(
                onTap = { menu = true },
                onPressing = { pressing = it },
                onHeld = { haptics.gestureEnd(); onExit() },
            )
        }
    }

    if (menu) ActionSheet(song.thumbnail, { menu = false; button = false }) { close ->
        ActionHeader(song.thumbnail, song.title, song.artistText)
        ActionGroup(
            "Immersive mode",
            listOf(
                ActionItem(PrismIcons.FullscreenExit, "Show the player", onClick = onExit),
                // Fill the screen → zoomed out → the whole cover, round again.
                ActionItem(PrismIcons.AspectRatio, "Artwork", value = art.label, keepOpen = true) {
                    c.settings.setImmersiveArt(ImmersiveArt.entries[(art.ordinal + 1) % ImmersiveArt.entries.size])
                },
                ActionItem(PrismIcons.More, "Song options", onClick = onSongOptions),
            ),
        ) { runAction(it, close) }
        ActionGroup(
            "Lyrics",
            listOf(
                // High → middle → low → over the whole screen, round again.
                ActionItem(PrismIcons.Lyrics, "Lyrics position", value = place.label, keepOpen = true) {
                    c.settings.setImmersiveLyrics(ImmersiveLyrics.entries[(place.ordinal + 1) % ImmersiveLyrics.entries.size])
                },
                ActionItem(PrismIcons.Timer, "Lyrics timing", onClick = onLyricsTiming),
                ActionItem(PrismIcons.Lyrics, "Lyrics source", onClick = onLyricsSource),
            ),
        ) { runAction(it, close) }
    }
}

/** What's sung now, big, with the next line small under it; the song's name before the first line and without lyrics. */
@Composable
private fun LineAtATime(song: Song, state: LyricsState, active: Boolean, align: Alignment) {
    val lines = state.lines
    val synced = lines.any { it.timeMs >= 0 }
    val lineStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center, textDirection = TextDirection.Content, shadow = lineShadow,
    ).withLyricsFont()
    if (lines.isEmpty() || !synced) {
        // Plain lyrics can't be followed along: the song's name stays up, as at the start of a lyric video.
        TitleCard(song)
        return
    }
    val clock = rememberLyricClock(active)
    val index by remember(lines) { derivedStateOf { lines.indexAtOrBefore(clock.value) } }
    val firstSung = remember(lines) { lines.indexOfFirst { !it.isGap } }
    AnimatedContent(
        index,
        transitionSpec = {
            (fadeIn(tween(420, easing = FastOutSlowInEasing)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 3 }) togetherWith
                (fadeOut(tween(260)) + slideOutVertically(tween(420, easing = FastOutSlowInEasing)) { -it / 3 }) using SizeTransform(clip = false)
        },
        contentAlignment = align,
        label = "immersiveLine",
    ) { i ->
        val line = lines.getOrNull(i)?.takeIf { !it.isGap }
        val next = remember(lines, i) { lines.drop(i + 1).firstOrNull { !it.isGap } }
        val arrange = when (align) { Alignment.TopCenter -> Arrangement.Top; Alignment.Center -> Arrangement.Center; else -> Arrangement.Bottom }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = arrange) {
            when {
                // Before the first line: the song's name, as a lyric video opens.
                line == null && i < firstSung -> TitleCard(song)
                line == null -> Text("♪", style = lineStyle, color = Color.White.copy(alpha = 0.7f))
                line.isWordSynced -> SweptLine(line, clock, lineStyle, Modifier.fillMaxWidth(), maxLines = 4, dimAlpha = 0.45f)
                else -> Text(line.text, style = lineStyle, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            }
            // The line to come, small and dim under it.
            next?.let { n ->
                Spacer(Modifier.height(14.dp))
                Text(
                    n.text, color = Color.White.copy(alpha = 0.55f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, textDirection = TextDirection.Content, shadow = lineShadow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TitleCard(song: Song) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            song.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, shadow = lineShadow),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            song.artistText, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, shadow = lineShadow),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The line playing at [ms] (the last one started), or -1 before the first. */
private fun List<LyricLine>.indexAtOrBefore(ms: Long): Int {
    var idx = -1
    for (i in indices) if (this[i].timeMs in 0..ms) idx = i else if (this[i].timeMs > ms) break
    return idx
}

/** How long the settings button is held to leave immersive mode. */
private const val HOLD_TO_LEAVE_MS = 2500

/**
 * Immersive mode's settings button: a tap opens its settings; held down, a ring fills around it (with a hint above)
 * and, once it's full, [onHeld] takes you back to the normal player. Letting go early empties the ring again.
 */
@Composable
private fun HoldToLeaveButton(onTap: () -> Unit, onPressing: (Boolean) -> Unit, onHeld: () -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    var held by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(held, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Text(
                "Keep holding to leave immersive mode", color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(shadow = lineShadow),
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Box(
            Modifier
                .size(54.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        held = true
                        onPressing(true)
                        val fill = scope.launch {
                            progress.animateTo(1f, tween((HOLD_TO_LEAVE_MS * (1f - progress.value)).toInt(), easing = LinearEasing))
                            onHeld()
                        }
                        val up = waitForUpOrCancellation()
                        up?.consume()
                        held = false
                        onPressing(false)
                        if (progress.value < 1f) {
                            // A quick press is a tap; a longer one let go early just empties the ring.
                            val tap = up != null && progress.value < 0.15f
                            fill.cancel()
                            scope.launch { progress.animateTo(0f, tween(250)) }
                            if (tap) onTap()
                        }
                    }
                }
                .drawBehind {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2
                    val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val at = androidx.compose.ui.geometry.Offset(inset, inset)
                    if (progress.value > 0f || held) drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, at, arc, style = Stroke(stroke))
                    drawArc(Color.White, -90f, 360f * progress.value, false, at, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                },
            contentAlignment = Alignment.Center,
        ) {
            FloatingDisc { ink -> Icon(PrismIcons.SettingsFilled, "Immersive mode settings (hold to leave)", Modifier.size(22.dp), tint = ink) }
        }
    }
}

/**
 * Every line over the whole screen, as in the lyrics panel but freer: the one being sung large and bright a little
 * above the middle, the others smaller and dimmer the further away they are, and the list gliding along by itself
 * (a drag on the screen stays the player's: down to close, sideways for another song). The edges fade out.
 */
@Composable
private fun LyricsOverAll(song: Song, state: LyricsState, active: Boolean, modifier: Modifier) {
    val lines = state.lines
    if (lines.isEmpty() || lines.none { it.timeMs >= 0 }) {
        Box(modifier, contentAlignment = Alignment.Center) { TitleCard(song) }
        return
    }
    val clock = rememberLyricClock(active)
    val index by remember(lines) { derivedStateOf { lines.indexAtOrBefore(clock.value) } }
    val list = rememberLazyListState()
    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center, textDirection = TextDirection.Content, shadow = lineShadow,
    ).withLyricsFont()
    LaunchedEffect(lines, index) {
        val target = index.coerceAtLeast(0)
        // Glide to a line on screen; jump to one further off (a seek).
        val seen = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
        if (seen != null) list.animateScrollBy(seen.offset.toFloat(), tween(700, easing = FastOutSlowInEasing)) else list.scrollToItem(target)
    }
    BoxWithConstraints(modifier) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.16f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
            state = list,
            // The line being sung sits just above the middle.
            contentPadding = PaddingValues(top = maxHeight * 0.36f, bottom = maxHeight * 0.6f),
            userScrollEnabled = false,
        ) {
            itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                val current = i == index
                val away = abs(i - index).coerceAtMost(4)
                val alpha by animateFloatAsState(if (current) 1f else 0.5f - 0.07f * away, tween(500), label = "lineAlpha")
                val scale by animateFloatAsState(if (current) 1f else 0.86f, tween(600, easing = FastOutSlowInEasing), label = "lineScale")
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp).graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        line.isGap -> Text("♪", style = style, color = Color.White)
                        current && line.isWordSynced -> SweptLine(line, clock, style, Modifier.fillMaxWidth(), dimAlpha = 0.45f)
                        else -> Text(line.text, style = style, color = Color.White, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
