package com.streamdeck.player.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamdeck.player.core.common.PinResult
import com.streamdeck.player.core.data.ParentalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.streamdeck.player.core.designsystem.R as DR

@HiltViewModel
class ParentalViewModel @Inject constructor(val parental: ParentalRepository) : ViewModel() {
    val hasPin: StateFlow<Boolean> = parental.hasPin.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    suspend fun verify(pin: String): PinResult = parental.verify(pin)
}

/**
 * Wraps actions that need the parental PIN (locked channels/groups, editor, settings — spec §4.7).
 * Call [Host] once in the composition that owns the gate.
 */
@Stable
class PinGate internal constructor(private val vm: ParentalViewModel) {
    internal var pending by mutableStateOf<(() -> Unit)?>(null)

    fun runUnlocked(locked: Boolean, action: () -> Unit) {
        if (!locked || !vm.hasPin.value || vm.parental.isUnlocked()) action() else pending = action
    }

    @Composable
    fun Host() {
        val action = pending ?: return
        PinDialog(
            title = stringResource(R.string.pin_enter),
            verify = vm::verify,
            onSuccess = { pending = null; action() },
            onDismiss = { pending = null },
        )
    }
}

@Composable
fun rememberPinGate(vm: ParentalViewModel = hiltViewModel()): PinGate = remember(vm) { PinGate(vm) }

/** PIN entry with attempts-left / lock-out feedback. */
@Composable
fun PinDialog(title: String, verify: suspend (String) -> PinResult, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val wrongFmt = stringResource(R.string.pin_wrong)
    val lockedFmt = stringResource(R.string.pin_locked)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    pin, { v -> pin = v.filter(Char::isDigit).take(6) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text(stringResource(R.string.pin_label)) },
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (val r = verify(pin)) {
                        PinResult.Ok -> onSuccess()
                        is PinResult.Wrong -> { message = wrongFmt.format(r.attemptsLeft); pin = "" }
                        is PinResult.LockedOut -> { message = lockedFmt.format((r.remainingMs + 999) / 1000); pin = "" }
                    }
                }
            }) { Text(stringResource(DR.string.ds_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(DR.string.ds_cancel)) } },
    )
}

/** New PIN, entered twice. */
@Composable
fun NewPinDialog(isValid: (String) -> Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_set)) },
        text = {
            Column {
                OutlinedTextField(
                    first, { v -> first = v.filter(Char::isDigit).take(6) }, singleLine = true, label = { Text(stringResource(R.string.pin_new)) },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    second, { v -> second = v.filter(Char::isDigit).take(6) }, singleLine = true, label = { Text(stringResource(R.string.pin_repeat)) },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    !isValid(first) -> R.string.pin_format
                    first != second -> R.string.pin_mismatch
                    else -> null
                }
                if (error == null) onSave(first)
            }) { Text(stringResource(DR.string.ds_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(DR.string.ds_cancel)) } },
    )
}
