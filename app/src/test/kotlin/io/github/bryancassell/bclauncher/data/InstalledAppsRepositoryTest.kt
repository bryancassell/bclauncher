package io.github.bryancassell.bclauncher.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledAppsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageSource = FakePackageSource()

    private fun TestScope.repository() =
        InstalledAppsRepository(context, packageSource, UnconfinedTestDispatcher(testScheduler))

    @Test
    fun listsAppsByLabelIgnoringCase() = runTest {
        packageSource.setApps(fakeApp("Zebra"), fakeApp("apple"), fakeApp("Banana"))

        val apps = repository().apps.first()

        assertEquals(listOf("apple", "Banana", "Zebra"), apps.map { it.label })
    }

    @Test
    fun leavesThisAppOut() = runTest {
        packageSource.setApps(fakeApp("BC Launcher", packageName = context.packageName), fakeApp("YouTube"))

        val apps = repository().apps.first()

        assertEquals(listOf("YouTube"), apps.map { it.label })
    }

    @Test
    fun emitsAnEmptyListWhenNoAppsAreInstalled() = runTest {
        val apps = repository().apps.first()

        assertEquals(emptyList<InstalledApp>(), apps)
    }

    @Test
    fun updatesWhenAnAppIsInstalledOrRemoved() = runTest {
        packageSource.setApps(fakeApp("YouTube"))
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository().apps.collect { apps -> emissions += apps.map { it.label } }
        }

        packageSource.setApps(fakeApp("YouTube"), fakeApp("Netflix"))
        packageSource.setApps(fakeApp("Netflix"))

        assertEquals(listOf(listOf("YouTube"), listOf("Netflix", "YouTube"), listOf("Netflix")), emissions)
    }
}
