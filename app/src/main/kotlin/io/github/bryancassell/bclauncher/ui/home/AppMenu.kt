package io.github.bryancassell.bclauncher.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.onFocusChanged
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
     * Whether selecting the entry does anything for [app]. A system app's Uninstall is shown disabled, so the user sees
     * that it can't be uninstalled. That includes one Play has updated, whose updates the system could uninstall, since
     * an entry named Uninstall shouldn't put back the factory version. Its App info screen offers that.
     */
    fun isEnabledFor(app: InstalledApp): Boolean = this != Uninstall || !app.isSystemApp
}

/**
 * [app]'s options, in a dialog over the grid with the app's name above them. A dialog is its own window, so it keeps
 * focus and handles Back. Choosing an entry or pressing Back closes the dialog, and focus is still on the tile, since
 * the grid's window losing focus to the dialog's doesn't clear it.
 *
 * Focus starts on the first entry. The focused entry is saved, so it's focused again after a configuration change
 * recreates the activity, as the grid's focused tile is.
 */
@Composable
internal fun AppMenu(app: InstalledApp, onOptionClick: (AppOption) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val entries = remember { AppOption.entries.associateWith { FocusRequester() } }
        var focused by rememberSaveable { mutableStateOf(AppOption.entries.first()) }
        val heldPress = remember { HeldPressFilter() }
        Surface(
            modifier = Modifier
                .width(320.dp)
                .onPreviewKeyEvent(heldPress::track),
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
                AppOption.entries.forEach { option ->
                    ListItem(
                        selected = false,
                        onClick = { onOptionClick(option) },
                        headlineContent = { Text(stringResource(option.label)) },
                        modifier = Modifier
                            .focusRequester(entries.getValue(option))
                            .onFocusChanged { if (it.isFocused) focused = option },
                        enabled = option.isEnabledFor(app),
                    )
                }
            }
        }
        // Read as the dialog is first composed. By the time the effect runs, focus has already gone to the first entry
        // as the dialog opened, which sets focused to it.
        val startEntry = remember { focused }
        LaunchedEffect(Unit) { entries.getValue(startEntry).requestFocus() }
    }
}

/**
 * Ignores the rest of any press that began before the menu opened. The tile opens the menu on the first repeat of
 * Select, while it's still held, so the press's later repeats and its release reach the menu once it has focus.
 * Compose passes on a release once its window has seen the key go down, even as a repeat, and tv-material's ListItem
 * chooses itself on any release of Select, without checking that it saw the press (handleDPadEnter in tv-material
 * 1.1.0). So the release would choose the focused entry. Keys are tracked one by one, since the user can press Down
 * while still holding Select.
 *
 * A cancelled release is ignored too, even for a press that began in the menu. Android sends one when the window loses
 * focus while the key is down, as when the user presses Home while holding Select on an entry, and ListItem doesn't
 * check for it.
 */
private class HeldPressFilter {
    // The keys that went down while the menu had focus and haven't been released.
    private val pressedHere = mutableSetOf<Int>()

    /** Notes which keys [event] presses or releases, and returns whether the menu should ignore it. */
    fun track(event: KeyEvent): Boolean {
        val key = event.nativeKeyEvent.keyCode
        val freshPress = event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0
        if (freshPress) pressedHere += key
        return when (event.type) {
            KeyEventType.KeyUp -> !pressedHere.remove(key) || event.nativeKeyEvent.isCanceled
            else -> key !in pressedHere
        }
    }
}
