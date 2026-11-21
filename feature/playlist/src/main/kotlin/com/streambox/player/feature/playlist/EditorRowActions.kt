package com.streambox.player.feature.playlist

import androidx.annotation.StringRes

/** Optional buttons of an [EditorRow]; a null callback hides the button. */
internal data class EditorRowActions(
    val onToggleVisible: (() -> Unit)? = null,
    val onRename: (() -> Unit)? = null,
    val onMoveUp: (() -> Unit)? = null,
    val onMoveDown: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    @StringRes val deleteLabel: Int = R.string.feature_playlist_delete,
    /** Click / OK on the text part (e.g. open a group's channels). */
    val onOpen: (() -> Unit)? = null,
)
