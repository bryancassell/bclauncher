package io.github.bryancassell.bclauncher.ui.home

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bclauncher.R
import io.github.bryancassell.bclauncher.data.InstalledApp
import io.github.bryancassell.bclauncher.data.fakeApp
import io.github.bryancassell.bclauncher.ui.theme.BcLauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Holds Select on the focused tile, as a long press on the remote does. Compose's test input repeats a held key after
 * 500 ms, and the tile opens its menu on the first repeat.
 */
fun SemanticsNodeInteractionsProvider.holdSelect() {
    onRoot().performKeyInput {
        keyDown(Key.DirectionCenter)
        advanceEventTime(600)
        keyUp(Key.DirectionCenter)
    }
}

/** The open app menu. */
fun SemanticsNodeInteractionsProvider.onMenu(): SemanticsNodeInteraction = onNode(isDialog())

/** The entry in the open app menu that shows [label]. */
fun SemanticsNodeInteractionsProvider.onMenuEntry(label: String): SemanticsNodeInteraction =
    onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isDialog()))

/** Presses [keys] on the remote while the app menu has focus. */
fun SemanticsNodeInteractionsProvider.pressKeysInMenu(keys: List<Key>) = pressKeys(keys, onMenu())

private const val SELECT = NativeKeyEvent.KEYCODE_DPAD_CENTER
private const val DOWN = NativeKeyEvent.KEYCODE_DPAD_DOWN

/** A key going down afresh, or as the [repeat]th repeat of a held key. */
private fun down(code: Int, repeat: Int = 0) =
    NativeKeyEvent(0, 0, NativeKeyEvent.ACTION_DOWN, code, repeat)

/** A key's release, or, when [canceled], the release Android sends when the window loses focus while it's down. */
private fun up(code: Int, canceled: Boolean = false): NativeKeyEvent {
    val flags = if (canceled) NativeKeyEvent.FLAG_CANCELED else 0
    return NativeKeyEvent(0, 0, NativeKeyEvent.ACTION_UP, code, 0, 0, 0, 0, flags)
}

/**
 * Sends [events] to the window that holds this node, as a TV's input system does. Unlike Compose's test input, it can
 * send a key's repeats or release to a window that didn't get its press.
 */
private fun SemanticsNodeInteraction.send(vararg events: NativeKeyEvent) {
    events.forEach { performKeyPress(KeyEvent(it)) }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val launch = context.getString(R.string.app_option_launch)
    private val appSettings = context.getString(R.string.app_option_app_settings)
    private val uninstall = context.getString(R.string.app_option_uninstall)

    private val clicked = mutableListOf<InstalledApp>()
    private val appSettingsOpened = mutableListOf<InstalledApp>()
    private val uninstallsAsked = mutableListOf<InstalledApp>()

    // Apps with banners, named App 1 to App 3. A test changes the list by setting uiState after showScreen.
    private val apps = (1..3).map { fakeApp("App $it").copy(banner = ColorDrawable()) }
    private var uiState by mutableStateOf<HomeUiState>(HomeUiState.Content(apps))

    private fun showScreen() {
        composeRule.setContent {
            BcLauncherTheme {
                HomeScreen(
                    uiState = uiState,
                    showsHomeButtonMessage = false,
                    onAppClick = { clicked += it },
                    onAppSettingsClick = { appSettingsOpened += it },
                    onUninstallClick = { uninstallsAsked += it },
                    onRetry = {},
                    onUseBcLauncher = {},
                )
            }
        }
    }

    /** Opens the second app's menu. */
    private fun openApp2sMenu() {
        showScreen()
        composeRule.pressKeys(listOf(Key.DirectionRight))
        composeRule.holdSelect()
    }

    @Test
    fun holdingSelectOpensTheAppsMenuWithoutOpeningTheApp() {
        openApp2sMenu()

        composeRule.onNode(isHeading() and hasAnyAncestor(isDialog())).assertTextEquals("App 2")
        // By their place on screen, since the focused entry comes last in the semantics tree, drawn above the others.
        val entries = composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(isDialog()))
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.Text].joinToString() }
        assertEquals(listOf(launch, appSettings, uninstall), entries)
        composeRule.onMenuEntry(launch).assertIsFocused()
        assertEquals(emptyList<InstalledApp>(), clicked)
    }

    @Test
    fun aShortPressOpensTheAppWithoutTheMenu() {
        showScreen()

        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionCenter))

        assertEquals(listOf(apps[1]), clicked)
        composeRule.onMenu().assertDoesNotExist()
    }

    @Test
    fun theRestOfTheHeldPressDoesNotChooseAnEntry() {
        openApp2sMenu()

        // On a TV, the menu takes focus while Select is still held, so the press's later repeats and its release come
        // to the menu.
        composeRule.onMenu().send(down(SELECT, repeat = 2), up(SELECT))

        assertEquals(emptyList<InstalledApp>(), clicked)
        composeRule.onMenuEntry(launch).assertIsFocused()
    }

    @Test
    fun theHeldPressDoesNotChooseAnEntryAfterAnotherKeyIsPressed() {
        openApp2sMenu()

        // Down is pressed and released while Select, which opened the menu, is still held and repeating.
        composeRule.onMenu().send(down(SELECT, repeat = 2), down(DOWN), up(DOWN), up(SELECT))

        assertEquals(emptyList<InstalledApp>(), appSettingsOpened)
        composeRule.onMenuEntry(appSettings).assertIsFocused()
    }

    @Test
    fun aPressInTheMenuThatAndroidCancelsDoesNotChooseAnEntry() {
        openApp2sMenu()

        // Home is pressed while Select is held on Launch, so the menu's window loses focus and gets a cancelled release.
        composeRule.onMenu().send(down(SELECT), up(SELECT, canceled = true))

        assertEquals(emptyList<InstalledApp>(), clicked)
        composeRule.onMenu().assertExists()
    }

    @Test
    fun releasingSelectAfterBackDoesNotOpenTheAppButTheNextPressDoes() {
        showScreen()
        composeRule.pressKeys(listOf(Key.DirectionRight))
        // Select is held on App 2's tile, and on a TV the menu takes focus, so the grid gets a cancelled release.
        composeRule.onTile(
            "App 2",
        ).send(down(SELECT), down(SELECT, repeat = 1), up(SELECT, canceled = true))
        composeRule.onMenu().assertExists()

        // The user presses Back while still holding Select, and the rest of the press reaches the grid.
        Espresso.pressBack()
        composeRule.onTile("App 2").send(down(SELECT, repeat = 5), up(SELECT))
        assertEquals(emptyList<InstalledApp>(), clicked)

        composeRule.pressKeys(listOf(Key.DirectionCenter))
        assertEquals(listOf(apps[1]), clicked)
    }

    @Test
    fun backClosesTheMenuWithFocusOnTheTile() {
        openApp2sMenu()

        Espresso.pressBack()

        composeRule.onMenu().assertDoesNotExist()
        composeRule.onTile("App 2").assertIsFocused()
    }

    @Test
    fun launchOpensTheApp() {
        openApp2sMenu()

        composeRule.pressKeysInMenu(listOf(Key.DirectionCenter))

        assertEquals(listOf(apps[1]), clicked)
        composeRule.onMenu().assertDoesNotExist()
        composeRule.onTile("App 2").assertIsFocused()
    }

    @Test
    fun appSettingsOpensTheAppsSettings() {
        openApp2sMenu()

        composeRule.pressKeysInMenu(listOf(Key.DirectionDown, Key.DirectionCenter))

        assertEquals(listOf(apps[1]), appSettingsOpened)
        composeRule.onMenu().assertDoesNotExist()
    }

    @Test
    fun uninstallAsksToUninstallTheApp() {
        openApp2sMenu()

        composeRule.pressKeysInMenu(
            listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionCenter),
        )

        assertEquals(listOf(apps[1]), uninstallsAsked)
        composeRule.onMenu().assertDoesNotExist()
    }

    @Test
    fun uninstallIsShownDisabledForASystemApp() {
        uiState = HomeUiState.Content(listOf(apps[0], apps[1].copy(isSystemApp = true), apps[2]))
        openApp2sMenu()

        composeRule.onMenuEntry(uninstall).assertIsNotEnabled()
        composeRule.pressKeysInMenu(
            listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionCenter),
        )

        assertEquals(emptyList<InstalledApp>(), uninstallsAsked)
        composeRule.onMenuEntry(uninstall).assertIsFocused()
    }

    @Test
    fun theMenuClosesWhenItsAppLeavesAndStaysClosedWhenItComesBack() {
        openApp2sMenu()

        uiState = HomeUiState.Content(apps - apps[1])
        composeRule.onMenu().assertDoesNotExist()

        uiState = HomeUiState.Content(apps)
        composeRule.onMenu().assertDoesNotExist()
    }
}
