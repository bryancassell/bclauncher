package io.github.bryancassell.bclauncher.ui.home

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bclauncher.R
import io.github.bryancassell.bclauncher.data.InstalledApp
import io.github.bryancassell.bclauncher.data.fakeApp
import io.github.bryancassell.bclauncher.ui.theme.BcLauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A tile, found the way TalkBack reads it: by its banner's description, or by its name when it has no banner. */
fun SemanticsNodeInteractionsProvider.onTile(label: String): SemanticsNodeInteraction =
    onNode(hasClickAction() and (hasContentDescription(label) or hasText(label)))

/** Presses [keys] on the remote, in order. */
fun SemanticsNodeInteractionsProvider.pressKeys(keys: List<Key>) {
    onRoot().performKeyInput { keys.forEach { pressKey(it) } }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val clicked = mutableListOf<InstalledApp>()
    private var retries = 0
    private var homeButtonSettingsOpened = 0
    private var googleTvOpened = 0
    private var settingsOpened = 0

    // A test changes what the screen shows by setting these after showScreen.
    private var uiState by mutableStateOf<HomeUiState>(HomeUiState.Loading)
    private var showsHomeButtonMessage by mutableStateOf(false)
    private var showsGoogleTv by mutableStateOf(true)

    private fun showScreen(
        initial: HomeUiState,
        homeButtonMessage: Boolean = false,
        googleTv: Boolean = true,
    ) {
        uiState = initial
        showsHomeButtonMessage = homeButtonMessage
        showsGoogleTv = googleTv
        composeRule.setContent {
            BcLauncherTheme {
                HomeScreen(
                    uiState = uiState,
                    showsHomeButtonMessage = showsHomeButtonMessage,
                    onAppClick = { clicked += it },
                    onRetry = { retries++ },
                    onUseBcLauncher = { homeButtonSettingsOpened++ },
                    showsGoogleTv = showsGoogleTv,
                    onGoogleTv = { googleTvOpened++ },
                    onSettings = { settingsOpened++ },
                )
            }
        }
    }

    private fun onGoogleTv() = composeRule.onNode(
        hasText(context.getString(R.string.google_tv)) and hasClickAction(),
    )

    private fun onSettings() = composeRule.onNode(
        hasText(context.getString(R.string.settings)) and hasClickAction(),
    )

    private fun onUseBcLauncher() = composeRule.onNode(
        hasText(context.getString(R.string.home_button_use)) and hasClickAction(),
    )

    // Apps with banners, named App 1 to App [count].
    private fun bannerApps(count: Int) =
        (1..count).map { fakeApp("App $it").copy(banner = ColorDrawable()) }

    @Test
    fun showsOnlyGoogleTvAndSettingsWhileLoading() {
        showScreen(HomeUiState.Loading)

        onGoogleTv().assertIsDisplayed()
        onSettings().assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(2)
        composeRule.onAllNodesWithText(context.getString(R.string.home_empty)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.home_error)).assertCountEquals(0)
    }

    @Test
    fun saysSoWhenThereAreNoApps() {
        showScreen(HomeUiState.Empty)

        composeRule.onNodeWithText(context.getString(R.string.home_empty)).assertIsDisplayed()
        // Google TV and Settings are all there is to select.
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun saysSoWhenTheAppsDidNotLoad() {
        showScreen(HomeUiState.Error)

        composeRule.onNodeWithText(context.getString(R.string.home_error)).assertIsDisplayed()
        composeRule.onNode(
            hasText(context.getString(R.string.home_retry)) and hasClickAction(),
        ).assertIsFocused()
    }

    @Test
    fun selectTriesAgainAfterTheAppsDidNotLoad() {
        showScreen(HomeUiState.Error)

        composeRule.pressKeys(listOf(Key.DirectionCenter))

        assertEquals(1, retries)
    }

    @Test
    fun showsATileForEachApp() {
        showScreen(
            HomeUiState.Content(
                listOf(fakeApp("Netflix").copy(banner = ColorDrawable()), fakeApp("Sideloaded")),
            ),
        )

        // The banner tile is described by the app's name, and the tile without a banner shows its name.
        composeRule.onNode(
            hasContentDescription("Netflix") and hasClickAction(),
        ).assertIsDisplayed()
        composeRule.onNode(hasText("Sideloaded") and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun focusStartsOnTheFirstApp() {
        showScreen(HomeUiState.Content(bannerApps(3)))

        composeRule.onTile("App 1").assertIsFocused()
    }

    @Test
    fun theDPadMovesFocusAcrossSixAppsToARow() {
        showScreen(HomeUiState.Content(bannerApps(8)))

        composeRule.pressKeys(listOf(Key.DirectionRight))
        composeRule.onTile("App 2").assertIsFocused()

        composeRule.pressKeys(listOf(Key.DirectionDown))
        composeRule.onTile("App 8").assertIsFocused()
    }

    @Test
    fun selectOpensTheFocusedApp() {
        val apps = bannerApps(3)
        showScreen(HomeUiState.Content(apps))

        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionCenter))

        assertEquals(listOf(apps[1]), clicked)
    }

    @Test
    fun focusMovesToTheTileThatTakesTheRemovedTilesPlace() {
        val apps = bannerApps(3)
        showScreen(HomeUiState.Content(apps))
        composeRule.pressKeys(listOf(Key.DirectionRight))

        uiState = HomeUiState.Content(apps - apps[1])

        composeRule.onTile("App 3").assertIsFocused()
    }

    @Test
    fun focusMovesToTheTileBeforeWhenTheLastTileIsRemoved() {
        val apps = bannerApps(3)
        showScreen(HomeUiState.Content(apps))
        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionRight))

        uiState = HomeUiState.Content(apps - apps[2])

        composeRule.onTile("App 2").assertIsFocused()
    }

    @Test
    fun focusStaysOnItsTileWhenAnotherTileIsRemoved() {
        val apps = bannerApps(3)
        showScreen(HomeUiState.Content(apps))
        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionRight))

        uiState = HomeUiState.Content(apps - apps[0])

        composeRule.onTile("App 3").assertIsFocused()
    }

    @Test
    fun focusMovesToTheNextRemainingTileWhenTilesBeforeTheFocusedOneGoToo() {
        val apps = bannerApps(6)
        showScreen(HomeUiState.Content(apps))
        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionRight))

        // One reload drops App 1 and the focused App 4, so App 5 takes App 4's place.
        uiState = HomeUiState.Content(apps - apps[0] - apps[3])

        composeRule.onTile("App 5").assertIsFocused()
    }

    @Test
    fun showsTheHomeButtonMessageAboveTheApps() {
        showScreen(HomeUiState.Content(bannerApps(3)), homeButtonMessage = true)

        composeRule.onNodeWithText(
            context.getString(R.string.home_button_message),
        ).assertIsDisplayed()
        onUseBcLauncher().assertIsDisplayed()
        composeRule.onTile("App 1").assertIsDisplayed()
    }

    @Test
    fun noHomeButtonMessageOnceHomeOpensBcLauncher() {
        showScreen(HomeUiState.Content(bannerApps(3)))

        composeRule.onAllNodesWithText(
            context.getString(R.string.home_button_message),
        ).assertCountEquals(0)
        onUseBcLauncher().assertDoesNotExist()
    }

    @Test
    fun focusStartsOnTheFirstAppBelowTheHomeButtonMessage() {
        showScreen(HomeUiState.Content(bannerApps(3)), homeButtonMessage = true)

        composeRule.onTile("App 1").assertIsFocused()
    }

    @Test
    fun upFromTheFirstRowReachesUseBcLauncher() {
        showScreen(HomeUiState.Content(bannerApps(8)), homeButtonMessage = true)

        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionUp))
        onUseBcLauncher().assertIsFocused()

        composeRule.pressKeys(listOf(Key.DirectionCenter))
        assertEquals(1, homeButtonSettingsOpened)
        assertEquals(emptyList<InstalledApp>(), clicked)
    }

    @Test
    fun focusReturnsToItsTileWhenTheHomeButtonMessageLeaves() {
        val apps = bannerApps(8)
        showScreen(HomeUiState.Content(apps), homeButtonMessage = true)
        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionUp))

        // The user turned on the service in Accessibility settings and came back.
        showsHomeButtonMessage = false

        composeRule.onTile("App 2").assertIsFocused()
        composeRule.pressKeys(listOf(Key.DirectionCenter))
        assertEquals(listOf(apps[1]), clicked)
    }

    @Test
    fun focusStaysOnItsTileWhenTheHomeButtonMessageArrives() {
        showScreen(HomeUiState.Content(bannerApps(8)))
        composeRule.pressKeys(listOf(Key.DirectionDown))

        showsHomeButtonMessage = true

        composeRule.onTile("App 7").assertIsFocused()
    }

    @Test
    fun focusReturnsToTryAgainWhenTheHomeButtonMessageLeaves() {
        showScreen(HomeUiState.Error, homeButtonMessage = true)
        composeRule.pressKeys(listOf(Key.DirectionUp))
        onUseBcLauncher().assertIsFocused()

        showsHomeButtonMessage = false

        composeRule.onNode(
            hasText(context.getString(R.string.home_retry)) and hasClickAction(),
        ).assertIsFocused()
    }

    @Test
    fun googleTvAndSettingsSitAtTheTopRightAboveTheApps() {
        showScreen(HomeUiState.Content(bannerApps(6)))

        val googleTv = onGoogleTv().getUnclippedBoundsInRoot()
        val settings = onSettings().getUnclippedBoundsInRoot()
        val lastTileInTheRow = composeRule.onTile("App 6").getUnclippedBoundsInRoot()
        assertEquals(googleTv.top, settings.top)
        assertTrue(googleTv.right < settings.left)
        // Settings ends where the row of tiles does, at the right margin.
        assertEquals(lastTileInTheRow.right, settings.right)
        assertTrue(settings.bottom < lastTileInTheRow.top)
    }

    @Test
    fun fiveRowsOfAppsFitAboveTheBottomMarginBelowGoogleTvAndSettings() {
        showScreen(HomeUiState.Content(bannerApps(30)))

        val lastTile = composeRule.onTile("App 30").getUnclippedBoundsInRoot()
        val screen = composeRule.onRoot().getUnclippedBoundsInRoot()
        // The bottom margin in Android's TV layout guide.
        assertTrue(lastTile.bottom <= screen.bottom - 28.dp)
    }

    @Test
    fun hidesGoogleTvWhenThereIsNoHomeToOpen() {
        showScreen(HomeUiState.Content(bannerApps(3)), googleTv = false)

        onGoogleTv().assertDoesNotExist()
        onSettings().assertIsDisplayed()
    }

    @Test
    fun upFromTheFirstRowReachesGoogleTvWhichSelectOpens() {
        showScreen(HomeUiState.Content(bannerApps(8)))

        composeRule.pressKeys(listOf(Key.DirectionUp))
        onGoogleTv().assertIsFocused()

        composeRule.pressKeys(listOf(Key.DirectionCenter))
        assertEquals(1, googleTvOpened)
        assertEquals(emptyList<InstalledApp>(), clicked)
    }

    @Test
    fun rightFromGoogleTvReachesSettingsWhichSelectOpens() {
        showScreen(HomeUiState.Content(bannerApps(8)))

        composeRule.pressKeys(listOf(Key.DirectionUp, Key.DirectionRight))
        onSettings().assertIsFocused()

        composeRule.pressKeys(listOf(Key.DirectionCenter))
        assertEquals(1, settingsOpened)
        assertEquals(0, googleTvOpened)
    }

    @Test
    fun downFromTheButtonsReturnsToTheTileFocusLeft() {
        showScreen(HomeUiState.Content(bannerApps(8)))

        composeRule.pressKeys(
            listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown),
        )

        composeRule.onTile("App 3").assertIsFocused()
    }

    @Test
    fun upFromUseBcLauncherReachesGoogleTv() {
        showScreen(HomeUiState.Content(bannerApps(8)), homeButtonMessage = true)

        composeRule.pressKeys(listOf(Key.DirectionUp, Key.DirectionUp))

        onGoogleTv().assertIsFocused()
    }

    @Test
    fun googleTvTakesFocusWhenThereAreNoApps() {
        showScreen(HomeUiState.Empty)

        onGoogleTv().assertIsFocused()
        composeRule.pressKeys(listOf(Key.DirectionCenter))
        assertEquals(1, googleTvOpened)
    }

    @Test
    fun settingsTakesFocusWhenThereAreNoAppsAndNoGoogleTv() {
        showScreen(HomeUiState.Empty, googleTv = false)

        onSettings().assertIsFocused()
    }

    @Test
    fun googleTvTakesFocusWhenItArrivesWithNoApps() {
        // The apps loaded before Google TV's home was found.
        showScreen(HomeUiState.Empty, googleTv = false)
        onSettings().assertIsFocused()

        showsGoogleTv = true

        onGoogleTv().assertIsFocused()
    }

    @Test
    fun settingsTakesFocusWhenGoogleTvLeavesWithNoApps() {
        showScreen(HomeUiState.Empty)
        onGoogleTv().assertIsFocused()

        // Google TV's launcher was turned off.
        showsGoogleTv = false

        onSettings().assertIsFocused()
    }

    @Test
    fun settingsTakesFocusWhenGoogleTvLeavesWithFocusOnIt() {
        showScreen(HomeUiState.Content(bannerApps(8)))
        composeRule.pressKeys(listOf(Key.DirectionUp))
        onGoogleTv().assertIsFocused()

        showsGoogleTv = false

        onSettings().assertIsFocused()
        composeRule.pressKeys(listOf(Key.DirectionCenter))
        assertEquals(1, settingsOpened)
    }

    @Test
    fun focusStaysOnItsTileWhenGoogleTvLeaves() {
        showScreen(HomeUiState.Content(bannerApps(8)))
        composeRule.pressKeys(listOf(Key.DirectionRight))

        showsGoogleTv = false

        composeRule.onTile("App 2").assertIsFocused()
    }

    @Test
    fun googleTvTakesFocusWhenTheHomeButtonMessageLeavesWithNoApps() {
        showScreen(HomeUiState.Empty, homeButtonMessage = true)
        onUseBcLauncher().assertIsFocused()

        // The user turned on the service in Accessibility settings and came back.
        showsHomeButtonMessage = false

        onGoogleTv().assertIsFocused()
    }

    @Test
    fun useBcLauncherTakesFocusWhenThereAreNoApps() {
        showScreen(HomeUiState.Empty, homeButtonMessage = true)

        onUseBcLauncher().assertIsFocused()
        composeRule.pressKeys(listOf(Key.DirectionCenter))
        assertEquals(1, homeButtonSettingsOpened)
    }
}
