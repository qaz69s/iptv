@file:Suppress("MatchingDeclarationName")

package com.streambox.player.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** Texts of a [TextInputDialog]; callers pass their own (localized) strings. */
data class TextInputDialogTexts(
    val title: String,
    val label: String,
    val confirm: String,
    val dismiss: String,
    /** Shown under the field, e.g. "Leave empty to restore the original name". */
    val hint: String? = null,
)

/**
 * A one-field dialog (rename, new group). The field takes focus with the text selected, so a remote
 * or keyboard can type right away; the IME action confirms. [allowBlank] false disables confirming an
 * empty value.
 */
@Composable
fun TextInputDialog(
    texts: TextInputDialogTexts,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    allowBlank: Boolean = false,
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length)))
    }
    val canConfirm = allowBlank || value.text.isNotBlank()
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(texts.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(texts.label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(value.text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                texts.hint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }, enabled = canConfirm, modifier = Modifier.focusRing()) {
                Text(texts.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRing()) { Text(texts.dismiss) }
        },
    )
}
