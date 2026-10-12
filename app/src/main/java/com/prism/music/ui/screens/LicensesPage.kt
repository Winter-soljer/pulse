package com.prism.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SOURCE_URL = "https://github.com/Yigder/Prism"

/** A library shipped in the APK; [text] names its license text in assets/licenses/. */
private class Library(val name: String, val by: String, val license: String, val text: String, val url: String)

private val libraries = listOf(
    Library("NewPipe Extractor", "Team NewPipe", "GPL-3.0-or-later", "gpl-3.0", "https://github.com/TeamNewPipe/NewPipeExtractor"),
    Library("nanojson", "Matt Mastracci, Team NewPipe", "MIT or Apache 2.0", "apache-2.0", "https://github.com/TeamNewPipe/nanojson"),
    Library("Rhino", "Mozilla", "MPL 2.0", "mpl-2.0", "https://github.com/mozilla/rhino"),
    Library("EJS", "yt-dlp", "Unlicense", "ejs", "https://github.com/yt-dlp/ejs"),
    Library("meriyah (inside EJS)", "KFlash and others", "ISC", "meriyah", "https://github.com/meriyah/meriyah"),
    Library("astring (inside EJS)", "David Bonnet", "MIT", "astring", "https://github.com/davidbonnet/astring"),
    Library("jsoup", "Jonathan Hedley", "MIT", "jsoup", "https://jsoup.org"),
    Library("AndroidX: Compose, Material 3, Media3, Room, Navigation, WorkManager, DataStore, JavaScriptEngine", "The Android Open Source Project", "Apache 2.0", "apache-2.0", "https://developer.android.com/jetpack/androidx"),
    Library("Kotlin, kotlinx.coroutines, kotlinx.serialization, Compose runtime", "JetBrains", "Apache 2.0", "apache-2.0", "https://kotlinlang.org"),
    Library("OkHttp, Okio", "Square", "Apache 2.0", "apache-2.0", "https://square.github.io/okhttp"),
    Library("Coil", "Coil Contributors", "Apache 2.0", "apache-2.0", "https://coil-kt.github.io/coil"),
    Library("Haze", "Chris Banes", "Apache 2.0", "apache-2.0", "https://github.com/chrisbanes/haze"),
    Library("Accompanist", "Google", "Apache 2.0", "apache-2.0", "https://github.com/google/accompanist"),
    Library("Guava, Error Prone annotations, J2ObjC annotations, JSR 305, JSpecify", "Google and others", "Apache 2.0", "apache-2.0", "https://github.com/google/guava"),
    Library("Protocol Buffers (lite)", "Google", "BSD 3-Clause", "protobuf", "https://github.com/protocolbuffers/protobuf"),
    Library("Checker Framework qualifiers", "Checker Framework developers", "MIT", "checker-qual", "https://checkerframework.org"),
)

/** Settings → About → Open-source licenses: Prism's own license, then everything it ships with. */
@Composable
fun LicensesPage() {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group("Prism") {
            Note("Prism is free software under the GNU General Public License, version 3 or later. You can use, study, share and change it; copies and changed versions have to stay under the same license, with their source.")
            Item("Source code", SOURCE_URL.removePrefix("https://"), onClick = { uri.openUri(SOURCE_URL) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LicenseRow("GNU GPL 3.0", "Prism's license", "gpl-3.0")
        }
        Group("Built with") {
            Note("Tap one to read its license.")
            libraries.forEach { LicenseRow(it.name, "${it.license} · ${it.by}", it.text, it.url) }
        }
    }
}

/** A library or license that opens in place to show its full license text. */
@Composable
private fun LicenseRow(title: String, subtitle: String, text: String, url: String? = null) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Item(title, subtitle, onClick = { open = !open }) {
        Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AnimatedVisibility(open) {
        Column {
            if (url != null) Item("Project page", url.removePrefix("https://"), onClick = { uri.openUri(url) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { LicenseText(text) }
        }
    }
}

/** License files are wrapped at ~80 columns; join each paragraph's lines so they wrap to the screen instead. */
private fun reflow(text: String): String =
    text.split(Regex("\\n\\s*\\n")).joinToString("\n\n") { para -> para.lines().joinToString(" ") { it.trim() }.trim() }.trim()

@Composable
private fun LicenseText(name: String) {
    val context = LocalContext.current
    val text by produceState<String?>(null, name) {
        value = withContext(Dispatchers.IO) {
            runCatching { reflow(context.assets.open("licenses/$name.txt").use { it.readBytes().decodeToString() }) }.getOrNull()
        }
    }
    Text(text ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
