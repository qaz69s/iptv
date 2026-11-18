package com.streambox.player.core.player.session

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.streambox.player.core.model.ZapDirection

/**
 * What the MediaSession sees: the app's player plus "previous / next" mapped to channel zapping. A live
 * channel is a single media item, so ExoPlayer itself never offers next / previous; this wrapper adds
 * those commands (also in [Player.Listener.onAvailableCommandsChanged], which the session forwards to
 * controllers) so the notification shows the buttons and headset / Bluetooth skip keys work.
 *
 * [release] is routed to [onRelease] because the player belongs to the playback controller, not the
 * session.
 */
@OptIn(UnstableApi::class)
internal class ChannelNavigationPlayer(
    player: Player,
    private val onZap: (ZapDirection) -> Unit,
    private val onRelease: () -> Unit,
) : ForwardingPlayer(player) {
    private val listeners = mutableMapOf<Player.Listener, Player.Listener>()

    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().withChannelNavigation()

    override fun isCommandAvailable(command: Int): Boolean =
        command in NAVIGATION_COMMANDS || super.isCommandAvailable(command)

    override fun seekToNext() = onZap(ZapDirection.NEXT)

    override fun seekToNextMediaItem() = onZap(ZapDirection.NEXT)

    override fun seekToPrevious() = onZap(ZapDirection.PREVIOUS)

    override fun seekToPreviousMediaItem() = onZap(ZapDirection.PREVIOUS)

    override fun release() = onRelease()

    override fun addListener(listener: Player.Listener) {
        super.addListener(
            listeners.getOrPut(listener) {
                NavigationCommandsListener(listener) { it.withChannelNavigation() }
            },
        )
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)?.let { super.removeListener(it) }
    }

    private companion object {
        val NAVIGATION_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )

        fun Player.Commands.withChannelNavigation(): Player.Commands {
            val builder = buildUpon()
            NAVIGATION_COMMANDS.forEach(builder::add)
            return builder.build()
        }
    }
}
