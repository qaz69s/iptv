package com.streamdeck.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.streamdeck.player.core.datastore.ThemeMode
import com.streamdeck.player.core.designsystem.resolvesDark
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamdeck.player.core.data.DeepLinkBus
import com.streamdeck.player.core.datastore.AppSettings
import com.streamdeck.player.core.datastore.LocaleStore
import com.streamdeck.player.core.datastore.SettingsRepository
import com.streamdeck.player.core.designsystem.StreamDeckTheme
import com.streamdeck.player.core.designsystem.isTv
import com.streamdeck.player.core.player.PipCoordinator
import com.streamdeck.player.core.player.PlayerController
import com.streamdeck.player.ui.AppNav
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var pip: PipCoordinator

    @Inject lateinit var controller: PlayerController

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var deepLinks: DeepLinkBus

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        rememberSplashTheme()
        addOnPictureInPictureModeChangedListener { info -> pip.inPip.value = info.isInPictureInPictureMode }
        setContent {
            val s by settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = s.theme.resolvesDark()
            // Status/navigation bar icons follow the app theme, not the system one (light mode had white-on-white icons).
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            StreamDeckTheme(mode = s.theme, accentIndex = s.accentIndex, fontScale = s.fontScale) { AppNav() }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    /**
     * Android 13+: the system splash is drawn before the app runs, so remember a splash style matching the chosen
     * theme + accent for the next launch. Older versions follow the system day/night resources.
     */
    private fun rememberSplashTheme() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            settings.settings.map { it.theme to it.accentIndex }.distinctUntilChanged().collect { (mode, accent) ->
                runCatching { splashScreen.setSplashScreenTheme(splashStyle(mode, accent)) }
            }
        }
    }

    private fun splashStyle(mode: ThemeMode, accent: Int): Int {
        val row = when (mode) {
            ThemeMode.SYSTEM -> 0
            ThemeMode.LIGHT -> 1
            ThemeMode.DARK -> 2
            ThemeMode.AMOLED -> 3
        }
        return SPLASH_STYLES[row * 5 + accent.coerceIn(0, 4)]
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Reminder notifications carry the target channel (spec §4.4 提醒). */
    private fun handleIntent(intent: Intent?) {
        DeepLinkBus.fromIntent(intent)?.let(deepLinks::emit)
    }

    /** Phone/tablet only: leaving the app while a stream plays enters Picture-in-Picture (spec §4.3). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isTv()) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        if (!pip.playerScreenVisible.value || controller.playerFlow.value?.isPlaying != true) return
        runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
    }
}

/** Splash styles by [theme row × accent], see res/values/splash_themes.xml. */
private val SPLASH_STYLES = intArrayOf(
    R.style.Theme_StreamDeck_Starting_S0, R.style.Theme_StreamDeck_Starting_S1, R.style.Theme_StreamDeck_Starting_S2, R.style.Theme_StreamDeck_Starting_S3, R.style.Theme_StreamDeck_Starting_S4,
    R.style.Theme_StreamDeck_Starting_L0, R.style.Theme_StreamDeck_Starting_L1, R.style.Theme_StreamDeck_Starting_L2, R.style.Theme_StreamDeck_Starting_L3, R.style.Theme_StreamDeck_Starting_L4,
    R.style.Theme_StreamDeck_Starting_D0, R.style.Theme_StreamDeck_Starting_D1, R.style.Theme_StreamDeck_Starting_D2, R.style.Theme_StreamDeck_Starting_D3, R.style.Theme_StreamDeck_Starting_D4,
    R.style.Theme_StreamDeck_Starting_A0, R.style.Theme_StreamDeck_Starting_A1, R.style.Theme_StreamDeck_Starting_A2, R.style.Theme_StreamDeck_Starting_A3, R.style.Theme_StreamDeck_Starting_A4,
)
