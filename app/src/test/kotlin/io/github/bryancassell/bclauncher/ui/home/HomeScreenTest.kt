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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
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

    // A test changes what the screen shows by setting this after showScreen.
    private var uiState by mutableStateOf<HomeUiState>(HomeUiState.Loading)

    private fun showScreen(initial: HomeUiState) {
        uiState = initial
        composeRule.setContent {
            BcLauncherTheme {
                HomeScreen(uiState = uiState, onAppClick = {
                    clicked += it
                }, onRetry = { retries++ })
            }
        }
    }

    // Apps with banners, named App 1 to App [count].
    private fun bannerApps(count: Int) =
        (1..count).map { fakeApp("App $it").copy(banner = ColorDrawable()) }

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
}
