package com.techfisher.pulsestream.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.techfisher.pulsestream.ui.theme.LocalAppSettings
import com.techfisher.pulsestream.ui.theme.LocalContainer

/**
 * Pulse Spatial (a software spatializer, so it works without Dolby Atmos or
 * spatial-audio hardware) and the equalizer — all sound settings live here.
 */
@Composable
fun SoundSettings(onOpenEq: () -> Unit) {
    val c = LocalContainer.current
    val s = LocalAppSettings.current

    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Pulse Spatial", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Software spatial audio for any phone and any headphones: a wider stage, sound placed outside your head, and a sense of room. Built into Pulse, so it doesn't need Dolby Atmos or spatial audio hardware.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(s.spatial, { c.settings.setSpatial(it) })
    }
    var amount by remember { mutableFloatStateOf(s.spatialAmount) }
    LaunchedEffect(s.spatialAmount) { amount = s.spatialAmount }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Intensity · ${(amount * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = if (s.spatial) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(amount, { amount = it }, enabled = s.spatial, valueRange = 0.1f..1f, onValueChangeFinished = { c.settings.setSpatialAmount(amount) })
    }
    Item(
        "Equalizer",
        if (s.eq.enabled) "On · ${s.eq.preset}" else "Off · 10-band EQ, bass boost, loudness",
        onClick = onOpenEq,
    )

    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("AI DJ Voice Assistant", style = MaterialTheme.typography.bodyLarge)
            Text(
                "An intelligent spoken DJ that introduces tracks, announces upcoming songs, and shares context-aware commentary between songs.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(s.aiDj, { c.settings.setAiDj(it) })
    }
    if (s.aiDj) {
        SliderSetting(
            title = "Commentary frequency",
            value = s.aiDjFrequency.toFloat(),
            range = 1f..10f,
            steps = 8,
            valueText = { if (it.toInt() == 1) "Every song" else "Every ${it.toInt()} songs" },
            onSave = { c.settings.setAiDjFrequency(it.toInt()) },
        )
        SliderSetting(
            title = "Speech speed",
            value = s.aiDjVoiceRate,
            range = 0.75f..1.5f,
            valueText = { "%.2fx".format(it) },
            onSave = { c.settings.setAiDjVoiceRate(it) },
        )
        Item(
            title = "Preview DJ voice",
            subtitle = "Test spoken commentary with music volume ducking",
            onClick = { c.player.aiDj.preview() },
        )
    }
}
