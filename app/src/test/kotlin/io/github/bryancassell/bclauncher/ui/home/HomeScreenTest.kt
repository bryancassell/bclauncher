package io.github.bryancassell.bclauncher.ui.home

import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bclauncher.R
import io.github.bryancassell.bclauncher.data.InstalledApp
import io.github.bryancassell.bclauncher.data.fakeApp
import io.github.bryancassell.bclauncher.ui.theme.BcLauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

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

    // A test changes what the screen shows by setting these after showScreen.
    private var uiState by mutableStateOf<HomeUiState>(HomeUiState.Loading)
    private var showsHomeButtonMessage by mutableStateOf(false)

    private fun showScreen(initial: HomeUiState, homeButtonMessage: Boolean = false) {
        uiState = initial
        showsHomeButtonMessage = homeButtonMessage
        composeRule.setContent {
            BcLauncherTheme {
                HomeScreen(
                    uiState = uiState,
                    showsHomeButtonMessage = showsHomeButtonMessage,
                    onAppClick = { clicked += it },
                    onRetry = { retries++ },
                    onUseBcLauncher = { homeButtonSettingsOpened++ },
                )
            }
        }
    }

    private fun onUseBcLauncher() = composeRule.onNode(
        hasText(context.getString(R.string.home_button_use)) and hasClickAction(),
    )

    // Apps with banners, named App 1 to App [count], or with another [name].
    private fun bannerApps(count: Int, name: String = "App") =
        (1..count).map { fakeApp("$name $it").copy(banner = ColorDrawable()) }

    // Scrolls the grid to a focused tile as a TV does, which puts its row 30% of the way down. Compose Foundation picks
    // that for a device with FEATURE_LEANBACK, and Robolectric's TV qualifiers don't add the feature.
    private fun scrollAsATvDoes() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    }

    @Test
    fun showsNothingWhileLoading() {
        showScreen(HomeUiState.Loading)

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.home_empty)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.home_error)).assertCountEquals(0)
    }

    @Test
    fun saysSoWhenThereAreNoApps() {
        showScreen(HomeUiState.Empty)

        composeRule.onNodeWithText(context.getString(R.string.home_empty)).assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
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

    // With more than 30 apps and no message above them, the grid scrolls. These tests move focus down the first column
    // only: below the screen, Robolectric's Down goes to the tile after the last one composed, which is in the first
    // column.

    @Test
    fun theFocusedTileStaysInPlaceWhenTilesAreAddedBeforeIt() {
        val apps = bannerApps(96)
        showScreen(HomeUiState.Content(apps))
        // The grid scrolls to show App 43, which ends up on the bottom row.
        composeRule.pressKeys(List(7) { Key.DirectionDown })
        val bounds = composeRule.onTile("App 43").assertIsFocused().getUnclippedBoundsInRoot()

        // Two rows of apps are installed between the first tile on screen and App 43.
        uiState = HomeUiState.Content(apps.take(13) + bannerApps(12, name = "New") + apps.drop(13))

        composeRule.onTile("App 43").assertIsFocused().assertIsDisplayed()
        assertEquals(bounds, composeRule.onTile("App 43").getUnclippedBoundsInRoot())
    }

    @Test
    fun focusMovesToTheTileThatTakesTheRemovedTilesPlaceOnScreen() {
        val apps = bannerApps(48)
        showScreen(HomeUiState.Content(apps))
        composeRule.pressKeys(List(4) { Key.DirectionDown })
        val bounds = composeRule.onTile("App 25").assertIsFocused().getUnclippedBoundsInRoot()

        // One reload drops App 25 and adds two rows of apps before it, so App 26 takes its place two rows further on.
        uiState =
            HomeUiState.Content(
                apps.take(1) + bannerApps(12, name = "New") + apps.drop(1) - apps[24],
            )

        composeRule.onTile("App 26").assertIsFocused().assertIsDisplayed()
        assertEquals(bounds, composeRule.onTile("App 26").getUnclippedBoundsInRoot())
    }

    @Test
    fun theTileThatTookTheRemovedTilesPlaceDoesNotTakeFocusBackLater() {
        val apps = bannerApps(96)
        showScreen(HomeUiState.Content(apps))
        composeRule.pressKeys(List(4) { Key.DirectionDown })
        uiState = HomeUiState.Content(apps - apps[24])
        composeRule.pressKeys(listOf(Key.DirectionRight))
        composeRule.onTile("App 27").assertIsFocused()

        // The grid scrolls App 26 out of view and back, as moving focus far down and back up does.
        val grid = composeRule.onNode(hasScrollToIndexAction())
        grid.performScrollToIndex(90)
        grid.performScrollToIndex(0)

        composeRule.onTile("App 27").assertIsFocused()
    }

    @Test
    fun aTileThatLandsInTheFirstRowIsAllOnScreen() {
        scrollAsATvDoes()
        val apps = bannerApps(96)
        showScreen(HomeUiState.Content(apps))
        // The grid scrolls to put App 25's row 30% of the way down, so the first row on screen is partly scrolled off
        // the top.
        composeRule.pressKeys(List(4) { Key.DirectionDown })

        // One reload drops the 24 apps before App 25, so it lands in the first row.
        uiState = HomeUiState.Content(apps.drop(24))

        // Below the grid's 28 dp top margin, where the first row is when the grid isn't scrolled.
        composeRule.onTile("App 25").assertIsFocused().assertTopPositionInRootIsEqualTo(28.dp)
    }

    @Test
    fun aReloadDoesNotStopTheGridScrollingToTheFocusedTile() {
        scrollAsATvDoes()
        val apps = bannerApps(48)
        showScreen(HomeUiState.Content(apps))
        composeRule.pressKeys(List(4) { Key.DirectionDown })
        // Where the grid scrolls App 25 to.
        val top = composeRule.onTile("App 25").getUnclippedBoundsInRoot().top
        composeRule.pressKeys(listOf(Key.DirectionUp))

        // The grid starts scrolling App 25 into view again, and two rows of apps are installed before it.
        composeRule.mainClock.autoAdvance = false
        composeRule.pressKeys(listOf(Key.DirectionDown))
        composeRule.mainClock.advanceTimeByFrame()
        uiState = HomeUiState.Content(apps.take(1) + bannerApps(12, name = "New") + apps.drop(1))
        composeRule.mainClock.autoAdvance = true

        composeRule.onTile("App 25").assertIsFocused().assertTopPositionInRootIsEqualTo(top)
    }

    @Test
    fun aTileCanTakeTheRemovedTilesPlaceTwice() {
        val apps = bannerApps(6)
        showScreen(HomeUiState.Content(apps))
        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionRight))
        uiState = HomeUiState.Content(apps - apps[2])
        composeRule.onTile("App 4").assertIsFocused()

        // The next reload drops App 2, the tile the user moved to, and App 4 takes its place too.
        composeRule.pressKeys(listOf(Key.DirectionLeft))
        uiState = HomeUiState.Content(apps - apps[2] - apps[1])

        composeRule.onTile("App 4").assertIsFocused()
    }

    @Test
    fun theTileThatTakesTheRemovedTilesPlaceIsOnScreenWhenTheFocusedTileWasOutOfView() {
        val apps = bannerApps(96)
        showScreen(HomeUiState.Content(apps))
        // Scrolling as TalkBack does leaves focus on App 1, out of view.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(60)
        composeRule.onTile("App 1").assertIsFocused()

        uiState = HomeUiState.Content(apps.drop(1))

        composeRule.onTile("App 2").assertIsFocused().assertIsDisplayed()
    }

    @Test
    fun aReloadLeavesFocusOnUseBcLauncher() {
        val apps = bannerApps(8)
        showScreen(HomeUiState.Content(apps), homeButtonMessage = true)
        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionUp))
        onUseBcLauncher().assertIsFocused()

        // App 2's tile last had focus, and a reload drops it.
        uiState = HomeUiState.Content(apps - apps[1])

        onUseBcLauncher().assertIsFocused()
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
    fun useBcLauncherTakesFocusWhenThereAreNoApps() {
        showScreen(HomeUiState.Empty, homeButtonMessage = true)

        onUseBcLauncher().assertIsFocused()
        composeRule.pressKeys(listOf(Key.DirectionCenter))
        assertEquals(1, homeButtonSettingsOpened)
    }
}
