package com.streambox.player.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.theme.StreamBoxTheme
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor

@Composable
internal fun DisclaimerRoute(
    onAccepted: () -> Unit,
    onBack: () -> Unit,
    viewModel: DisclaimerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.completed) {
        if (uiState.completed) onAccepted()
    }
    DisclaimerScreen(
        uiState = uiState,
        onConsentChanged = viewModel::onConsentChanged,
        onAccept = viewModel::onAccept,
        onBack = onBack,
    )
}

@Composable
internal fun DisclaimerScreen(
    uiState: DisclaimerUiState,
    onConsentChanged: (Boolean) -> Unit,
    onAccept: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val isTv = LocalFormFactor.current == FormFactor.Tv
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 640.dp)) {
                Text(
                    text = stringResource(R.string.feature_onboarding_disclaimer_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.feature_onboarding_disclaimer_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.feature_onboarding_privacy_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .focusRing()
                        .toggleable(
                            value = uiState.consentChecked,
                            enabled = !uiState.submitting,
                            role = Role.Checkbox,
                            onValueChange = onConsentChanged,
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = uiState.consentChecked, onCheckedChange = null)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(
                        text = stringResource(R.string.feature_onboarding_disclaimer_consent),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.focusRing()) {
                        Text(stringResource(R.string.feature_onboarding_back))
                    }
                    Button(
                        onClick = onAccept,
                        enabled = uiState.canAccept,
                        modifier = Modifier.focusRing(),
                    ) {
                        Text(stringResource(R.string.feature_onboarding_accept))
                    }
                }
            }
        }
    }
    if (isTv) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Preview
@Composable
private fun DisclaimerScreenPreview() {
    StreamBoxTheme {
        DisclaimerScreen(
            uiState = DisclaimerUiState(consentChecked = true),
            onConsentChanged = {},
            onAccept = {},
            onBack = {},
        )
    }
}
