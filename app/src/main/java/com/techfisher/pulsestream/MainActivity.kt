package com.techfisher.pulsestream

import android.Manifest
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.techfisher.pulsestream.data.model.Song
import com.techfisher.pulsestream.data.model.hiRes
import com.techfisher.pulsestream.data.prefs.AppSettings
import com.techfisher.pulsestream.data.prefs.MiniPlayerStyle
import com.techfisher.pulsestream.data.prefs.NavBarStyle
import com.techfisher.pulsestream.data.prefs.NavLabels
import com.techfisher.pulsestream.data.prefs.NavTab
import com.techfisher.pulsestream.download.SmartDownloadWorker
import com.techfisher.pulsestream.ui.LocalNavigator
import com.techfisher.pulsestream.ui.Navigator
import com.techfisher.pulsestream.ui.Routes
import com.techfisher.pulsestream.ui.player.MiniPlayer
import com.techfisher.pulsestream.ui.player.NowPlayingScreen
import com.techfisher.pulsestream.ui.screens.ArtistScreen
import com.techfisher.pulsestream.ui.screens.BrowseScreen
import com.techfisher.pulsestream.ui.screens.CatalogueScreen
import com.techfisher.pulsestream.ui.screens.CollectionScreen
import com.techfisher.pulsestream.ui.screens.CollectionType
import com.techfisher.pulsestream.ui.screens.EqualizerScreen
import com.techfisher.pulsestream.ui.screens.HomeScreen
import com.techfisher.pulsestream.ui.screens.LibraryScreen
import com.techfisher.pulsestream.ui.screens.LoginScreen
import com.techfisher.pulsestream.ui.screens.ReplayScreen
import com.techfisher.pulsestream.ui.screens.SearchScreen
import com.techfisher.pulsestream.ui.screens.SettingsScreen
import com.techfisher.pulsestream.ui.screens.WelcomeScreen
import com.techfisher.pulsestream.ui.theme.BackdropScreen
import com.techfisher.pulsestream.ui.theme.GlassSurface
import com.techfisher.pulsestream.ui.theme.LocalContainer
import com.techfisher.pulsestream.ui.theme.LocalHazeState
import com.techfisher.pulsestream.ui.theme.PulseIcons
import com.techfisher.pulsestream.ui.theme.PulseTheme
import com.techfisher.pulsestream.ui.theme.ScreenBackdrop
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The app's screen. Declared in the manifest as [PulseActivity], so that ".MainActivity" can be the classic app-icon
 * alias (#20) and home-screen icons made before stay valid.
 */
open class MainActivity : ComponentActivity() {
    private val deepLink = MutableStateFlow<Uri?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private data class SavedLockscreenState(
        val wasImmersive: Boolean,
        val wasPlayerOpen: Boolean,
        val wasBackground: Boolean,
    )

    companion object {
        private const val LOCKSCREEN_CHANNEL_ID = "lockscreen_display_v2"
        private const val LOCKSCREEN_NOTIF_ID = 1002
        private const val ACTION_LOCKSCREEN_MODE = "com.techfisher.pulsestream.action.LOCKSCREEN_MODE"
    }

    private var isBackground = false
    private var savedLockscreenState: SavedLockscreenState? = null
    private val isPlayerOpenState = mutableStateOf(false)

    private fun createLockscreenNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.deleteNotificationChannel("lockscreen_display")
            val channel = android.app.NotificationChannel(
                LOCKSCREEN_CHANNEL_ID,
                "Lockscreen Display",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Used to display immersive player over lockscreen"
                setSound(null, null)
                enableVibration(false)
            }
            nm?.createNotificationChannel(channel)
        }
    }

    private fun postLockscreenNotification(c: AppContainer) {
        val fullScreenIntent = Intent(this, PulseActivity::class.java).apply {
            action = ACTION_LOCKSCREEN_MODE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

        @Suppress("DEPRECATION")
        val pendingIntentOptions = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic().apply {
                if (Build.VERSION.SDK_INT >= 35) {
                    setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                } else {
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
            }.toBundle()
        } else null

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            flags,
            pendingIntentOptions
        )

        val songTitle = c.player.currentSong.value?.title ?: getString(R.string.app_name)
        val notif = NotificationCompat.Builder(this, LOCKSCREEN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(songTitle)
            .setContentText("Playing on lockscreen")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setSound(null as Uri?)
            .setVibrate(null as LongArray?)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(LOCKSCREEN_NOTIF_ID, notif)
        }
    }

    private fun setLockscreenVisible(show: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(show)
            setTurnScreenOn(show)
        }
        @Suppress("DEPRECATION")
        if (show) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun enterLockscreenMode(c: AppContainer) {
        val settings = c.settings.current
        if (!settings.immersiveLockscreen) return
        if (!c.player.isPlaying.value) return

        val saved = savedLockscreenState
        val wasBackground = if (saved != null) {
            saved.wasBackground
        } else {
            val bg = isBackground
            savedLockscreenState = SavedLockscreenState(
                wasImmersive = settings.immersivePlayer,
                wasPlayerOpen = isPlayerOpenState.value,
                wasBackground = bg,
            )
            bg
        }

        setLockscreenVisible(true)
        if (!settings.immersivePlayer) {
            c.settings.setImmersivePlayer(true)
        }
        isPlayerOpenState.value = true

        if (wasBackground) {
            postLockscreenNotification(c)
            runCatching {
                val intent = Intent(this, PulseActivity::class.java).apply {
                    action = ACTION_LOCKSCREEN_MODE
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                startActivity(intent)
            }
        }
    }

    private fun restoreLockscreenState(c: AppContainer) {
        runCatching {
            NotificationManagerCompat.from(this).cancel(LOCKSCREEN_NOTIF_ID)
        }
        val saved = savedLockscreenState ?: return
        savedLockscreenState = null

        setLockscreenVisible(false)
        if (!saved.wasImmersive) {
            c.settings.setImmersivePlayer(false)
        }
        isPlayerOpenState.value = saved.wasPlayerOpen

        if (saved.wasBackground) {
            moveTaskToBack(true)
        }
    }

    private val lockscreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val c = context?.container ?: container
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> enterLockscreenMode(c)
                Intent.ACTION_SCREEN_ON -> {
                    val km = (context ?: this@MainActivity).getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (km?.isKeyguardLocked == true) {
                        enterLockscreenMode(c)
                    } else if (savedLockscreenState != null) {
                        restoreLockscreenState(c)
                    }
                }
                Intent.ACTION_USER_PRESENT -> restoreLockscreenState(c)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        createLockscreenNotificationChannel()
        val c = container
        if (savedInstanceState != null) {
            isPlayerOpenState.value = savedInstanceState.getBoolean("player_open", false)
            isBackground = savedInstanceState.getBoolean("is_background", false)
            if (savedInstanceState.getBoolean("lockscreen_active", false)) {
                savedLockscreenState = SavedLockscreenState(
                    wasImmersive = savedInstanceState.getBoolean("lockscreen_was_immersive", false),
                    wasPlayerOpen = savedInstanceState.getBoolean("lockscreen_was_player_open", false),
                    wasBackground = savedInstanceState.getBoolean("lockscreen_was_background", false),
                )
                setLockscreenVisible(true)
            }
        } else if (intent?.action == ACTION_LOCKSCREEN_MODE) {
            enterLockscreenMode(c)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, lockscreenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        lifecycleScope.launch {
            combine(c.player.isPlaying, c.settings.flow) { isPlaying, s ->
                isPlaying to s.immersiveLockscreen
            }.collect { (isPlaying, lockscreenEnabled) ->
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isScreenOffOrLocked = pm?.isInteractive == false || km?.isKeyguardLocked == true
                if (isPlaying && lockscreenEnabled && isScreenOffOrLocked && savedLockscreenState == null) {
                    enterLockscreenMode(c)
                } else if (savedLockscreenState != null && (!isPlaying || !lockscreenEnabled)) {
                    restoreLockscreenState(c)
                }
            }
        }

        c.player.connect()
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        SmartDownloadWorker.schedule(this, c.settings.current.smartDownloads)
        // Tidies the smart-download queue (it only adds more if it's been a while).
        if (c.settings.current.smartDownloads) SmartDownloadWorker.runNow(this, force = false)
        // Picks up lossless files added while Pulse is closed.
        com.techfisher.pulsestream.download.LosslessSyncWorker.schedule(this)
        if (c.settings.current.isLoggedIn) c.library.syncInBackground()
        deepLink.value = linkIn(intent)
        setContent {
            CompositionLocalProvider(LocalContainer provides c, com.techfisher.pulsestream.ui.player.LocalPip provides inPip.value) {
                PulseRoot(deepLink, isPlayerOpenState)
            }
        }
    }

    // ---------------------------------------------------------------- Picture-in-picture

    /** True while Pulse is shown as a picture-in-picture window (a music video floating over other apps). */
    private val inPip = androidx.compose.runtime.mutableStateOf(false)
    /** A music video is playing on the open player, so leaving Pulse floats it. */
    private var pipEligible = false
    private var pipAspect = android.util.Rational(16, 9)

    private val pipSupported by lazy { packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    private fun pipParams(): android.app.PictureInPictureParams = android.app.PictureInPictureParams.Builder()
        .setAspectRatio(pipAspect)
        .apply { if (Build.VERSION.SDK_INT >= 31) { setAutoEnterEnabled(pipEligible); setSeamlessResizeEnabled(true) } }
        .build()

    /** The player says whether a music video is playing on it, and its shape ([width] × [height], 0 if unknown). */
    fun setPipEligible(eligible: Boolean, width: Int = 0, height: Int = 0) {
        if (!pipSupported) return
        pipEligible = eligible
        // Android only takes ratios between 1:2.39 and 2.39:1.
        if (width > 0 && height > 0) pipAspect = android.util.Rational(width, height).let { r ->
            when {
                r.toFloat() > 2.39f -> android.util.Rational(239, 100)
                r.toFloat() < 1 / 2.39f -> android.util.Rational(100, 239)
                else -> r
            }
        }
        // Android 12+ floats the video by itself when the listener goes Home.
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    /** Floats the music video now (the player's PiP button). False if Android or the listener's settings won't allow it. */
    fun enterPip(): Boolean = pipSupported && runCatching { enterPictureInPictureMode(pipParams()) }.getOrDefault(false)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Before Android 12 there's no auto-enter: going Home with a video playing floats it here.
        if (Build.VERSION.SDK_INT < 31 && pipEligible) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_LOCKSCREEN_MODE) {
            val c = container
            enterLockscreenMode(c)
            setLockscreenVisible(true)
            isPlayerOpenState.value = true
        }
        linkIn(intent)?.let { deepLink.value = it }
    }

    /** Gamepad, D-Pad, joystick, and external media key handling for controller support (Level 25). */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val c = container
            val handled = when (event.keyCode) {
                // Gamepad Face Buttons
                android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                    c.player.togglePlay()
                    true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_X,
                android.view.KeyEvent.KEYCODE_BUTTON_R1,
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    c.player.next()
                    true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_Y,
                android.view.KeyEvent.KEYCODE_BUTTON_L1,
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    c.player.previous()
                    true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_L2 -> {
                    c.player.seekBy(-10_000)
                    true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_R2 -> {
                    c.player.seekBy(10_000)
                    true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_START,
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                android.view.KeyEvent.KEYCODE_HEADSETHOOK -> {
                    c.player.togglePlay()
                    true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    c.player.setPlaying(true)
                    true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                    c.player.setPlaying(false)
                    true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_SELECT -> {
                    c.player.cycleRepeat()
                    true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                    c.player.currentSong.value?.let { s ->
                        c.library.toggleLike(s)
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                    c.player.toggleShuffle()
                    true
                }
                else -> false
            }
            if (handled) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_CLASS_JOYSTICK)) {
            val c = container
            val axisX = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)
            val lTrigger = event.getAxisValue(android.view.MotionEvent.AXIS_LTRIGGER)
            val rTrigger = event.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER)
            val gas = event.getAxisValue(android.view.MotionEvent.AXIS_GAS)
            val brake = event.getAxisValue(android.view.MotionEvent.AXIS_BRAKE)

            if (rTrigger > 0.7f || gas > 0.7f) {
                c.player.seekBy(10_000)
                return true
            }
            if (lTrigger > 0.7f || brake > 0.7f) {
                c.player.seekBy(-10_000)
                return true
            }
            if (axisX > 0.8f) {
                c.player.next()
                return true
            } else if (axisX < -0.8f) {
                c.player.previous()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    /** A music.youtube.com link opened in Pulse, or a YouTube / YouTube Music link shared to it. */
    private fun linkIn(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> com.techfisher.pulsestream.data.ShareLinks.findIn(intent.getStringExtra(Intent.EXTRA_TEXT))?.let(Uri::parse).also {
            if (it == null) Toast.makeText(this, "That isn't a YouTube or YouTube Music link", Toast.LENGTH_SHORT).show()
        }
        else -> intent?.data
    }

    override fun onStart() {
        super.onStart()
        if (savedLockscreenState == null) {
            isBackground = false
        }
    }

    override fun onStop() {
        super.onStop()
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (savedLockscreenState == null && pm?.isInteractive == true && !isChangingConfigurations) {
            isBackground = true
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(lockscreenReceiver) }
        runCatching { NotificationManagerCompat.from(this).cancel(LOCKSCREEN_NOTIF_ID) }
        val c = container
        if (savedLockscreenState != null && !isChangingConfigurations) {
            restoreLockscreenState(c)
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("player_open", isPlayerOpenState.value)
        outState.putBoolean("is_background", isBackground)
        savedLockscreenState?.let {
            outState.putBoolean("lockscreen_active", true)
            outState.putBoolean("lockscreen_was_immersive", it.wasImmersive)
            outState.putBoolean("lockscreen_was_player_open", it.wasPlayerOpen)
            outState.putBoolean("lockscreen_was_background", it.wasBackground)
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private fun NavTab.tab(): Tab = when (this) {
    NavTab.HOME -> Tab(Routes.HOME, label, PulseIcons.Home, PulseIcons.HomeFilled)
    NavTab.SEARCH -> Tab(Routes.SEARCH, label, PulseIcons.Search, PulseIcons.SearchBold)
    NavTab.LIBRARY -> Tab(Routes.LIBRARY, label, PulseIcons.Library, PulseIcons.LibraryFilled)
    NavTab.REPLAY -> Tab(Routes.REPLAY, label, PulseIcons.Replay, PulseIcons.ReplayFilled)
    NavTab.SETTINGS -> Tab(Routes.SETTINGS, label, PulseIcons.Settings, PulseIcons.SettingsFilled)
    NavTab.LOCAL -> Tab(Routes.LOCAL, label, PulseIcons.Phone, PulseIcons.Phone)
}

/** Heights of the floating chrome, so pages can leave room for it. */
private fun navHeight(style: NavBarStyle): Dp = when (style) {
    NavBarStyle.FLOATING -> 64.dp
    NavBarStyle.DOCKED -> 62.dp
    NavBarStyle.MINIMAL -> 54.dp
}

private fun miniHeight(style: MiniPlayerStyle): Dp = when (style) {
    MiniPlayerStyle.CARD -> 64.dp
    MiniPlayerStyle.SLIM -> 54.dp
    MiniPlayerStyle.PILL -> 56.dp
}

@Composable
private fun rememberArtworkColor(song: Song?): Color? {
    val context = LocalContext.current
    var color by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(song?.thumbnail) {
        val url = song?.thumbnail ?: return@LaunchedEffect
        color = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context).data(hiRes(url, 160)).allowHardware(false).build()
                val bmp = (context.imageLoader.execute(req) as? SuccessResult)?.image?.toBitmap() ?: return@runCatching null
                val p = Palette.from(bmp).generate()
                (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch)?.rgb?.let { Color(it) }
            }.getOrNull()
        } ?: color
    }
    return color
}

@Composable
private fun PulseRoot(
    deepLink: MutableStateFlow<Uri?>,
    playerOpenState: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) }
) {
    val c = LocalContainer.current
    val settings by c.settings.flow.collectAsState()
    val song by c.player.currentSong.collectAsState()
    val artColor = rememberArtworkColor(song)
    val context = LocalContext.current

    PulseTheme(settings, artColor) {
        // System bar icons follow Pulse's theme, not the phone's (they differ when Appearance isn't "System").
        val dark = com.techfisher.pulsestream.ui.theme.LocalIsDark.current
        val rootView = androidx.compose.ui.platform.LocalView.current
        LaunchedEffect(dark) {
            (rootView.context as? android.app.Activity)?.window?.let { w ->
                androidx.core.view.WindowCompat.getInsetsController(w, rootView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        val navController = rememberNavController()
        var playerOpen by playerOpenState
        val navigator = remember(navController) {
            Navigator(navController, openPlayer = { playerOpen = true }, playMix = { mix ->
                c.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.radio(mix.seedVideoId, mix.id, mix.params, keepFirst = true) } }.getOrNull()
                    if (r == null || r.songs.isEmpty()) {
                        Toast.makeText(context, "Couldn't start ${mix.title}", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    // Stations like "Listen together" open on a live stream: found out now (the stream is resolved anyway), it starts as one.
                    val first = r.songs.first()
                    val live = first.isLive || kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        kotlinx.coroutines.withTimeoutOrNull(8_000) { runCatching { c.streams.isLive(first.id) }.getOrDefault(false) } ?: false
                    }
                    val songs = if (live && !first.isLive) listOf(first.copy(isLive = true)) + r.songs.drop(1) else r.songs
                    c.queue.counterparts.putAll(r.counterparts)
                    c.player.playQueue(songs, 0, com.techfisher.pulsestream.playback.QueueSource(com.techfisher.pulsestream.playback.QueueKind.RADIO, mix.title))
                }
            })
        }
        // Playback that should be watched (a music video picked from a list) brings the player up.
        LaunchedEffect(Unit) { c.player.showPlayer.collect { playerOpen = true } }
        com.techfisher.pulsestream.ui.components.LyricsToFilesHost()
        val haze = rememberHazeState()
        val backStack by navController.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val showChrome = route != Routes.LOGIN && route != Routes.LOGIN_ADD && route != Routes.DISCORD_LOGIN && route != Routes.WELCOME
        val tabs = settings.navTabs.map { it.tab() }

        val together by c.together.state.collectAsState()
        val inSession = together is com.techfisher.pulsestream.playback.together.TogetherState.Hosting || together is com.techfisher.pulsestream.playback.together.TogetherState.Guest
        // Room the pages leave at the bottom for the bar, the mini player and the system's own bar.
        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomPadding = navInset + navHeight(settings.navStyle) +
            (if (settings.navStyle == NavBarStyle.DOCKED) 0.dp else 16.dp) +
            (if (song != null) miniHeight(settings.miniPlayerStyle) + 8.dp else 0.dp) +
            (if (inSession) 42.dp else 0.dp)

        // Decided once: the tab Pulse opens on is the root every other tab sits on, and the welcome
        // screen is a real destination, so signing in from it navigates inside a graph that already exists.
        val root = remember { (settings.startTab.takeIf { it in settings.navTabs } ?: settings.navTabs.first()).tab().route }
        val start = remember { if (!settings.onboarded && !settings.isLoggedIn) Routes.WELCOME else root }
        val goRoot: () -> Unit = {
            navController.navigate(root) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }

        // The tab bar shrinks while you scroll down a page and grows back as you scroll up.
        var barCompact by remember { mutableStateOf(false) }
        val barScroll = remember {
            object : NestedScrollConnection {
                var acc = 0f
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    if (dy == 0f || abs(available.x) > abs(dy)) return Offset.Zero
                    acc = if ((acc < 0) == (dy < 0)) acc + dy else dy
                    if (acc < -28f) barCompact = true else if (acc > 28f) barCompact = false
                    return Offset.Zero
                }
            }
        }
        LaunchedEffect(route) { barCompact = false }
        val barT by animateFloatAsState(
            if (barCompact && settings.navHideOnScroll) 1f else 0f,
            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), label = "bar",
        )

        LaunchedEffect(Unit) {
            c.player.errors.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
        LaunchedEffect(Unit) {
            c.together.events.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
        LaunchedEffect(Unit) {
            deepLink.collect { uri -> if (uri != null) { handleDeepLink(uri, navigator, c); deepLink.value = null } }
        }

        val selectTab: (String) -> Unit = { r ->
            // The tab whose pages are showing: the first tab page above the root, else the root's own.
            val tabRoutes = tabs.map { it.route }.toSet()
            val stack = navController.currentBackStack.value.mapNotNull { it.destination.route }
            val currentTab = stack.drop(1).firstOrNull { it in tabRoutes } ?: root
            when {
                // The root tab always means its top, from anywhere.
                r == root -> if (!navController.popBackStack(root, inclusive = false, saveState = true)) goRoot()
                // Tapping the tab you're in takes you back to its top.
                r == currentTab -> navController.popBackStack(r, inclusive = false)
                // Every other tab keeps its own pages: leaving Settings on Appearance comes back to Appearance.
                else -> navController.navigate(r) {
                    popUpTo(root) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }

        CompositionLocalProvider(LocalNavigator provides navigator, LocalHazeState provides haze) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.fillMaxSize().nestedScroll(barScroll).hazeSource(haze)) {
                    // The same cross-fade both ways, and for the back gesture too: left to its default,
                    // a predictive back (swipe from the edge) shrinks the page like a card instead.
                    NavHost(
                        navController, startDestination = start,
                        enterTransition = { fadeIn(tween(700)) },
                        exitTransition = { fadeOut(tween(700)) },
                        popEnterTransition = { fadeIn(tween(700)) },
                        popExitTransition = { fadeOut(tween(700)) },
                        predictivePopEnterTransition = { fadeIn(tween(700)) },
                        predictivePopExitTransition = { fadeOut(tween(700)) },
                    ) {
                        composable(Routes.WELCOME) {
                            WelcomeScreen(
                                onSignIn = { navController.navigate(Routes.LOGIN) },
                                onSkip = { c.settings.setOnboarded(true); goRoot() },
                            )
                        }
                        composable(Routes.HOME) { ScreenBackdrop(BackdropScreen.HOME) { HomeScreen(bottomPadding) } }
                        composable(Routes.SEARCH) { ScreenBackdrop(BackdropScreen.SEARCH) { SearchScreen(bottomPadding) } }
                        composable(Routes.LIBRARY) { ScreenBackdrop(BackdropScreen.LIBRARY) { LibraryScreen(bottomPadding) } }
                        composable(Routes.REPLAY) { ScreenBackdrop(BackdropScreen.REPLAY) { ReplayScreen(bottomPadding) } }
                        composable(Routes.SETTINGS) { ScreenBackdrop(BackdropScreen.SETTINGS) { SettingsScreen(bottomPadding) } }
                        composable(Routes.BACKGROUNDS) { ScreenBackdrop(BackdropScreen.SETTINGS) { com.techfisher.pulsestream.ui.screens.BackgroundsScreen(bottomPadding) } }
                        composable("settings/{section}", listOf(navArgument("section") { type = NavType.StringType })) {
                            ScreenBackdrop(BackdropScreen.SETTINGS) { SettingsScreen(bottomPadding, com.techfisher.pulsestream.ui.screens.SettingsSection.of(it.arguments?.getString("section"))) }
                        }
                        composable(Routes.EQ) { ScreenBackdrop(BackdropScreen.SETTINGS) { EqualizerScreen(bottomPadding) } }
                        composable(Routes.CATALOGUE) { ScreenBackdrop(BackdropScreen.HOME) { CatalogueScreen(bottomPadding) } }
                        composable(Routes.MOODS) { ScreenBackdrop(BackdropScreen.SEARCH) { com.techfisher.pulsestream.ui.screens.MoodsScreen(bottomPadding) } }
                        composable(Routes.IMPORT) { ScreenBackdrop(BackdropScreen.LIBRARY) { com.techfisher.pulsestream.ui.screens.ImportScreen(bottomPadding) } }
                        composable(Routes.TOGETHER) { ScreenBackdrop(BackdropScreen.LIBRARY) { com.techfisher.pulsestream.ui.screens.TogetherScreen(bottomPadding) } }
                        composable(Routes.PODCASTS) { ScreenBackdrop(BackdropScreen.LIBRARY) { com.techfisher.pulsestream.ui.screens.PodcastsScreen(bottomPadding) } }
                        composable("podcast/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                            com.techfisher.pulsestream.ui.screens.PodcastScreen(it.arguments?.getString("id")!!, bottomPadding)
                        }
                        composable(Routes.DOWNLOADS) { CollectionScreen(CollectionType.DOWNLOADS, "downloads", null, bottomPadding) }
                        composable(Routes.LOCAL) { ScreenBackdrop(BackdropScreen.LIBRARY) { com.techfisher.pulsestream.ui.screens.LocalMusicScreen(bottomPadding) } }
                        composable(Routes.LOGIN) {
                            LoginScreen(
                                onDone = goRoot,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(Routes.DISCORD_LOGIN) {
                            com.techfisher.pulsestream.ui.screens.DiscordLoginScreen(onBack = { navController.popBackStack() })
                        }
                        composable(Routes.LOGIN_ADD) {
                            LoginScreen(
                                onDone = goRoot,
                                onBack = { navController.popBackStack() },
                                addAccount = true,
                            )
                        }
                        composable("album/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                            CollectionScreen(CollectionType.ALBUM, it.arguments?.getString("id")!!, null, bottomPadding)
                        }
                        composable("playlist/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                            CollectionScreen(CollectionType.PLAYLIST, it.arguments?.getString("id")!!, null, bottomPadding)
                        }
                        composable("liked?genre={genre}", listOf(navArgument("genre") { type = NavType.StringType; nullable = true; defaultValue = null })) {
                            CollectionScreen(CollectionType.LIKED, "liked", it.arguments?.getString("genre"), bottomPadding)
                        }
                        composable("artist/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                            ArtistScreen(it.arguments?.getString("id")!!, bottomPadding)
                        }
                        composable(
                            "browse/{id}?params={params}&title={title}",
                            listOf(
                                navArgument("id") { type = NavType.StringType },
                                navArgument("params") { type = NavType.StringType; defaultValue = "" },
                                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) {
                            BrowseScreen(
                                it.arguments?.getString("id")!!,
                                it.arguments?.getString("params")?.takeIf { p -> p.isNotBlank() },
                                it.arguments?.getString("title") ?: "",
                                bottomPadding,
                            )
                        }
                    }
                }

                // Floating chrome: mini player + tab bar
                if (showChrome) {
                    val docked = settings.navStyle == NavBarStyle.DOCKED
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .then(if (docked) Modifier else Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)),
                    ) {
                        AnimatedVisibility(inSession && !playerOpen && route != Routes.TOGETHER, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                            TogetherPill(together, Modifier.padding(bottom = 8.dp)) { navigator.go(Routes.TOGETHER) }
                        }
                        AnimatedVisibility(song != null && !playerOpen, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                            MiniPlayer(
                                Modifier.padding(bottom = 8.dp).then(if (docked) Modifier.padding(horizontal = 10.dp) else Modifier),
                            ) { playerOpen = true }
                        }
                        TabBar(tabs, route, barT, settings, selectTab)
                    }
                }

                AnimatedVisibility(
                    playerOpen && song != null,
                    enter = slideInVertically(tween(if (settings.reduceMotion) 260 else 420)) { it } + fadeIn(tween(200)),
                    exit = slideOutVertically(tween(if (settings.reduceMotion) 240 else 360)) { it } + fadeOut(tween(300)),
                ) {
                    NowPlayingScreen { playerOpen = false }
                }
            }
        }
    }
}

/** Above the mini player while a Listen Together session is on: who with, and a way back to it. */
@Composable
private fun TogetherPill(state: com.techfisher.pulsestream.playback.together.TogetherState, modifier: Modifier, onClick: () -> Unit) {
    val (icon, text) = when (state) {
        is com.techfisher.pulsestream.playback.together.TogetherState.Hosting ->
            PulseIcons.Radio to "Hosting · code ${state.code}" + when (state.guests.size) { 0 -> ""; 1 -> " · 1 listening"; else -> " · ${state.guests.size} listening" }
        is com.techfisher.pulsestream.playback.together.TogetherState.Guest ->
            PulseIcons.Groups to "With ${state.hostName}" + when {
                !state.listenHere -> " · adding songs"
                !state.inSync -> " · out of step"
                else -> ""
            }
        else -> return
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.height(34.dp), shape = RoundedCornerShape(17.dp), elevation = 4.dp) {
            Row(
                Modifier.fillMaxHeight().clickable(onClickLabel = "Open Listen together", onClick = onClick).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

private fun isSelected(tab: Tab, route: String?): Boolean =
    route == tab.route || (tab.route == Routes.SETTINGS && route?.startsWith("settings/") == true)

/** @param compact 0 = full bar, 1 = slim bar (while scrolling down). */
@Composable
private fun TabBar(tabs: List<Tab>, route: String?, compact: Float, settings: AppSettings, onSelect: (String) -> Unit) {
    when (settings.navStyle) {
        NavBarStyle.FLOATING -> {
            val height = lerp(64.dp, 50.dp, compact)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassSurface(Modifier.fillMaxWidth(1f - 0.30f * compact).height(height), shape = RoundedCornerShape(height / 2)) {
                    Row(Modifier.fillMaxSize().padding(lerp(6.dp, 4.dp, compact)), verticalAlignment = Alignment.CenterVertically) {
                        tabs.forEach { tab -> TabItem(tab, isSelected(tab, route), settings.navLabels, compact, Modifier.weight(1f).fillMaxHeight()) { onSelect(tab.route) } }
                    }
                }
            }
        }
        NavBarStyle.DOCKED -> {
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), elevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().height(lerp(62.dp, 50.dp, compact)).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab -> TabItem(tab, isSelected(tab, route), settings.navLabels, compact, Modifier.weight(1f).fillMaxHeight()) { onSelect(tab.route) } }
                }
            }
        }
        NavBarStyle.MINIMAL -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassSurface(Modifier.height(54.dp), shape = RoundedCornerShape(27.dp)) {
                    Row(Modifier.fillMaxHeight().padding(5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        tabs.forEach { tab -> MinimalTab(tab, isSelected(tab, route)) { onSelect(tab.route) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, labels: NavLabels, compact: Float, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (selected) scheme.primary else scheme.onSurfaceVariant
    val indicator by animateColorAsState(if (selected) scheme.primary.copy(alpha = 0.16f) else Color.Transparent, label = "tab")
    val click = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = tab.label, onClick = onClick)
    val icon = if (selected) tab.selectedIcon else tab.icon
    when (labels) {
        NavLabels.ALWAYS -> Column(
            modifier.clip(RoundedCornerShape(28.dp)).background(indicator).then(click),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, tab.label, tint = fg, modifier = Modifier.size(24.dp))
            if (compact < 0.98f) Text(
                tab.label, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.height(lerp(16.dp, 0.dp, compact)).graphicsLayer { alpha = (1f - compact * 1.6f).coerceIn(0f, 1f) },
            )
        }
        // The picked tab grows into a capsule with its name beside the icon.
        NavLabels.SELECTED -> Box(modifier.then(click), contentAlignment = Alignment.Center) {
            Row(
                Modifier.height(40.dp).clip(CircleShape).background(indicator).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, tab.label, tint = fg, modifier = Modifier.size(22.dp))
                AnimatedVisibility(selected && compact < 0.5f, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                    Row {
                        Spacer(Modifier.width(6.dp))
                        Text(tab.label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        NavLabels.NEVER -> Box(modifier.then(click), contentAlignment = Alignment.Center) {
            Box(Modifier.size(52.dp, 40.dp).clip(CircleShape).background(indicator), contentAlignment = Alignment.Center) {
                Icon(icon, tab.label, tint = fg, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun MinimalTab(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.onSurface else Color.Transparent, label = "minTab")
    Box(
        Modifier.size(54.dp, 44.dp).clip(CircleShape).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = tab.label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, tint = if (selected) scheme.surface else scheme.onSurfaceVariant, modifier = Modifier.size(23.dp))
    }
}

/** Opens music.youtube.com links shared into the app. */
private fun handleDeepLink(uri: Uri, nav: Navigator, c: AppContainer) {
    val v = uri.getQueryParameter("v")
    val list = uri.getQueryParameter("list")
    val path = uri.pathSegments
    when {
        // A widget's cover or title: the player.
        uri.scheme == "pulse" && uri.host == "player" -> nav.openPlayer()
        v != null -> {
            nav.openPlayer()
            launchSingle(c, v)
        }
        list != null -> nav.go(Routes.playlist(list))
        path.firstOrNull() == "browse" && path.size > 1 -> {
            val id = path[1]
            if (id.startsWith("MPRE")) nav.go(Routes.album(id))
            else if (id.startsWith("MPSP")) nav.go(Routes.podcast(id))
            else nav.go(Routes.browse(id, null, ""))
        }
        path.firstOrNull() == "channel" && path.size > 1 -> nav.go(Routes.artist(path[1]))
    }
}

private fun launchSingle(c: AppContainer, videoId: String) = c.scope.launch {
    val r = runCatching { c.ytm.next(videoId) }.getOrNull()
    val song = r?.songs?.firstOrNull { it.id == videoId } ?: Song(videoId, "Loading…")
    withContext(Dispatchers.Main) { c.player.playSingle(song) }
}
