package com.streambox.player.core.model

enum class ThemeMode { System, Light, Dark, Amoled }

/**
 * Persisted app-level settings. Grows phase by phase (playback, EPG, parental control...).
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val useDynamicColor: Boolean = false,
    val playback: PlaybackSettings = PlaybackSettings(),
    /** Channel browser view and sort order (P3b). */
    val browse: BrowseSettings = BrowseSettings(),
)

/** First-launch flow progress. Content is never preloaded; the app starts empty. */
data class OnboardingState(val welcomeSeen: Boolean = false, val disclaimerAccepted: Boolean = false) {
    val isComplete: Boolean get() = welcomeSeen && disclaimerAccepted
}
