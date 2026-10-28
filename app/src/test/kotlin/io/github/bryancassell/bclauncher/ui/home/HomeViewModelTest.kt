package io.github.bryancassell.bclauncher.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.bryancassell.bclauncher.MainDispatcherRule
import io.github.bryancassell.bclauncher.data.FakePackageSource
import io.github.bryancassell.bclauncher.data.InstalledAppsRepository
import io.github.bryancassell.bclauncher.data.fakeApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageSource = FakePackageSource()

    // Created on first use, after MainDispatcherRule has set the main dispatcher, so viewModelScope runs on the rule's
    // dispatcher. A field initializer would run first, and viewModelScope would take the real main dispatcher.
    private val viewModel by lazy {
        HomeViewModel(
            InstalledAppsRepository(context, packageSource, mainDispatcherRule.dispatcher),
        )
    }

    private val contentLabels: List<String>
        get() = (viewModel.uiState.value as HomeUiState.Content).apps.map { it.label }

    // The state only updates while something collects it, as the screen does.
    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

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
}
