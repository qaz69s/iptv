package com.streambox.player.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a clearly visible ring while the element has focus (D-pad / keyboard navigation).
 * Apply before the clickable/focusable modifier so it observes that element's focus.
 */
fun Modifier.focusRing(
    shape: Shape = RoundedCornerShape(12.dp),
    width: Dp = 3.dp,
    color: Color = Color.Unspecified,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ringColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    this
        .onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(width, ringColor, shape) else Modifier)
}
