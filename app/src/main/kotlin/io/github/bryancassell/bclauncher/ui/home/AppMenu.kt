package io.github.bryancassell.bclauncher.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import io.github.bryancassell.bclauncher.R
import io.github.bryancassell.bclauncher.data.InstalledApp

/** The entries in an app's menu, in the order it shows them. */
internal enum class AppOption(@StringRes val label: Int) {
    Launch(R.string.app_option_launch),
    AppSettings(R.string.app_option_app_settings),
    Uninstall(R.string.app_option_uninstall),
    ;

    /**
     * Whether selecting the entry does anything for [app]. A system app's Uninstall is shown disabled (PRD.md, Design
     * decisions).
     */
    fun isEnabledFor(app: InstalledApp): Boolean = this != Uninstall || !app.isSystemApp
}

/**
 * [app]'s options, in a dialog over the grid with the app's name above them (PRD.md, Design decisions). Focus starts on
 * the first entry. Choosing one or pressing Back closes the dialog, and focus is still on the tile, since the grid's
 * window losing focus to the dialog's doesn't clear it.
 */
@Composable
internal fun AppMenu(app: InstalledApp, onOptionClick: (AppOption) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val firstEntry = remember { FocusRequester() }
        val heldPress = remember { HeldPressFilter() }
        Surface(
            modifier = Modifier
                .width(320.dp)
                .onPreviewKeyEvent(heldPress::ignores),
            shape = MaterialTheme.shapes.medium,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = app.label,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                AppOption.entries.forEachIndexed { index, option ->
                    val focus = if (index == 0) Modifier.focusRequester(firstEntry) else Modifier
                    ListItem(
                        selected = false,
                        onClick = { onOptionClick(option) },
                        headlineContent = { Text(stringResource(option.label)) },
                        modifier = focus,
                        enabled = option.isEnabledFor(app),
                    )
                }
            }
        }
        LaunchedEffect(Unit) { firstEntry.requestFocus() }
    }
}

/**
 * Ignores the rest of the press that opened the menu, until a key goes down afresh. The tile opens the menu on the
 * first repeat of Select, while it's still held, so the press's later repeats and its release reach the menu once it
 * has focus. tv-material's ListItem chooses itself on any release of Select, without checking that it saw the press
 * (handleDPadEnter in tv-material 1.1.0), so the release would choose Launch.
 */
private class HeldPressFilter {
    private var pressedHere = false

    fun ignores(event: KeyEvent): Boolean {
        val freshPress = event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0
        if (freshPress) pressedHere = true
        return !pressedHere
    }
}
