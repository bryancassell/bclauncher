package io.github.bryancassell.bclauncher.ui.home

import android.content.Context
import android.graphics.drawable.ColorDrawable
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

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val clicked = mutableListOf<InstalledApp>()

    private fun setUiState(uiState: HomeUiState) {
        composeRule.setContent {
            BcLauncherTheme {
                HomeScreen(uiState = uiState, onAppClick = { clicked += it })
            }
        }
    }

    // Apps with banners, named App 1 to App [count].
    private fun bannerApps(count: Int) =
        (1..count).map { fakeApp("App $it").copy(banner = ColorDrawable()) }

    private fun pressKeys(keys: List<Key>) {
        composeRule.onRoot().performKeyInput { keys.forEach { pressKey(it) } }
    }

    @Test
    fun showsNothingWhileLoading() {
        setUiState(HomeUiState.Loading)

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.home_empty)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.home_error)).assertCountEquals(0)
    }

    @Test
    fun saysSoWhenThereAreNoApps() {
        setUiState(HomeUiState.Empty)

        composeRule.onNodeWithText(context.getString(R.string.home_empty)).assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun saysSoWhenTheAppsDidNotLoad() {
        setUiState(HomeUiState.Error)

        composeRule.onNodeWithText(context.getString(R.string.home_error)).assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun showsATileForEachApp() {
        setUiState(
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
        setUiState(HomeUiState.Content(bannerApps(3)))

        composeRule.onTile("App 1").assertIsFocused()
    }

    @Test
    fun theDPadMovesFocusAcrossSixAppsToARow() {
        setUiState(HomeUiState.Content(bannerApps(8)))

        pressKeys(listOf(Key.DirectionRight))
        composeRule.onTile("App 2").assertIsFocused()

        pressKeys(listOf(Key.DirectionDown))
        composeRule.onTile("App 8").assertIsFocused()
    }

    @Test
    fun selectOpensTheFocusedApp() {
        val apps = bannerApps(3)
        setUiState(HomeUiState.Content(apps))

        pressKeys(listOf(Key.DirectionRight, Key.DirectionCenter))

        assertEquals(listOf(apps[1]), clicked)
    }
}
