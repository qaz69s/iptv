package com.streambox.player.feature.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.player.sleep.SleepTimer
import com.streambox.player.core.player.sleep.SleepTimerState
import java.util.Locale

/** Chips of the sleep timer section: off, the presets, custom. */
internal sealed interface SleepChoice {
    data object Off : SleepChoice

    data class Preset(val minutes: Int) : SleepChoice

    data object Custom : SleepChoice

    companion object {
        val ALL: List<SleepChoice> = listOf(Off) + SleepTimer.PRESET_MINUTES.map(::Preset) + Custom

        /** The chip that shows as selected for a running timer. */
        fun of(state: SleepTimerState?): SleepChoice = when {
            state == null -> Off
            state.durationMinutes in SleepTimer.PRESET_MINUTES -> Preset(state.durationMinutes)
            else -> Custom
        }
    }
}

/** "Sleep timer" heading with the remaining time, then the chips; custom opens a minutes dialog. */
@Composable
internal fun SleepTimerSection(state: SleepTimerState?, onSet: (Int?) -> Unit, firstFocus: FocusRequester?) {
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.feature_player_sleep_timer)
    Text(
        text = if (state == null) {
            title
        } else {
            stringResource(R.string.feature_player_sleep_timer_remaining, title, formatRemaining(state.remainingMs))
        },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
    val selected = SleepChoice.of(state)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SleepChoice.ALL.forEachIndexed { index, choice ->
            val focusModifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
            FilterChip(
                selected = choice == selected,
                onClick = {
                    when (choice) {
                        SleepChoice.Off -> onSet(null)
                        is SleepChoice.Preset -> onSet(choice.minutes)
                        SleepChoice.Custom -> customOpen = true
                    }
                },
                label = { Text(choiceLabel(choice, state)) },
                modifier = focusModifier.focusRing(),
            )
        }
    }
    if (customOpen) {
        CustomMinutesDialog(
            onConfirm = { minutes ->
                customOpen = false
                onSet(minutes)
            },
            onDismiss = { customOpen = false },
        )
    }
}

@Composable
private fun choiceLabel(choice: SleepChoice, state: SleepTimerState?): String = when (choice) {
    SleepChoice.Off -> stringResource(R.string.feature_player_sleep_off)

    is SleepChoice.Preset -> stringResource(R.string.feature_player_sleep_minutes, choice.minutes)

    SleepChoice.Custom -> if (state != null && SleepChoice.of(state) == SleepChoice.Custom) {
        stringResource(R.string.feature_player_sleep_custom_value, state.durationMinutes)
    } else {
        stringResource(R.string.feature_player_sleep_custom)
    }
}

/** Plain number input, 1..600 minutes; OK stays disabled for anything else. */
@Composable
private fun CustomMinutesDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val minutes = SleepTimerInput.parseMinutes(text)
    val fieldFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_player_sleep_custom_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(MAX_DIGITS) },
                label = { Text(stringResource(R.string.feature_player_sleep_custom_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.feature_player_sleep_custom_hint,
                            SleepTimer.MIN_MINUTES,
                            SleepTimer.MAX_MINUTES,
                        ),
                    )
                },
                isError = text.isNotEmpty() && minutes == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { minutes?.let(onConfirm) }),
                modifier = Modifier.focusRequester(fieldFocus),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { minutes?.let(onConfirm) },
                enabled = minutes != null,
                modifier = Modifier.focusRing(),
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRing()) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
}

/** "m:ss" below an hour, else "h:mm:ss". */
internal fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs + MS_PER_SECOND - 1) / MS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** Validation of the custom minutes field (kept out of Compose for unit tests). */
internal object SleepTimerInput {
    /** Minutes in [SleepTimer.MIN_MINUTES]..[SleepTimer.MAX_MINUTES], else null. */
    fun parseMinutes(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in SleepTimer.MIN_MINUTES..SleepTimer.MAX_MINUTES }
}

private const val MAX_DIGITS = 3
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
