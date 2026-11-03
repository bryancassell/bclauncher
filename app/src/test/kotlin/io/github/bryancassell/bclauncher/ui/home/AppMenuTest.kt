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
fun SemanticsNodeInteractionsProvider.pressKeysInMenu(keys: List<Key>) {
    onMenu().performKeyInput { keys.forEach { pressKey(it) } }
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
        composeRule.onMenu().performKeyPress(
            KeyEvent(
                NativeKeyEvent(
                    0,
                    0,
                    NativeKeyEvent.ACTION_DOWN,
                    NativeKeyEvent.KEYCODE_DPAD_CENTER,
                    2,
                ),
            ),
        )
        composeRule.onMenu().performKeyPress(
            KeyEvent(NativeKeyEvent(NativeKeyEvent.ACTION_UP, NativeKeyEvent.KEYCODE_DPAD_CENTER)),
        )

        assertEquals(emptyList<InstalledApp>(), clicked)
        composeRule.onMenuEntry(launch).assertIsFocused()
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
