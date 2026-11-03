package io.github.bryancassell.bclauncher.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bclauncher.HomeButtonService
import io.github.bryancassell.bclauncher.MainDispatcherRule
import io.github.bryancassell.bclauncher.data.FakeAccessibilitySource
import io.github.bryancassell.bclauncher.data.FakePackageSource
import io.github.bryancassell.bclauncher.data.HomeButtonRepository
import io.github.bryancassell.bclauncher.data.InstalledAppsRepository
import io.github.bryancassell.bclauncher.data.SystemScreensRepository
import io.github.bryancassell.bclauncher.data.fakeApp
import io.github.bryancassell.bclauncher.data.homeButtonService
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageSource = FakePackageSource()
    private val accessibilitySource = FakeAccessibilitySource()

    // Created on first use, after MainDispatcherRule has set the main dispatcher, so viewModelScope runs on the rule's
    // dispatcher. A field initializer would run first, and viewModelScope would take the real main dispatcher.
    private val viewModel by lazy {
        HomeViewModel(
            InstalledAppsRepository(context, packageSource, mainDispatcherRule.dispatcher),
            HomeButtonRepository(context, accessibilitySource),
            SystemScreensRepository(packageSource, mainDispatcherRule.dispatcher),
        )
    }

    @After
    fun stopViewModel() {
        viewModel.viewModelScope.cancel()
    }

    private val contentLabels: List<String>
        get() = (viewModel.uiState.value as HomeUiState.Content).apps.map { it.label }

    // The state only updates while something collects it, as the screen does.
    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.showsHomeButtonMessage.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.googleTvHomeIntent.collect {}
        }
    }

    private val googleTvHome = ComponentName(
        "com.google.android.apps.tv.launcherx",
        "com.google.android.apps.tv.launcherx.home.HomeActivity",
    )

    @Test
    fun isLoadingBeforeTheAppsLoad() {
        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun showsTheAppsOnceTheyLoad() = runTest {
        val apps = arrayOf(fakeApp("Netflix"), fakeApp("YouTube"))
        packageSource.setApps(*apps)

        collectState()

        assertEquals(HomeUiState.Content(apps.toList()), viewModel.uiState.value)
    }

    @Test
    fun isEmptyWhenNoAppsAreInstalled() = runTest {
        collectState()

        assertEquals(HomeUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun showsAnErrorWhenLoadingFails() = runTest {
        packageSource.duringNextLoad { throw OutOfMemoryError() }

        collectState()

        assertEquals(HomeUiState.Error, viewModel.uiState.value)
        // Logged, so a release build that R8 broke shows up in logcat (docs/toolchain.md, Checking a release build).
        val log = ShadowLog.getLogsForTag("HomeViewModel").single()
        assertEquals(Log.ERROR, log.type)
        assertTrue(log.throwable is OutOfMemoryError)
    }

    @Test
    fun updatesWhenAnAppIsInstalled() = runTest {
        packageSource.setApps(fakeApp("YouTube"))
        collectState()

        packageSource.setApps(fakeApp("Netflix"), fakeApp("YouTube"))

        assertEquals(listOf("Netflix", "YouTube"), contentLabels)
    }

    @Test
    fun loadsAgainAfterALaunchFails() = runTest {
        packageSource.setApps(fakeApp("Netflix"), fakeApp("YouTube"))
        collectState()
        // An update removed Netflix's TV activity, which BC Launcher isn't told about.
        packageSource.setAppsWithoutReporting(fakeApp("YouTube"))

        viewModel.onLaunchFailed()

        assertEquals(listOf("YouTube"), contentLabels)
    }

    @Test
    fun loadsAgainWhenRetriedAfterAnError() = runTest {
        packageSource.duringNextLoad { throw OutOfMemoryError() }
        collectState()
        packageSource.setAppsWithoutReporting(fakeApp("YouTube"))

        viewModel.retry()

        assertEquals(listOf("YouTube"), contentLabels)
    }

    @Test
    fun hidesTheHomeButtonMessageUntilItKnowsWhatHomeOpens() {
        assertFalse(viewModel.showsHomeButtonMessage.value)
    }

    @Test
    fun showsTheHomeButtonMessageWhileHomeOpensGoogleTv() = runTest {
        collectState()

        assertTrue(viewModel.showsHomeButtonMessage.value)
    }

    @Test
    fun hidesTheHomeButtonMessageOnceTheUserTurnsOnTheService() = runTest {
        collectState()

        accessibilitySource.setEnabledServices(homeButtonService(context))

        assertFalse(viewModel.showsHomeButtonMessage.value)
    }

    @Test
    fun homeButtonSettingsIntentOpensAccessibilitySettings() {
        assertEquals(
            Settings.ACTION_ACCESSIBILITY_SETTINGS,
            viewModel.homeButtonSettingsIntent.action,
        )
    }

    @Test
    fun hidesGoogleTvUntilItsHomeIsFound() {
        packageSource.setHome(googleTvHome)

        assertNull(viewModel.googleTvHomeIntent.value)
    }

    @Test
    fun opensGoogleTvsHomeOnceFound() = runTest {
        packageSource.setHome(googleTvHome)

        collectState()

        assertEquals(googleTvHome, viewModel.googleTvHomeIntent.value?.component)
    }

    @Test
    fun hidesGoogleTvWithoutAHomeActivity() = runTest {
        collectState()

        assertNull(viewModel.googleTvHomeIntent.value)
    }

    @Test
    fun hidesGoogleTvWhenItsHomeCannotBeFound() = runTest {
        packageSource.failFindingHome(SecurityException("package manager unavailable"))

        collectState()

        assertNull(viewModel.googleTvHomeIntent.value)
        val log = ShadowLog.getLogsForTag("HomeViewModel").single()
        assertEquals(Log.ERROR, log.type)
        assertTrue(log.throwable is SecurityException)
    }

    @Test
    fun openingGoogleTvsHomeTellsTheHomeButtonService() {
        viewModel.onOpeningGoogleTvHome()

        assertEquals(
            HomeButtonService.ACTION_OPENING_GOOGLE_TV_HOME,
            shadowOf(context as Application).broadcastIntents.single().action,
        )
    }

    @Test
    fun settingsIntentOpensDeviceSettings() {
        assertEquals(Settings.ACTION_SETTINGS, viewModel.settingsIntent.action)
    }

    @Test
    fun hidesTheHomeButtonMessageWhenTheSettingCannotBeRead() = runTest {
        accessibilitySource.failReading(IllegalStateException("settings provider unavailable"))

        collectState()

        assertFalse(viewModel.showsHomeButtonMessage.value)
        val log = ShadowLog.getLogsForTag("HomeViewModel").single()
        assertEquals(Log.ERROR, log.type)
        assertTrue(log.throwable is IllegalStateException)
    }
}
