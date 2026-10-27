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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class InstalledAppsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageSource = FakePackageSource()
    private val defaultLocale = Locale.getDefault()

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

        // An app update sends several changes in a row. Two arrive while the load for the first is running.
        packageSource.duringNextLoad {
            packageSource.setApps(fakeApp("YouTube"), fakeApp("Netflix"))
            packageSource.setApps(fakeApp("Netflix"))
        }
        packageSource.setApps(fakeApp("YouTube"))
        runCurrent()

        assertEquals(3, packageSource.loads)
        assertEquals(listOf(emptyList(), listOf("YouTube"), listOf("Netflix")), emissions)
    }

    @Test
    fun sortsInTheLanguageOfEachLoad() = runTest {
        val apps = arrayOf(fakeApp("Zebra"), fakeApp("Äpple", packageName = "com.example.apple"))
        Locale.setDefault(Locale.ENGLISH)
        packageSource.setApps(*apps)
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository().apps.collect { emissions += it.map { app -> app.label } }
        }

        // Swedish sorts Ä as its own letter, after Z.
        Locale.setDefault(Locale.forLanguageTag("sv-SE"))
        packageSource.setApps(*apps)

        assertEquals(listOf(listOf("Äpple", "Zebra"), listOf("Zebra", "Äpple")), emissions)
    }

    @After
    fun restoreTheDefaultLocale() {
        Locale.setDefault(defaultLocale)
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
