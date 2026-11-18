package com.streambox.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Success(val settings: AppSettings) : SettingsUiState
}

@Suppress("TooManyFunctions") // One handler per preference.
@HiltViewModel
class SettingsViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map<AppSettings, SettingsUiState> { SettingsUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState.Loading,
        )

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun onDynamicColorChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    // --- Playback ----------------------------------------------------------------------------------

    fun onBufferProfileSelected(profile: BufferProfile) {
        viewModelScope.launch { settingsRepository.setBufferProfile(profile) }
    }

    fun onSoftwareDecodingChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoftwareDecodingOnly(enabled) }
    }

    /** 0 turns automatic reconnecting off. */
    fun onReconnectAttemptsSelected(attempts: Int) {
        viewModelScope.launch { settingsRepository.setMaxReconnectAttempts(attempts) }
    }

    fun onDefaultAspectRatioSelected(mode: AspectRatioMode) {
        viewModelScope.launch { settingsRepository.setDefaultAspectRatio(mode) }
    }

    fun onSubtitleStyleChanged(style: SubtitleStyle) {
        viewModelScope.launch { settingsRepository.setSubtitleStyle(style) }
    }

    fun onBackgroundPlaybackChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBackgroundPlayback(enabled) }
    }

    fun onPictureInPictureChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPictureInPicture(enabled) }
    }

    fun onPlaybackGesturesChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPlaybackGestures(enabled) }
    }

    fun onResumeLastChannelChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setResumeLastChannel(enabled) }
    }

    fun onAutoPlayNextChanged(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoPlayNext(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
