package com.techfisher.pulsestream.playback

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.techfisher.pulsestream.data.ShareLinks
import com.techfisher.pulsestream.data.model.Song

/**
 * 1-Tap WhatsApp Status Sharing Utility.
 *
 * WhatsApp does not provide a public, official API for third-party background presence updates.
 * Automated background presence updates on WhatsApp require unofficial protocols or session
 * hijacking which frequently lead to permanent account/phone number bans.
 *
 * This utility provides a safe, 1-tap sharing mechanism utilizing Android's official Intent system.
 * It directly targets WhatsApp with pre-formatted Now Playing metadata for instant Status or Chat sharing
 * with 100% account safety and zero risk of ban.
 */
object WhatsAppStatusShare {
    private const val PACKAGE_WHATSAPP = "com.whatsapp"
    private const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    /**
     * Checks if WhatsApp or WhatsApp Business is installed on the user's device.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        return getInstalledWhatsAppPackage(context) != null
    }

    /**
     * Resolves the installed WhatsApp package name (standard or business).
     */
    fun getInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in listOf(PACKAGE_WHATSAPP, PACKAGE_WHATSAPP_BUSINESS)) {
            try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed, continue check
            }
        }
        return null
    }

    /**
     * Formats the Now Playing message ready for sharing to WhatsApp status or chat.
     */
    fun formatSongStatus(song: Song?): String {
        if (song == null) {
            return "🎵 Listening to music on Pulse"
        }
        val songPart = "🎵 Listening to \"${song.title}\" by ${song.artistText} on Pulse"
        return if (!song.isLocal && song.id.isNotBlank()) {
            val url = ShareLinks.song(song.id)
            "$songPart\n$url"
        } else {
            songPart
        }
    }

    /**
     * Launches an official Android share intent targeting WhatsApp with the current song.
     * Falls back gracefully to system share chooser if WhatsApp is not installed.
     *
     * @return true if WhatsApp intent was directly launched; false if fallback chooser was opened.
     */
    fun shareToWhatsApp(context: Context, song: Song?): Boolean {
        val text = formatSongStatus(song)
        val targetPackage = getInstalledWhatsAppPackage(context)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (targetPackage != null) {
            sendIntent.setPackage(targetPackage)
            try {
                context.startActivity(sendIntent)
                true
            } catch (e: Exception) {
                // If package launch fails for any reason, use fallback chooser
                fallbackChooser(context, text)
                false
            }
        } else {
            Toast.makeText(context, "WhatsApp is not installed. Choose an app to share to:", Toast.LENGTH_SHORT).show()
            fallbackChooser(context, text)
            false
        }
    }

    private fun fallbackChooser(context: Context, text: String) {
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share What You're Listening To"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
