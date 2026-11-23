package com.streamdeck.player.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ---------------------------------------------------------------- top bar

/** Page header shared by the secondary phone pages: round back button, bold title, optional subtitle and actions. */
@Composable
fun SdTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val p = SdTheme.palette
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            SdIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ds_back), onBack)
            Box(Modifier.width(8.dp))
        } else {
            Box(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

// ---------------------------------------------------------------- buttons

enum class SdButtonStyle { Primary, Secondary, Danger }

/**
 * Pill button of the new design. Primary = accent fill, Secondary = card fill, Danger = red text on card.
 * On TV the focused button switches to the high-contrast focus fill (design 4 “立即观看”).
 */
@Composable
fun SdButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: SdButtonStyle = SdButtonStyle.Primary,
    large: Boolean = false,
    enabled: Boolean = true,
) {
    val p = SdTheme.palette
    val container = when (style) {
        SdButtonStyle.Primary -> p.accent
        SdButtonStyle.Secondary, SdButtonStyle.Danger -> p.cardHigh
    }
    FocusSurface(
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(if (large) 16.dp else 14.dp),
        container = container,
        focusFill = true,
        focusScale = 1.04f,
        enabled = enabled,
        contentDescription = text,
    ) { focused ->
        val fg = when {
            focused -> p.onFocusFill
            style == SdButtonStyle.Primary -> p.onAccent
            style == SdButtonStyle.Danger -> p.live
            else -> p.textPrimary
        }
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = if (large) 26.dp else 18.dp, vertical = if (large) 15.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(if (large) 22.dp else 18.dp))
            Text(
                text, color = fg, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Round icon button (back, more, play …). [contentDescription] is mandatory for TalkBack. */
@Composable
fun SdIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    container: Color = SdTheme.palette.card,
    tint: Color = SdTheme.palette.textPrimary,
    enabled: Boolean = true,
) {
    val p = SdTheme.palette
    FocusSurface(
        onClick = onClick, modifier = modifier.size(size).alpha(if (enabled) 1f else 0.4f), shape = CircleShape, container = container,
        focusFill = true, focusScale = 1.06f, contentDescription = contentDescription, enabled = enabled,
    ) { focused ->
        Icon(icon, null, tint = if (focused) p.onFocusFill else tint, modifier = Modifier.align(Alignment.Center).size(size * 0.48f))
    }
}

// ---------------------------------------------------------------- cards & fields

/** Rounded card container with an optional caption above it (form groups, info blocks). */
@Composable
fun SdCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = SdTheme.palette
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, color = p.textTertiary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        }
        Column(
            Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(20.dp)).border(1.dp, p.stroke.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/**
 * Text field in the card style: label above, filled box, accent border while focused.
 * [large] gives the bigger TV variant (10-foot UI). The label doubles as the TalkBack description.
 */
@Composable
fun SdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    large: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val p = SdTheme.palette
    var focused by remember { mutableStateOf(false) }
    val ring = when {
        isError -> p.live
        focused -> p.accent
        else -> p.stroke
    }
    val textStyle = (if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge).copy(color = p.textPrimary)
    Column(modifier.fillMaxWidth()) {
        Text(label, color = if (focused) p.accent else p.textSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp,
            bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            textStyle = textStyle,
            cursorBrush = SolidColor(p.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            modifier = Modifier.fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = if (large) 60.dp else 52.dp)
                        .background(p.cardHigh.copy(alpha = if (p.isDark) 0.7f else 1f), RoundedCornerShape(14.dp))
                        .border(if (focused || isError) 2.dp else 1.dp, ring, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = if (large) 16.dp else 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = textStyle, color = p.textTertiary, maxLines = 1)
                    inner()
                }
            },
        )
        if (supporting != null) {
            Text(
                supporting, color = if (isError) p.live else p.textTertiary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

/** Row with title, hint and a switch; the whole row toggles (one focus stop on TV). */
@Composable
fun SwitchRow(title: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier, hint: String? = null, icon: ImageVector? = null,
    tint: Color = SdTheme.palette.accent) {
    val p = SdTheme.palette
    FocusSurface(
        onClick = { onToggle(!checked) }, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), container = Color.Transparent,
        contentDescription = title,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconTile(icon, tint)
                Box(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = p.textPrimary, style = MaterialTheme.typography.bodyLarge)
                if (hint != null) Text(hint, color = p.textTertiary, style = MaterialTheme.typography.bodySmall)
            }
            SdSwitch(checked)
        }
    }
}

/** Selectable option card: icon tile, title, description and a check when [selected] (add-playlist methods). */
@Composable
fun OptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val p = SdTheme.palette
    FocusSurface(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        container = if (selected) p.accent.copy(alpha = if (p.isDark) 0.14f else 0.10f) else p.card,
        border = if (selected) p.accent else p.stroke, focusScale = 1.02f,
        contentDescription = "$title, $description",
    ) {
        Row(Modifier.fillMaxWidth().padding(if (large) 20.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint, size = if (large) 52.dp else 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, color = p.textPrimary, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                Text(description, color = p.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                Box(Modifier.size(26.dp).background(p.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Page indicator of the onboarding. */
@Composable
fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val p = SdTheme.palette
    val desc = stringResource(R.string.ds_page_of, current + 1, count)
    Row(modifier.semantics { contentDescription = desc }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier.size(width = if (i == current) 24.dp else 8.dp, height = 8.dp)
                    .background(if (i == current) p.accent else p.track, RoundedCornerShape(50)),
            )
        }
    }
}

// ---------------------------------------------------------------- action panel (bottom sheet on phone, side panel on TV)

data class PanelAction(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val selected: Boolean = false,
    val hint: String? = null,
    val onClick: () -> Unit,
)

/**
 * Context actions for an item. Phone: Material bottom sheet. TV: right-hand side panel that takes focus on its
 * first action; BACK closes it. The panel dismisses itself before running the chosen action, so an action may open
 * another panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPanel(title: String, actions: List<PanelAction>, onDismiss: () -> Unit, subtitle: String? = null) {
    val p = SdTheme.palette
    if (rememberIsTv()) {
        val first = remember { FocusRequester() }
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(p.scrim.copy(alpha = 0.55f))) {
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(440.dp).background(p.background)
                        .border(1.dp, p.stroke, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                        .padding(horizontal = 28.dp, vertical = 36.dp),
                ) {
                    PanelHeader(title, subtitle)
                    LazyColumn(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(actions) { i, a ->
                            PanelRow(a, large = true, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) { onDismiss(); a.onClick() }
                        }
                    }
                }
            }
            // Inside the dialog window so the requester is attached when focus is requested.
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.card) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
                PanelHeader(title, subtitle)
                LazyColumn(Modifier.padding(top = 12.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(actions) { _, a -> PanelRow(a, large = false) { onDismiss(); a.onClick() } }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, subtitle: String?) {
    val p = SdTheme.palette
    Text(title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun PanelRow(a: PanelAction, large: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = SdTheme.palette
    FocusSurface(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        container = if (large) p.card else Color.Transparent, focusFill = true, contentDescription = a.label,
    ) { focused ->
        val fg = when {
            focused -> p.onFocusFill
            a.destructive -> p.live
            a.selected -> p.accent
            else -> p.textPrimary
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (large) 16.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (a.icon != null) {
                Icon(a.icon, null, tint = fg, modifier = Modifier.size(22.dp))
                Box(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(a.label, color = fg, style = MaterialTheme.typography.bodyLarge, fontWeight = if (a.selected) FontWeight.Bold else FontWeight.Medium)
                if (a.hint != null) {
                    Text(a.hint, color = if (focused) p.onFocusFill.copy(alpha = 0.7f) else p.textTertiary,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (a.selected) Icon(Icons.Default.Check, null, tint = fg, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------- dialogs

/** Card dialog used for text input and confirmations; sized for phone or TV. */
@Composable
fun SdDialog(onDismiss: () -> Unit, title: String, message: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = SdTheme.palette
    val tv = rememberIsTv()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(24.dp).widthIn(max = if (tv) 560.dp else 420.dp).fillMaxWidth()
                .background(p.card, RoundedCornerShape(26.dp)).border(1.dp, p.stroke, RoundedCornerShape(26.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
            if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
            content()
        }
    }
}

/** Single text input (rename, new group …). Focus starts in the field. */
@Composable
fun InputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, label: String = title) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    val tv = rememberIsTv()
    SdDialog(onDismiss = onDismiss, title = title) {
        SdTextField(text, { text = it }, label = label, large = tv, focusRequester = field)
        DialogButtons(confirm = stringResource(R.string.ds_ok), onConfirm = { onConfirm(text) }, onDismiss = onDismiss, confirmEnabled = text.isNotBlank())
        LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    }
}

/** Yes/no confirmation; [destructive] paints the confirm button red. */
@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = true) {
    SdDialog(onDismiss = onDismiss, title = title, message = message) {
        DialogButtons(confirm = confirmLabel, onConfirm = onConfirm, onDismiss = onDismiss, danger = destructive)
    }
}

@Composable
fun DialogButtons(confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false, confirmEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
        SdButton(stringResource(R.string.ds_cancel), onDismiss, style = SdButtonStyle.Secondary)
        SdButton(confirm, onConfirm, style = if (danger) SdButtonStyle.Danger else SdButtonStyle.Primary, enabled = confirmEnabled)
    }
}
