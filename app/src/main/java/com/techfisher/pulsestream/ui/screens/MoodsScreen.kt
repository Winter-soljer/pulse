package com.techfisher.pulsestream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.techfisher.pulsestream.ui.Load
import com.techfisher.pulsestream.ui.LocalNavigator
import com.techfisher.pulsestream.ui.components.ErrorState
import com.techfisher.pulsestream.ui.components.LoadingState
import com.techfisher.pulsestream.ui.components.MoodTile
import com.techfisher.pulsestream.ui.components.SubPage
import com.techfisher.pulsestream.ui.rememberLoad
import com.techfisher.pulsestream.ui.theme.LocalContainer

/** Every mood and genre YouTube Music offers, best fits for you first. */
@Composable
fun MoodsScreen(bottomPadding: Dp) {
    val c = LocalContainer.current
    val nav = LocalNavigator.current
    val moods = rememberLoad("moods") { c.taste.rank(c.ytm.moodsAndGenres()) }
    SubPage("Moods & genres", bottomPadding, subtitle = "Best fits for you first") {
        when (val s = moods.state) {
            Load.Loading -> item { LoadingState() }
            is Load.Err -> item { ErrorState(s.message) { moods.reload(false) } }
            is Load.Ok -> {
                val groups = listOf("Picked for you" to s.value.picks.take(12)) + s.value.sections
                groups.forEach { (title, tiles) ->
                    if (tiles.isEmpty()) return@forEach
                    item(key = "h:$title") {
                        Text(
                            title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                        )
                    }
                    tiles.chunked(2).forEachIndexed { r, pair ->
                        item(key = "r:$title:$r") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { mood ->
                                    MoodTile(mood, Modifier.weight(1f).height(84.dp)) { nav.open(mood) { c.player.playSingle(it.song) } }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
