package com.prism.music.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the phone can reach the internet right now, and over what. Offline, Prism shows what it saved (playlists,
 * covers, downloads) instead of waiting on requests that can't go anywhere; on mobile data, data saver applies.
 */
class NetworkState(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(read().first)
    /** A network that has been checked to reach the internet. */
    val online: StateFlow<Boolean> = _online
    private val _metered = MutableStateFlow(read().second)
    /** On mobile data (or another network that charges by use). */
    val metered: StateFlow<Boolean> = _metered

    private fun read(): Pair<Boolean, Boolean> {
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return false to true
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) to
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    /** [block] runs (on a background thread) each time the phone gets back online. */
    fun onReconnect(block: () -> Unit) { listeners += block }

    init {
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
                override fun onLost(network: Network) = update()
                override fun onAvailable(network: Network) = update()
            })
        }
    }

    private fun update() {
        val (online, metered) = read()
        val back = online && !_online.value
        _online.value = online
        _metered.value = metered
        if (back) listeners.forEach { runCatching { it() } }
    }

    /** True when it's worth trying the network (a fresh check, as the callback can lag a moment behind). */
    fun isOnline(): Boolean = read().first.also { _online.value = it }
}
