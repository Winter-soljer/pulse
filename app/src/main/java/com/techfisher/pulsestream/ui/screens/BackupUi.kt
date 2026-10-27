package com.techfisher.pulsestream.ui.screens

import com.techfisher.pulsestream.ui.theme.PulseIcons
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.techfisher.pulsestream.ui.theme.LocalContainer
import kotlinx.coroutines.launch
import java.io.File

private fun backupName() = "Pulse backup " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".zip"

private fun sizeText(bytes: Long) = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    else -> "%.0f MB".format(maxOf(bytes, 1_048_576L) / 1_048_576.0)
}

/**
 * Backing up: asks whether the downloaded audio goes in too, then where to save the file.
 * [onDone] gets whether the backup was written.
 */
@Composable
fun rememberBackupFlow(onDone: (Boolean) -> Unit = {}): () -> Unit {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var withDownloads by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) { onDone(false); return@rememberLauncherForActivityResult }
        busy = true
        scope.launch {
            val r = runCatching { c.backup.export(uri, withDownloads) }
            busy = false
            Toast.makeText(context, r.exceptionOrNull()?.let { "Backup failed: ${it.message}" } ?: "Backed up", Toast.LENGTH_SHORT).show()
            onDone(r.isSuccess)
        }
    }
    if (asking) {
        var small by remember { mutableLongStateOf(0L) }
        var full by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) { small = c.backup.estimate(false); full = c.backup.estimate(true) }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Back up Pulse") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your library, Replay history, settings, sign-in, covers and backgrounds go into one file. Keep it private: it can sign in as you.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Include downloaded songs", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (withDownloads) "About ${sizeText(full)}" else "About ${sizeText(small)}; downloads are fetched again after restoring",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(withDownloads, { withDownloads = it })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { asking = false; save.launch(backupName()) }) { Text("Choose where") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
        )
    }
    if (busy) WorkingDialog("Backing up…")
    return { asking = true }
}

/** Restoring: pick a backup, confirm, and Pulse restarts with it. */
@Composable
fun rememberRestoreFlow(): () -> Unit {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked = it }
    picked?.let { uri ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text("Restore this backup?") },
            text = { Text("Pulse's library, history and settings on this phone are replaced with the backup's, then Pulse restarts.") },
            confirmButton = {
                TextButton(onClick = {
                    picked = null
                    busy = true
                    scope.launch {
                        // Only comes back if it failed; on success Pulse restarts.
                        runCatching { c.backup.restore(uri) }.onFailure {
                            busy = false
                            Toast.makeText(context, "Couldn't restore: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { picked = null }) { Text("Cancel") } },
        )
    }
    if (busy) WorkingDialog("Restoring…")
    return { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
}

@Composable
private fun WorkingDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(16.dp))
                Text(text)
            }
        },
    )
}

/** Settings → About: back up and restore. */
@Composable
fun BackupGroup() {
    val backup = rememberBackupFlow()
    val restore = rememberRestoreFlow()
    Group("Backup") {
        Item("Back up", "Save your library, Replay, settings and sign-in to a file", onClick = backup)
        Item("Restore from a backup", "Replaces what's on this phone, then restarts Pulse", onClick = restore)
    }
}

/** On the welcome screen, for a fresh install coming from a backup. */
@Composable
fun WelcomeRestoreButton() {
    val restore = rememberRestoreFlow()
    TextButton(onClick = restore, Modifier.fillMaxWidth()) { Text("Restore from a backup", color = Color.White.copy(alpha = 0.8f)) }
}

/**
 * The new Pulse is signed with a new key, so Android can't install it over this one. Three steps:
 * back up, keep the new Pulse somewhere safe, uninstall this one; then open the saved file and restore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSignatureSheet(version: String, apk: File, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backedUp by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val backup = rememberBackupFlow { if (it) backedUp = true }
    val saveApk = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.updates.saveApk(apk, uri) }
                .onSuccess { saved = true }
                .onFailure { Toast.makeText(context, "Couldn't save it: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Moving to Pulse $version", style = MaterialTheme.typography.titleLarge)
            Text(
                "From $version, Pulse is signed with its own key instead of a shared test key, so Android stops flagging it. " +
                    "Android can't update across that change, so this once Pulse is reinstalled. Your things come with you:",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Step(1, "Back up", "Save a backup (Downloads is a good place)", backedUp) { backup() }
            Step(2, "Save the new Pulse", "Keep Pulse-$version.apk next to it", saved) { saveApk.launch("Pulse-$version.apk") }
            Step(3, "Uninstall this Pulse", "Then open Pulse-$version.apk from your files", false, enabled = backedUp && saved) {
                runCatching { context.startActivity(c.updates.uninstallIntent()) }.onFailure {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
                }
            }
            Text(
                "When the new Pulse opens, tap \"Restore from a backup\" and pick the backup.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Step(n: Int, title: String, detail: String, done: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$n. $title", style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (done) Icon(PulseIcons.Check, "Done", tint = MaterialTheme.colorScheme.primary)
        else if (n == 3) Button(onClick = onClick, enabled = enabled) { Text("Uninstall") }
        else OutlinedButton(onClick = onClick, enabled = enabled) { Text(if (n == 1) "Back up" else "Save") }
    }
}
