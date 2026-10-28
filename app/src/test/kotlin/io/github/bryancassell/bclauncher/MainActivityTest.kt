package io.github.bryancassell.bclauncher

import android.app.Application
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.github.bryancassell.bclauncher.data.DispatchersModule
import io.github.bryancassell.bclauncher.data.FakePackageSource
import io.github.bryancassell.bclauncher.data.IoDispatcher
import io.github.bryancassell.bclauncher.data.fakeApp
import io.github.bryancassell.bclauncher.ui.home.onTile
import io.github.bryancassell.bclauncher.ui.home.pressKeys
import io.github.bryancassell.bclauncher.ui.theme.DarkColorScheme
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

// HiltTestApplication gives each test its own components, with FakePackageSourceModule in place of the real source.
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@UninstallModules(DispatchersModule::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * Runs loads on the main thread. Compose's test rule runs the main looper until it's idle before each check, so a
     * test sees the apps it set without waiting for another thread. Only this test swaps it: a test that blocks the main
     * thread until a load finishes, as `runTest` does, would wait forever for a load queued behind it.
     */
    @BindValue
    @JvmField
    @IoDispatcher
    val ioDispatcher: CoroutineDispatcher = Dispatchers.Main

    @Inject
    lateinit var packageSource: FakePackageSource

    private val application: Application = ApplicationProvider.getApplicationContext()

    private val youTube = fakeApp("YouTube").copy(banner = ColorDrawable())
    private val netflix = fakeApp("Netflix").copy(banner = ColorDrawable())
    private val plex = fakeApp("Plex").copy(banner = ColorDrawable())

    @Before
    fun installApps() {
        hiltRule.inject()
        packageSource.setApps(netflix, plex, youTube)
        // The load runs on the main looper, so this shows the apps before a test changes them again.
        composeRule.waitForIdle()
    }

    @Test
    fun opensToTheInstalledApps() {
        composeRule.onTile("Netflix").assertIsDisplayed()
        composeRule.onTile("Plex").assertIsDisplayed()
        composeRule.onTile("YouTube").assertIsDisplayed()
    }

    @Test
    fun selectingATileOpensItsApp() {
        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionCenter))

        val started = shadowOf(application).nextStartedActivity
        assertTrue(started.filterEquals(plex.launchIntent))
        assertEquals(plex.launchIntent.flags, started.flags)
    }

    @Test
    fun anAppThatDoesNotOpenIsReportedAndLeavesTheGrid() {
        // An update removed Plex's TV activity, which BC Launcher isn't told about. Robolectric's package manager has
        // none of the fake apps, so checking activities makes starting any of them fail.
        packageSource.setAppsWithoutReporting(netflix, youTube)
        shadowOf(application).checkActivities(true)

        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionCenter))

        assertEquals(
            application.getString(R.string.app_did_not_open, "Plex"),
            ShadowToast.getTextOfLatestToast(),
        )
        composeRule.onTile("Plex").assertDoesNotExist()
        // YouTube took Plex's place, so the user keeps their place in the grid.
        composeRule.onTile("YouTube").assertIsFocused()
    }

    @Test
    fun backLeavesTheHomeScreenOpen() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()

        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun focusIsOnTheSameTileAfterReturningFromAnApp() {
        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionCenter))

        // The opened app covers the home screen, then closes.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.onTile("Plex").assertIsFocused()
    }

    @Test
    fun focusIsOnTheSameTileAfterTheHomeScreenIsRecreatedTwice() {
        composeRule.pressKeys(listOf(Key.DirectionRight, Key.DirectionCenter))

        // The system can relaunch it for two configuration changes in a row, as when an input device is added.
        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.recreate()

        composeRule.onTile("Plex").assertIsFocused()
    }

    @Test
    fun focusStaysOnTheSameTileWhenTheHomeScreenIsRecreated() {
        // A configuration change, such as the font size, while the user browses the grid.
        composeRule.pressKeys(listOf(Key.DirectionRight))

        composeRule.activityRule.scenario.recreate()

        composeRule.onTile("Plex").assertIsFocused()
    }

    @Test
    fun windowBackgroundMatchesTheSurfaceComposeDrawsFirst() {
        val attributes = composeRule.activity.theme.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowBackground),
        )
        val windowBackground = attributes.getColor(0, 0)
        attributes.recycle()

        assertEquals(DarkColorScheme.surface.toArgb(), windowBackground)
    }
}
