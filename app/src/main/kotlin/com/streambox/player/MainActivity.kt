package com.streambox.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.theme.StreamBoxTheme
import com.streambox.player.core.designsystem.theme.StreamBoxTvTheme
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor
import com.streambox.player.core.ui.detectFormFactor
import com.streambox.player.ui.StreamBoxApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val formFactor = detectFormFactor()
        // Only a cold start continues the last channel; a recreated activity keeps its own back stack.
        val resumeChannel: suspend () -> Long? =
            if (savedInstanceState == null) viewModel::channelToResume else NO_RESUME

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = uiState as? MainUiState.Ready ?: return@setContent
            // The start destination is decided once; later onboarding changes navigate explicitly.
            val onboardingCompleteAtLaunch = remember { ready.onboardingComplete }

            CompositionLocalProvider(LocalFormFactor provides formFactor) {
                if (formFactor == FormFactor.Tv) {
                    StreamBoxTvTheme(themeMode = ready.settings.themeMode) {
                        StreamBoxApp(formFactor, onboardingCompleteAtLaunch, viewModel::hasPlaylists, resumeChannel)
                    }
                } else {
                    StreamBoxTheme(
                        themeMode = ready.settings.themeMode,
                        useDynamicColor = ready.settings.useDynamicColor,
                    ) {
                        StreamBoxApp(formFactor, onboardingCompleteAtLaunch, viewModel::hasPlaylists, resumeChannel)
                    }
                }
            }
        }
    }

    private companion object {
        val NO_RESUME: suspend () -> Long? = { null }
    }
}
