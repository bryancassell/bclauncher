package io.github.bryancassell.bclauncher.data

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemScreensRepositoryTest {
    private val packageSource = FakePackageSource()

    private fun TestScope.repository() =
        SystemScreensRepository(packageSource, UnconfinedTestDispatcher(testScheduler))

    private val googleTvHome = ComponentName(
        "com.google.android.apps.tv.launcherx",
        "com.google.android.apps.tv.launcherx.home.HomeActivity",
    )

    @Test
    fun googleTvHomeIntentOpensTheHomeActivitysPackageAsHomeInItsTask() = runTest {
        packageSource.setHome(googleTvHome)

        val intent = repository().googleTvHomeIntent.first()

        assertEquals(Intent.ACTION_MAIN, intent?.action)
        assertEquals(setOf(Intent.CATEGORY_HOME), intent?.categories)
        assertEquals(googleTvHome.packageName, intent?.`package`)
        // Named outright, the activity would start as an ordinary one rather than as Home.
        assertNull(intent?.component)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent?.flags)
    }

    @Test
    fun googleTvHomeIntentIsNullWithoutAHomeActivity() = runTest {
        assertNull(repository().googleTvHomeIntent.first())
    }

    @Test
    fun googleTvHomeIntentEmitsAgainOnlyWhenTheHomeActivityChanges() = runTest {
        val emitted = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository().googleTvHomeIntent.collect { emitted += it?.`package` }
        }

        // As when Google TV's launcher is turned on, another app is installed, and the launcher is turned off.
        packageSource.setHome(googleTvHome)
        packageSource.setApps(fakeApp("YouTube"))
        packageSource.setHome(null)

        assertEquals(listOf(null, googleTvHome.packageName, null), emitted)
    }

    @Test
    fun settingsIntentOpensDeviceSettingsInTheirOwnTask() = runTest {
        val intent = repository().settingsIntent

        assertEquals(Settings.ACTION_SETTINGS, intent.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
    }
}
