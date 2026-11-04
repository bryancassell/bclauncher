package io.github.bryancassell.bclauncher.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The app's Hilt graph builds the repositories with the IO dispatcher and, in tests, the fake sources. */
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

    @Inject
    lateinit var systemScreensRepository: SystemScreensRepository

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
    fun injectedHomeButtonRepositoryReadsTheSourcesServices() = runTest {
        accessibilitySource.setEnabledServices(homeButtonService(context))

        assertTrue(homeButtonRepository.isOn.first())
    }

    @Test
    fun injectedSystemScreensRepositoryOpensTheSourcesHomeApp() = runTest {
        packageSource.setHome("com.example.home")

        assertEquals(
            "com.example.home",
            systemScreensRepository.googleTvHomeIntent.first()?.`package`,
        )
    }
}
