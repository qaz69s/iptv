package com.streamdeck.player.feature.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.datastore.SettingsRepository
import com.streamdeck.player.core.designsystem.IconTile
import com.streamdeck.player.core.designsystem.OptionCard
import com.streamdeck.player.core.designsystem.PageDots
import com.streamdeck.player.core.designsystem.SdButton
import com.streamdeck.player.core.designsystem.SdButtonStyle
import com.streamdeck.player.core.designsystem.SdCard
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.backdropGlow
import com.streamdeck.player.core.designsystem.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.streamdeck.player.core.designsystem.R as DR

private const val PAGES = 3

/**
 * First start (spec §9): welcome → compliance notice → add the first playlist, with a page indicator.
 * The app ships without any content; the last page only offers the user's own sources. Phone: single column with
 * bottom buttons. TV: brand panel on the left, large text and focusable buttons on the right.
 */
@Composable
fun OnboardingScreen(
    onDone: (PlaylistType) -> Unit,
    onSkip: (() -> Unit)? = null,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val finish: (PlaylistType?) -> Unit = { type ->
        scope.launch {
            vm.settings.setOnboarded()
            if (type == null) onSkip?.invoke() else onDone(type)
        }
    }
    BackHandler(enabled = step > 0) { step-- }
    if (rememberIsTv()) {
        TvOnboarding(step, { step = it }, finish, skipAvailable = onSkip != null)
    } else {
        PhoneOnboarding(step, { step = it }, finish, skipAvailable = onSkip != null)
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(val settings: SettingsRepository) : ViewModel()

// ------------------------------------------------------------------------------------------- phone

@Composable
private fun PhoneOnboarding(step: Int, setStep: (Int) -> Unit, finish: (PlaylistType?) -> Unit, skipAvailable: Boolean) {
    Column(Modifier.fillMaxSize().backdropGlow().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        PageDots(PAGES, step, Modifier.padding(start = 24.dp, top = 20.dp))
        AnimatedContent(targetState = step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding", modifier = Modifier.weight(1f)) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            ) {
                when (s) {
                    0 -> WelcomePage(large = false)
                    1 -> NoticePage(large = false)
                    else -> AddPage(large = false, finish = finish)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) SdButton(stringResource(DR.string.ds_back), { setStep(step - 1) }, style = SdButtonStyle.Secondary, large = true)
            Box(Modifier.weight(1f))
            when (step) {
                0 -> SdButton(stringResource(R.string.continue_), { setStep(1) }, icon = Icons.AutoMirrored.Filled.ArrowForward, large = true)
                1 -> SdButton(stringResource(R.string.accept), { setStep(2) }, icon = Icons.Default.Check, large = true)
                else -> if (skipAvailable) SdButton(stringResource(R.string.later), { finish(null) }, style = SdButtonStyle.Secondary, large = true)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- TV

@Composable
private fun TvOnboarding(step: Int, setStep: (Int) -> Unit, finish: (PlaylistType?) -> Unit, skipAvailable: Boolean) {
    val p = SdTheme.palette
    val primary = remember { FocusRequester() }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(0.9f).fillMaxHeight().backdropGlow(p.card).padding(56.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            BrandLogo(96.dp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandName(56)
                Text(stringResource(R.string.welcome_body), color = p.textSecondary, style = MaterialTheme.typography.headlineSmall)
            }
            PageDots(PAGES, step)
        }
        Column(
            Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 64.dp, vertical = 56.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        ) {
            when (step) {
                0 -> {
                    WelcomePage(large = true, showBrand = false)
                    SdButton(stringResource(R.string.continue_), { setStep(1) }, Modifier.focusRequester(primary),
                        icon = Icons.AutoMirrored.Filled.ArrowForward, large = true)
                }
                1 -> {
                    NoticePage(large = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SdButton(stringResource(R.string.accept), { setStep(2) }, Modifier.focusRequester(primary), icon = Icons.Default.Check, large = true)
                        SdButton(stringResource(DR.string.ds_back), { setStep(0) }, style = SdButtonStyle.Secondary, large = true)
                    }
                }
                else -> {
                    AddPage(large = true, finish = finish, firstFocus = primary)
                    if (skipAvailable) SdButton(stringResource(R.string.later), { finish(null) }, style = SdButtonStyle.Secondary, large = true)
                }
            }
        }
    }
    LaunchedEffect(step) { runCatching { primary.requestFocus() } }
}

// ------------------------------------------------------------------------------------------- pages

@Composable
private fun BrandLogo(size: androidx.compose.ui.unit.Dp) {
    val p = SdTheme.palette
    Box(Modifier.size(size).background(p.accent, RoundedCornerShape(size / 4)), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.brand_initial), color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.48f).sp)
    }
}

/** “Stream**Deck**” word mark with the accent half (design 1). */
@Composable
private fun BrandName(sizeSp: Int) {
    val p = SdTheme.palette
    val stream = stringResource(R.string.brand_stream)
    val deck = stringResource(R.string.brand_deck)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = p.textPrimary)) { append(stream) }
            withStyle(SpanStyle(color = p.accent)) { append(deck) }
        },
        fontWeight = FontWeight.ExtraBold, fontSize = sizeSp.sp,
    )
}

@Composable
private fun WelcomePage(large: Boolean, showBrand: Boolean = true) {
    val p = SdTheme.palette
    if (showBrand) {
        BrandLogo(if (large) 96.dp else 80.dp)
        BrandName(if (large) 48 else 38)
    }
    Text(
        stringResource(R.string.welcome_title), color = p.textPrimary,
        style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
    )
    if (showBrand) Text(stringResource(R.string.welcome_body), color = p.textSecondary, style = MaterialTheme.typography.bodyLarge)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
        Feature(Icons.Outlined.LiveTv, p.hueGreen, R.string.onboard_feature_live, R.string.onboard_feature_live_hint, large)
        Feature(Icons.Outlined.CalendarViewMonth, p.hueBlue, R.string.onboard_feature_epg, R.string.onboard_feature_epg_hint, large)
        Feature(Icons.Outlined.Devices, p.huePurple, R.string.onboard_feature_devices, R.string.onboard_feature_devices_hint, large)
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: Int, hint: Int, large: Boolean) {
    val p = SdTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, size = if (large) 52.dp else 44.dp)
        Column(Modifier.padding(start = 16.dp)) {
            Text(stringResource(title), color = p.textPrimary, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
            Text(stringResource(hint), color = p.textSecondary, style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NoticePage(large: Boolean) {
    val p = SdTheme.palette
    IconTile(Icons.Outlined.Gavel, p.hueAmber, size = if (large) 64.dp else 56.dp)
    Text(
        stringResource(R.string.disclaimer_title), color = p.textPrimary,
        style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
    )
    SdCard(Modifier.widthIn(max = 640.dp)) {
        Text(stringResource(R.string.disclaimer), color = p.textPrimary,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 640.dp)) {
        IconTile(Icons.Outlined.PrivacyTip, p.hueGreen, size = 36.dp)
        Text(
            stringResource(R.string.privacy_note), color = p.textSecondary, modifier = Modifier.padding(start = 12.dp),
            style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AddPage(large: Boolean, finish: (PlaylistType?) -> Unit, firstFocus: FocusRequester? = null) {
    val p = SdTheme.palette
    Text(
        stringResource(R.string.first_playlist_title), color = p.textPrimary,
        style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
    )
    Text(stringResource(R.string.first_playlist_body), color = p.textSecondary,
        style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 640.dp)) {
        listOf(
            Triple(PlaylistType.M3U_URL, R.string.method_url_desc, Icons.Outlined.Link) to p.hueGreen,
            Triple(PlaylistType.M3U_FILE, R.string.method_file_desc, Icons.Outlined.FolderOpen) to p.hueAmber,
            Triple(PlaylistType.XTREAM, R.string.method_xtream_desc, Icons.Outlined.Dns) to p.hueBlue,
        ).forEachIndexed { i, (t, tint) ->
            OptionCard(
                title = stringResource(t.first.labelRes()), description = stringResource(t.second), icon = t.third, tint = tint,
                selected = false, onClick = { finish(t.first) }, large = large,
                modifier = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}
