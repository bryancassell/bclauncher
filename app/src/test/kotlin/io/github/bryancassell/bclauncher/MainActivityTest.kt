package io.github.bryancassell.bclauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.github.bryancassell.bclauncher.data.DispatchersModule
import io.github.bryancassell.bclauncher.data.FakeAccessibilitySource
import io.github.bryancassell.bclauncher.data.FakePackageSource
import io.github.bryancassell.bclauncher.data.IoDispatcher
import io.github.bryancassell.bclauncher.data.fakeApp
import io.github.bryancassell.bclauncher.data.homeButtonService
import io.github.bryancassell.bclauncher.ui.home.onTile
import io.github.bryancassell.bclauncher.ui.home.pressKeys
import io.github.bryancassell.bclauncher.ui.theme.DarkColorScheme
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

// HiltTestApplication gives each test its own components, with the fake sources' modules in place of the real ones.
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

    @Inject
    lateinit var accessibilitySource: FakeAccessibilitySource

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

    @Test
    fun offersToMakeHomeOpenBcLauncherWhileItOpensGoogleTv() {
        composeRule.onNodeWithText(
            application.getString(R.string.home_button_message),
        ).assertIsDisplayed()
    }

    @Test
    fun useBcLauncherOpensAccessibilitySettings() {
        composeRule.pressKeys(listOf(Key.DirectionUp, Key.DirectionCenter))

        assertEquals(
            Settings.ACTION_ACCESSIBILITY_SETTINGS,
            shadowOf(application).nextStartedActivity.action,
        )
    }

    @Test
    fun accessibilitySettingsThatDoNotOpenAreReported() {
        // Robolectric's package manager has no Accessibility settings, so checking activities makes starting it fail.
        shadowOf(application).checkActivities(true)

        composeRule.pressKeys(listOf(Key.DirectionUp, Key.DirectionCenter))

        assertEquals(
            application.getString(R.string.home_button_settings_did_not_open),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun theMessageLeavesOnceTheUserTurnsOnTheService() {
        accessibilitySource.setEnabledServices(homeButtonService(application))

        composeRule.onNodeWithText(
            application.getString(R.string.home_button_message),
        ).assertDoesNotExist()
        composeRule.onTile("Netflix").assertIsFocused()
    }

    private val googleTv = "com.google.android.apps.tv.launcherx"

    // From the first tile, past Use BC Launcher in the message.
    private val toGoogleTv = listOf(Key.DirectionUp, Key.DirectionUp)

    @Test
    fun googleTvTellsTheHomeButtonServiceThenOpensGoogleTvsHome() {
        packageSource.setHome(googleTv)

        composeRule.pressKeys(toGoogleTv + Key.DirectionCenter)
        // Google TV's home starts once the service has the broadcast, as its result.
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            HomeButtonService.ACTION_OPENING_GOOGLE_TV_HOME,
            shadowOf(application).broadcastIntents.single().action,
        )
        val started = shadowOf(application).nextStartedActivity
        assertEquals(googleTv, started.`package`)
        assertTrue(started.hasCategory(Intent.CATEGORY_HOME))
    }

    @Test
    fun googleTvsHomeThatDoesNotOpenIsReported() {
        packageSource.setHome(googleTv)
        // Robolectric's package manager has no Google TV, so checking activities makes starting it fail.
        shadowOf(application).checkActivities(true)

        composeRule.pressKeys(toGoogleTv + Key.DirectionCenter)
        // Google TV's home starts once the service has the broadcast, as its result.
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            application.getString(R.string.google_tv_did_not_open),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun googleTvsHomeDoesNotOpenOnceTheHomeScreenIsCovered() {
        packageSource.setHome(googleTv)
        // Stands in for a service slow to answer: the broadcast's result waits until this receiver finishes.
        var slowService: BroadcastReceiver.PendingResult? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                slowService = goAsync()
            }
        }
        // Registered as the service registers, with the permission Android grants at install before API 33.
        shadowOf(
            application,
        ).grantPermissions("${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        ContextCompat.registerReceiver(
            application,
            receiver,
            IntentFilter(HomeButtonService.ACTION_OPENING_GOOGLE_TV_HOME),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        composeRule.pressKeys(toGoogleTv + Key.DirectionCenter)
        shadowOf(Looper.getMainLooper()).idle()
        // The user opened an app before the broadcast came back.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        checkNotNull(slowService).finish()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun noGoogleTvWithoutAHomeActivity() {
        composeRule.onNode(
            hasText(application.getString(R.string.google_tv)) and hasClickAction(),
        ).assertDoesNotExist()
    }

    @Test
    fun settingsOpensDeviceSettings() {
        packageSource.setHome(googleTv)

        composeRule.pressKeys(toGoogleTv + Key.DirectionRight + Key.DirectionCenter)

        assertEquals(Settings.ACTION_SETTINGS, shadowOf(application).nextStartedActivity.action)
    }

    @Test
    fun settingsThatDoNotOpenAreReported() {
        shadowOf(application).checkActivities(true)

        // With no Google TV, Up from Use BC Launcher reaches Settings.
        composeRule.pressKeys(toGoogleTv + Key.DirectionCenter)

        assertEquals(
            application.getString(R.string.settings_did_not_open),
            ShadowToast.getTextOfLatestToast(),
        )
    }
}
