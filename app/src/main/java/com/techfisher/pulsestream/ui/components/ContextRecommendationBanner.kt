package com.techfisher.pulsestream.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techfisher.pulsestream.data.context.ContextEngine
import com.techfisher.pulsestream.data.context.ContextVibe
import com.techfisher.pulsestream.playback.QueueKind
import com.techfisher.pulsestream.playback.QueueSource
import com.techfisher.pulsestream.ui.LocalNavigator
import com.techfisher.pulsestream.ui.Routes
import com.techfisher.pulsestream.ui.theme.LocalContainer
import com.techfisher.pulsestream.ui.theme.PulseIcons
import kotlinx.coroutines.launch

@Composable
fun ContextRecommendationBanner(
    onOpenAiPlaylist: (initialPrompt: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentVibe: ContextVibe = remember { ContextEngine.getCurrentContext() }
    var isGenerating by remember { mutableStateOf(false) }

    val gradient = remember(currentVibe) {
        val colors = currentVibe.gradientColors.map { Color(it) }
        Brush.horizontalGradient(
            colors = if (colors.size >= 2) colors else listOf(Color(0xFF2C3E50), Color(0xFF000000))
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 4.dp,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentVibe.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                        )
                    }

                    // AI Playlist generation trigger
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { onOpenAiPlaylist(currentVibe.defaultQuery) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = PulseIcons.Replay,
                                contentDescription = "AI Playlist",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "AI Mix",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = currentVibe.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.85f)
                    ),
                )

                Spacer(Modifier.height(12.dp))

                // Scrollable Contextual Vibe Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    currentVibe.vibes.forEach { vibe ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .clickable {
                                    nav.go(Routes.SEARCH)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "#$vibe",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Play Context Mix Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (isGenerating) return@FilledTonalButton
                            isGenerating = true
                            scope.launch {
                                val songs = runCatching {
                                    ContextEngine.generateContextMix(currentVibe, c)
                                }.getOrDefault(emptyList())

                                isGenerating = false
                                if (songs.isNotEmpty()) {
                                    c.player.playQueue(
                                        songs,
                                        0,
                                        QueueSource(QueueKind.RADIO, currentVibe.title)
                                    )
                                    Toast.makeText(
                                        context,
                                        "Playing ${currentVibe.title} (${songs.size} tracks)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Could not fetch contextual mix right now",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Curating Mix…", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(
                                imageVector = PulseIcons.Play,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Black
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Play Context Mix", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Text(
                        text = "Real-time context",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}
