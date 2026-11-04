package io.github.bryancassell.bclauncher.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * The app's Hilt graph builds the repositories with the IO dispatcher, the app's DataStore files and, in tests, the
 * fake sources.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DataModuleTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: InstalledAppsRepository

    @Inject
    lateinit var packageSource: FakePackageSource

    @Inject
    lateinit var homeButtonRepository: HomeButtonRepository

    @Inject
    lateinit var accessibilitySource: FakeAccessibilitySource

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @Test
    fun injectedRepositoryListsTheSourcesApps() = runTest {
        packageSource.setApps(fakeApp("YouTube"))

        val apps = repository.apps.first()

        assertEquals(listOf("YouTube"), apps.map { it.label })
    }

    @Test
    fun injectedRepositorySavesTheOrderInDataStoresDirectory() = runTest {
        val apple = fakeApp("Apple")
        val youTube = fakeApp("YouTube")
        packageSource.setApps(apple, youTube)

        repository.saveOrder(listOf(youTube.component, apple.component))

        assertEquals(listOf("YouTube", "Apple"), repository.apps.first().map { it.label })
        assertTrue(context.filesDir.resolve("datastore/app_order.json").exists())
    }

    @Test
    fun injectedRepositoryReplacesACorruptOrderFile() = runTest {
        val youTube = fakeApp("YouTube")
        packageSource.setApps(youTube, fakeApp("Apple"))
        val file = context.dataStoreFile("app_order.json").apply { parentFile?.mkdirs() }
        file.writeText("not json")

        assertEquals(listOf("Apple", "YouTube"), repository.apps.first().map { it.label })
        assertTrue(ShadowLog.getLogsForTag("DataModule").single().throwable is CorruptionException)
        assertNotEquals("not json", file.readText())

        // Saving works afterwards, rather than failing on the corrupt file each time.
        repository.saveOrder(listOf(youTube.component))
        assertEquals(listOf("YouTube", "Apple"), repository.apps.first().map { it.label })
    }

    @Test
    fun injectedHomeButtonRepositoryReadsTheSourcesServices() = runTest {
        accessibilitySource.setEnabledServices(homeButtonService(context))

        assertTrue(homeButtonRepository.isOn.first())
    }
}
