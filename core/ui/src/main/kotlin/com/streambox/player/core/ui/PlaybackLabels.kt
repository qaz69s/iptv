package com.streambox.player.core.ui

import androidx.annotation.StringRes
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.SubtitleBackground
import com.streambox.player.core.model.SubtitleColor
import com.streambox.player.core.model.SubtitleSize

/** Labels shared by the player panel and the settings screen (features cannot depend on each other). */
@StringRes
fun AspectRatioMode.labelRes(): Int = when (this) {
    AspectRatioMode.Fit -> R.string.core_ui_aspect_fit
    AspectRatioMode.Ratio16x9 -> R.string.core_ui_aspect_16_9
    AspectRatioMode.Ratio4x3 -> R.string.core_ui_aspect_4_3
    AspectRatioMode.Fill -> R.string.core_ui_aspect_fill
    AspectRatioMode.Stretch -> R.string.core_ui_aspect_stretch
    AspectRatioMode.Original -> R.string.core_ui_aspect_original
}

@StringRes
fun SubtitleSize.labelRes(): Int = when (this) {
    SubtitleSize.Small -> R.string.core_ui_subtitle_size_small
    SubtitleSize.Medium -> R.string.core_ui_subtitle_size_medium
    SubtitleSize.Large -> R.string.core_ui_subtitle_size_large
    SubtitleSize.ExtraLarge -> R.string.core_ui_subtitle_size_extra_large
}

@StringRes
fun SubtitleColor.labelRes(): Int = when (this) {
    SubtitleColor.White -> R.string.core_ui_subtitle_color_white
    SubtitleColor.Yellow -> R.string.core_ui_subtitle_color_yellow
    SubtitleColor.Green -> R.string.core_ui_subtitle_color_green
    SubtitleColor.Cyan -> R.string.core_ui_subtitle_color_cyan
}

@StringRes
fun SubtitleBackground.labelRes(): Int = when (this) {
    SubtitleBackground.None -> R.string.core_ui_subtitle_background_none
    SubtitleBackground.SemiTransparent -> R.string.core_ui_subtitle_background_semi
    SubtitleBackground.Opaque -> R.string.core_ui_subtitle_background_opaque
}
