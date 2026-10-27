package com.techfisher.pulsestream.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.techfisher.pulsestream.data.ai.AiPlaylistGenerator
import com.techfisher.pulsestream.data.ai.GeneratedPlaylist
import com.techfisher.pulsestream.data.model.Song
import com.techfisher.pulsestream.data.nlp.NaturalSearchEngine
import com.techfisher.pulsestream.playback.QueueKind
import com.techfisher.pulsestream.playback.QueueSource
import com.techfisher.pulsestream.ui.LocalNavigator
import com.techfisher.pulsestream.ui.Routes
import com.techfisher.pulsestream.ui.theme.LocalContainer
import com.techfisher.pulsestream.ui.theme.PulseIcons
import kotlinx.coroutines.launch

private val PRESET_PROMPTS = listOf(
    "🌌 Late Night Synth",
    "☕ Coffeehouse Acoustic",
    "⚡ High Energy Workout",
    "🌧️ Rainy Day Lofi",
    "🔥 2000s R&B Nostalgia",
    "🧘 Deep Ambient Focus",
    "🏎️ Fast Hyperpop & Drum & Bass",
    "🏖️ Tropical Summer Chill",
)

@Composable
fun AiPlaylistDialog(
    initialPrompt: String = "",
    onDismiss: () -> Unit,
) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val generator = remember { AiPlaylistGenerator(c) }

    var prompt by remember { mutableStateOf(initialPrompt) }
    var trackCount by remember { mutableIntStateOf(25) }
    var isGenerating by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var generatedResult by remember { mutableStateOf<GeneratedPlaylist?>(null) }
    var playlistTitle by remember { mutableStateOf("") }

    val recognizedIntent = remember(prompt) {
        if (prompt.isNotBlank()) NaturalSearchEngine.parse(prompt) else null
    }

    AlertDialog(
        onDismissRequest = {
            if (!isGenerating && !isSaving) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    PulseIcons.Replay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (generatedResult == null) "AI Playlist Creator" else "Preview Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (generatedResult == null) {
                    Text(
                        "Describe your ideal music vibe, mood, activity, or favorite sounds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(200) },
                        placeholder = { Text("e.g. Melodic techno for late night focus") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        enabled = !isGenerating,
                    )

                    // Recognition badges
                    recognizedIntent?.let { intent ->
                        if (intent.mood != null || intent.genre != null || intent.activity != null || intent.era != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                intent.mood?.let { VibeChip("Mood: $it") }
                                intent.genre?.let { VibeChip("Genre: $it") }
                                intent.activity?.let { VibeChip("Vibe: $it") }
                                intent.era?.let { VibeChip("Era: $it") }
                            }
                        }
                    }

                    // Prompt Presets
                    Text(
                        "Or pick an inspiration:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PRESET_PROMPTS.forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable(enabled = !isGenerating) {
                                    prompt = preset.replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}]+\\s*"), "")
                                },
                            ) {
                                Text(
                                    preset,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    // Track count selection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Length:", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15, 25, 40).forEach { count ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (trackCount == count) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { trackCount = count },
                                ) {
                                    Text(
                                        "$count tracks",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (trackCount == count) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (isGenerating) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Curating tracks & mixing radio seeds...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    // Generated preview mode
                    val result = generatedResult!!

                    OutlinedTextField(
                        value = playlistTitle,
                        onValueChange = { playlistTitle = it },
                        label = { Text("Playlist Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Text(
                        result.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        "${result.songs.size} curated tracks",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    // Song list preview
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(result.songs, key = { it.id }) { song ->
                            SongPreviewRow(song)
                        }
                    }

                    if (isSaving) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Saving to your YouTube Music library...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (generatedResult == null) {
                Button(
                    onClick = {
                        if (prompt.isNotBlank()) {
                            isGenerating = true
                            scope.launch {
                                val res = generator.generate(prompt.trim(), trackCount)
                                generatedResult = res
                                playlistTitle = res.title
                                isGenerating = false
                            }
                        }
                    },
                    enabled = prompt.isNotBlank() && !isGenerating,
                ) {
                    Text("Generate Mix")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val res = generatedResult!!
                            c.player.playQueue(
                                songs = res.songs,
                                startIndex = 0,
                                source = QueueSource(QueueKind.PLAYLIST, playlistTitle.ifBlank { res.title }),
                            )
                            onDismiss()
                        },
                        enabled = !isSaving,
                    ) {
                        Text("Play Now")
                    }

                    Button(
                        onClick = {
                            val res = generatedResult!!
                            val finalPlaylist = res.copy(title = playlistTitle.ifBlank { res.title })
                            isSaving = true
                            scope.launch {
                                val id = generator.savePlaylist(finalPlaylist)
                                isSaving = false
                                if (id != null) {
                                    Toast.makeText(context, "Playlist saved!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                    nav.go(Routes.playlist(id))
                                } else {
                                    Toast.makeText(context, "Sign in to save playlists to YouTube Music", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isSaving,
                    ) {
                        Text("Save Playlist")
                    }
                }
            }
        },
        dismissButton = {
            if (generatedResult != null) {
                TextButton(
                    onClick = {
                        generatedResult = null
                    },
                    enabled = !isSaving,
                ) {
                    Text("Back")
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isGenerating,
                ) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun VibeChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SongPreviewRow(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artistText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (song.durationSec > 0) {
            val min = song.durationSec / 60
            val sec = song.durationSec % 60
            Text(
                String.format("%d:%02d", min, sec),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
