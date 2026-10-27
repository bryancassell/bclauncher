package io.github.bryancassell.bclauncher.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun mergesChangesThatArriveDuringALoadIntoOneMoreLoad() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(dispatcher) {
            InstalledAppsRepository(context, packageSource, dispatcher).apps
                .collect { apps -> emissions += apps.map { it.label } }
        }
        runCurrent()

        // Three changes in a row, as an app update sends. The first starts a load, and the two that arrive before it
        // runs are merged into one more, so there are two loads after the first rather than three.
        packageSource.setApps(fakeApp("YouTube"))
        packageSource.setApps(fakeApp("YouTube"), fakeApp("Netflix"))
        packageSource.setApps(fakeApp("Netflix"))
        runCurrent()

        assertEquals(3, packageSource.loads)
        assertEquals(listOf(emptyList(), listOf("Netflix"), listOf("Netflix")), emissions)
    }

    @Test
    fun keepsAppsWithTheSameLabelInTheSameOrder() = runTest {
        val first = fakeApp("TV", packageName = "com.example.a")
        val second = fakeApp("TV", packageName = "com.example.b")
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository().apps.collect { apps -> emissions += apps.map { it.component.packageName } }
        }

        packageSource.setApps(second, first)
        packageSource.setApps(first, second)

        assertEquals(listOf("com.example.a", "com.example.b"), emissions[1])
        assertEquals(emissions[1], emissions[2])
    }
}
