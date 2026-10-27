package com.techfisher.pulsestream.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techfisher.pulsestream.data.model.Song
import com.techfisher.pulsestream.playback.analysis.AudioAnalysisEngine
import com.techfisher.pulsestream.playback.analysis.AudioAnalysisResult
import com.techfisher.pulsestream.playback.analysis.HarmonicCompatibility
import com.techfisher.pulsestream.ui.components.ActionHeader
import com.techfisher.pulsestream.ui.components.ActionSheet
import com.techfisher.pulsestream.ui.components.FrostedPanel
import com.techfisher.pulsestream.ui.theme.LocalContainer
import com.techfisher.pulsestream.ui.theme.PulseIcons
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Audio Analysis & Camelot Wheel Sheet (Level 32).
 *
 * Displays:
 * 1. Camelot Key Badge Card with 12-color harmonic palette and key signature.
 * 2. Next-Track Harmonic Transition preview with compatibility score & DJ mixing technique.
 * 3. 2x2 Rhythm & Dynamics Grid (Tempo, Dynamic Range, LUFS, Beat Grid).
 * 4. LUFS Loudness Meter calibrated against ITU-R BS.1770 / EBU R128 standards.
 * 5. Acoustic Profile Meters (Energy, Danceability, Valence).
 */
@Composable
fun AudioAnalysisSheet(
    song: Song,
    nextSong: Song? = null,
    onDismiss: () -> Unit,
) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val engine = remember(song.id) { AudioAnalysisEngine(context, c.mixTempo, c.loudness) }
    val analysis = remember(song.id) { engine.analyze(song) }
    val nextAnalysis = remember(nextSong?.id) { nextSong?.let { engine.analyze(it) } }
    val compatibility = remember(analysis, nextAnalysis) {
        if (nextAnalysis != null) engine.evaluateCompatibility(analysis, nextAnalysis) else null
    }

    ActionSheet(song.thumbnail, onDismiss) {
        ActionHeader(
            art = song.thumbnail,
            title = "Audio Analysis & Camelot",
            subtitle = "${song.title} · ${song.artistText}",
            placeholderIcon = PulseIcons.Equalizer,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Camelot Key Badge Card
            CamelotKeyCard(analysis)

            // 2. Next-Track Harmonic Transition Card
            TransitionCard(analysis, nextSong, nextAnalysis, compatibility)

            // 3. 2x2 Rhythm & Dynamics Grid
            RhythmDynamicsCard(analysis)

            // 4. LUFS Loudness Meter
            LoudnessMeterCard(analysis)

            // 5. Acoustic Profile (Energy, Danceability, Valence)
            AcousticProfileCard(analysis)
        }
    }
}

@Composable
private fun CamelotKeyCard(analysis: AudioAnalysisResult) {
    val badgeColor = Color(analysis.camelotColorHex)
    FrostedPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Camelot Wheel Badge
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
                    .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = analysis.camelotCode,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = analysis.musicalKey,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Camelot ${analysis.camelotCode} · ${if (analysis.camelotLetter == "A") "Minor Key" else "Major Key"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // BPM Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(0.7.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f BPM", analysis.bpm),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun TransitionCard(
    analysis: AudioAnalysisResult,
    nextSong: Song?,
    nextAnalysis: AudioAnalysisResult?,
    compatibility: HarmonicCompatibility?,
) {
    FrostedPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            if (nextSong != null && compatibility != null && nextAnalysis != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NEXT TRACK TRANSITION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${nextSong.title} · ${nextSong.artistText}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Score Match Badge
                    val matchColor = when {
                        compatibility.score >= 85 -> Color(0xFF10B981) // Green
                        compatibility.score >= 70 -> Color(0xFFF59E0B) // Amber
                        else -> Color(0xFFEF4444) // Red
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(matchColor.copy(alpha = 0.18f))
                            .border(1.dp, matchColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${compatibility.score}% Match",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = matchColor,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(10.dp))

                // Key Flow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Harmonic Shift: ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${analysis.camelotCode} (${analysis.musicalKey}) → ${nextAnalysis.camelotCode} (${nextAnalysis.musicalKey})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Relationship & Technique
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "DJ Technique: ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = compatibility.djTechnique,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Advice
                Text(
                    text = "${compatibility.keyAdvice}. ${compatibility.tempoAdvice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = PulseIcons.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Queue an upcoming track to preview Camelot harmonic mixing compatibility and DJ transition recommendations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RhythmDynamicsCard(analysis: AudioAnalysisResult) {
    FrostedPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Text(
                text = "RHYTHM & DYNAMICS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(10.dp))

            // Row 1: Tempo & Dynamic Range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCell(
                    label = "Tempo",
                    value = String.format(Locale.US, "%.1f BPM", analysis.bpm),
                    sub = "${(analysis.bpmConfidence * 100).roundToInt()}% confidence",
                    modifier = Modifier.weight(1f),
                )
                MetricCell(
                    label = "Dynamic Range",
                    value = String.format(Locale.US, "%.1f dB", analysis.dynamicRangeDb),
                    sub = "Crest factor",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Row 2: Integrated Loudness & Beat Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCell(
                    label = "Integrated Loudness",
                    value = analysis.lufs?.let { String.format(Locale.US, "%.1f LUFS", it) } ?: "—",
                    sub = "ITU-R BS.1770",
                    modifier = Modifier.weight(1f),
                )
                val outroSeconds = (analysis.outroBeatMs ?: 0L) / 1000
                MetricCell(
                    label = "Beat Grid",
                    value = "${analysis.firstBeatMs ?: 0}ms",
                    sub = "Outro: ${outroSeconds}s",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun LoudnessMeterCard(analysis: AudioAnalysisResult) {
    FrostedPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LUFS LOUDNESS METER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Text(
                    text = analysis.lufs?.let { String.format(Locale.US, "%.1f LUFS", it) } ?: "Unmeasured",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(10.dp))

            // -24 LUFS to -6 LUFS progress scale (Streaming standard: -14 LUFS)
            val rawLufs = analysis.lufs ?: -14f
            val lufsProgress = ((rawLufs - (-24f)) / 18f).coerceIn(0f, 1f)

            LinearProgressIndicator(
                progress = { lufsProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("-24 LUFS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text("Target: -14 LUFS", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("-6 LUFS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun AcousticProfileCard(analysis: AudioAnalysisResult) {
    FrostedPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "ACOUSTIC PROFILE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )

            AcousticBar(
                title = "Energy",
                value = analysis.energy,
                color = Color(0xFFF43F5E), // Rose / Red
            )

            AcousticBar(
                title = "Danceability",
                value = analysis.danceability,
                color = Color(0xFF3B82F6), // Blue
            )

            AcousticBar(
                title = "Valence (Mood)",
                value = analysis.valence,
                color = Color(0xFF10B981), // Emerald
            )
        }
    }
}

@Composable
private fun AcousticBar(
    title: String,
    value: Float,
    color: Color,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }

        Spacer(Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
        )
    }
}
