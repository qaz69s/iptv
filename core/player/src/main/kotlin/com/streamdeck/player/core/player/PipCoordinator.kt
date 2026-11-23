package com.streamdeck.player.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Shared UI flags so the Activity knows when Picture-in-Picture is allowed and the player UI knows when it is active. */
@Singleton
class PipCoordinator @Inject constructor() {
    val playerScreenVisible = MutableStateFlow(false)
    val inPip = MutableStateFlow(false)
}
